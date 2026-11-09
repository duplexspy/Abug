#include "real_esrgan_engine.h"
#include "tile_engine.h"
#include "color_convert.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>
#include <fstream>
#include <iostream>

namespace frameforge {

RealEsrganEngine::RealEsrganEngine() = default;
RealEsrganEngine::~RealEsrganEngine() {
    m_net.clear();
}

bool RealEsrganEngine::loadModel(
    const std::string& modelPath,
    UpscaleModelType modelType,
    int scale,
    PrecisionMode precision,
    ComputeBackend backend
) {
    m_isLoaded = false;
    m_net.clear();

    m_modelPath = modelPath;
    m_modelType = modelType;
    m_precision = precision;
    m_backend = backend;
    m_scale = (scale > 0) ? scale : ((modelType == UpscaleModelType::REAL_ESRGAN_X2PLUS) ? 2 : 4);

    // Resolve .param and .bin files
    std::string paramPath;
    std::string binPath;

    if (modelPath.length() >= 4 && modelPath.substr(modelPath.length() - 4) == ".bin") {
        binPath = modelPath;
        paramPath = modelPath.substr(0, modelPath.length() - 4) + ".param";
    } else if (modelPath.length() >= 6 && modelPath.substr(modelPath.length() - 6) == ".param") {
        paramPath = modelPath;
        binPath = modelPath.substr(0, modelPath.length() - 6) + ".bin";
    } else {
        paramPath = modelPath + ".param";
        binPath = modelPath + ".bin";
    }

    // Verify files exist and are readable
    std::ifstream pfile(paramPath, std::ios::binary);
    if (!pfile.is_open()) {
        std::cerr << "[RealEsrganEngine] Failed to open param file: " << paramPath << std::endl;
        return false;
    }
    pfile.close();

    std::ifstream bfile(binPath, std::ios::binary);
    if (!bfile.is_open()) {
        std::cerr << "[RealEsrganEngine] Failed to open bin file: " << binPath << std::endl;
        return false;
    }
    bfile.close();

    // Configure NCNN options
    m_net.opt.lightmode = true;
    m_net.opt.num_threads = 4;
    // On ARM64 CPU without ARM82 hardware fp16 arithmetic, use fast 32-bit NEON execution to avoid slow emulation
    m_net.opt.use_fp16_packed = false;
    m_net.opt.use_fp16_storage = false;
    m_net.opt.use_fp16_arithmetic = false;
    m_net.opt.use_packing_layout = true;

    int retParam = m_net.load_param(paramPath.c_str());
    if (retParam != 0) {
        std::cerr << "[RealEsrganEngine] load_param failed with code: " << retParam << std::endl;
        return false;
    }

    int retModel = m_net.load_model(binPath.c_str());
    if (retModel != 0) {
        std::cerr << "[RealEsrganEngine] load_model failed with code: " << retModel << std::endl;
        return false;
    }

    m_isLoaded = true;
    std::cout << "[RealEsrganEngine] Successfully loaded model: " << binPath << " (scale=" << m_scale << ")" << std::endl;
    return true;
}

bool RealEsrganEngine::processTile(
    const uint8_t* tileIn,
    int tileInW,
    int tileInH,
    int tileInStride,
    uint8_t* tileOut,
    int tileOutW,
    int tileOutH,
    int tileOutStride,
    int scale
) {
    if (!m_isLoaded) return false;

    // Convert input RGBA tile to NCNN Mat RGB in [0, 1]
    ncnn::Mat inMat = ncnn::Mat::from_pixels(tileIn, ncnn::Mat::PIXEL_RGBA2RGB, tileInW, tileInH, tileInStride);
    if (inMat.empty()) return false;

    const float norm_vals[3] = { 1.0f / 255.0f, 1.0f / 255.0f, 1.0f / 255.0f };
    inMat.substract_mean_normalize(0, norm_vals);

    ncnn::Extractor ex = m_net.create_extractor();
    ex.input("data", inMat);

    ncnn::Mat outMat;
    int ret = ex.extract("output", outMat);
    if (ret != 0 || outMat.empty()) {
        std::cerr << "[RealEsrganEngine] Inference failed with ret=" << ret << std::endl;
        return false;
    }

    // Convert back from [0, 1] float to [0, 255]
    const float denorm_vals[3] = { 255.0f, 255.0f, 255.0f };
    outMat.substract_mean_normalize(0, denorm_vals);

    // If neural output dimensions differ from expected tileOutW/tileOutH (e.g. model is x4 but x2 was requested)
    if (outMat.w != tileOutW || outMat.h != tileOutH) {
        ncnn::Mat resizedOut;
        ncnn::resize_bilinear(outMat, resizedOut, tileOutW, tileOutH);
        resizedOut.to_pixels(tileOut, ncnn::Mat::PIXEL_RGB2RGBA, tileOutStride);
    } else {
        outMat.to_pixels(tileOut, ncnn::Mat::PIXEL_RGB2RGBA, tileOutStride);
    }

    // Ensure alpha is 255
    for (int y = 0; y < tileOutH; ++y) {
        uint8_t* row = tileOut + y * tileOutStride;
        for (int x = 0; x < tileOutW; ++x) {
            row[x * 4 + 3] = 255;
        }
    }

    return true;
}

bool RealEsrganEngine::upscale(
    const uint8_t* inputRgba,
    int inWidth,
    int inHeight,
    int inStride,
    uint8_t* outputRgba,
    int outWidth,
    int outHeight,
    int outStride,
    int scale,
    int tileSize,
    int overlapPadding,
    std::function<void(int current, int total)> progressCallback
) {
    if (!m_isLoaded || !inputRgba || !outputRgba || inWidth <= 0 || inHeight <= 0) return false;
    if (scale <= 0) scale = m_scale;

    // Use optimal tileSize (256x256 reduces tile count by 4x compared to 128x128 while fitting in memory)
    if (tileSize <= 0) {
        if (inWidth <= 256 && inHeight <= 256) {
            tileSize = std::max(inWidth, inHeight);
        } else {
            tileSize = 256;
        }
    }

    std::vector<TileRect> tiles = TileEngine::computeTiles(inWidth, inHeight, tileSize, overlapPadding);

    for (size_t i = 0; i < tiles.size(); ++i) {
        const auto& tile = tiles[i];
        int tileOutW = tile.width * scale;
        int tileOutH = tile.height * scale;
        int tileInStride = tile.width * 4;
        int tileOutStride = tileOutW * 4;

        std::vector<uint8_t> tileInBuf(tileInStride * tile.height);
        std::vector<uint8_t> tileOutBuf(tileOutStride * tileOutH);

        // Copy input tile slice
        for (int ty = 0; ty < tile.height; ++ty) {
            const uint8_t* src = inputRgba + (tile.y + ty) * inStride + tile.x * 4;
            uint8_t* dst = tileInBuf.data() + ty * tileInStride;
            std::memcpy(dst, src, tileInStride);
        }

        // Process tile with real neural super-resolution
        bool tileOk = processTile(
            tileInBuf.data(),
            tile.width,
            tile.height,
            tileInStride,
            tileOutBuf.data(),
            tileOutW,
            tileOutH,
            tileOutStride,
            scale
        );

        if (!tileOk) {
            std::cerr << "[RealEsrganEngine] Failed to process tile at (" << tile.x << ", " << tile.y << ")" << std::endl;
            return false;
        }

        // Blend tile into output image
        TileEngine::blendTileIntoTarget(
            tileOutBuf.data(),
            tileOutW,
            tileOutH,
            tileOutStride,
            outputRgba,
            outWidth,
            outHeight,
            outStride,
            tile,
            scale,
            overlapPadding
        );

        if (progressCallback) {
            progressCallback(static_cast<int>(i + 1), static_cast<int>(tiles.size()));
        }
    }

    return true;
}

} // namespace frameforge
