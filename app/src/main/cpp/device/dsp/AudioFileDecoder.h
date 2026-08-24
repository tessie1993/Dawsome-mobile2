#pragma once

#include <string>
#include <vector>

// Assuming dr_libs is in include path
// #define DR_WAV_IMPLEMENTATION
// #include "dr_wav.h"
// #define DR_FLAC_IMPLEMENTATION
// #include "dr_flac.h"

namespace dsp {

/**
 * Wrapper for dr_libs (dr_wav, dr_flac).
 * Replaces libsndfile and libaudiofile.
 */
class AudioFileDecoder {
public:
    AudioFileDecoder() = default;

    bool loadFile(const std::string& filePath) {
        // e.g. using drwav_init_file and drwav_read_pcm_frames_f32
        return false;
    }

    const float* getBuffer() const { return audioData_.data(); }
    size_t getNumFrames() const { return numFrames_; }
    unsigned int getSampleRate() const { return sampleRate_; }
    unsigned int getChannels() const { return channels_; }

private:
    std::vector<float> audioData_;
    size_t numFrames_{0};
    unsigned int sampleRate_{0};
    unsigned int channels_{0};
};

} // namespace dsp
