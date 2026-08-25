#pragma once

#include <cstdint>

#include "../core/EngineConfig.h"

// Per-instrument voice pool + the cross-layer steal contract types
// (blueprint device/VoiceAllocator; the graph's VoiceBudgetLedger consumes
// VoiceGroup from up-stack, correct dependency direction graph -> device).
//
// Pool model: MaxVoices slots, of which `polyphony` may sound musically;
// the headroom absorbs steal fades - a stolen voice fast-releases IN ITS
// SLOT until silent while the new note takes a free slot, so steals never
// hard-cut unless the pool itself is exhausted (then the quietest stolen /
// releasing slot is kill()ed and reused - the documented last resort).
//
// Steal ranking (contract): RELEASING first, then unprotected, then OLDEST
// (smallest serial), quietness as the final tiebreak. Protected = inside
// the voice's transient window (drum hits) or one of the K=2 most recently
// started musical voices.
//
// VoiceT requirements (duck-typed, all [RT]):
//   bool  active() const;             // consuming a budget slot
//   bool  releasing() const;          // musical release stage
//   float level() const;              // current output envelope 0..1
//   bool  inTransientWindow() const;  // early-hit protection (drums)
//   void  beginRelease();             // musical note-off
//   void  fastRelease();              // steal: few-ms ramp to silence
//   void  kill();                     // immediate stop (pool exhaustion)
// The instrument starts the voice itself on the slot acquire() returns.

namespace daw {

struct StealCandidate {
    bool     valid = false;
    bool     releasing = false;
    bool     isProtected = false;   // transient window / most-recent notes
    uint64_t ageSerial = 0;         // smaller = older
    float    level = 1.0f;          // current output level 0..1
};

class VoiceGroup {
public:
    virtual ~VoiceGroup() = default;
    virtual int  activeVoiceCount() const = 0;               // [RT]
    virtual StealCandidate bestStealCandidate() const = 0;   // [RT]
    virtual void stealVoices(int count) = 0;                 // [RT]
};

template <typename VoiceT, int MaxVoices>
class VoiceAllocator final : public VoiceGroup {
public:
    static constexpr int kRecentProtected = 2;

    struct Slot {
        VoiceT   voice;
        uint32_t noteId = 0;
        uint64_t serial = 0;
        bool     stolen = false;    // fast-releasing after a steal
    };

    void setPolyphony(int n) noexcept {
        polyphony_ = n < 1 ? 1 : (n > MaxVoices ? MaxVoices : n);
    }
    int polyphony() const noexcept { return polyphony_; }

    // ---- allocation [RT] ----------------------------------------------------

    // Returns the slot to start, or null only when the pool is exhausted
    // AND nothing is killable (all MaxVoices protected + musical).
    Slot* acquire(uint32_t noteId) noexcept {
        // Local musical polyphony: steal (fast-release in place) if full.
        if (musicalCount() >= polyphony_) stealVoices(1);

        for (int i = 0; i < MaxVoices; ++i) {
            if (!slots_[i].voice.active()) return startSlot(i, noteId);
        }
        // Pool exhausted by steal/release tails: reclaim the quietest
        // stolen-or-releasing slot immediately (documented last resort).
        int victim = -1;
        float quietest = 2.0f;
        for (int i = 0; i < MaxVoices; ++i) {
            const Slot& s = slots_[i];
            if (!(s.stolen || s.voice.releasing())) continue;
            if (s.voice.level() < quietest) {
                quietest = s.voice.level();
                victim = i;
            }
        }
        if (victim < 0) return nullptr;
        slots_[victim].voice.kill();
        return startSlot(victim, noteId);
    }

    // Musical note-off: releases the NEWEST sounding voice with this id
    // (loop-pass instance ids make collisions rare by construction).
    void noteOff(uint32_t noteId) noexcept {
        int best = -1;
        for (int i = 0; i < MaxVoices; ++i) {
            Slot& s = slots_[i];
            if (!s.voice.active() || s.stolen || s.noteId != noteId) continue;
            if (s.voice.releasing()) continue;
            if (best < 0 || s.serial > slots_[best].serial) best = i;
        }
        if (best >= 0) slots_[best].voice.beginRelease();
    }

