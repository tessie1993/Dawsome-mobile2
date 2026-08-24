#pragma once

#include "DeviceNode.h"

/**
 * Base abstract class for Polyphonic and Monophonic Instruments.
 */
class InstrumentNode : public DeviceNode {
public:
    InstrumentNode(std::string id) : DeviceNode(std::move(id), NodeType::INSTRUMENT) {}
    ~InstrumentNode() override = default;

    virtual void noteOn(int noteNumber, float velocity) = 0;
    virtual void noteOff(int noteNumber) = 0;
    virtual void allNotesOff() = 0;
    virtual void setPitchBend(float bendSemitones) = 0;
    virtual void setModWheel(float modWheel) = 0;

    int getPolyphony() const noexcept { return polyphony_; }
    void setPolyphony(int polyphony) noexcept { polyphony_ = polyphony; }

protected:
    int polyphony_{16};
};
