#pragma once

#include <atomic>
#include <cstdint>

#include "EngineConfig.h"
#include "NodeUid.h"
#include "RtAssert.h"

// Coalescing latest-wins channel for parameter moves (CONTRACTS.md seam 2).
// One table per producer thread (JNI, MIDI I/O) - single writer, single
// reader (the audio thread) - so no CAS is needed anywhere:
//
//   producer: claims/updates a slot keyed (nodeUid, paramKeyHash), publishes
//             fields under a per-slot version counter (odd = writing), then
//             sets the dirty flag.
//   RT:       scans all slots each block; for dirty slots it clears the flag
//             FIRST, then seqlock-reads the fields and applies through the
//             installed graph's key resolver (flag-before-data: a concurrent
//             producer update re-raises the flag, so nothing is ever lost).
//
// Slots persist after application - they are the post-swap re-apply memory:
// after a graph swap, RT re-applies every occupied slot whose editSeq is
// newer than the incoming graph's editSeq. The producer lazily reclaims
// slots the installed graph has already baked in (editSeq <= graphSeq,
// published by RT), keeping the table sized to "moves in flight since the
// last rebuild".
//
// Overflow (no free slot in the probe run) drops the move and raises the
// reconcile flag; EngineSync re-sends current model values for the evicted
// keys (contract rule: overflow reconciles, never silently diverges).

namespace daw {

class ParamMoveTable {
public:
    struct Slot {
        std::atomic<uint32_t> ver{0};        // even = stable, odd = writing; 0 = empty
        std::atomic<bool>     dirty{false};
        NodeUid               uid = 0;
        ParamKeyHash          key = 0;
        double                plain = 0.0;
        uint32_t              editSeq = 0;
    };

    // ---- producer thread ----------------------------------------------------

    // Latest-wins set. Returns false on table overflow (caller raises the
    // reconcile path; the move itself is dropped here).
    bool set(NodeUid uid, ParamKeyHash key, double plain, uint32_t editSeq) noexcept {
        const uint32_t graphSeq = installedGraphSeq_.load(std::memory_order_acquire);
        int freeIdx = -1;

        for (int probe = 0; probe < kProbeLimit; ++probe) {
            Slot& s = slots_[slotIndex(uid, key, probe)];
            const uint32_t v = s.ver.load(std::memory_order_relaxed);

            if (v == 0) {                                    // empty: remember, keep probing for a match
                if (freeIdx < 0) freeIdx = slotIndex(uid, key, probe);
                continue;
            }
            if (s.uid == uid && s.key == key) {              // existing entry: update in place
                writeSlot(s, uid, key, plain, editSeq);
                return true;
            }
            // Occupied by another param: reclaim if the graph already owns it.
            if (freeIdx < 0 && !s.dirty.load(std::memory_order_relaxed) &&
                s.editSeq <= graphSeq) {
                s.ver.store(0, std::memory_order_relaxed);   // producer-owned lifecycle
                freeIdx = slotIndex(uid, key, probe);
            }
        }

        if (freeIdx < 0) {
            overflowed_.store(true, std::memory_order_release);
            return false;
        }
        writeSlot(slots_[freeIdx], uid, key, plain, editSeq);
        return true;
    }

    // ---- audio thread -------------------------------------------------------

    // Visit every dirty slot: clears the flag first, then hands a stable
    // snapshot of (uid, key, plain, editSeq) to `apply`. Fn must be RT-safe.
    template <typename Fn>
    void drainDirty(Fn&& apply) noexcept {
        for (Slot& s : slots_) {
            if (!s.dirty.load(std::memory_order_acquire)) continue;
            s.dirty.store(false, std::memory_order_relaxed);   // flag before data
            NodeUid uid; ParamKeyHash key; double plain; uint32_t seq;
            if (readSlot(s, uid, key, plain, seq)) apply(uid, key, plain, seq);
        }
    }

    // Re-apply memory after a graph swap: every occupied slot newer than the
    // incoming graph. Does not touch dirty flags.
    template <typename Fn>
    void reapplyNewerThan(uint32_t graphSeq, Fn&& apply) noexcept {
        for (Slot& s : slots_) {
            if (s.ver.load(std::memory_order_acquire) == 0) continue;
            NodeUid uid; ParamKeyHash key; double plain; uint32_t seq;
            if (readSlot(s, uid, key, plain, seq) && seq > graphSeq)
                apply(uid, key, plain, seq);
        }
    }

    // RT publishes the installed graph's editSeq so the producer can reclaim.
    void publishInstalledGraphSeq(uint32_t seq) noexcept {
        installedGraphSeq_.store(seq, std::memory_order_release);
    }

    // ---- any thread ---------------------------------------------------------

    // Test-and-clear the reconcile flag (EngineSync polls this).
    bool consumeOverflowFlag() noexcept {
        return overflowed_.exchange(false, std::memory_order_acq_rel);
    }

private:
    static constexpr int kProbeLimit = 8;

    static int slotIndex(NodeUid uid, ParamKeyHash key, int probe) noexcept {
        uint64_t h = uid ^ (uint64_t(key) * 0x9e3779b97f4a7c15ull);
        h ^= h >> 29;
        return static_cast<int>((h + uint64_t(probe)) & (kParamTableCap - 1));
    }

    static void writeSlot(Slot& s, NodeUid uid, ParamKeyHash key,
                          double plain, uint32_t editSeq) noexcept {
        const uint32_t v = s.ver.load(std::memory_order_relaxed);
        s.ver.store(v | 1u, std::memory_order_relaxed);        // odd: writing
        std::atomic_thread_fence(std::memory_order_release);   // odd visible before fields
        s.uid = uid; s.key = key; s.plain = plain; s.editSeq = editSeq;
        s.ver.store((v | 1u) + 1u, std::memory_order_release); // even, advanced
        s.dirty.store(true, std::memory_order_release);        // data before flag
    }

    static bool readSlot(const Slot& s, NodeUid& uid, ParamKeyHash& key,
                         double& plain, uint32_t& seq) noexcept {
        for (int attempt = 0; attempt < 4; ++attempt) {
            const uint32_t v0 = s.ver.load(std::memory_order_acquire);
            if (v0 == 0 || (v0 & 1u)) continue;                // empty or mid-write
            uid = s.uid; key = s.key; plain = s.plain; seq = s.editSeq;
            std::atomic_thread_fence(std::memory_order_acquire);
            if (s.ver.load(std::memory_order_relaxed) == v0) return true;
        }
        return false;                                          // writer active; next block
    }

    static_assert((kParamTableCap & (kParamTableCap - 1)) == 0,
                  "table capacity must be a power of two");

    Slot slots_[kParamTableCap];
    std::atomic<uint32_t> installedGraphSeq_{0};
    std::atomic<bool>     overflowed_{false};
};

} // namespace daw
