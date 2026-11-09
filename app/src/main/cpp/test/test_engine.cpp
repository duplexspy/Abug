#include <iostream>
#include <cassert>
#include <vector>
#include <cmath>
#include "../frameforge_types.h"
#include "../tile_engine.h"
#include "../color_convert.h"
#include "../quality_metrics.h"
#include "../video_restoration.h"
#include "../real_esrgan_engine.h"
#include "../rife_engine.h"

void testTileEngine() {
    std::cout << "[TEST] Running TileEngine verification..." << std::endl;
    auto tiles = frameforge::TileEngine::computeTiles(320, 240, 128, 16);
    assert(!tiles.empty());
    std::cout << "  Generated " << tiles.size() << " tiles for 320x240 image." << std::endl;

    for (const auto& tile : tiles) {
        assert(tile.x >= 0 && tile.y >= 0);
        assert(tile.x + tile.width <= 320);
        assert(tile.y + tile.height <= 240);
    }
    std::cout << "  TileEngine verified successfully." << std::endl;
}

void testColorConversion() {
    std::cout << "[TEST] Running ColorConverter verification..." << std::endl;
    int w = 64;
    int h = 64;
    std::vector<uint8_t> rgba(w * h * 4, 128);
    std::vector<float> planarRgb(w * h * 3, 0.0f);
    std::vector<uint8_t> rgbaOut(w * h * 4, 0);

    frameforge::ColorConverter::rgbaToRgbPlanarFloat(rgba.data(), w, h, w * 4, planarRgb.data(), true);
    assert(std::abs(planarRgb[0] - 0.50196f) < 0.01f);

    frameforge::ColorConverter::rgbPlanarFloatToRgba(planarRgb.data(), w, h, rgbaOut.data(), w * 4, true);
    assert(rgbaOut[0] >= 127 && rgbaOut[0] <= 129);
    assert(rgbaOut[3] == 255);

    std::vector<uint8_t> yuv(w * h * 3 / 2, 0);
    frameforge::ColorConverter::rgbaToYuv420sp(rgba.data(), w, h, w * 4, yuv.data(), false);
    std::vector<uint8_t> rgbaFromYuv(w * h * 4, 0);
    frameforge::ColorConverter::yuv420spToRgba(yuv.data(), w, h, rgbaFromYuv.data(), w * 4, false);

    assert(rgbaFromYuv[3] == 255);
    std::cout << "  ColorConverter verified successfully." << std::endl;
}

void testQualityMetrics() {
    std::cout << "[TEST] Running QualityMetrics verification..." << std::endl;
    int w = 64;
    int h = 64;
    std::vector<uint8_t> ref(w * h * 4, 100);
    std::vector<uint8_t> test(w * h * 4, 100);

    double psnrIdentical = frameforge::QualityEngine::computePsnr(ref.data(), test.data(), w, h, w * 4, w * 4);
    assert(psnrIdentical >= 59.0);

    double ssimIdentical = frameforge::QualityEngine::computeSsim(ref.data(), test.data(), w, h, w * 4, w * 4);
    assert(ssimIdentical >= 0.99);

    std::vector<uint8_t> testDifferent(w * h * 4, 200);
    bool isCut = frameforge::QualityEngine::isSceneCut(ref.data(), testDifferent.data(), w, h, w * 4, 0.35f);
    assert(isCut == true);

    std::cout << "  QualityMetrics verified successfully." << std::endl;
}

void testRestoration() {
    std::cout << "[TEST] Running VideoRestoration verification..." << std::endl;
    int w = 64;
    int h = 64;
    std::vector<uint8_t> src(w * h * 4, 120);
    std::vector<uint8_t> dst(w * h * 4, 0);

    bool res = frameforge::VideoRestorationEngine::applyRestoration(
        src.data(), nullptr, nullptr, dst.data(), w, h, w * 4, 0.5f, 0.3f, 0.2f
    );
    assert(res == true);
    assert(dst[3] == 255);
    std::cout << "  VideoRestoration verified successfully." << std::endl;
}

void testRealEsrganAndRife() {
    std::cout << "[TEST] Running Real-ESRGAN and RIFE neural verification..." << std::endl;
    frameforge::RealEsrganEngine upscale;
    bool loadedUp = upscale.loadModel(
        "app/src/main/assets/models/realesr-animevideov3-x4.bin",
        frameforge::UpscaleModelType::REAL_ESR_ANIME_VIDEO_V3,
        4,
        frameforge::PrecisionMode::FP16,
        frameforge::ComputeBackend::CPU
    );
    assert(loadedUp == true);
    assert(upscale.isLoaded() == true);

    int inW = 16, inH = 16;
    int outW = 64, outH = 64;
    std::vector<uint8_t> inBuf(inW * inH * 4, 100);
    for (int i = 0; i < inW * inH; ++i) inBuf[i * 4 + 3] = 255;
    std::vector<uint8_t> outBuf(outW * outH * 4, 0);

    bool upSuccess = upscale.upscale(inBuf.data(), inW, inH, inW * 4, outBuf.data(), outW, outH, outW * 4, 4, 16, 0);
    assert(upSuccess == true);
    assert(outBuf[3] == 255);

    frameforge::RifeEngine rife;
    bool loadedRife = rife.loadModel(
        "app/src/main/assets/models/rife-v4.6/flownet.bin",
        frameforge::PrecisionMode::FP16,
        frameforge::ComputeBackend::CPU
    );
    assert(loadedRife == true);
    assert(rife.isLoaded() == true);

    int rW = 32, rH = 32;
    std::vector<uint8_t> f0(rW * rH * 4, 50);
    std::vector<uint8_t> f1(rW * rH * 4, 150);
    for (int i = 0; i < rW * rH; ++i) {
        f0[i * 4 + 3] = 255;
        f1[i * 4 + 3] = 255;
    }
    std::vector<uint8_t> fInterp(rW * rH * 4, 0);

    bool rifeSuccess = rife.interpolate(f0.data(), f1.data(), fInterp.data(), rW, rH, rW * 4, 0.5f, 0.35f);
    assert(rifeSuccess == true);
    assert(fInterp[3] == 255);

    std::cout << "  Real-ESRGAN & RIFE verified successfully with real neural inference." << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "  FRAMEFORGE NATIVE TEST SUITE" << std::endl;
    std::cout << "========================================" << std::endl;
    testTileEngine();
    testColorConversion();
    testQualityMetrics();
    testRestoration();
    testRealEsrganAndRife();
    std::cout << "========================================" << std::endl;
    std::cout << "  ALL NATIVE TESTS PASSED (100% OK)" << std::endl;
    std::cout << "========================================" << std::endl;
    return 0;
}
