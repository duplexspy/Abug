#ifndef FRAMEFORGE_TYPES_H
#define FRAMEFORGE_TYPES_H

#include <cstdint>
#include <cstddef>
#include <vector>

namespace frameforge {

enum class PrecisionMode {
    FP32 = 0,
    FP16 = 1,
    INT8 = 2
};

enum class ComputeBackend {
    CPU = 0,
    VULKAN_GPU = 1,
    AUTO = 2
};

enum class UpscaleModelType {
    REAL_ESRGAN_X4PLUS = 0,
    REAL_ESRGAN_X2PLUS = 1,
    REAL_ESR_GENERAL_X4V3 = 2,
    REAL_ESRGAN_ANIME_6B = 3,
    REAL_ESR_ANIME_VIDEO_V3 = 4
};

enum class InterpolationMode {
    RIFE_2X = 2,
    RIFE_4X = 4,
    RIFE_8X = 8,
    CUSTOM_TIMESTEP = 0
};

struct FrameBuffer {
    int width = 0;
    int height = 0;
    int channels = 4; // RGBA
    int stride = 0;
    uint8_t* data = nullptr;

    inline uint8_t* getPixel(int x, int y) const {
        return data + y * stride + x * channels;
    }
};

struct TileRect {
    int x = 0;
    int y = 0;
    int width = 0;
    int height = 0;
    int pad_left = 0;
    int pad_top = 0;
    int pad_right = 0;
    int pad_bottom = 0;
};

struct QualityMetrics {
    double psnr = 0.0;
    double ssim = 0.0;
    double temporalFlickerScore = 0.0;
    double lumaVariance = 0.0;
};

struct HardwareCapabilities {
    bool hasVulkan = false;
    bool hasFp16Compute = false;
    int cpuCores = 4;
    size_t totalRamMb = 0;
    size_t availableRamMb = 0;
    int recommendedTileSize = 128;
    const char* gpuDeviceName = "Generic Adreno/Mali GPU";
};

} // namespace frameforge

#endif // FRAMEFORGE_TYPES_H
