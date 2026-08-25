#pragma once

#include <cstring>

#include "../InstrumentNode.h"
#include "../VoiceAllocator.h"
#include "DrumPad.h"

// 16-pad drum rack (blueprint device/instruments/DrumRackDevice,
// DeviceTypeId 6 "16-Pad Drum Rack" - the default project's drum track).
//
// The pad roster, MIDI roots and choke groups mirror Kotlin DrumPadType in
// ENUM ORDER (the wire truth: EngineSync flattens drum steps to these
// pitches). Pitch lookup is first-match, faithfully reproducing the model's
// PERC_2/COWBELL shared pitch 56 (PERC_2 wins; COWBELL is reachable through
// its pad params, not MIDI). Note-offs are IGNORED - pads are one-shots
// that die by their own decay (step clips emit short notes; honoring their
// OFFs would chop every drum).
//
// The rack IS its VoiceGroup (per-pad mono voices, up to 16 sounding):
// ledger admission gates every trigger; chokes (same nonzero group) and
// steals both go through the 5 ms fastRelease. Retrigger hard-cuts the
// pad's own tail (classic drum-machine behavior).
//
// Params: 7 per pad x 16 pads = 112 contract descriptors ("padN.mode" ..
// "padN.choke"). Descriptor defaults are the neutral reset values; the
// MUSICAL kit defaults (researched 808-style settings per pad) live in
// DrumRackShared's initializer - they are state, not contract.

namespace daw {

struct DrumKitDefault {
    uint16_t root;
    float mode, levelDb, tuneSemi, decayMs, tone, shape, choke;
};

// Kotlin DrumPadType enum order: KICK, SNARE, CLAP, HIHAT_CLOSED,
// HIHAT_OPEN, TOM_LOW, TOM_MID, TOM_HIGH, CRASH, RIDE, PERC_1, PERC_2,
// SUB_BOOM, SHAKER, COWBELL, RIMSHOT.
inline constexpr DrumKitDefault kDrumKit[16] = {
    {36, 0, 2.0f, -5.0f, 350.0f, 0.45f, 0.60f, 0},   // Kick: subby, hard drop
    {38, 1, 0.0f, 0.0f, 180.0f, 0.55f, 0.35f, 0},    // Snare: body + noise
    {39, 1, 0.0f, 2.0f, 140.0f, 0.65f, 0.15f, 0},    // Clap: bright noise
    {42, 2, 0.0f, 0.0f, 60.0f, 0.75f, 0.50f, 1},     // Closed hat (choke 1)
    {46, 2, 0.0f, 0.0f, 380.0f, 0.70f, 0.50f, 1},    // Open hat (choke 1)
    {41, 0, 0.0f, -2.0f, 280.0f, 0.40f, 0.45f, 0},   // Low tom
    {45, 0, 0.0f, 0.0f, 240.0f, 0.40f, 0.45f, 0},    // Mid tom
    {48, 0, 0.0f, 2.0f, 200.0f, 0.40f, 0.45f, 0},    // High tom
    {49, 2, -4.0f, -3.0f, 900.0f, 0.55f, 0.65f, 0},  // Crash: long metal
    {51, 2, -2.0f, 1.0f, 700.0f, 0.80f, 0.35f, 0},   // Ride
    {54, 3, 0.0f, 0.0f, 150.0f, 0.50f, 0.35f, 0},    // Perc 1: ring
    {56, 3, 0.0f, 3.0f, 120.0f, 0.50f, 0.60f, 0},    // Perc 2: ring
    {35, 0, 2.0f, -7.0f, 600.0f, 0.25f, 0.70f, 2},   // Sub boom (choke 2)
    {70, 1, -6.0f, 6.0f, 70.0f, 0.90f, 0.05f, 0},    // Shaker: thin noise
    {56, 3, 0.0f, 0.0f, 180.0f, 0.50f, 0.48f, 0},    // Cowbell (pitch shadowed)
    {37, 4, 0.0f, 4.0f, 80.0f, 0.70f, 0.10f, 0},     // Rimshot: bit click
};

struct DrumRackShared {                // seam-3 migrating state body, POD
    DrumPadShared pads[16];

