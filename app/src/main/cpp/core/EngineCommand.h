#pragma once

#include <cstdint>

enum class CommandType : uint8_t {
    NONE = 0,
    TRANSPORT_PLAY,
    TRANSPORT_PAUSE,
    TRANSPORT_STOP,
    TRANSPORT_SEEK,
    TRANSPORT_SET_BPM,
    TRANSPORT_SET_LOOP,
    TRACK_SET_VOLUME,
    TRACK_SET_PAN,
    TRACK_SET_MUTE,
    TRACK_SET_SOLO,
    TRACK_SET_ARM,
    TRACK_SET_SEND,
    DEVICE_SET_PARAM,
    DEVICE_SET_BYPASS,
    INSTRUMENT_NOTE_ON,
    INSTRUMENT_NOTE_OFF,
    INSTRUMENT_ALL_NOTES_OFF,
    INSTRUMENT_PITCH_BEND,
    INSTRUMENT_MOD_WHEEL,
    DRUM_TRIGGER_PAD,
    CLIP_LAUNCH,
    CLIP_STOP,
    SCENE_LAUNCH
};

/**
 * Fixed-size 32-byte POD Command Struct for lock-free SPSC Command Queue.
 * Real-time audio thread pops and executes commands deterministically.
 */
struct EngineCommand {
    CommandType type{CommandType::NONE};
    int16_t trackIndex{-1};
    int16_t deviceIndex{-1};
    int16_t paramId{-1};
    int16_t noteNumber{0};
    float floatValue1{0.0f};
    float floatValue2{0.0f};
    int32_t intValue1{0};
    int32_t intValue2{0};
};
