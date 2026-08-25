#pragma once

#include <cstdint>

// Engine-wide compile-time configuration. Values are contractual: see
// docs/spec/CONTRACTS.md (global constants) and ARCHITECTURE_BLUEPRINT.md D4.
// Kotlin mirrors live in EnginePrefs; change here first.

namespace daw {

inline constexpr int kMaxBlock           = 1024;  // frames per processing slice
inline constexpr int kMaxChannels        = 2;     // per node port (stereo engine)

inline constexpr int kMaxTracks          = 64;
inline constexpr int kMaxGroups          = 8;
inline constexpr int kMaxReturns         = 8;
inline constexpr int kMaxDevicesPerChain = 16;
inline constexpr int kMaxChainsPerRack   = 8;
inline constexpr int kMaxRackDepth       = 3;
inline constexpr int kMaxMacros          = 16;

inline constexpr int kVoiceBudget        = 64;    // global, VoiceBudgetLedger-enforced
inline constexpr int kParamTableCap      = 256;   // distinct moved params per block
inline constexpr int kEventRingCap       = 4096;  // EngineMessage slots (power of two)
inline constexpr int kTempoTailCap       = 64;    // RT tempo tail events
inline constexpr int kLaunchWindowScenes = 32;    // per track
inline constexpr int kCorrectiveStretch  = 4;     // outside the RT stretch budget

// Cache-line size used to pad producer/consumer state apart. 64 is correct for
// every ARM64 and x86-64 target we ship or host-build on.
inline constexpr int kCacheLine          = 64;

} // namespace daw
