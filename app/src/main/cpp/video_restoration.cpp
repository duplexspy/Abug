#include "video_restoration.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>

namespace frameforge {

void VideoRestorationEngine::applyBilateralDenoise(
    const uint8_t* src,
    uint8_t* dst,
    int width,
    int height,
    int stride,
    float spatialSigma,
    float rangeSigma
) {
    int radius = 2;
    float twoSpatialSq = 2.0f * spatialSigma * spatialSigma;
    float twoRangeSq = 2.0f * rangeSigma * rangeSigma;

    for (int y = 0; y < height; ++y) {
        const uint8_t* srcRow = src + y * stride;
        uint8_t* dstRow = dst + y * stride;

        for (int x = 0; x < width; ++x) {
            const uint8_t* centerPx = srcRow + x * 4;
            float rSum = 0.0f, gSum = 0.0f, bSum = 0.0f;
            float weightSum = 0.0f;

            for (int dy = -radius; dy <= radius; ++dy) {
                int ny = std::clamp(y + dy, 0, height - 1);
                const uint8_t* nRow = src + ny * stride;

                for (int dx = -radius; dx <= radius; ++dx) {
                    int nx = std::clamp(x + dx, 0, width - 1);
                    const uint8_t* nPx = nRow + nx * 4;

                    float spatialDistSq = (float)(dx * dx + dy * dy);
                    float rangeDistSq = (float)(
                        (centerPx[0] - nPx[0]) * (centerPx[0] - nPx[0]) +
                        (centerPx[1] - nPx[1]) * (centerPx[1] - nPx[1]) +
                        (centerPx[2] - nPx[2]) * (centerPx[2] - nPx[2])
                    );

                    float weight = std::exp(-spatialDistSq / twoSpatialSq - rangeDistSq / twoRangeSq);
                    rSum += (float)nPx[0] * weight;
                    gSum += (float)nPx[1] * weight;
                    bSum += (float)nPx[2] * weight;
                    weightSum += weight;
                }
            }

            if (weightSum > 1e-5f) {
                dstRow[x * 4]     = (uint8_t)std::clamp(rSum / weightSum, 0.0f, 255.0f);
                dstRow[x * 4 + 1] = (uint8_t)std::clamp(gSum / weightSum, 0.0f, 255.0f);
                dstRow[x * 4 + 2] = (uint8_t)std::clamp(bSum / weightSum, 0.0f, 255.0f);
            } else {
                dstRow[x * 4]     = centerPx[0];
                dstRow[x * 4 + 1] = centerPx[1];
                dstRow[x * 4 + 2] = centerPx[2];
            }
            dstRow[x * 4 + 3] = 255;
        }
    }
}

void VideoRestorationEngine::applyDeblocking(
    const uint8_t* src,
    uint8_t* dst,
    int width,
    int height,
    int stride,
    float strength
) {
    // 8x8 block boundary smoothing filter (standard H.264/MPEG deblock filter)
    std::memcpy(dst, src, (size_t)stride * height);
    if (strength <= 0.01f) return;

    for (int y = 8; y < height; y += 8) {
        uint8_t* p0 = dst + (y - 1) * stride;
        uint8_t* q0 = dst + y * stride;
        for (int x = 0; x < width; ++x) {
            for (int c = 0; c < 3; ++c) {
                int delta = (int)q0[x * 4 + c] - (int)p0[x * 4 + c];
                if (std::abs(delta) < 32) { // Edge is an artifact, not an object boundary
                    int adjustment = (int)((float)delta * strength * 0.5f);
                    p0[x * 4 + c] = (uint8_t)std::clamp((int)p0[x * 4 + c] + adjustment, 0, 255);
                    q0[x * 4 + c] = (uint8_t)std::clamp((int)q0[x * 4 + c] - adjustment, 0, 255);
                }
            }
        }
    }
}

void VideoRestorationEngine::applyTemporalSmoothing(
    const uint8_t* prev,
    const uint8_t* curr,
    const uint8_t* next,
    uint8_t* dst,
    int width,
    int height,
    int stride,
    float temporalWeight
) {
    if (!prev || !next) {
        std::memcpy(dst, curr, (size_t)stride * height);
        return;
    }

    float wCurr = 1.0f - 2.0f * temporalWeight;
    for (int y = 0; y < height; ++y) {
        const uint8_t* pRow = prev + y * stride;
        const uint8_t* cRow = curr + y * stride;
        const uint8_t* nRow = next + y * stride;
        uint8_t* dRow = dst + y * stride;

        for (int x = 0; x < width; ++x) {
            for (int c = 0; c < 3; ++c) {
                float val = (float)pRow[x * 4 + c] * temporalWeight +
                            (float)cRow[x * 4 + c] * wCurr +
                            (float)nRow[x * 4 + c] * temporalWeight;
                dRow[x * 4 + c] = (uint8_t)std::clamp(val, 0.0f, 255.0f);
            }
            dRow[x * 4 + 3] = 255;
        }
    }
}

bool VideoRestorationEngine::applyRestoration(
    const uint8_t* currentFrame,
    const uint8_t* prevFrame,
    const uint8_t* nextFrame,
    uint8_t* outputFrame,
    int width,
    int height,
    int stride,
    float denoiseStrength,
    float deblockStrength,
    float sharpenStrength
) {
    if (!currentFrame || !outputFrame || width <= 0 || height <= 0) return false;

    std::vector<uint8_t> temp1((size_t)stride * height);
    std::vector<uint8_t> temp2((size_t)stride * height);

    // 1. Deblocking
    applyDeblocking(currentFrame, temp1.data(), width, height, stride, deblockStrength);

    // 2. Bilateral Denoise
    float spatialSigma = 1.5f + denoiseStrength * 1.5f;
    float rangeSigma = 15.0f + denoiseStrength * 30.0f;
    applyBilateralDenoise(temp1.data(), temp2.data(), width, height, stride, spatialSigma, rangeSigma);

    // 3. Temporal consistency smoothing
    if (prevFrame && nextFrame) {
        applyTemporalSmoothing(prevFrame, temp2.data(), nextFrame, temp1.data(), width, height, stride, 0.15f);
    } else {
        std::memcpy(temp1.data(), temp2.data(), (size_t)stride * height);
    }

    // 4. Detail Sharpening (Unsharp Masking)
    std::memcpy(outputFrame, temp1.data(), (size_t)stride * height);
    if (sharpenStrength > 0.01f) {
        for (int y = 1; y < height - 1; ++y) {
            const uint8_t* rowPrev = temp1.data() + (y - 1) * stride;
            const uint8_t* rowCurr = temp1.data() + y * stride;
            const uint8_t* rowNext = temp1.data() + (y + 1) * stride;
            uint8_t* outRow = outputFrame + y * stride;

            for (int x = 1; x < width - 1; ++x) {
                for (int c = 0; c < 3; ++c) {
                    float center = (float)rowCurr[x * 4 + c];
                    float laplacian = 4.0f * center -
                                     ((float)rowPrev[x * 4 + c] +
                                      (float)rowNext[x * 4 + c] +
                                      (float)rowCurr[(x - 1) * 4 + c] +
                                      (float)rowCurr[(x + 1) * 4 + c]);

                    float sharpened = center + laplacian * sharpenStrength * 0.4f;
                    outRow[x * 4 + c] = (uint8_t)std::clamp(sharpened, 0.0f, 255.0f);
                }
                outRow[x * 4 + 3] = 255;
            }
        }
    }

    return true;
}

} // namespace frameforge