    void allNotesOff() noexcept {
        for (int i = 0; i < MaxVoices; ++i)
            if (slots_[i].voice.active() && !slots_[i].voice.releasing())
                slots_[i].voice.beginRelease();
    }

    void killAll() noexcept {
        for (int i = 0; i < MaxVoices; ++i) slots_[i].voice.kill();
    }

    // ---- render iteration [RT] ----------------------------------------------
    Slot* begin() noexcept { return slots_; }
    Slot* end() noexcept { return slots_ + MaxVoices; }

    // ---- VoiceGroup (ledger adapter) ----------------------------------------

    int activeVoiceCount() const override {
        int n = 0;
        for (int i = 0; i < MaxVoices; ++i)
            if (slots_[i].voice.active()) ++n;
        return n;
    }

    StealCandidate bestStealCandidate() const override {
        StealCandidate best;
        uint64_t recent[kRecentProtected];
        topSerials(recent);
        for (int i = 0; i < MaxVoices; ++i) {
            const Slot& s = slots_[i];
            if (!s.voice.active() || s.stolen) continue;
            StealCandidate c;
            c.valid = true;
            c.releasing = s.voice.releasing();
            c.isProtected = isProtectedSlot(s, recent);
            c.ageSerial = s.serial;
            c.level = s.voice.level();
            if (!best.valid || betterSteal(c, best)) best = c;
        }
        return best;
    }

    void stealVoices(int count) override {
        for (int k = 0; k < count; ++k) {
            uint64_t recent[kRecentProtected];
            topSerials(recent);
            int best = -1;
            StealCandidate bestCand;
            for (int i = 0; i < MaxVoices; ++i) {
                Slot& s = slots_[i];
                if (!s.voice.active() || s.stolen) continue;
                StealCandidate c;
                c.valid = true;
                c.releasing = s.voice.releasing();
                c.isProtected = isProtectedSlot(s, recent);
                c.ageSerial = s.serial;
                c.level = s.voice.level();
                if (best < 0 || betterSteal(c, bestCand)) {
                    best = i;
                    bestCand = c;
                }
            }
            if (best < 0) return;
            slots_[best].stolen = true;
            slots_[best].voice.fastRelease();
        }
    }

private:
    static bool betterSteal(const StealCandidate& a, const StealCandidate& b) noexcept {
        if (a.releasing != b.releasing) return a.releasing;
        if (a.isProtected != b.isProtected) return !a.isProtected;
        if (a.ageSerial != b.ageSerial) return a.ageSerial < b.ageSerial;
        return a.level < b.level;
    }

    Slot* startSlot(int i, uint32_t noteId) noexcept {
        Slot& s = slots_[i];
        s.noteId = noteId;
        s.serial = ++serial_;
        s.stolen = false;
        return &s;
    }

    int musicalCount() const noexcept {
        int n = 0;
        for (int i = 0; i < MaxVoices; ++i)
            if (slots_[i].voice.active() && !slots_[i].stolen) ++n;
        return n;
    }

    // The K highest serials among musical (non-stolen) voices.
    void topSerials(uint64_t out[kRecentProtected]) const noexcept {
        for (int k = 0; k < kRecentProtected; ++k) out[k] = 0;
        for (int i = 0; i < MaxVoices; ++i) {
            const Slot& s = slots_[i];
            if (!s.voice.active() || s.stolen) continue;
            uint64_t v = s.serial;
            for (int k = 0; k < kRecentProtected; ++k) {
                if (v > out[k]) {
                    const uint64_t t = out[k];
                    out[k] = v;
                    v = t;
                }
            }
        }
    }

    static bool contains(const uint64_t arr[kRecentProtected], uint64_t v) noexcept {
        for (int k = 0; k < kRecentProtected; ++k)
            if (arr[k] == v && v != 0) return true;
        return false;
    }

    bool isProtectedSlot(const Slot& s, const uint64_t recent[kRecentProtected]) const noexcept {
        if (s.voice.releasing()) return false;       // releasing is never protected
        return s.voice.inTransientWindow() || contains(recent, s.serial);
    }

    Slot slots_[MaxVoices]{};
    int polyphony_ = MaxVoices;
    uint64_t serial_ = 0;
};

} // namespace daw
