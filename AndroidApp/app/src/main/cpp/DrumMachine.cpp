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

void DrumMachine::start(int tempo) {
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

    // Create a builder
    AudioStreamBuilder builder;
    builder.setFormat(AudioFormat::I16);
    builder.setChannelCount(2);
    builder.setSampleRate(kSampleRateHz);
    builder.setCallback(this);
    builder.setPerformanceMode(PerformanceMode::LowLatency);
    builder.setSharingMode(SharingMode::Exclusive);

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

    result = mAudioStream->requestStart();
    if (result != Result::OK){
        LOGE("Failed to start stream. Error: %s", convertToText(result));
    }

    setTempo(tempo);
    processUpdateEvents();
    preparePlayerEvents();
    printBeatMap();
}

void DrumMachine::stop(){

    if (mAudioStream != nullptr){
        mAudioStream->close();
        delete mAudioStream;
        mAudioStream = nullptr;
    }
}

void DrumMachine::setTempo(int tempo) {
    mTempo = tempo;
}

void DrumMachine::resetTrack(int track_idx) {
    for (int i = 0; i < kTotalTrack; i++) {
        mBeatMap[track_idx][i] = 0;
    }
}

void DrumMachine::resetAll() {
    for (int i = 0; i < kTotalTrack; i++) {
        for (int j = 0; j < kTotalBeat; j++) {
            mBeatMap[i][j] = 0;
        }
    }
}

void DrumMachine::insertBeat(int track_idx) {
    // TODO check audio stream state
    mPlayerList[track_idx]->setPlaying(true);
    // update beat map at the end of the loop
    int64_t currentFrame = mCurrentFrame;
    mUpdateEvents.push(std::make_tuple(currentFrame, track_idx));
}

void DrumMachine::processUpdateEvents() {
    std::tuple<int64_t, int> nextUpdateEvent;
    int frame_per_beat =  static_cast<int>(round((60.0f / mTempo) * kSampleRateHz));
    LOGD("[processUpdateEvent] using frames_per_beat: %d)", frame_per_beat);

    while (mUpdateEvents.peek(nextUpdateEvent)) {
        int64_t frameNum = std::get<0>(nextUpdateEvent);
        int track_idx = std::get<1>(nextUpdateEvent);
        int beat_idx = quantizeFrameNum(frameNum);
        mBeatMap[track_idx][beat_idx] = 1;
        mUpdateEvents.pop(nextUpdateEvent);
        LOGD("[processUpdateEvent] event(%lld,%d)-> beat_idx: %d", frameNum, track_idx, beat_idx);
    }
}

int DrumMachine::quantizeFrameNum(int64_t frameNum) {
    /* returns the beat idx of a given frameNum, after quantization*/
    float frame_per_beat = round((60.0f / mTempo) * kSampleRateHz);
    int beat_idx = static_cast<int>(round((float)frameNum / frame_per_beat));
    return beat_idx;
}


void DrumMachine::preparePlayerEvents(){
    // Add the audio frame numbers on which the sample sound should be played to the sample event queue.
    // For example the tempo is 60 beats per minute, which is 1 beats per second. At a sample
    // rate of 48000 frames per second this means a beat occurs every 48000 frames, starting at
    // zero.
    int frame_per_beat =  static_cast<int>(round((60.0f / mTempo) * kSampleRateHz));

    for (int j=0; j < kTotalBeat; j++){
        for (int i=0; i < kTotalTrack; i++){
            if (mBeatMap[i][j] == 1) {
                mPlayerEvents.push(std::make_tuple((int64_t) j * frame_per_beat, i));
            }
        }
        // always add metronome events
        mPlayerEvents.push(std::make_tuple((int64_t) j * frame_per_beat, kMetronomeTrackIdx));
    }
}

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

void DrumMachine::toggleMetronome() {
    mMetronomeOn = !mMetronomeOn;
}

DataCallbackResult DrumMachine::onAudioReady(AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    std::tuple<int64_t, int> nextClapEvent;

    int32_t loop_duration = kTotalBeat * static_cast<int>(round((60.0f / mTempo) * kSampleRateHz));

    for (int i = 0; i < numFrames; ++i) {
        // play sample sounds
        while (mPlayerEvents.peek(nextClapEvent) && mCurrentFrame == std::get<0>(nextClapEvent)) {
            int track_idx = std::get<1>(nextClapEvent);
            if ((track_idx != kMetronomeTrackIdx) || (track_idx == kMetronomeTrackIdx && mMetronomeOn)){
                mPlayerList[std::get<1>(nextClapEvent)]->setPlaying(true);
                mPlayerEvents.pop(nextClapEvent);
            }
        }

        mMixer.renderAudio(static_cast<int16_t*>(audioData)+(kChannelCount*i), 1);
        mCurrentFrame++;

        if ( mCurrentFrame > loop_duration ) {
            mCurrentFrame = 0;
            processUpdateEvents();
            preparePlayerEvents();
            printBeatMap();
        }

    }


    return DataCallbackResult::Continue;
}

