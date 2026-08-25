#include "DeviceRegistry.h"

#include "instruments/SubtractiveSynth.h"

// Built-in device registration (one call per process, idempotent). Types
// register here as their milestones land - the frozen DeviceTypeId wire
// numbers gain factories over time, and projects referencing types that
// haven't landed keep compiling working mixers (builder skips + counts).

namespace daw {

namespace {

std::unique_ptr<DeviceNode> makeSubtractiveSynth() {
    return std::make_unique<SubtractiveSynth>();
}

} // namespace

void registerBuiltinDevices() {
    static const bool once = [] {
        DeviceRegistry& r = DeviceRegistry::instance();
        r.registerType(DeviceTypeId::SubtractiveSynth, "Subtractive Synth",
                       &makeSubtractiveSynth, kSubtractiveParams,
                       SubtractiveSynth::kParamCount, /*isInstrument=*/true);
        // M4 continues: WavetableSynth, FmSynth. M5: DrumRack, samplers.
        // M8-M10: the effects waves.
        return true;
    }();
    (void)once;
}

} // namespace daw
