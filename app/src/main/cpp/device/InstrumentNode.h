#pragma once

#include "DeviceNode.h"
#include "VoiceAllocator.h"

// Instrument base (blueprint device/InstrumentNode): a DeviceNode that also
// implements the seam-1 voice interface, plus the two hooks the graph
// compiler wires without knowing concrete types:
//
//   - voiceGroup(): registered with the graph's VoiceBudgetLedger.
//   - setVoiceAdmission(): a type-erased callback (the ledger's
//     requestVoice) consulted BEFORE each allocation - global budget
//     enforcement without a device -> graph dependency.
//
// DeviceRegistry marks instrument types isInstrument, so the builder
// static_casts DeviceNode* -> InstrumentNode* safely without RTTI.

namespace daw {

class InstrumentNode : public DeviceNode, public VoiceInterface {
public:
    using AdmitFn = bool (*)(void* ctx);

    void setVoiceAdmission(AdmitFn fn, void* ctx) noexcept {
        admitFn_ = fn;
        admitCtx_ = ctx;
    }

    virtual VoiceGroup* voiceGroup() = 0;

protected:
    // [RT] True when the global budget grants (or no ledger is wired).
    bool admitVoice() noexcept {
        return admitFn_ == nullptr || admitFn_(admitCtx_);
    }

private:
    AdmitFn admitFn_ = nullptr;
    void* admitCtx_ = nullptr;
};

} // namespace daw
