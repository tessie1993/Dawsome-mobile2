#pragma once

#include <cstring>

#include "InstrumentNode.h"
#include "VoiceAllocator.h"

// Shared shell for polyphonic instruments (device platform, M4): owns the
// VoiceAllocator, the sample-accurate event-split process loop, the ledger
// admission, the seam-1 live-input mapping (note number -> voice id) and
// SharedT-POD state migration. A concrete synth supplies:
//
//   SharedT   - POD settings voices read; ALSO the migrating state body.
//   VoiceT    - the VoiceAllocator voice contract plus:
//                 void prepare(double sampleRate);
//                 void start(uint16_t pitch, float velocity01, const SharedT&);
//                 void renderAdd(float* l, float* r, int n, const SharedT&);
//   and overrides paramCount/paramDescriptor/setParamImmediate.
//
// Sounding voices reset on structural rebuilds (shared params migrate; full
// voice-state adoption is the deferred polish tracked in BUILD_LOG).

namespace daw {

template <typename VoiceT, typename SharedT,
          uint16_t StateVersion, int PoolVoices = 16, int DefaultPolyphony = 8>
class PolyInstrument : public InstrumentNode {
public:
    // ---- DeviceNode (common half) -------------------------------------------

    void prepare(double sampleRate, int maxBlock) override {
        (void)maxBlock;
        for (auto& slot : voices_) slot.voice.prepare(sampleRate);
        voices_.setPolyphony(DefaultPolyphony);
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
                renderVoices(l + cursor, r + cursor, m);
                cursor = next;
            }
        }
        while (ev != evEnd) { handleEvent(*ev); ++ev; }
    }

    void reset() override { voices_.killAll(); }

    int latencySamples() const override { return 0; }

    size_t stateBytes() const override { return sizeof(SharedT); }

    void saveState(NodeState& out) const override {
        out.hdr.version = StateVersion;
        out.hdr.sizeBytes = sizeof(SharedT);
        out.hdr.flags = 0;
        std::memcpy(out.body, &shared_, sizeof shared_);
    }

    bool loadState(const NodeState& in) override {
        if (in.hdr.version != StateVersion ||
            in.hdr.sizeBytes != sizeof(SharedT) || in.body == nullptr)
            return false;
        std::memcpy(&shared_, in.body, sizeof shared_);
        return true;
    }

    // ---- VoiceInterface (live input: note number keys the voice) ------------
    void noteOn(int note, float velocity, const MpeNoteState&) override {
        noteOnId(static_cast<uint32_t>(note),
                 static_cast<uint16_t>(note & 0x7F), velocity);
    }
    void noteOff(int note, float) override {
        voices_.noteOff(static_cast<uint32_t>(note));
    }
    void allNotesOff() override { voices_.allNotesOff(); }
    void stealVoices(int count) override { voices_.stealVoices(count); }

    // ---- InstrumentNode -----------------------------------------------------
    VoiceGroup* voiceGroup() override { return &voices_; }

    uint32_t budgetRefusals() const noexcept { return budgetRefusals_; }

protected:
    void handleEvent(const MidiEvent& e) noexcept {
        switch (static_cast<MidiEventType>(e.type)) {
            case MidiEventType::NoteOn:
                noteOnId(e.noteId, e.pitch,
                         static_cast<float>(e.velocity) * (1.0f / 127.0f));
                break;
            case MidiEventType::NoteOff:
                voices_.noteOff(e.noteId);
                break;
        }
    }

    void noteOnId(uint32_t noteId, uint16_t pitch, float velocity01) noexcept {
        if (!admitVoice()) {
            ++budgetRefusals_;
            return;
        }
        if (auto* slot = voices_.acquire(noteId))
            slot->voice.start(pitch, velocity01, shared_);
    }

    void renderVoices(float* l, float* r, int n) noexcept {
        for (auto& slot : voices_)
            if (slot.voice.active()) slot.voice.renderAdd(l, r, n, shared_);
    }

    SharedT shared_;
    VoiceAllocator<VoiceT, PoolVoices> voices_;
    uint32_t budgetRefusals_ = 0;
};

} // namespace daw
