#ifndef FRAMEFORGE_VIDEO_RESTORATION_H
#define FRAMEFORGE_VIDEO_RESTORATION_H

#include "frameforge_types.h"

namespace frameforge {

class VideoRestorationEngine {
public:
    static bool applyRestoration(
        const uint8_t* currentFrame,
        const uint8_t* prevFrame,
        const uint8_t* nextFrame,
        uint8_t* outputFrame,
        int width,
        int height,
        int stride,
        float denoiseStrength = 0.5f,
        float deblockStrength = 0.4f,
        float sharpenStrength = 0.3f
    );

    static void applyBilateralDenoise(
        const uint8_t* src,
        uint8_t* dst,
        int width,
        int height,
        int stride,
        float spatialSigma,
        float rangeSigma
    );

    static void applyDeblocking(
        const uint8_t* src,
        uint8_t* dst,
        int width,
        int height,
        int stride,
        float strength
    );

    static void applyTemporalSmoothing(
        const uint8_t* prev,
        const uint8_t* curr,
        const uint8_t* next,
        uint8_t* dst,
        int width,
        int height,
        int stride,
        float temporalWeight = 0.2f
    );
};

} // namespace frameforge

#endif // FRAMEFORGE_VIDEO_RESTORATION_H
