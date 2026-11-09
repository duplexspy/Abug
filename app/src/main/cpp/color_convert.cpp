#include "color_convert.h"
#include <algorithm>
#include <cmath>

namespace frameforge {

void ColorConverter::rgbaToRgbPlanarFloat(
    const uint8_t* rgba,
    int width,
    int height,
    int stride,
    float* planarRgb,
    bool normalizeToUnit
) {
    int planeSize = width * height;
    float* rPlane = planarRgb;
    float* gPlane = planarRgb + planeSize;
    float* bPlane = planarRgb + planeSize * 2;
    float scale = normalizeToUnit ? (1.0f / 255.0f) : 1.0f;

    for (int y = 0; y < height; ++y) {
        const uint8_t* row = rgba + y * stride;
        int rowIdx = y * width;
        for (int x = 0; x < width; ++x) {
            const uint8_t* px = row + x * 4;
            int idx = rowIdx + x;
            rPlane[idx] = (float)px[0] * scale;
            gPlane[idx] = (float)px[1] * scale;
            bPlane[idx] = (float)px[2] * scale;
        }
    }
}

void ColorConverter::rgbPlanarFloatToRgba(
    const float* planarRgb,
    int width,
    int height,
    uint8_t* rgba,
    int stride,
    bool denormalizeFromUnit
) {
    int planeSize = width * height;
    const float* rPlane = planarRgb;
    const float* gPlane = planarRgb + planeSize;
    const float* bPlane = planarRgb + planeSize * 2;
    float scale = denormalizeFromUnit ? 255.0f : 1.0f;

    for (int y = 0; y < height; ++y) {
        uint8_t* row = rgba + y * stride;
        int rowIdx = y * width;
        for (int x = 0; x < width; ++x) {
            int idx = rowIdx + x;
            uint8_t* px = row + x * 4;
            px[0] = (uint8_t)std::clamp(rPlane[idx] * scale, 0.0f, 255.0f);
            px[1] = (uint8_t)std::clamp(gPlane[idx] * scale, 0.0f, 255.0f);
            px[2] = (uint8_t)std::clamp(bPlane[idx] * scale, 0.0f, 255.0f);
            px[3] = 255;
        }
    }
}

void ColorConverter::yuvPlanesToRgba(
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
) {
    if (!yPlane || !uPlane || !vPlane || !rgbaOut || width <= 0 || height <= 0) return;

    for (int j = 0; j < height; ++j) {
        const uint8_t* yRow = yPlane + j * yRowStride;
        const uint8_t* uRow = uPlane + (j >> 1) * uRowStride;
        const uint8_t* vRow = vPlane + (j >> 1) * vRowStride;
        uint8_t* outRow = rgbaOut + j * rgbaStride;

        for (int i = 0; i < width; ++i) {
            int yVal = (int)yRow[i * yPixelStride] - 16;
            if (yVal < 0) yVal = 0;

            int uvCol = (i >> 1);
            int uVal = (int)uRow[uvCol * uPixelStride] - 128;
            int vVal = (int)vRow[uvCol * vPixelStride] - 128;

            int y1192 = 1192 * yVal;
            int r = (y1192 + 1634 * vVal);
            int g = (y1192 - 833 * vVal - 400 * uVal);
            int b = (y1192 + 2066 * uVal);

            uint8_t* px = outRow + i * 4;
            px[0] = (uint8_t)std::clamp(r >> 10, 0, 255);
            px[1] = (uint8_t)std::clamp(g >> 10, 0, 255);
            px[2] = (uint8_t)std::clamp(b >> 10, 0, 255);
            px[3] = 255;
        }
    }
}

void ColorConverter::yuv420spToRgba(
    const uint8_t* yuvData,
    int width,
    int height,
    uint8_t* rgbaOut,
    int rgbaStride,
    bool isNv21
) {
    int frameSize = width * height;
    const uint8_t* yPlane = yuvData;
    const uint8_t* uvPlane = yuvData + frameSize;

    const uint8_t* uPlane = isNv21 ? (uvPlane + 1) : uvPlane;
    const uint8_t* vPlane = isNv21 ? uvPlane : (uvPlane + 1);

    yuvPlanesToRgba(
        yPlane, width, 1,
        uPlane, width, 2,
        vPlane, width, 2,
        rgbaOut, width, height, rgbaStride
    );
}

void ColorConverter::rgbaToYuv(
    const uint8_t* rgbaData,
    int width,
    int height,
    int rgbaStride,
    uint8_t* yuvOut,
    int formatMode // 0 = NV12, 1 = NV21, 2 = I420
) {
    if (!rgbaData || !yuvOut || width <= 0 || height <= 0) return;

    int frameSize = width * height;
    uint8_t* yPlane = yuvOut;
    uint8_t* uvPlane = yuvOut + frameSize;
    uint8_t* uPlane = yuvOut + frameSize;
    uint8_t* vPlane = yuvOut + frameSize + (frameSize / 4);

    for (int j = 0; j < height; ++j) {
        const uint8_t* inRow = rgbaData + j * rgbaStride;
        uint8_t* yRow = yPlane + j * width;

        for (int i = 0; i < width; ++i) {
            const uint8_t* px = inRow + i * 4;
            int r = px[0];
            int g = px[1];
            int b = px[2];

            // Standard ITU-R BT.601 limited-range formula
            int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
            yRow[i] = (uint8_t)std::clamp(y, 16, 235);
        }
    }

    // Chroma 2x2 box-filter downsampling for artifact-free color fidelity
    for (int j = 0; j < height; j += 2) {
        const uint8_t* row0 = rgbaData + j * rgbaStride;
        const uint8_t* row1 = (j + 1 < height) ? (rgbaData + (j + 1) * rgbaStride) : row0;

        for (int i = 0; i < width; i += 2) {
            int iNext = (i + 1 < width) ? (i + 1) : i;

            const uint8_t* px00 = row0 + i * 4;
            const uint8_t* px01 = row0 + iNext * 4;
            const uint8_t* px10 = row1 + i * 4;
            const uint8_t* px11 = row1 + iNext * 4;

            int rAvg = (px00[0] + px01[0] + px10[0] + px11[0] + 2) >> 2;
            int gAvg = (px00[1] + px01[1] + px10[1] + px11[1] + 2) >> 2;
            int bAvg = (px00[2] + px01[2] + px10[2] + px11[2] + 2) >> 2;

            int u = ((-38 * rAvg - 74 * gAvg + 112 * bAvg + 128) >> 8) + 128;
            int v = ((112 * rAvg - 94 * gAvg - 18 * bAvg + 128) >> 8) + 128;

            uint8_t uByte = (uint8_t)std::clamp(u, 16, 240);
            uint8_t vByte = (uint8_t)std::clamp(v, 16, 240);

            if (formatMode == 0) { // NV12: interleaved U, V
                int uvIdx = (j >> 1) * width + i;
                uvPlane[uvIdx] = uByte;
                uvPlane[uvIdx + 1] = vByte;
            } else if (formatMode == 1) { // NV21: interleaved V, U
                int uvIdx = (j >> 1) * width + i;
                uvPlane[uvIdx] = vByte;
                uvPlane[uvIdx + 1] = uByte;
            } else { // I420: Planar U plane followed by V plane
                int chromaIdx = (j >> 1) * (width >> 1) + (i >> 1);
                uPlane[chromaIdx] = uByte;
                vPlane[chromaIdx] = vByte;
            }
        }
    }
}

void ColorConverter::rgbaToYuv420sp(
    const uint8_t* rgbaData,
    int width,
    int height,
    int rgbaStride,
    uint8_t* yuvOut,
    bool isNv21
) {
    rgbaToYuv(rgbaData, width, height, rgbaStride, yuvOut, isNv21 ? 1 : 0);
}

} // namespace frameforge
