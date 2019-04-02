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

#ifndef DRUMMACHINE_CONSTANTS_H
#define DRUMMACHINE_CONSTANTS_H

constexpr int kSampleRateHz = 48000; // Fixed sample rate, see README
constexpr int kBufferSizeInBursts = 2; // Use 2 bursts as the buffer size (double buffer)
constexpr int kMaxQueueItems = 64; // Must be power of 2
constexpr int kTotalBeat = 16;
constexpr int kTotalTrack = 9;
constexpr int kMetronomeTrackIdx = 8; // last track reserved for metronome

#endif //DRUM_MACHINE_CONSTANTS_H
