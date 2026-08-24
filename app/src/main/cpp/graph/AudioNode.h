#pragma once

#include <string>
#include <vector>
#include <memory>
#include "../core/ProcessContext.h"

enum class NodeType {
    TRACK,
    GROUP_TRACK,
    RETURN_TRACK,
    MASTER,
    INSTRUMENT,
    EFFECT
};

/**
 * Base Abstract Audio Node in Directed Acyclic Graph (DAG).
 */
class AudioNode {
public:
    AudioNode(std::string id, NodeType type)
        : id_(std::move(id)), type_(type), isEnabled_(true), isMuted_(false), isSoloed_(false) {}

    virtual ~AudioNode() = default;

    virtual void prepareToPlay(double sampleRate, size_t maxBlockSize) = 0;
    virtual void process(const ProcessContext& ctx, float** inBuffers, float** outBuffers) = 0;
    virtual void releaseResources() = 0;

    const std::string& getId() const noexcept { return id_; }
    NodeType getType() const noexcept { return type_; }

    bool isEnabled() const noexcept { return isEnabled_; }
    void setEnabled(bool enabled) noexcept { isEnabled_ = enabled; }

    bool isMuted() const noexcept { return isMuted_; }
    void setMuted(bool muted) noexcept { isMuted_ = muted; }

    bool isSoloed() const noexcept { return isSoloed_; }
    void setSoloed(bool soloed) noexcept { isSoloed_ = soloed; }

protected:
    std::string id_;
    NodeType type_;
    bool isEnabled_{true};
    bool isMuted_{false};
    bool isSoloed_{false};
};
