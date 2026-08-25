#pragma once

#include <cstddef>
#include <vector>

#include "../device/DeviceNode.h"

// State adoption across graph swaps (CONTRACTS.md seam 3; blueprint 2.3).
//
// ADOPT ENTRIES ONLY: the builder adds {newNode, oldNode} pairs where the
// NodeUid matches AND the configHash matches AND rate/maxBlock are
// unchanged. Fresh nodes and reset-with-fade state are fully pre-installed
// by the builder off-thread - the audio thread never touches them at swap.
// RT executes executeAdopt() at the block boundary: bounded by the retired
// graph's node count, pointer/POD moves only (save into a pre-sized scratch,
// load into the successor), no allocation, no frees.

namespace daw {

struct MigrationEntry {
    DeviceNode* newNode = nullptr;
    DeviceNode* oldNode = nullptr;
};

class MigrationPlan {
public:
    // ---- builder thread -----------------------------------------------------

    void add(DeviceNode* newNode, DeviceNode* oldNode, size_t stateBytes) {
        entries_.push_back({newNode, oldNode});
        if (stateBytes > scratchBytes_) scratchBytes_ = stateBytes;
    }

    void finalize() { scratch_.resize(scratchBytes_); }

    size_t size() const noexcept { return entries_.size(); }

    // ---- audio thread [RT at swap] ------------------------------------------

    void executeAdopt() noexcept {
        NodeState st;
        st.body = scratch_.data();
        for (const MigrationEntry& e : entries_) {
            st.hdr = NodeStateHeader{};
            e.oldNode->saveState(st);
            e.newNode->loadState(st);
        }
    }

private:
    std::vector<MigrationEntry> entries_;
    std::vector<std::byte> scratch_;
    size_t scratchBytes_ = 0;
};

} // namespace daw
