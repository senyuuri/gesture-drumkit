/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <utils/logging.h>
#include <thread>
#include <cmath>

#include "DrumMachine.h"

DrumMachine::DrumMachine(AAssetManager &assetManager): mAssetManager(assetManager) {
}

/**
 * Initialise DrumMachine, must always be called first
 */
void DrumMachine::init(){
    std::vector<std::string> asset_list = { "clap.wav", "finger-cymbal.wav", "hihat.wav", "kick.wav", "rim.wav",
                                            "scratch.wav", "snare.wav", "splash.wav", "metronome.wav"};
    for(std::string wav_file : asset_list){
        // Load the RAW PCM data files for both the sample sound and backing track into memory.
        std::shared_ptr<AAssetDataSource> mSampleSource(AAssetDataSource::newFromAssetManager(mAssetManager,
                                                                                              wav_file.c_str(),
                                                                                              oboe::ChannelCount::Stereo));
        if (mSampleSource == nullptr){
            LOGE("Could not load source data for kick sound");
            return;
        }
        std::shared_ptr<Player> mSamplePlayer = std::make_shared<Player>(mSampleSource);
        mPlayerList.push_back(mSamplePlayer);
        // Add the sample sounds to a mixer so that they can be played together
        // simultaneously using a single audio stream.
        mMixer.addTrack(mSamplePlayer);
    }
}

/**
 * Start playback from a given position
 *
 * @param tempo - playback speed, measured in beats per minute(bpm)
 * @param beatIdx - position of the starting beat
 */
void DrumMachine::start(int tempo, int beatIdx) {
    // Start the drum machine
    // Note: must call stop() first before calling start() for a second time

    // Create a builder
    AudioStreamBuilder builder;
    builder.setFormat(AudioFormat::I16);
    builder.setChannelCount(2);
    builder.setSampleRate(kSampleRateHz);
    builder.setCallback(this);
    builder.setPerformanceMode(PerformanceMode::LowLatency);
    builder.setSharingMode(SharingMode::Exclusive);

    // Initialise tempo, starting beat etc.
    setTempo(tempo);
    setBeat(beatIdx);
    mMetronomeOnly = false;
    refreshLoop();

    Result result = builder.openStream(&mAudioStream);
    if (result != Result::OK){
        LOGE("Failed to open stream. Error: %s", convertToText(result));
    }

    // Reduce stream latency by setting the buffer size to a multiple of the burst size
    auto setBufferSizeResult = mAudioStream->setBufferSizeInFrames(
            mAudioStream->getFramesPerBurst() * kBufferSizeInBursts);
    if (setBufferSizeResult != Result::OK){
        LOGW("Failed to set buffer size. Error: %s", convertToText(setBufferSizeResult.error()));
    }

    // Start mixer
    result = mAudioStream->requestStart();
    if (result != Result::OK){
        LOGE("Failed to start stream. Error: %s", convertToText(result));
    }
}

/**
 * Process beat events at the beginning of a loop
 */
void DrumMachine::refreshLoop() {
    // process all pending events and initialise a loop
    mPlayerEvents = {};
    processUpdateEvents();
    preparePlayerEvents();
    printBeatMap();
}

/**
 * Stop playback close the audio stream
 */
void DrumMachine::stop(){

    if (mAudioStream != nullptr){
        mAudioStream->close();
        delete mAudioStream;
        mAudioStream = nullptr;
    }
}

/**
 * Start playback from the beginning(beat 0), metronome-only mode
 * @param tempo - playback speed, measured in beats per minute(bpm)
 */
void DrumMachine::startMetronome(int tempo) {
    // Play only the metronome track
    mMetronomeOnly = true;
    start(tempo, 0);
}

/**
 * Stop playback which was in metronome-only mode
 */
void DrumMachine::stopMetronome() {
    // Stop the metronome playback
    mMetronomeOnly = false;
    stop();
}

/**
 * Update drum machine tempo
 * @param tempo - playback speed, measured in beats per minute(bpm)
 */
