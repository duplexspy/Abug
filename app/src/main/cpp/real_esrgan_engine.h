#ifndef FRAMEFORGE_REAL_ESRGAN_ENGINE_H
#define FRAMEFORGE_REAL_ESRGAN_ENGINE_H

#include "frameforge_types.h"
#include <string>
#include <memory>
#include <functional>
#include <ncnn/net.h>

namespace frameforge {

class RealEsrganEngine {
public:
    RealEsrganEngine();
    ~RealEsrganEngine();

    bool loadModel(
        const std::string& modelPath,
        UpscaleModelType modelType,
        int scale,
        PrecisionMode precision,
        ComputeBackend backend
    );

    bool upscale(
        const uint8_t* inputRgba,
        int inWidth,
        int inHeight,
        int inStride,
        uint8_t* outputRgba,
        int outWidth,
        int outHeight,
        int outStride,
        int scale,
        int tileSize = 0,
        int overlapPadding = 16,
        std::function<void(int current, int total)> progressCallback = nullptr
    );

    int getScale() const { return m_scale; }
    bool isLoaded() const { return m_isLoaded; }

private:
    bool processTile(
        const uint8_t* tileIn,
        int tileInW,
        int tileInH,
        int tileInStride,
        uint8_t* tileOut,
        int tileOutW,
        int tileOutH,
        int tileOutStride,
        int scale
    );

    ncnn::Net m_net;
    bool m_isLoaded = false;
    int m_scale = 4;
    UpscaleModelType m_modelType = UpscaleModelType::REAL_ESRGAN_X4PLUS;
    PrecisionMode m_precision = PrecisionMode::FP16;
    ComputeBackend m_backend = ComputeBackend::AUTO;
    std::string m_modelPath;
};

} // namespace frameforge

#endif // FRAMEFORGE_REAL_ESRGAN_ENGINE_H
