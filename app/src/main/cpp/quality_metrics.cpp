#include "quality_metrics.h"
#include <cmath>
#include <algorithm>
#include <vector>

namespace frameforge {

double QualityEngine::computePsnr(
    const uint8_t* refData,
    const uint8_t* testData,
    int width,
    int height,
    int refStride,
    int testStride
) {
    if (width <= 0 || height <= 0) return 0.0;

    double mse = 0.0;
    size_t totalSamples = (size_t)width * height * 3;

    for (int y = 0; y < height; ++y) {
        const uint8_t* rRow = refData + y * refStride;
        const uint8_t* tRow = testData + y * testStride;
        for (int x = 0; x < width; ++x) {
            int dr = (int)rRow[x * 4] - (int)tRow[x * 4];
            int dg = (int)rRow[x * 4 + 1] - (int)tRow[x * 4 + 1];
            int db = (int)rRow[x * 4 + 2] - (int)tRow[x * 4 + 2];
            mse += (double)(dr * dr + dg * dg + db * db);
        }
    }

    mse /= (double)totalSamples;
    if (mse < 1e-10) return 99.99; // identical

    double maxVal = 255.0;
    return 10.0 * std::log10((maxVal * maxVal) / mse);
}

double QualityEngine::computeSsim(
    const uint8_t* refData,
    const uint8_t* testData,
    int width,
    int height,
    int refStride,
    int testStride
) {
    if (width < 8 || height < 8) return 1.0;

    const double C1 = 6.5025;   // (0.01 * 255)^2
    const double C2 = 58.5225;  // (0.03 * 255)^2

    double totalSsim = 0.0;
    int windowCount = 0;

    for (int y = 0; y <= height - 8; y += 8) {
        for (int x = 0; x <= width - 8; x += 8) {
            double meanX = 0.0, meanY = 0.0;
            for (int wy = 0; wy < 8; ++wy) {
                const uint8_t* rRow = refData + (y + wy) * refStride + x * 4;
                const uint8_t* tRow = testData + (y + wy) * testStride + x * 4;
                for (int wx = 0; wx < 8; ++wx) {
                    double lumRef = 0.299 * rRow[wx * 4] + 0.587 * rRow[wx * 4 + 1] + 0.114 * rRow[wx * 4 + 2];
                    double lumTest = 0.299 * tRow[wx * 4] + 0.587 * tRow[wx * 4 + 1] + 0.114 * tRow[wx * 4 + 2];
                    meanX += lumRef;
                    meanY += lumTest;
                }
            }
            meanX /= 64.0;
            meanY /= 64.0;

            double varX = 0.0, varY = 0.0, covarXY = 0.0;
            for (int wy = 0; wy < 8; ++wy) {
                const uint8_t* rRow = refData + (y + wy) * refStride + x * 4;
                const uint8_t* tRow = testData + (y + wy) * testStride + x * 4;
                for (int wx = 0; wx < 8; ++wx) {
                    double lumRef = 0.299 * rRow[wx * 4] + 0.587 * rRow[wx * 4 + 1] + 0.114 * rRow[wx * 4 + 2];
                    double lumTest = 0.299 * tRow[wx * 4] + 0.587 * tRow[wx * 4 + 1] + 0.114 * tRow[wx * 4 + 2];
                    double dx = lumRef - meanX;
                    double dy = lumTest - meanY;
                    varX += dx * dx;
                    varY += dy * dy;
                    covarXY += dx * dy;
                }
            }
            varX /= 63.0;
            varY /= 63.0;
            covarXY /= 63.0;

            double ssim = ((2.0 * meanX * meanY + C1) * (2.0 * covarXY + C2)) /
                          ((meanX * meanX + meanY * meanY + C1) * (varX + varY + C2));
            totalSsim += ssim;
            windowCount++;
        }
    }

    return (windowCount > 0) ? (totalSsim / (double)windowCount) : 1.0;
}

double QualityEngine::computeTemporalFlicker(
    const uint8_t* prevFrame,
    const uint8_t* currentFrame,
    const uint8_t* nextFrame,
    int width,
    int height,
    int stride
) {
    if (!prevFrame || !currentFrame || !nextFrame || width <= 0 || height <= 0) return 0.0;

    double flickerSum = 0.0;
    size_t count = 0;

    for (int y = 0; y < height; y += 2) {
        const uint8_t* pRow = prevFrame + y * stride;
        const uint8_t* cRow = currentFrame + y * stride;
        const uint8_t* nRow = nextFrame + y * stride;

        for (int x = 0; x < width; x += 2) {
            double lumP = 0.299 * pRow[x * 4] + 0.587 * pRow[x * 4 + 1] + 0.114 * pRow[x * 4 + 2];
            double lumC = 0.299 * cRow[x * 4] + 0.587 * cRow[x * 4 + 1] + 0.114 * cRow[x * 4 + 2];
            double lumN = 0.299 * nRow[x * 4] + 0.587 * nRow[x * 4 + 1] + 0.114 * nRow[x * 4 + 2];

            // 2nd order discrete derivative across time: |lumN - 2*lumC + lumP|
            double d2 = std::abs(lumN - 2.0 * lumC + lumP);
            flickerSum += d2;
            count++;
        }
    }

    return (count > 0) ? (flickerSum / (double)count) : 0.0;
}

bool QualityEngine::isSceneCut(
    const uint8_t* frame0,
    const uint8_t* frame1,
    int width,
    int height,
    int stride,
    float threshold
) {
    if (!frame0 || !frame1 || width <= 0 || height <= 0) return false;

    // Calculate SAD (Sum of Absolute Differences) normalized
    double sad = 0.0;
    size_t sampled = 0;

    for (int y = 0; y < height; y += 4) {
        const uint8_t* r0 = frame0 + y * stride;
        const uint8_t* r1 = frame1 + y * stride;
        for (int x = 0; x < width; x += 4) {
            int dr = std::abs((int)r0[x * 4] - (int)r1[x * 4]);
            int dg = std::abs((int)r0[x * 4 + 1] - (int)r1[x * 4 + 1]);
            int db = std::abs((int)r0[x * 4 + 2] - (int)r1[x * 4 + 2]);
            sad += (double)(dr + dg + db);
            sampled += 3;
        }
    }

    double normalizedDiff = sad / (double)(sampled * 255.0);
    return normalizedDiff > (double)threshold;
}

} // namespace frameforge