void DrumMachine::setTempo(int tempo) {
    mTempo = tempo;
}

/**
 * Set starting beat for the next playback
 * @param beatIdx
 */
void DrumMachine::setBeat(int beatIdx){
    if (beatIdx < 0 || beatIdx >= kTotalBeat) {
        beatIdx = 0;
    }
    // save beat index for calculation later
    mBeatStartIndex = beatIdx;
    int64_t frameNum = quantizeBeatIdx(beatIdx);
    mCurrentFrame = frameNum;
    // LOGD("setBeat: beat %d => mCurrentFrame %lld", beatIdx, frameNum);
}

/**
 * Clear all beats on a given track
 *
 * @param track_idx - index of track to be reset
 */
void DrumMachine::resetTrack(int trackIdx) {
    for (int i = 0; i < kTotalTrack; i++) {
        mBeatMap[trackIdx][i] = 0;
    }
}

/**
 * Clear all beats on all tracks
 */
void DrumMachine::resetAll() {
    for (int i = 0; i < kTotalTrack; i++) {
        for (int j = 0; j < kTotalBeat; j++) {
            mBeatMap[i][j] = 0;
        }
    }
}

/**
 * Quantizes frame number & rounds beat to the right value if required
 *
 * Quantization: after conversion, the beat index is rounded to the nearest integer [0, kTotalBeat)
 *
 * @param frameNum - index of frame
 * @return index of beat
 */
int DrumMachine::getBeatIdx(int64_t frameNum) {
    /* Return the beat idx of a given frameNum, after quantization*/
    float framePerBeat = round((60.0f / mTempo) * kSampleRateHz);
    int beatIdx = static_cast<int>(round((float)frameNum / framePerBeat));
    return beatIdx % kTotalBeat;
}

/**
 * Add a beat to a given track, at the current playback position
 *
 * The function comprises of two steps:
 *  1) play the beat sample immediately
 *  2) enqueue the event to mUpdateEvents queue and update mBeatMap at
 *     the next round of the loop
 *
 * @param track_idx - index of track
 * @return the index of beat to be inserted
 */
int DrumMachine::insertBeat(int trackIdx) {
    // TODO check audio stream state
    mPlayerList[trackIdx]->setPlaying(true);
    // update beat map at the end of the loop
    int64_t currentFrame = mCurrentFrame;
    mUpdateEvents.push(std::make_tuple(currentFrame, trackIdx));
    return getBeatIdx(currentFrame);
}

/**
 * Process events in mUpdteEvents queue and add beats to mBeatMap accordingly
 */
void DrumMachine::processUpdateEvents() {
    std::tuple<int64_t, int> nextUpdateEvent;
    int framePerBeat =  static_cast<int>(round((60.0f / mTempo) * kSampleRateHz));
    LOGD("[processUpdateEvent] using frames_per_beat: %d)", framePerBeat);


    while (!mUpdateEvents.empty()) {
        nextUpdateEvent = mUpdateEvents.front();
        int64_t frameNum = std::get<0>(nextUpdateEvent);
        int trackIdx = std::get<1>(nextUpdateEvent);
        int beatIdx = getBeatIdx(frameNum);
        mBeatMap[trackIdx][beatIdx] = 1;
        mUpdateEvents.pop();
        // LOGD("[processUpdateEvent] event(%lld,%d)-> beat_idx: %d", frameNum, trackIdx, beatIdx);
    }
}

/**
 * Convert the index of beat to the index of frame
 *
 * @param beatIdx - index of  beat
 * @return index of frame
 */
int64_t DrumMachine::quantizeBeatIdx(int beatIdx) {
    /* Return the frame number of a given beat */
    if (beatIdx < 0 || beatIdx >= kTotalBeat) {
        beatIdx = 0;
    }
    float framePerBeat = round((60.0f / mTempo) * kSampleRateHz);
    int64_t frameNum = static_cast<int64_t>(beatIdx * framePerBeat);
    return frameNum;
}

/**
 * Read mBeatMap and insert beats to the playback queue
 */
