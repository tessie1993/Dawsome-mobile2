#pragma once

// Assertion for engine invariants. Active only in debug/host builds: in
// release the expression is not evaluated, so an assert can never introduce
// work or a syscall on the audio thread. Never assert on conditions that can
// occur in healthy production use (ring full, cache miss) - those are counted
// and reported through telemetry instead.

#if defined(NDEBUG)
  #define DAW_RT_ASSERT(expr) ((void)0)
#else
  #include <cassert>
  #define DAW_RT_ASSERT(expr) assert(expr)
#endif
