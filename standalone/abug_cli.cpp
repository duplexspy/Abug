#include <iostream>
#include <vector>
#include <string>
#include <chrono>
#include <cstring>
#include <cstdlib>
#include <math.h>

#define STB_IMAGE_IMPLEMENTATION
#include "app/src/main/cpp/ncnn_install/include/ncnn/stb_image.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "app/src/main/cpp/ncnn_install/include/ncnn/stb_image_write.h"

#include "app/src/main/cpp/frameforge_types.h"
#include "app/src/main/cpp/tile_engine.h"
#include "app/src/main/cpp/color_convert.h"
#include "app/src/main/cpp/quality_metrics.h"
#include "app/src/main/cpp/video_restoration.h"
#include "app/src/main/cpp/real_esrgan_engine.h"
#include "app/src/main/cpp/rife_engine.h"

void printBanner() {
    std::cout << "==========================================================" << std::endl;
    std::cout << "  abug (🐞) Standalone Desktop Studio & AI CLI v1.0.0" << std::endl;
    std::cout << "  Offline Open-Source AI Video Upscaler & Twixtor" << std::endl;
    std::cout << "  Created by duplexspy (https://t.me/effectcraftport)" << std::endl;
    std::cout << "==========================================================" << std::endl;
}

void printUsage(const char* prog) {
    printBanner();
    std::cout << "Usage:" << std::endl;
    std::cout << "  " << prog << " upscale <in.png> <out.png> <scale 2|3|4> <model.bin> [tileSize=128]" << std::endl;
    std::cout << "  " << prog << " twixtor <frame0.png> <frame1.png> <out.png> <model.bin> [timestep=0.5]" << std::endl;
    std::cout << "  " << prog << " test" << std::endl;
    std::cout << std::endl;
    std::cout << "Examples:" << std::endl;
    std::cout << "  " << prog << " upscale input.jpg output.png 4 app/src/main/assets/models/realesr-animevideov3-x4.bin" << std::endl;
    std::cout << "  " << prog << " twixtor f0.png f1.png interp.png app/src/main/assets/models/rife-v4.6/flownet.bin 0.5" << std::endl;
}

int runUpscale(const std::string& inPath, const std::string& outPath, int scale, const std::string& modelPath, int tileSize) {
    int w, h, channels;
    unsigned char* data = stbi_load(inPath.c_str(), &w, &h, &channels, 4);
    if (!data) {
        std::cerr << "Error: Failed to load input image: " << inPath << std::endl;
        return 1;
    }
    std::cout << "[abug] Loaded input image: " << inPath << " (" << w << "x" << h << ", 4 channels RGBA)" << std::endl;

    frameforge::RealEsrganEngine engine;
    frameforge::UpscaleModelType mType = frameforge::UpscaleModelType::REAL_ESR_ANIME_VIDEO_V3;
    if (modelPath.find("x4plus") != std::string::npos) {
        mType = frameforge::UpscaleModelType::REAL_ESRGAN_X4PLUS;
    }

    std::cout << "[abug] Loading model: " << modelPath << " (scale=" << scale << "x)..." << std::endl;
    bool loaded = engine.loadModel(modelPath, mType, scale, frameforge::PrecisionMode::FP16, frameforge::ComputeBackend::CPU);
    if (!loaded) {
        std::cerr << "Error: Failed to load Real-ESRGAN neural model." << std::endl;
        stbi_image_free(data);
        return 1;
    }

    int outW = w * scale;
    int outH = h * scale;
    std::vector<uint8_t> outBuf(outW * outH * 4, 0);

    std::cout << "[abug] Running neural upscaling to " << outW << "x" << outH << " (tileSize=" << tileSize << ")..." << std::endl;
    auto t0 = std::chrono::high_resolution_clock::now();
    bool ok = engine.upscale(data, w, h, w * 4, outBuf.data(), outW, outH, outW * 4, scale, tileSize, 0);
    auto t1 = std::chrono::high_resolution_clock::now();
    double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();

    stbi_image_free(data);

    if (!ok) {
        std::cerr << "Error: Neural upscaling failed." << std::endl;
        return 1;
    }

    std::cout << "[abug] Inference completed in " << ms << " ms (" << (ms / 1000.0) << "s)" << std::endl;
    std::cout << "[abug] Saving output image: " << outPath << "..." << std::endl;
    int writeOk = stbi_write_png(outPath.c_str(), outW, outH, 4, outBuf.data(), outW * 4);
    if (!writeOk) {
        std::cerr << "Error: Failed to write PNG to: " << outPath << std::endl;
        return 1;
    }

    std::cout << "[abug] SUCCESS! Saved upscaled image to " << outPath << std::endl;
    return 0;
}

