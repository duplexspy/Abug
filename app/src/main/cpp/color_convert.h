#ifndef FRAMEFORGE_COLOR_CONVERT_H
#define FRAMEFORGE_COLOR_CONVERT_H

#include <cstdint>
#include <cstddef>

namespace frameforge {

class ColorConverter {
public:
    static void rgbaToRgbPlanarFloat(
        const uint8_t* rgba,
        int width,
        int height,
        int stride,
        float* planarRgb,
        bool normalizeToUnit = true
    );

    static void rgbPlanarFloatToRgba(
        const float* planarRgb,
        int width,
        int height,
        uint8_t* rgba,
        int stride,
        bool denormalizeFromUnit = true
    );

    static void yuv420spToRgba(
        const uint8_t* yuvData,
        int width,
        int height,
        uint8_t* rgbaOut,
        int rgbaStride,
        bool isNv21 = false
    );

    static void yuvPlanesToRgba(
        const uint8_t* yPlane,
        int yRowStride,
        int yPixelStride,
        const uint8_t* uPlane,
        int uRowStride,
        int uPixelStride,
        const uint8_t* vPlane,
        int vRowStride,
        int vPixelStride,
        uint8_t* rgbaOut,
        int width,
        int height,
        int rgbaStride
    );

    static void rgbaToYuv(
        const uint8_t* rgbaData,
        int width,
        int height,
        int rgbaStride,
        uint8_t* yuvOut,
        int formatMode = 0 // 0 = NV12, 1 = NV21, 2 = I420
    );

    static void rgbaToYuv420sp(
        const uint8_t* rgbaData,
        int width,
        int height,
        int rgbaStride,
        uint8_t* yuvOut,
        bool isNv21 = false
    );
};

} // namespace frameforge

#endif // FRAMEFORGE_COLOR_CONVERT_H
