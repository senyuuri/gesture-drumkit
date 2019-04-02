# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.
include_directories(app/src/main/cpp/)


add_library( native-lib
        SHARED

        # main game files
        app/src/main/cpp/native-lib.cpp
        app/src/main/cpp/DrumMachine.cpp

        # audio engine
        app/src/main/cpp/audio/AAssetDataSource.cpp
        app/src/main/cpp/audio/Player.cpp
        app/src/main/cpp/audio/Mixer.cpp

        # utility functions
        app/src/main/cpp/utils/logging.h

        )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( native-lib
        log
        android
        oboe
        )

# Set the path to the Oboe directory.
set (OBOE_DIR ../dependencies/oboe)
add_subdirectory (${OBOE_DIR} ./oboe)

# Make the Oboe public headers available to our app
include_directories (${OBOE_DIR}/include)


# Enable optimization flags: if having problems with source level debugging,
# disable -Ofast ( and debug ), re-enable after done debugging.
target_compile_options(native-lib
        PRIVATE -std=c++14 -Wall -Werror "$<$<CONFIG:RELEASE>:-Ofast>")
