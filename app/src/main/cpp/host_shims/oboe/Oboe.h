#pragma once

// HOST-ONLY Oboe API shim - compile verification of OboeDriver off-device.
//
// Mirrors the slice of the real Oboe 1.5 API the engine uses, with the real
// signatures (raw-pointer callbacks, chainable builder returning
// AudioStreamBuilder*, ResultWithValue<T> accessors) so `override` checks
// and call sites typecheck identically to the Android build. Behavior is
// inert: openStream() fails, so a host process simply gets no audio device.
// The Android build NEVER sees this header - it uses the prefab Oboe; this
// directory is only on the host target's include path (CMakeLists).

#include <cstdint>
#include <ctime>
#include <memory>

namespace oboe {

constexpr int32_t kUnspecified = 0;

// Values mirror real Oboe/AAudio (review finding: -899 is Disconnected,
// Internal is -896 - value-faithful so error handling verifies truthfully).
enum class Result : int32_t {
    OK = 0,
    ErrorDisconnected = -899,
    ErrorInternal = -896,
    ErrorClosed = -869,
};
enum class Direction : int32_t { Output = 0, Input = 1 };
enum class PerformanceMode : int32_t { None = 10, PowerSaving = 11, LowLatency = 12 };
enum class SharingMode : int32_t { Exclusive = 0, Shared = 1 };
enum class AudioFormat : int32_t { Invalid = -1, Unspecified = 0, I16 = 1, Float = 2 };
// Real Oboe's ChannelCount is an UNSCOPED enum (converts to int32_t - that
// is why setChannelCount(int32_t) accepts ChannelCount::Stereo on Android).
enum ChannelCount : int32_t { Unspecified = 0, Mono = 1, Stereo = 2 };
enum class SampleRateConversionQuality : int32_t {
    None = 0, Fastest = 1, Low = 2, Medium = 3, High = 4, Best = 5
};
enum class DataCallbackResult : int32_t { Continue = 0, Stop = 1 };

template <typename T>
class ResultWithValue {
public:
    // Ctor shapes mirror real 1.5: value ctor + IMPLICIT Result ctor, no
    // default ctor (a shim-only default could let host-passing code fail
    // on Android - review finding).
    explicit ResultWithValue(T v) : value_(v), error_(Result::OK), ok_(true) {}
    ResultWithValue(Result e) : error_(e), ok_(false) {}
    explicit operator bool() const { return ok_; }
    T value() const { return value_; }
    Result error() const { return error_; }

private:
    T value_{};
    Result error_ = Result::ErrorInternal;
    bool ok_ = false;
};

struct FrameTimestamp {
    int64_t position = 0;
    int64_t timestamp = 0;
};

class AudioStream;

class AudioStreamDataCallback {
public:
    virtual ~AudioStreamDataCallback() = default;
    virtual DataCallbackResult onAudioReady(AudioStream* stream, void* audioData,
                                            int32_t numFrames) = 0;
};

class AudioStreamErrorCallback {
public:
    virtual ~AudioStreamErrorCallback() = default;
    virtual bool onError(AudioStream*, Result) { return false; }
    virtual void onErrorBeforeClose(AudioStream*, Result) {}
    virtual void onErrorAfterClose(AudioStream*, Result) {}
};

class AudioStream {
public:
    virtual ~AudioStream() = default;

    Result requestStart() { return Result::ErrorInternal; }
    Result requestStop() { return Result::ErrorInternal; }
    Result close() { return Result::OK; }

    // Const-ness mirrors real 1.5 declarations exactly (review finding:
    // const-flipped call sites must fail/pass identically on both builds).
    int32_t getChannelCount() const { return 2; }
    int32_t getSampleRate() const { return 48000; }
    int32_t getFramesPerBurst() { return 192; }             // non-const in 1.5
    int64_t getFramesWritten() { return 0; }
    ResultWithValue<int32_t> setBufferSizeInFrames(int32_t) {
        return ResultWithValue<int32_t>(Result::ErrorInternal);
    }
    ResultWithValue<int32_t> getAvailableFrames() {
        return ResultWithValue<int32_t>(Result::ErrorInternal);
    }
    ResultWithValue<int32_t> read(void*, int32_t, int64_t) {
        return ResultWithValue<int32_t>(Result::ErrorInternal);
    }
    ResultWithValue<double> calculateLatencyMillis() {
        return ResultWithValue<double>(Result::ErrorInternal);
    }
    ResultWithValue<FrameTimestamp> getTimestamp(clockid_t) {
        return ResultWithValue<FrameTimestamp>(Result::ErrorInternal);
    }
    ResultWithValue<int32_t> getXRunCount() const {         // const in 1.5
        return ResultWithValue<int32_t>(Result::ErrorInternal);
    }
};

class AudioStreamBuilder {
public:
    AudioStreamBuilder* setDirection(Direction) { return this; }
    AudioStreamBuilder* setPerformanceMode(PerformanceMode) { return this; }
    AudioStreamBuilder* setSharingMode(SharingMode) { return this; }
    AudioStreamBuilder* setFormat(AudioFormat) { return this; }
    AudioStreamBuilder* setChannelCount(int32_t) { return this; }   // real 1.5: int only
    AudioStreamBuilder* setSampleRate(int32_t) { return this; }
    AudioStreamBuilder* setSampleRateConversionQuality(SampleRateConversionQuality) { return this; }
    AudioStreamBuilder* setDeviceId(int32_t) { return this; }
    AudioStreamBuilder* setBufferCapacityInFrames(int32_t) { return this; }
    AudioStreamBuilder* setDataCallback(AudioStreamDataCallback*) { return this; }
    AudioStreamBuilder* setErrorCallback(AudioStreamErrorCallback*) { return this; }

    Result openStream(std::shared_ptr<AudioStream>& out) {
        out.reset();                       // host: no audio device
        return Result::ErrorInternal;
    }
};

} // namespace oboe
