#include "rife_engine.h"
#include "quality_metrics.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <iostream>
#include <ncnn/layer.h>

namespace frameforge {

namespace {

class WarpLayer : public ncnn::Layer {
public:
    WarpLayer() {
        one_blob_only = false;
        support_inplace = false;
    }

    virtual int forward(const std::vector<ncnn::Mat>& bottom_blobs, std::vector<ncnn::Mat>& top_blobs, const ncnn::Option& opt) const override {
        const ncnn::Mat& image_blob = bottom_blobs[0];
        const ncnn::Mat& flow_blob = bottom_blobs[1];

        int w = image_blob.w;
        int h = image_blob.h;
        int channels = image_blob.c;

        ncnn::Mat& top_blob = top_blobs[0];
        top_blob.create(w, h, channels);
        if (top_blob.empty())
            return -100;

        #pragma omp parallel for num_threads(opt.num_threads)
        for (int q = 0; q < channels; q++) {
            float* outptr = top_blob.channel(q);
            const ncnn::Mat image = image_blob.channel(q);
            const float* fxptr = flow_blob.channel(0);
            const float* fyptr = flow_blob.channel(1);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int idx = y * w + x;
                    float flow_x = fxptr[idx];
                    float flow_y = fyptr[idx];

                    if (!std::isfinite(flow_x)) flow_x = 0.0f;
                    if (!std::isfinite(flow_y)) flow_y = 0.0f;

                    float sample_x = (float)x + flow_x;
                    float sample_y = (float)y + flow_y;

                    int x0 = (int)std::floor(sample_x);
                    int y0 = (int)std::floor(sample_y);
                    int x1 = x0 + 1;
                    int y1 = y0 + 1;

                    // Interpolation fractional weights must be computed BEFORE clamping!
                    float alpha = sample_x - (float)x0;
                    float beta = sample_y - (float)y0;

                    // Clamp pixel coordinates to image boundary
                    x0 = std::clamp(x0, 0, w - 1);
                    y0 = std::clamp(y0, 0, h - 1);
                    x1 = std::clamp(x1, 0, w - 1);
                    y1 = std::clamp(y1, 0, h - 1);

                    float v0 = image.row(y0)[x0];
                    float v1 = image.row(y0)[x1];
                    float v2 = image.row(y1)[x0];
                    float v3 = image.row(y1)[x1];

                    float top = v0 * (1.0f - alpha) + v1 * alpha;
                    float bottom = v2 * (1.0f - alpha) + v3 * alpha;
                    outptr[idx] = top * (1.0f - beta) + bottom * beta;
                }
            }
        }
        return 0;
    }
};

static ncnn::Layer* WarpLayer_creator(void* /*userdata*/) {
    return new WarpLayer();
}

static void WarpLayer_destroyer(ncnn::Layer* layer, void* /*userdata*/) {
    delete layer;
}

} // anonymous namespace

RifeEngine::RifeEngine() = default;
RifeEngine::~RifeEngine() {
    m_net.clear();
}

bool RifeEngine::loadModel(
    const std::string& modelPath,
    PrecisionMode precision,
    ComputeBackend backend
) {
    m_isLoaded = false;
    m_net.clear();

    m_modelPath = modelPath;
    m_precision = precision;
    m_backend = backend;

    // Register custom warp layer required by RIFE FlowNet
    m_net.register_custom_layer("rife.Warp", WarpLayer_creator, WarpLayer_destroyer);

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

    std::ifstream pfile(paramPath, std::ios::binary);
    if (!pfile.is_open()) {
        std::cerr << "[RifeEngine] Failed to open param file: " << paramPath << std::endl;
        return false;
    }
    pfile.close();

    std::ifstream bfile(binPath, std::ios::binary);
    if (!bfile.is_open()) {
        std::cerr << "[RifeEngine] Failed to open bin file: " << binPath << std::endl;
        return false;
    }
    bfile.close();

    // Configure options
    m_net.opt.lightmode = true;
    m_net.opt.num_threads = 4;
    m_net.opt.use_fp16_packed = (precision == PrecisionMode::FP16);
    m_net.opt.use_fp16_storage = (precision == PrecisionMode::FP16);
    m_net.opt.use_fp16_arithmetic = (precision == PrecisionMode::FP16);
    m_net.opt.use_packing_layout = true;

    int retParam = m_net.load_param(paramPath.c_str());
    if (retParam != 0) {
        std::cerr << "[RifeEngine] load_param failed: " << retParam << std::endl;
        return false;
    }

    int retModel = m_net.load_model(binPath.c_str());
    if (retModel != 0) {
        std::cerr << "[RifeEngine] load_model failed: " << retModel << std::endl;
        return false;
    }

    m_isLoaded = true;
    std::cout << "[RifeEngine] Successfully loaded RIFE FlowNet model: " << binPath << std::endl;
    return true;
}

