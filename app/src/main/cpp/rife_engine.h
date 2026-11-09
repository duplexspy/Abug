#ifndef FRAMEFORGE_RIFE_ENGINE_H
#define FRAMEFORGE_RIFE_ENGINE_H

#include "frameforge_types.h"
#include <string>
#include <vector>
#include <memory>
#include <ncnn/net.h>

namespace frameforge {

class RifeEngine {
public:
    RifeEngine();
    ~RifeEngine();

    bool loadModel(
        const std::string& modelPath,
        PrecisionMode precision,
        ComputeBackend backend
    );

    bool interpolate(
        const uint8_t* frame0,
        const uint8_t* frame1,
        uint8_t* outputFrame,
        int width,
        int height,
        int stride,
        float timestep = 0.5f,
        float sceneCutThreshold = 0.35f
    );

    bool isLoaded() const { return m_isLoaded; }

private:
    ncnn::Net m_net;
    bool m_isLoaded = false;
    PrecisionMode m_precision = PrecisionMode::FP16;
    ComputeBackend m_backend = ComputeBackend::AUTO;
    std::string m_modelPath;
};

} // namespace frameforge

#endif // FRAMEFORGE_RIFE_ENGINE_H
