/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <jni.h>
#include <memory>

#include <android/asset_manager_jni.h>

#include "utils/logging.h"
#include "DrumMachine.h"


extern "C" {

std::unique_ptr<DrumMachine> dmachine;

JNIEXPORT void JNICALL
Java_com_cs4347_drumkit_RecordingActivity_native_1onStart(JNIEnv *env, jobject instance,
                                                                    jobject jAssetManager, jint tempo) {

    AAssetManager *assetManager = AAssetManager_fromJava(env, jAssetManager);
    if (assetManager == nullptr) {
        LOGE("Could not obtain the AAssetManager");
        return;
    }

    dmachine = std::make_unique<DrumMachine>(*assetManager);
    dmachine->start(tempo);
}


JNIEXPORT void JNICALL
Java_com_cs4347_drumkit_RecordingActivity_native_1onStop(JNIEnv *env, jobject instance) {
    dmachine->stop();
}


JNIEXPORT void JNICALL
Java_com_cs4347_drumkit_RecordingActivity_native_1setTempo(JNIEnv *env, jobject instance, jint tempo) {
    dmachine->setTempo(tempo);
}


JNIEXPORT void JNICALL
Java_com_cs4347_drumkit_RecordingActivity_native_1resetTrack(JNIEnv *env, jobject instance, jint track_idx) {
    dmachine->resetTrack(track_idx);
}


JNIEXPORT void JNICALL
Java_com_cs4347_drumkit_RecordingActivity_native_1resetAll(JNIEnv *env, jobject instance) {
    dmachine->resetAll();
}

JNIEXPORT void JNICALL
Java_com_cs4347_drumkit_RecordingActivity_native_1insertBeat(JNIEnv *env, jobject instance, jint track_idx) {
    dmachine->insertBeat(track_idx);
}

JNIEXPORT void JNICALL
Java_com_cs4347_drumkit_RecordingActivity_native_1toggleMetronome(JNIEnv *env, jobject instance) {
    dmachine->toggleMetronome();
}


}