bool RifeEngine::interpolate(
    const uint8_t* frame0,
    const uint8_t* frame1,
    uint8_t* outputFrame,
    int width,
    int height,
    int stride,
    float timestep,
    float sceneCutThreshold
) {
    if (!m_isLoaded || !frame0 || !frame1 || !outputFrame || width <= 0 || height <= 0) return false;
    timestep = std::clamp(timestep, 0.0f, 1.0f);

    if (timestep <= 0.001f) {
        for (int y = 0; y < height; ++y) {
            std::memcpy(outputFrame + y * stride, frame0 + y * stride, width * 4);
        }
        return true;
    }
    if (timestep >= 0.999f) {
        for (int y = 0; y < height; ++y) {
            std::memcpy(outputFrame + y * stride, frame1 + y * stride, width * 4);
        }
        return true;
    }

    // Scene cut handling: use reliable threshold to only trigger on actual scene cuts.
    // If scene transition cut detected, don't crossfade/ghost! Snap cleanly to nearest neighbor frame.
    float effectiveCutThreshold = (sceneCutThreshold > 0.01f) ? sceneCutThreshold : 0.50f;
    if (QualityEngine::isSceneCut(frame0, frame1, width, height, stride, effectiveCutThreshold)) {
        const uint8_t* chosen = (timestep < 0.5f) ? frame0 : frame1;
        for (int y = 0; y < height; ++y) {
            std::memcpy(outputFrame + y * stride, chosen + y * stride, width * 4);
        }
        return true;
    }

    // Convert input RGBA frames to RGB Mats normalized to [0, 1]
    ncnn::Mat in0 = ncnn::Mat::from_pixels(frame0, ncnn::Mat::PIXEL_RGBA2RGB, width, height, stride);
    ncnn::Mat in1 = ncnn::Mat::from_pixels(frame1, ncnn::Mat::PIXEL_RGBA2RGB, width, height, stride);
    if (in0.empty() || in1.empty()) return false;

    const float norm_vals[3] = { 1.0f / 255.0f, 1.0f / 255.0f, 1.0f / 255.0f };
    in0.substract_mean_normalize(0, norm_vals);
    in1.substract_mean_normalize(0, norm_vals);

    // RIFE requires input dimensions to be multiple of 32
    int w_padded = (width + 31) / 32 * 32;
    int h_padded = (height + 31) / 32 * 32;

    ncnn::Mat in0_padded;
    ncnn::Mat in1_padded;
    if (w_padded != width || h_padded != height) {
        ncnn::copy_make_border(in0, in0_padded, 0, h_padded - height, 0, w_padded - width, ncnn::BORDER_CONSTANT, 0.f);
        ncnn::copy_make_border(in1, in1_padded, 0, h_padded - height, 0, w_padded - width, ncnn::BORDER_CONSTANT, 0.f);
    } else {
        in0_padded = in0;
        in1_padded = in1;
    }

    ncnn::Mat in2(w_padded, h_padded, 1);
    in2.fill(timestep);

    ncnn::Extractor ex = m_net.create_extractor();
    ex.input("in0", in0_padded);
    ex.input("in1", in1_padded);
    ex.input("in2", in2);

    ncnn::Mat out0;
    int ret = ex.extract("out0", out0);
    if (ret != 0 || out0.empty()) {
        std::cerr << "[RifeEngine] RIFE neural extraction failed with ret=" << ret << std::endl;
        return false;
    }

    ncnn::Mat out_cut;
    if (w_padded != width || h_padded != height) {
        ncnn::copy_cut_border(out0, out_cut, 0, h_padded - height, 0, w_padded - width);
    } else {
        out_cut = out0;
    }

    // Direct pixel denormalization and bounds clamping to avoid color overflow / weird artifacts
    #pragma omp parallel for num_threads(m_net.opt.num_threads)
    for (int y = 0; y < height; ++y) {
        const float* rRow = out_cut.channel(0).row(y);
        const float* gRow = out_cut.channel(1).row(y);
        const float* bRow = out_cut.channel(2).row(y);
        uint8_t* outRow = outputFrame + y * stride;
        for (int x = 0; x < width; ++x) {
            float r = rRow[x] * 255.0f + 0.5f;
            float g = gRow[x] * 255.0f + 0.5f;
            float b = bRow[x] * 255.0f + 0.5f;
            outRow[x * 4 + 0] = (uint8_t)std::clamp(r, 0.0f, 255.0f);
            outRow[x * 4 + 1] = (uint8_t)std::clamp(g, 0.0f, 255.0f);
            outRow[x * 4 + 2] = (uint8_t)std::clamp(b, 0.0f, 255.0f);
            outRow[x * 4 + 3] = 255;
        }
    }

    return true;
}

} // namespace frameforge
