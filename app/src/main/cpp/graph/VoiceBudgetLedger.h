#pragma once

#include <cstdint>

#include "../core/EngineConfig.h"
#include "../core/FixedVector.h"
#include "../device/VoiceAllocator.h"

// Global polyphony enforcement (blueprint graph/VoiceBudgetLedger; contract
// kVoiceBudget = 64 across ALL instruments). Lives inside a PlaybackGraph -
// voice accounting is transient render state and rebuilds with the graph.
// The VoiceGroup/StealCandidate contract types live in device/ (the
// allocator implements them); this class only ranks and demands.
//
// Protocol, all [RT]:
//   - beginBlock(): recount active voices from every registered group -
//     drift-proof, no incremental bookkeeping to corrupt.
//   - requestVoice(): an instrument asks BEFORE allocating. Within budget:
//     granted. Over budget: rank every group's best steal candidate by the
//     contract order (releasing -> unprotected -> oldest, level as final
//     tiebreak; protection = most-recent notes + drum transients) and
//     demand stealVoices(1) from the winner. Refuses only when nothing is
//     stealable. The stolen voice's fade overlaps the new voice - the
//     budget charges the new voice immediately, honest accounting.

namespace daw {

class VoiceBudgetLedger {
public:
    // ---- builder ------------------------------------------------------------
    bool registerGroup(VoiceGroup* g) {
        return g != nullptr && groups_.push_back(g);
    }

    // ---- audio thread -------------------------------------------------------
    void beginBlock() noexcept {
        active_ = 0;
        for (VoiceGroup* g : groups_) active_ += g->activeVoiceCount();
    }

    bool requestVoice() noexcept {
        if (active_ < kVoiceBudget) {
            ++active_;
            return true;
        }
        VoiceGroup* victim = pickVictimGroup();
        if (victim == nullptr) return false;
        victim->stealVoices(1);
        ++steals_;
        ++active_;
        return true;
    }

    int activeVoices() const noexcept { return active_; }
    uint32_t stealCount() const noexcept { return steals_; }
    size_t groupCount() const noexcept { return groups_.size(); }

private:
    static bool better(const StealCandidate& a, const StealCandidate& b) noexcept {
        if (a.releasing != b.releasing) return a.releasing;
        if (a.isProtected != b.isProtected) return !a.isProtected;
        if (a.ageSerial != b.ageSerial) return a.ageSerial < b.ageSerial;
        return a.level < b.level;
    }

    VoiceGroup* pickVictimGroup() noexcept {
        VoiceGroup* best = nullptr;
        StealCandidate bestCand;
        for (VoiceGroup* g : groups_) {
            const StealCandidate c = g->bestStealCandidate();
            if (!c.valid) continue;
            if (best == nullptr || better(c, bestCand)) {
                best = g;
                bestCand = c;
            }
        }
        return best;
    }

    FixedVector<VoiceGroup*, kMaxTracks * 2> groups_;
    int active_ = 0;
    uint32_t steals_ = 0;
};

} // namespace daw
