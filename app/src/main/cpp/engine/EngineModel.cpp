#include "EngineModel.h"

#include <algorithm>

namespace daw {

bool EngineModel::applyDelta(const EntityDelta& d) {
    switch (static_cast<EntityKind>(d.entityKind)) {
        case EntityKind::Track:       return applyTrack(d.entityId, d.payload, d.byteLen);
        case EntityKind::Clip:        return applyClip(d.entityId, d.payload, d.byteLen);
        case EntityKind::ClipContent: return applyContent(d.entityId, d.payload, d.byteLen);
        case EntityKind::Device:      return applyDevice(d.entityId, d.payload, d.byteLen);
        case EntityKind::Scene:       return applyScene(d.entityId, d.payload, d.byteLen);
        case EntityKind::TempoMap:    return applyTempoMap(d.payload, d.byteLen);
        case EntityKind::SampleRef:   return applySampleRef(d.entityId, d.payload, d.byteLen);
        case EntityKind::Preview:     return applyPreview(d.entityId, d.payload, d.byteLen);
        case EntityKind::Rack:
        case EntityKind::Routing:
        case EntityKind::LaneGroup:
        case EntityKind::Groove:
            ++deferredKinds_;         // consumers arrive M2/M3
            return true;
    }
    return false;                     // StateCodec already filters unknown kinds
}

bool EngineModel::applyTrack(NodeUid id, const uint8_t* p, uint32_t len) {
    if (len == 0) {
        tracks_.erase(id);
        dirty_ |= kDirtyTimeline | kDirtyGraph;
        return true;
    }
    TrackDeltaPayload w;
    if (!readRecord(p, len, 0, w)) return false;
    ModelTrack& t = tracks_[id];
    t.type = w.trackType;
    t.flags = w.flags;
    t.order = w.order;
    t.volumeDb = w.volumeDb;
    t.pan = w.pan;
    t.sendA = w.sendA;
    t.sendB = w.sendB;
    dirty_ |= kDirtyTimeline | kDirtyGraph;
    return true;
}

bool EngineModel::applyClip(NodeUid id, const uint8_t* p, uint32_t len) {
    if (len == 0) {
        clips_.erase(id);
        dirty_ |= kDirtyTimeline;
        return true;
    }
    ClipDeltaPayload w;
    if (!readRecord(p, len, 0, w)) return false;
    ModelClip& c = clips_[id];
    c.trackUid = w.trackUid;
    c.contentUid = w.contentUid;
    c.startBeat = w.startBeat;
    c.lengthBeats = w.lengthBeats;
    c.slotIndex = w.slotIndex;
    c.looping = (w.flags & kClipFlagLooping) != 0;
    dirty_ |= kDirtyTimeline;
    return true;
}

bool EngineModel::applyContent(NodeUid id, const uint8_t* p, uint32_t len) {
    if (len == 0) {
        contents_.erase(id);
        dirty_ |= kDirtyTimeline;
        return true;
    }
    ClipContentDeltaHead head;
    if (!readRecord(p, len, 0, head)) return false;
    const size_t need = sizeof(head) + size_t(head.noteCount) * sizeof(NoteRecord);
    if (need > len) return false;

    ModelClipContent& c = contents_[id];
    c.lengthBeats = head.lengthBeats > 0.0 ? head.lengthBeats : 4.0;
    c.notes.clear();
    c.notes.reserve(head.noteCount);
    size_t off = sizeof(head);
    for (uint32_t i = 0; i < head.noteCount; ++i, off += sizeof(NoteRecord)) {
        NoteRecord r;
        if (!readRecord(p, len, off, r)) return false;
        ModelNote n;
        n.id = r.id;
        n.pitch = static_cast<uint16_t>(r.pitch & 0x7Fu);
        n.velocity = static_cast<uint16_t>(r.velocity & 0x7Fu);
        n.startBeat = r.startBeat;
        n.lengthBeats = r.lengthBeats > 0.0 ? r.lengthBeats : 0.0;
        c.notes.push_back(n);
    }
    dirty_ |= kDirtyTimeline;
    return true;
}

bool EngineModel::applyDevice(NodeUid id, const uint8_t* p, uint32_t len) {
    if (len == 0) {
        devices_.erase(id);
        dirty_ |= kDirtyGraph;
        return true;
    }
    DeviceDeltaPayload w;
    if (!readRecord(p, len, 0, w)) return false;
    const size_t tail = len - sizeof(w);
    if (tail % sizeof(ParamValueRecord) != 0) return false;
    const size_t paramCount = tail / sizeof(ParamValueRecord);

    // Param-only refreshes (knob moves keeping their model residency) must
    // NOT rebuild the graph - live values ride the param path; the model
    // copy is baked at the next structural rebuild. Only structural facts
    // mark graph-dirty.
    const auto it = devices_.find(id);
    const bool structural =
        it == devices_.end() ||
        it->second.trackUid != w.trackUid ||
        it->second.type != w.deviceType ||
        it->second.enabled != ((w.flags & kDeviceFlagEnabled) != 0) ||
        it->second.order != w.order;

    ModelDevice& dev = devices_[id];
    dev.trackUid = w.trackUid;
    dev.type = w.deviceType;
    dev.enabled = (w.flags & kDeviceFlagEnabled) != 0;
    dev.order = w.order;
    dev.params.clear();
    dev.params.reserve(paramCount);
    size_t off = sizeof(w);
    for (size_t i = 0; i < paramCount; ++i, off += sizeof(ParamValueRecord)) {
        ParamValueRecord r;
        if (!readRecord(p, len, off, r)) return false;
        dev.params.emplace_back(r.keyHash, r.plain);
    }
    if (structural) dirty_ |= kDirtyGraph;
    return true;
}

bool EngineModel::applyScene(NodeUid id, const uint8_t* p, uint32_t len) {
    if (len == 0) {
        scenes_.erase(id);
        dirty_ |= kDirtyTimeline;
        return true;
    }
    SceneDeltaPayload w;
    if (!readRecord(p, len, 0, w)) return false;
    scenes_[id].index = w.index;
    dirty_ |= kDirtyTimeline;
    return true;
}

bool EngineModel::applySampleRef(NodeUid deviceUid, const uint8_t* p, uint32_t len) {
    if (len == 0) {                              // remove every ref of the device
        sampleRefs_.erase(deviceUid);
        dirty_ |= kDirtyGraph;
        return true;
    }
    SampleRefDeltaHead head;
    if (!readRecord(p, len, 0, head)) return false;
    const uint32_t pathLen = len - uint32_t(sizeof head);
    if (pathLen > kMaxSamplePathBytes) return false;

    auto& refs = sampleRefs_[deviceUid];
    auto it = refs.begin();
    while (it != refs.end() && it->slot != head.slot) ++it;

    if (head.fileId == 0) {                      // clear one slot
        if (it != refs.end()) refs.erase(it);
        if (refs.empty()) sampleRefs_.erase(deviceUid);
    } else {
        // Slot count is bounded so a hostile stream cannot balloon the map
        // (real devices use slot 0 or pads 0..15; refused frames are counted
        // by the caller like any malformed payload).
        if (it == refs.end() && refs.size() >= kMaxSampleSlotsPerDevice) return false;
        ModelSampleRef r;
        r.slot = head.slot;
        r.fileId = head.fileId;
        r.path.assign(reinterpret_cast<const char*>(p) + sizeof head, pathLen);
        if (it != refs.end()) *it = std::move(r);
        else refs.push_back(std::move(r));
    }
    dirty_ |= kDirtyGraph;                       // builder re-pins at compile
    return true;
}

bool EngineModel::applyPreview(uint64_t fileId, const uint8_t* p, uint32_t len) {
    if (len > kMaxSamplePathBytes) return false;
    if (fileId == 0 || len == 0) {               // contract: either form stops
        preview_.fileId = 0;
        preview_.path.clear();
    } else {
        preview_.fileId = fileId;
        preview_.path.assign(reinterpret_cast<const char*>(p), len);
    }
    ++preview_.serial;                           // same file again = retrigger
    dirty_ |= kDirtyPreview;
    return true;
}

bool EngineModel::applyTempoMap(const uint8_t* p, uint32_t len) {
    if (len == 0) {
        tempo_.events.clear();
        tempo_.sigs.clear();
        hasTempoDelta_ = false;       // back to the 120/4-4 default
        dirty_ |= kDirtyTempo;
        return true;
    }
    TempoMapDeltaHead head;
    if (!readRecord(p, len, 0, head)) return false;
    const size_t need = sizeof(head) +
        size_t(head.tempoCount) * sizeof(TempoEventRecord) +
        size_t(head.sigCount) * sizeof(SigEventRecord);
    if (need > len) return false;

    tempo_.events.clear();
    tempo_.events.reserve(head.tempoCount);
    size_t off = sizeof(head);
    for (uint32_t i = 0; i < head.tempoCount; ++i, off += sizeof(TempoEventRecord)) {
        TempoEventRecord r;
        if (!readRecord(p, len, off, r)) return false;
        if (r.bpm > 0.0) tempo_.events.push_back({r.beat, r.bpm});
    }
    tempo_.sigs.clear();
    tempo_.sigs.reserve(head.sigCount);
    for (uint32_t i = 0; i < head.sigCount; ++i, off += sizeof(SigEventRecord)) {
        SigEventRecord r;
        if (!readRecord(p, len, off, r)) return false;
        if (r.numerator >= 1 && r.denominator >= 1)
            tempo_.sigs.push_back({r.beat, r.numerator, r.denominator});
    }
    std::sort(tempo_.events.begin(), tempo_.events.end(),
              [](const ModelTempo::Ev& a, const ModelTempo::Ev& b) { return a.beat < b.beat; });
    std::sort(tempo_.sigs.begin(), tempo_.sigs.end(),
              [](const ModelTempo::Sig& a, const ModelTempo::Sig& b) { return a.beat < b.beat; });
    hasTempoDelta_ = true;
    dirty_ |= kDirtyTempo;
    return true;
}

} // namespace daw
