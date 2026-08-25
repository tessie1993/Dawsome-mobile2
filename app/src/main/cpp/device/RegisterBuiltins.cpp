#include "DeviceRegistry.h"

#include "instruments/DrumRackDevice.h"
#include "instruments/FmSynth.h"
#include "instruments/SimpleSampler.h"
#include "instruments/SubtractiveSynth.h"
#include "instruments/WavetableSynth.h"

// Built-in device registration (one call per process, idempotent). Types
// register here as their milestones land - the frozen DeviceTypeId wire
// numbers gain factories over time, and projects referencing types that
// haven't landed keep compiling working mixers (builder skips + counts).

namespace daw {

namespace {

std::unique_ptr<DeviceNode> makeSubtractiveSynth() {
    return std::make_unique<SubtractiveSynth>();
}

std::unique_ptr<DeviceNode> makeWavetableSynth() {
    return std::make_unique<WavetableSynth>();
}

std::unique_ptr<DeviceNode> makeFmSynth() {
    return std::make_unique<FmSynth>();
}

std::unique_ptr<DeviceNode> makeDrumRack() {
    return std::make_unique<DrumRackDevice>();
}

std::unique_ptr<DeviceNode> makeSimpleSampler() {
    return std::make_unique<SimpleSampler>();
}

} // namespace

void registerBuiltinDevices() {
    static const bool once = [] {
        DeviceRegistry& r = DeviceRegistry::instance();
        r.registerType(DeviceTypeId::SubtractiveSynth, "Subtractive Synth",
                       &makeSubtractiveSynth, kSubtractiveParams,
                       SubtractiveSynth::kParamCount, /*isInstrument=*/true);
        r.registerType(DeviceTypeId::WavetableSynth, "Wavetable Lab",
                       &makeWavetableSynth, kWavetableParams,
                       WavetableSynth::kParamCount, /*isInstrument=*/true);
        r.registerType(DeviceTypeId::FmSynth, "FM Four",
                       &makeFmSynth, kFmParams,
                       FmSynth::kParamCount, /*isInstrument=*/true);
        r.registerType(DeviceTypeId::DrumRack, "16-Pad Drum Rack",
                       &makeDrumRack, kDrumRackParams,
                       DrumRackDevice::kParamCount, /*isInstrument=*/true);
        r.registerType(DeviceTypeId::Sampler, "Sampler",
                       &makeSimpleSampler, kSamplerParams,
                       SimpleSampler::kParamCount, /*isInstrument=*/true);
        // The bank behind WavetableVoice is a lazy static; touch it HERE so
        // the ~4M-sin generation runs at engine construction, never on the
        // audio thread's first render of a wavetable voice.
        WavetableBank::instance();
        // M5: DrumRack, samplers. M8-M10: the effects waves.
        return true;
    }();
    (void)once;
}

} // namespace daw