void DrumMachine::preparePlayerEvents(){
    // Add the audio frame numbers on which the sample sound should be played to the sample event queue.
    // For example the tempo is 60 beats per minute, which is 1 beats per second. At a sample
    // rate of 48000 frames per second this means a beat occurs every 48000 frames, starting at
    // zero.
    int frame_per_beat =  static_cast<int>(round((60.0f / mTempo) * kSampleRateHz));

    for (int j=mBeatStartIndex; j < kTotalBeat; j++){
        for (int i=0; i < kTotalTrack; i++){
            if (mBeatMap[i][j] == 1 && !mMetronomeOnly) {
                mPlayerEvents.push(std::make_tuple((int64_t) j * frame_per_beat, i));
                // DEBUG
                // LOGD("Add PlayerEvent: ch%d, beat%d, frame %ld", j, i, (int64_t) j * frame_per_beat);
            }
        }
        // always add metronome events
        mPlayerEvents.push(std::make_tuple((int64_t) j * frame_per_beat, kMetronomeTrackIdx));
        // DEBUG
        // LOGD("Add PlayerEvent: ch-metro, beat%d, frame %ld", j, (int64_t) j * frame_per_beat);
    }
    // reset starting beat in the next round so we can start from the beginning again
    if (mBeatStartIndex != 0){
        mBeatStartIndex = 0;
    }
}

/**
 * Print out beat arragements in all channels
 */
void DrumMachine::printBeatMap(){
    LOGD("[mBeatMap]");
    for (int i=0; i < (kTotalTrack - 1); i++){
        std::string output = "ch" + std::to_string(i);
        for (int j=0; j < kTotalBeat; j++){
            if (mBeatMap[i][j] == 0){
                output += "[ ]";
            } else {
                output += "[x]";
            }
        }
        LOGD("%s", output.c_str());
    }
}

/**
 * Turn on/off metronome-only playback mode
 */
void DrumMachine::toggleMetronome() {
    mMetronomeOn = !mMetronomeOn;
}

/**
 * A callback function for the audio driver to fetch the next numFrames of audio to be played
 *
 * @param oboeStream
 * @param audioData
 * @param numFrames
 * @return keep the audio stream open
 */
DataCallbackResult DrumMachine::onAudioReady(AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    std::tuple<int64_t, int> nextClapEvent;
    int32_t loop_duration = kTotalBeat * static_cast<int>(round((60.0f / mTempo) * kSampleRateHz));

    for (int i = 0; i < numFrames; ++i) {

        // DEBUG
//        int64_t tmpFrame = mCurrentFrame;
//        std::tuple<int64_t, int> tmpClapEvent;
//        if (tmpFrame % kSampleRateHz == 0) {
//            tmpClapEvent = mPlayerEvents.front();
//            int64_t nextFrame = std::get<0>(tmpClapEvent);
//            int nextTrack = std::get<1>(tmpClapEvent);
//            LOGD("ON_BEAT %ld, nextClapEvent(%d, %ld)", tmpFrame, nextTrack, nextFrame);
//        }

        // play sample sounds
        while (!mPlayerEvents.empty() && mCurrentFrame == std::get<0>(mPlayerEvents.front())) {
            nextClapEvent = mPlayerEvents.front();
            int trackIdx = std::get<1>(nextClapEvent);

            if ((trackIdx != kMetronomeTrackIdx) || (trackIdx == kMetronomeTrackIdx && mMetronomeOn)) {
                // DEBUG
                // LOGD("onAudioReady - Play ch%d at %ld", trackIdx, tmpFrame);
                mPlayerList[std::get<1>(nextClapEvent)]->setPlaying(true);
                mPlayerEvents.pop();
            }
        }

        mMixer.renderAudio(static_cast<int16_t*>(audioData)+(kChannelCount*i), 1);
        mCurrentFrame++;

        if ( mCurrentFrame > loop_duration ) {
            mCurrentFrame = 0;
            refreshLoop();
        }

    }
    return DataCallbackResult::Continue;
}