int runTwixtor(const std::string& f0Path, const std::string& f1Path, const std::string& outPath, const std::string& modelPath, float timestep) {
    int w0, h0, c0, w1, h1, c1;
    unsigned char* d0 = stbi_load(f0Path.c_str(), &w0, &h0, &c0, 4);
    unsigned char* d1 = stbi_load(f1Path.c_str(), &w1, &h1, &c1, 4);

    if (!d0 || !d1) {
        std::cerr << "Error: Failed to load input frames: " << f0Path << " / " << f1Path << std::endl;
        if (d0) stbi_image_free(d0);
        if (d1) stbi_image_free(d1);
        return 1;
    }

    if (w0 != w1 || h0 != h1) {
        std::cerr << "Error: Frame dimension mismatch: " << w0 << "x" << h0 << " vs " << w1 << "x" << h1 << std::endl;
        stbi_image_free(d0);
        stbi_image_free(d1);
        return 1;
    }

    std::cout << "[abug] Loaded frames: " << w0 << "x" << h0 << " (timestep=" << timestep << ")" << std::endl;
    frameforge::RifeEngine rife;
    std::cout << "[abug] Loading RIFE FlowNet model: " << modelPath << "..." << std::endl;
    bool loaded = rife.loadModel(modelPath, frameforge::PrecisionMode::FP16, frameforge::ComputeBackend::CPU);
    if (!loaded) {
        std::cerr << "Error: Failed to load RIFE FlowNet neural model." << std::endl;
        stbi_image_free(d0);
        stbi_image_free(d1);
        return 1;
    }

    std::vector<uint8_t> outBuf(w0 * h0 * 4, 0);
    auto t0 = std::chrono::high_resolution_clock::now();
    bool ok = rife.interpolate(d0, d1, outBuf.data(), w0, h0, w0 * 4, timestep, 0.35f);
    auto t1 = std::chrono::high_resolution_clock::now();
    double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();

    stbi_image_free(d0);
    stbi_image_free(d1);

    if (!ok) {
        std::cerr << "Error: Twixtor interpolation failed." << std::endl;
        return 1;
    }

    std::cout << "[abug] FlowNet inference completed in " << ms << " ms (" << (ms / 1000.0) << "s)" << std::endl;
    std::cout << "[abug] Saving interpolated frame: " << outPath << "..." << std::endl;
    int writeOk = stbi_write_png(outPath.c_str(), w0, h0, 4, outBuf.data(), w0 * 4);
    if (!writeOk) {
        std::cerr << "Error: Failed to write PNG to: " << outPath << std::endl;
        return 1;
    }

    std::cout << "[abug] SUCCESS! Saved interpolated frame to " << outPath << std::endl;
    return 0;
}

int main(int argc, char** argv) {
    if (argc < 2) {
        printUsage(argv[0]);
        return 1;
    }

    std::string mode = argv[1];
    if (mode == "upscale") {
        if (argc < 6) {
            printUsage(argv[0]);
            return 1;
        }
        std::string inPath = argv[2];
        std::string outPath = argv[3];
        int scale = std::atoi(argv[4]);
        std::string modelPath = argv[5];
        int tileSize = (argc >= 7) ? std::atoi(argv[6]) : 128;
        return runUpscale(inPath, outPath, scale, modelPath, tileSize);
    } else if (mode == "twixtor") {
        if (argc < 6) {
            printUsage(argv[0]);
            return 1;
        }
        std::string f0Path = argv[2];
        std::string f1Path = argv[3];
        std::string outPath = argv[4];
        std::string modelPath = argv[5];
        float timestep = (argc >= 7) ? std::atof(argv[6]) : 0.5f;
        return runTwixtor(f0Path, f1Path, outPath, modelPath, timestep);
    } else if (mode == "test") {
        printBanner();
        std::cout << "[abug] Testing model loading..." << std::endl;
        frameforge::RealEsrganEngine upscale;
        bool uOk = upscale.loadModel(
            "app/src/main/assets/models/realesr-animevideov3-x4.bin",
            frameforge::UpscaleModelType::REAL_ESR_ANIME_VIDEO_V3,
            4,
            frameforge::PrecisionMode::FP16,
            frameforge::ComputeBackend::CPU
        );
        std::cout << "  Real-ESRGAN Model Load: " << (uOk ? "PASSED (OK)" : "FAILED") << std::endl;

        frameforge::RifeEngine rife;
        bool rOk = rife.loadModel(
            "app/src/main/assets/models/rife-v4.6/flownet.bin",
            frameforge::PrecisionMode::FP16,
            frameforge::ComputeBackend::CPU
        );
        std::cout << "  RIFE FlowNet Model Load: " << (rOk ? "PASSED (OK)" : "FAILED") << std::endl;
        return (uOk && rOk) ? 0 : 1;
    } else {
        printUsage(argv[0]);
        return 1;
    }
}
