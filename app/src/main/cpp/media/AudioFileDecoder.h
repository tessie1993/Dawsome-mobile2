#pragma once

#include <cstdint>
#include <vector>

// Whole-file decode to float PCM (blueprint media/AudioFileDecoder; license
// decisions D-table: dr_wav/dr_flac/dr_mp3 [PD/MIT-0] for WAV/FLAC/MP3,
// platform MediaCodec for AAC/M4A - no MP3 encode anywhere per D5).
// [non-RT]: allocates and does file IO; callers are the SampleCache loader,
// preview audition, and analysis - never the audio thread.
//
// Format is sniffed from content magic (RIFF/WAVE, fLaC, ID3 / MPEG sync,
// ftyp box), not the file extension. Channels are capped at 2 (first two
// kept); deeper multichannel import is out of scope for the phone product.
// Output is channel-planar to match SampleBuffer.
//
// The dr_libs single-file headers are vendored under third_party/dr_libs/
// (added with the first compile milestone; AudioFileDecoder.cpp is their
// single implementation TU). The AAC path uses NdkMediaExtractor/-Codec and
// compiles only for Android (host builds report unsupported).

namespace daw {

struct DecodeResult {
    bool ok = false;
    const char* error = "";        // static string, valid forever
    double sampleRate = 0.0;       // source rate as decoded
    int channels = 0;              // 1 or 2 after capping
    int64_t frames = 0;
    std::vector<float> planar;     // [ch0 frames][ch1 frames]
};

class AudioFileDecoder {
public:
    static DecodeResult decodeFile(const char* path);

private:
    enum class Sniff { Wav, Flac, Mp3, Mp4, Unknown };
    static Sniff sniff(const char* path);
    static DecodeResult decodeWav(const char* path);
    static DecodeResult decodeFlac(const char* path);
    static DecodeResult decodeMp3(const char* path);
    static DecodeResult decodeAac(const char* path);   // Android MediaCodec
    static DecodeResult fromInterleaved(const float* frames, int64_t frameCount,
                                        int srcChannels, double rate);
};

} // namespace daw
