#include "AudioFileDecoder.h"

#include <cstdio>
#include <cstring>

// Single implementation TU for the vendored dr_libs single-file decoders
// (third_party/dr_libs/, PD/MIT-0; headers land with the first compile
// milestone - this file is written against their stable public API).
#define DR_WAV_IMPLEMENTATION
#include "../third_party/dr_libs/dr_wav.h"
#define DR_FLAC_IMPLEMENTATION
#include "../third_party/dr_libs/dr_flac.h"
#define DR_MP3_IMPLEMENTATION
#include "../third_party/dr_libs/dr_mp3.h"

#ifdef __ANDROID__
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#endif

namespace daw {

namespace {
constexpr int64_t kMaxFrames = int64_t(1) << 31;   // ~12h stereo: refuse absurdity
}

// ---- format sniff (content magic, never the extension) ----------------------

AudioFileDecoder::Sniff AudioFileDecoder::sniff(const char* path) {
    std::FILE* f = std::fopen(path, "rb");
    if (f == nullptr) return Sniff::Unknown;
    unsigned char h[12] = {0};
    const size_t n = std::fread(h, 1, sizeof h, f);
    std::fclose(f);
    if (n < 12) return Sniff::Unknown;

    if ((std::memcmp(h, "RIFF", 4) == 0 || std::memcmp(h, "RF64", 4) == 0) &&
        std::memcmp(h + 8, "WAVE", 4) == 0)
        return Sniff::Wav;
    if (std::memcmp(h, "fLaC", 4) == 0) return Sniff::Flac;
    if (std::memcmp(h + 4, "ftyp", 4) == 0) return Sniff::Mp4;
    if (std::memcmp(h, "ID3", 3) == 0) return Sniff::Mp3;
    if (h[0] == 0xFF) {
        // Frame sync: ADTS AAC is 0xFFF with layer bits 00; MP3 keeps layer.
        if ((h[1] & 0xF6) == 0xF0) return Sniff::Mp4;   // extractor handles ADTS
        if ((h[1] & 0xE0) == 0xE0) return Sniff::Mp3;
    }
    return Sniff::Unknown;
}

// ---- planar conversion ------------------------------------------------------

DecodeResult AudioFileDecoder::fromInterleaved(const float* frames, int64_t frameCount,
                                               int srcChannels, double rate) {
    DecodeResult r;
    if (frames == nullptr || frameCount <= 0 || srcChannels <= 0 || rate <= 0.0) {
        r.error = "empty or malformed audio stream";
        return r;
    }
    if (frameCount > kMaxFrames) {
        r.error = "file too long";
        return r;
    }
    const int outCh = srcChannels >= 2 ? 2 : 1;   // cap: first two channels
    r.sampleRate = rate;
    r.channels = outCh;
    r.frames = frameCount;
    r.planar.resize(size_t(outCh) * size_t(frameCount));
    for (int c = 0; c < outCh; ++c) {
        float* dst = r.planar.data() + size_t(c) * size_t(frameCount);
        const float* src = frames + c;
        for (int64_t i = 0; i < frameCount; ++i)
            dst[i] = src[size_t(i) * size_t(srcChannels)];
    }
    r.ok = true;
    r.error = "";
    return r;
}

// ---- dr_libs paths ----------------------------------------------------------

DecodeResult AudioFileDecoder::decodeWav(const char* path) {
    unsigned int channels = 0, rate = 0;
    drwav_uint64 frames = 0;
    float* pcm = drwav_open_file_and_read_pcm_frames_f32(
        path, &channels, &rate, &frames, nullptr);
    if (pcm == nullptr) {
        DecodeResult r;
        r.error = "WAV decode failed";
        return r;
    }
    DecodeResult r = fromInterleaved(pcm, int64_t(frames), int(channels), double(rate));
    drwav_free(pcm, nullptr);
    return r;
}

DecodeResult AudioFileDecoder::decodeFlac(const char* path) {
    unsigned int channels = 0, rate = 0;
    drflac_uint64 frames = 0;
    float* pcm = drflac_open_file_and_read_pcm_frames_f32(
        path, &channels, &rate, &frames, nullptr);
    if (pcm == nullptr) {
        DecodeResult r;
        r.error = "FLAC decode failed";
        return r;
    }
    DecodeResult r = fromInterleaved(pcm, int64_t(frames), int(channels), double(rate));
    drflac_free(pcm, nullptr);
    return r;
}

DecodeResult AudioFileDecoder::decodeMp3(const char* path) {
    drmp3_config cfg{};
    drmp3_uint64 frames = 0;
    float* pcm = drmp3_open_file_and_read_pcm_frames_f32(
        path, &cfg, &frames, nullptr);
    if (pcm == nullptr) {
        DecodeResult r;
        r.error = "MP3 decode failed";
        return r;
    }
    DecodeResult r = fromInterleaved(pcm, int64_t(frames), int(cfg.channels),
                                     double(cfg.sampleRate));
    drmp3_free(pcm, nullptr);
    return r;
}

// ---- AAC / M4A via platform MediaCodec (Android only) -----------------------

#ifdef __ANDROID__
DecodeResult AudioFileDecoder::decodeAac(const char* path) {
    DecodeResult r;
    AMediaExtractor* ex = AMediaExtractor_new();
    if (AMediaExtractor_setDataSource(ex, path) != AMEDIA_OK) {
        AMediaExtractor_delete(ex);
        r.error = "extractor could not open file";
        return r;
    }

    // First audio track wins.
    AMediaFormat* fmt = nullptr;
    const char* mime = nullptr;
    const size_t tracks = AMediaExtractor_getTrackCount(ex);
    for (size_t t = 0; t < tracks; ++t) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(ex, t);
        const char* m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) &&
            std::strncmp(m, "audio/", 6) == 0) {
            AMediaExtractor_selectTrack(ex, t);
            fmt = f;
            mime = m;
            break;
        }
        AMediaFormat_delete(f);
    }
    if (fmt == nullptr) {
        AMediaExtractor_delete(ex);
        r.error = "no audio track";
        return r;
    }

    int32_t rate = 0, channels = 0;
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_SAMPLE_RATE, &rate);
    AMediaFormat_getInt32(fmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);

    AMediaCodec* codec = AMediaCodec_createDecoderByType(mime);
    if (codec == nullptr ||
        AMediaCodec_configure(codec, fmt, nullptr, nullptr, 0) != AMEDIA_OK ||
        AMediaCodec_start(codec) != AMEDIA_OK) {
        if (codec != nullptr) AMediaCodec_delete(codec);
        AMediaFormat_delete(fmt);
        AMediaExtractor_delete(ex);
        r.error = "decoder start failed";
        return r;
    }

    std::vector<float> interleaved;
    bool inputDone = false, outputDone = false;
    bool codecError = false;
    int dryPolls = 0;                       // EOS-loss / stall belt
    while (!outputDone && !codecError && dryPolls < 10000) {
        if (!inputDone) {
            const ssize_t inIdx = AMediaCodec_dequeueInputBuffer(codec, 10000);
            if (inIdx >= 0) {
                size_t cap = 0;
                uint8_t* buf = AMediaCodec_getInputBuffer(codec, size_t(inIdx), &cap);
                const ssize_t n = AMediaExtractor_readSampleData(ex, buf, cap);
                if (n < 0) {
                    AMediaCodec_queueInputBuffer(codec, size_t(inIdx), 0, 0, 0,
                                                 AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                } else {
                    AMediaCodec_queueInputBuffer(
                        codec, size_t(inIdx), 0, size_t(n),
                        uint64_t(AMediaExtractor_getSampleTime(ex)), 0);
                    AMediaExtractor_advance(ex);
                }
            }
        }
        AMediaCodecBufferInfo info;
        const ssize_t outIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, 10000);
        if (outIdx >= 0) {
            if (info.size > 0) {
                size_t cap = 0;
                const uint8_t* buf =
                    AMediaCodec_getOutputBuffer(codec, size_t(outIdx), &cap);
                // NDK decoders emit 16-bit interleaved PCM.
                const int16_t* s16 =
                    reinterpret_cast<const int16_t*>(buf + info.offset);
                const size_t samples = size_t(info.size) / sizeof(int16_t);
                const size_t base = interleaved.size();
                interleaved.resize(base + samples);
                for (size_t i = 0; i < samples; ++i)
                    interleaved[base + i] =
                        static_cast<float>(s16[i]) * (1.0f / 32768.0f);
            }
            AMediaCodec_releaseOutputBuffer(codec, size_t(outIdx), false);
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) outputDone = true;
            dryPolls = 0;
        } else if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* of = AMediaCodec_getOutputFormat(codec);
            AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_SAMPLE_RATE, &rate);
            AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);
            AMediaFormat_delete(of);
        } else if (outIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER ||
                   outIdx == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            ++dryPolls;
        } else {
            codecError = true;              // real AMEDIA_ERROR_*
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaFormat_delete(fmt);
    AMediaExtractor_delete(ex);

    if (!outputDone) {                       // stalled or errored: refuse partials
        r.error = "AAC decode failed mid-stream";
        return r;
    }
    if (channels <= 0 || rate <= 0 || interleaved.empty()) {
        r.error = "AAC produced no audio";
        return r;
    }
    return fromInterleaved(interleaved.data(),
                           int64_t(interleaved.size() / size_t(channels)),
                           int(channels), double(rate));
}
#else
DecodeResult AudioFileDecoder::decodeAac(const char*) {
    DecodeResult r;
    r.error = "AAC decode requires Android MediaCodec";
    return r;
}
#endif

// ---- entry ------------------------------------------------------------------

DecodeResult AudioFileDecoder::decodeFile(const char* path) {
    if (path == nullptr || path[0] == '\0') {
        DecodeResult r;
        r.error = "empty path";
        return r;
    }
    switch (sniff(path)) {
        case Sniff::Wav:  return decodeWav(path);
        case Sniff::Flac: return decodeFlac(path);
        case Sniff::Mp3:  return decodeMp3(path);
        case Sniff::Mp4:  return decodeAac(path);
        case Sniff::Unknown: break;
    }
    DecodeResult r;
    r.error = "unrecognized audio container";
    return r;
}

} // namespace daw