    DrumRackShared() {
        for (int i = 0; i < 16; ++i) {
            pads[i].mode = kDrumKit[i].mode;
            pads[i].levelDb = kDrumKit[i].levelDb;
            pads[i].tuneSemi = kDrumKit[i].tuneSemi;
            pads[i].decayMs = kDrumKit[i].decayMs;
            pads[i].tone = kDrumKit[i].tone;
            pads[i].shape = kDrumKit[i].shape;
            pads[i].chokeGroup = kDrumKit[i].choke;
        }
    }
};

// 7 descriptors per pad; N is the pad index in the key.
#define DAW_DRUM_PAD_PARAMS(N)                                                             \
    {"pad" #N ".mode", "Pad " #N " Mode", 0, 5, 0, ParamDescriptor::Curve::Switch, "", 0, true, true, false},   \
    {"pad" #N ".level", "Pad " #N " Level", -60, 6, 0, ParamDescriptor::Curve::Db, "dB", 5, true, false, false}, \
    {"pad" #N ".tune", "Pad " #N " Tune", -24, 24, 0, ParamDescriptor::Curve::Linear, "st", 0, true, false, false}, \
    {"pad" #N ".decay", "Pad " #N " Decay", 20, 4000, 300, ParamDescriptor::Curve::Log, "ms", 0, true, false, false}, \
    {"pad" #N ".tone", "Pad " #N " Tone", 0, 1, 0.5f, ParamDescriptor::Curve::Linear, "", 0, true, false, false}, \
    {"pad" #N ".shape", "Pad " #N " Shape", 0, 1, 0.5f, ParamDescriptor::Curve::Linear, "", 0, true, false, false}, \
    {"pad" #N ".choke", "Pad " #N " Choke", 0, 4, 0, ParamDescriptor::Curve::Switch, "", 0, true, true, false}

inline constexpr ParamDescriptor kDrumRackParams[] = {
    DAW_DRUM_PAD_PARAMS(0),  DAW_DRUM_PAD_PARAMS(1),  DAW_DRUM_PAD_PARAMS(2),
    DAW_DRUM_PAD_PARAMS(3),  DAW_DRUM_PAD_PARAMS(4),  DAW_DRUM_PAD_PARAMS(5),
    DAW_DRUM_PAD_PARAMS(6),  DAW_DRUM_PAD_PARAMS(7),  DAW_DRUM_PAD_PARAMS(8),
    DAW_DRUM_PAD_PARAMS(9),  DAW_DRUM_PAD_PARAMS(10), DAW_DRUM_PAD_PARAMS(11),
    DAW_DRUM_PAD_PARAMS(12), DAW_DRUM_PAD_PARAMS(13), DAW_DRUM_PAD_PARAMS(14),
    DAW_DRUM_PAD_PARAMS(15),
};
#undef DAW_DRUM_PAD_PARAMS

class DrumRackDevice final : public InstrumentNode, public VoiceGroup {
public:
    static constexpr uint16_t kStateVersion = 1;
    static constexpr int kPads = 16;
    static constexpr int kFieldsPerPad = 7;
    static constexpr int kParamCount =
        static_cast<int>(sizeof(kDrumRackParams) / sizeof(kDrumRackParams[0]));
    static_assert(sizeof(kDrumRackParams) / sizeof(kDrumRackParams[0]) ==
                  size_t(kPads) * kFieldsPerPad);

    // ---- DeviceNode ---------------------------------------------------------

    void prepare(double sampleRate, int maxBlock) override {
        (void)maxBlock;
        for (auto& pad : pads_) pad.prepare(sampleRate);
    }

    void process(ProcessContext& ctx) override {
        float* l = ctx.outputs[0];
        float* r = ctx.numChannels > 1 ? ctx.outputs[1] : ctx.outputs[0];
        const MidiEvent* ev = ctx.midiIn != nullptr ? ctx.midiIn->begin() : nullptr;
        const MidiEvent* evEnd = ctx.midiIn != nullptr ? ctx.midiIn->end() : nullptr;

        int cursor = 0;
        while (cursor < ctx.numFrames) {
            while (ev != evEnd && ev->sampleOffset <= cursor) {
                handleEvent(*ev);
                ++ev;
            }
            int next = ctx.numFrames;
            if (ev != evEnd && ev->sampleOffset < next) next = ev->sampleOffset;
            const int m = next - cursor;
            if (m > 0) {
                for (auto& pad : pads_)
                    if (pad.active()) pad.renderAdd(l + cursor, r + cursor, m);
                cursor = next;
            }
        }
        while (ev != evEnd) { handleEvent(*ev); ++ev; }
    }

    void reset() override {
        for (auto& pad : pads_) pad.kill();
    }

    int latencySamples() const override { return 0; }

    int paramCount() const override { return kParamCount; }

    const ParamDescriptor& paramDescriptor(int i) const override {
        return kDrumRackParams[i < 0 || i >= kParamCount ? 0 : i];
    }

    void setParamImmediate(int denseIndex, float plain) override {
        if (denseIndex < 0 || denseIndex >= kParamCount) return;
        DrumPadShared& p = shared_.pads[denseIndex / kFieldsPerPad];
        switch (denseIndex % kFieldsPerPad) {
            case 0: p.mode = plain; break;
            case 1: p.levelDb = plain; break;
            case 2: p.tuneSemi = plain; break;
            case 3: p.decayMs = plain; break;
            case 4: p.tone = plain; break;
            case 5: p.shape = plain; break;
            case 6: p.chokeGroup = plain; break;
        }
    }

    size_t stateBytes() const override { return sizeof(DrumRackShared); }

    void saveState(NodeState& out) const override {
        out.hdr.version = kStateVersion;
        out.hdr.sizeBytes = sizeof(DrumRackShared);
        out.hdr.flags = 0;
        std::memcpy(out.body, &shared_, sizeof shared_);
    }

    bool loadState(const NodeState& in) override {
        if (in.hdr.version != kStateVersion ||
            in.hdr.sizeBytes != sizeof(DrumRackShared) || in.body == nullptr)
            return false;
        std::memcpy(&shared_, in.body, sizeof shared_);
        return true;
    }

    // ---- VoiceInterface (live pads / onscreen input) ------------------------
    void noteOn(int note, float velocity, const MpeNoteState&) override {
        triggerPitch(static_cast<uint16_t>(note & 0x7F), velocity);
    }
    void noteOff(int, float) override {}          // one-shots: OFF ignored
    void allNotesOff() override {
        for (auto& pad : pads_) pad.kill();
    }

    // ---- VoiceGroup (the rack is its own group) -----------------------------
    int activeVoiceCount() const override {
        int n = 0;
        for (const auto& pad : pads_) n += pad.active() ? 1 : 0;
        return n;
    }

    StealCandidate bestStealCandidate() const override {
        StealCandidate best;
        for (const auto& pad : pads_) {
            if (!pad.active()) continue;
            StealCandidate c;
            c.valid = true;
            c.releasing = pad.releasing();
            // Allocator convention: releasing is never protected.
            c.isProtected = !pad.releasing() && pad.inTransientWindow();
            c.ageSerial = pad.serial();
            c.level = pad.level();
            if (!best.valid || stealsBefore(c, best)) best = c;
        }
        return best;
    }

    void stealVoices(int count) override {
        for (int k = 0; k < count; ++k) {
            DrumPadVoice* victim = nullptr;
            StealCandidate vc;
            for (auto& pad : pads_) {
                if (!pad.active() || pad.releasing()) continue;
                StealCandidate c;
                c.valid = true;
                c.releasing = false;
                c.isProtected = pad.inTransientWindow();
                c.ageSerial = pad.serial();
                c.level = pad.level();
                if (victim == nullptr || stealsBefore(c, vc)) {
                    victim = &pad;
                    vc = c;
                }
            }
            if (victim == nullptr) return;
            victim->fastRelease();
        }
    }

    // ---- InstrumentNode -----------------------------------------------------
    VoiceGroup* voiceGroup() override { return this; }

    uint32_t budgetRefusals() const noexcept { return budgetRefusals_; }

private:
    // Steal order mirror of the allocator convention: releasing first, then
    // unprotected, then OLDEST, level as the tiebreak.
    static bool stealsBefore(const StealCandidate& a, const StealCandidate& b) noexcept {
        if (a.releasing != b.releasing) return a.releasing;
        if (a.isProtected != b.isProtected) return !a.isProtected;
        if (a.ageSerial != b.ageSerial) return a.ageSerial < b.ageSerial;
        return a.level < b.level;
    }

    static int padForPitch(uint16_t pitch) noexcept {
        for (int i = 0; i < kPads; ++i)                 // first match wins
            if (kDrumKit[i].root == pitch) return i;
        return -1;
    }

    void handleEvent(const MidiEvent& e) noexcept {
        if (static_cast<MidiEventType>(e.type) != MidiEventType::NoteOn) return;
        triggerPitch(e.pitch, static_cast<float>(e.velocity) * (1.0f / 127.0f));
    }

    void triggerPitch(uint16_t pitch, float velocity01) noexcept {
        const int idx = padForPitch(pitch);
        if (idx < 0) return;                            // not a pad note
        if (!pads_[idx].active() && !admitVoice()) {    // retrigger reuses its slot
            ++budgetRefusals_;
            return;
        }
        const DrumPadShared& p = shared_.pads[idx];
        // Choke: cut sounding pads sharing a nonzero group (open hat dies
        // when the closed hat hits; self-retrigger hard-cuts in trigger()).
        const int group = static_cast<int>(p.chokeGroup + 0.5f);
        if (group != 0) {
            for (int i = 0; i < kPads; ++i) {
                if (i == idx || !pads_[i].active()) continue;
                if (static_cast<int>(shared_.pads[i].chokeGroup + 0.5f) == group)
                    pads_[i].fastRelease();
            }
        }
        pads_[idx].trigger(velocity01, kDrumKit[idx].root, p, ++serial_);
    }

    DrumRackShared shared_;
    DrumPadVoice pads_[kPads];
    uint64_t serial_ = 0;
    uint32_t budgetRefusals_ = 0;
};

} // namespace daw
