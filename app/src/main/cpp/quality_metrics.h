#ifndef FRAMEFORGE_QUALITY_METRICS_H
#define FRAMEFORGE_QUALITY_METRICS_H

#include "frameforge_types.h"

namespace frameforge {

class QualityEngine {
public:
    static double computePsnr(
        const uint8_t* refData,
        const uint8_t* testData,
        int width,
        int height,
        int refStride,
        int testStride
    );

    static double computeSsim(
        const uint8_t* refData,
        const uint8_t* testData,
        int width,
        int height,
        int refStride,
        int testStride
    );

    static double computeTemporalFlicker(
        const uint8_t* prevFrame,
        const uint8_t* currentFrame,
        const uint8_t* nextFrame,
        int width,
        int height,
        int stride
    );

    static bool isSceneCut(
        const uint8_t* frame0,
        const uint8_t* frame1,
        int width,
        int height,
        int stride,
        float threshold = 0.50f
    );
};

} // namespace frameforge

#endif // FRAMEFORGE_QUALITY_METRICS_H
