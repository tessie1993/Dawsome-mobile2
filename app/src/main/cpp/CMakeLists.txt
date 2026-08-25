# libdawcore - native engine build (blueprint D13).
#
# Two configurations:
#   ANDROID: the real engine library, linked into the app by the
#            externalNativeBuild block in app/build.gradle.kts. Oboe arrives
#            through the prefab dependency (com.google.oboe:oboe).
#   host:    dawcore_hostcheck - full-engine compile verification off-device
#            (CONTRACTS.md host-compilability rule). Android-only APIs are
#            satisfied by host_shims/ (Oboe) and the JDK's jni.h; the NDK
#            media AAC path is already #ifdef __ANDROID__.

cmake_minimum_required(VERSION 3.22)
project(dawcore CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(DAWCORE_SOURCES
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

if(ANDROID)
    add_library(dawcore SHARED ${DAWCORE_SOURCES})
    target_include_directories(dawcore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

    find_package(oboe REQUIRED CONFIG)
    target_link_libraries(dawcore
        PRIVATE
        oboe::oboe
        android
        log
        mediandk      # AMediaExtractor/AMediaCodec (AAC decode)
        )
else()
    # Host verification: compile EVERY translation unit. Warnings are the
    # reviewers we don't have on-device; keep them loud.
    find_package(JNI REQUIRED)
    add_library(dawcore_hostcheck STATIC ${DAWCORE_SOURCES})
    target_include_directories(dawcore_hostcheck PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/host_shims
        ${JNI_INCLUDE_DIRS}
        )
    target_compile_options(dawcore_hostcheck PRIVATE -Wall -Wextra)
endif()
