# libdawcore - native engine build (blueprint D13).
#
# DELIBERATELY NOT WIRED INTO GRADLE YET: the app builds UI-only while the
# engine grows class-by-class (no-compile phase). When the engine is ready to
# link, add an externalNativeBuild block to app/build.gradle.kts pointing here.
#
# Two targets:
#   dawcore      - the Android engine library (requires ANDROID toolchain)
#   dawcore_host - hostside target over core/ + dsp/ only (always configurable;
#                  CONTRACTS.md host-compilability rule). Tests attach here
#                  later and run only on explicit request.

cmake_minimum_required(VERSION 3.22)
project(dawcore CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# core/ is header-only so far; an INTERFACE target carries include paths and
# gains sources as .cpp translation units appear.
add_library(dawcore_core INTERFACE)
target_include_directories(dawcore_core INTERFACE ${CMAKE_CURRENT_SOURCE_DIR})

if(ANDROID)
    add_library(dawcore SHARED
        device/RegisterBuiltins.cpp
        engine/AudioEngine.cpp
        engine/EngineModel.cpp
        engine/OboeDriver.cpp
        graph/GraphBuilder.cpp
        graph/PlaybackGraph.cpp
        jni/NativeAudioBridge.cpp
        media/AudioFileDecoder.cpp
        media/SampleCache.cpp
        )
    target_link_libraries(dawcore PRIVATE dawcore_core)
    # Oboe arrives via prefab dependency when Gradle wiring is enabled:
    # find_package(oboe REQUIRED CONFIG); target_link_libraries(dawcore PRIVATE oboe::oboe log)
    # Media prerequisites at the same milestone:
    #   - vendor dr_wav.h/dr_flac.h/dr_mp3.h under third_party/dr_libs/
    #     (PD/MIT-0; AudioFileDecoder.cpp is their single implementation TU)
    #   - target_link_libraries(dawcore PRIVATE mediandk)  # NDK AAC decode
else()
    add_library(dawcore_host INTERFACE)
    target_link_libraries(dawcore_host INTERFACE dawcore_core)
endif()
