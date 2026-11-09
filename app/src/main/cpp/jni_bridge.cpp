#include <jni.h>
#include <string>
#include <memory>
#include "frameforge_types.h"
#include "tile_engine.h"
#include "color_convert.h"
#include "quality_metrics.h"
#include "real_esrgan_engine.h"
#include "rife_engine.h"
#include "video_restoration.h"

namespace {
    std::unique_ptr<frameforge::RealEsrganEngine> g_upscaleEngine;
    std::unique_ptr<frameforge::RifeEngine> g_rifeEngine;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeInit(
    JNIEnv* env,
    jobject /* thiz */,
    jstring /* cacheDir */
) {
    g_upscaleEngine = std::make_unique<frameforge::RealEsrganEngine>();
    g_rifeEngine = std::make_unique<frameforge::RifeEngine>();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeLoadUpscaleModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring modelPath,
    jint modelType,
    jint scale,
    jint precision,
    jint backend
) {
    if (!g_upscaleEngine) {
        g_upscaleEngine = std::make_unique<frameforge::RealEsrganEngine>();
    }

    const char* pathStr = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(pathStr ? pathStr : "");
    if (pathStr) env->ReleaseStringUTFChars(modelPath, pathStr);

    return g_upscaleEngine->loadModel(
        path,
        static_cast<frameforge::UpscaleModelType>(modelType),
        scale,
        static_cast<frameforge::PrecisionMode>(precision),
        static_cast<frameforge::ComputeBackend>(backend)
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeUpscale(
    JNIEnv* env,
    jobject /* thiz */,
    jobject inDirectBuf,
    jint inW,
    jint inH,
    jint inStride,
    jobject outDirectBuf,
    jint outW,
    jint outH,
    jint outStride,
    jint scale,
    jint tileSize,
    jint overlap,
    jobject callback
) {
    if (!g_upscaleEngine) return JNI_FALSE;

    auto* inPtr = static_cast<uint8_t*>(env->GetDirectBufferAddress(inDirectBuf));
    auto* outPtr = static_cast<uint8_t*>(env->GetDirectBufferAddress(outDirectBuf));
    if (!inPtr || !outPtr) return JNI_FALSE;

    jmethodID onTileMethod = nullptr;
    if (callback) {
        jclass cbClass = env->GetObjectClass(callback);
        if (cbClass) {
            onTileMethod = env->GetMethodID(cbClass, "onTileProgress", "(II)V");
        }
    }

    std::function<void(int, int)> cb = nullptr;
    if (callback && onTileMethod) {
        cb = [env, callback, onTileMethod](int current, int total) {
            env->CallVoidMethod(callback, onTileMethod, static_cast<jint>(current), static_cast<jint>(total));
        };
    }

    return g_upscaleEngine->upscale(
        inPtr, inW, inH, inStride,
        outPtr, outW, outH, outStride,
        scale, tileSize, overlap,
        cb
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeLoadInterpolationModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring modelPath,
    jint precision,
    jint backend
) {
    if (!g_rifeEngine) {
        g_rifeEngine = std::make_unique<frameforge::RifeEngine>();
    }

    const char* pathStr = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(pathStr ? pathStr : "");
    if (pathStr) env->ReleaseStringUTFChars(modelPath, pathStr);

    return g_rifeEngine->loadModel(
        path,
        static_cast<frameforge::PrecisionMode>(precision),
        static_cast<frameforge::ComputeBackend>(backend)
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeInterpolate(
    JNIEnv* env,
    jobject /* thiz */,
    jobject f0Buf,
    jobject f1Buf,
    jobject outBuf,
    jint width,
    jint height,
    jint stride,
    jfloat timestep,
    jfloat sceneCutThreshold
) {
    if (!g_rifeEngine) return JNI_FALSE;

    auto* p0 = static_cast<uint8_t*>(env->GetDirectBufferAddress(f0Buf));
    auto* p1 = static_cast<uint8_t*>(env->GetDirectBufferAddress(f1Buf));
    auto* po = static_cast<uint8_t*>(env->GetDirectBufferAddress(outBuf));
    if (!p0 || !p1 || !po) return JNI_FALSE;

    return g_rifeEngine->interpolate(
        p0, p1, po, width, height, stride, timestep, sceneCutThreshold
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeDetectSceneCut(
    JNIEnv* env,
    jobject /* thiz */,
    jobject f0Buf,
    jobject f1Buf,
    jint width,
    jint height,
    jint stride,
    jfloat threshold
) {
    auto* p0 = static_cast<uint8_t*>(env->GetDirectBufferAddress(f0Buf));
    auto* p1 = static_cast<uint8_t*>(env->GetDirectBufferAddress(f1Buf));
    if (!p0 || !p1) return JNI_FALSE;

    return frameforge::QualityEngine::isSceneCut(p0, p1, width, height, stride, threshold) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeRestore(
    JNIEnv* env,
    jobject /* thiz */,
    jobject currBuf,
    jobject prevBuf,
    jobject nextBuf,
    jobject outBuf,
    jint width,
    jint height,
    jint stride,
    jfloat denoise,
    jfloat deblock,
    jfloat sharpen
) {
    auto* pCurr = static_cast<uint8_t*>(env->GetDirectBufferAddress(currBuf));
    auto* pOut = static_cast<uint8_t*>(env->GetDirectBufferAddress(outBuf));
    if (!pCurr || !pOut) return JNI_FALSE;

    uint8_t* pPrev = prevBuf ? static_cast<uint8_t*>(env->GetDirectBufferAddress(prevBuf)) : nullptr;
    uint8_t* pNext = nextBuf ? static_cast<uint8_t*>(env->GetDirectBufferAddress(nextBuf)) : nullptr;

    return frameforge::VideoRestorationEngine::applyRestoration(
        pCurr, pPrev, pNext, pOut, width, height, stride, denoise, deblock, sharpen
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdoubleArray JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeComputeQualityMetrics(
    JNIEnv* env,
    jobject /* thiz */,
    jobject refBuf,
    jobject testBuf,
    jint width,
    jint height,
    jint refStride,
    jint testStride
) {
    auto* pRef = static_cast<uint8_t*>(env->GetDirectBufferAddress(refBuf));
    auto* pTest = static_cast<uint8_t*>(env->GetDirectBufferAddress(testBuf));

    jdoubleArray resultArray = env->NewDoubleArray(2);
    if (!pRef || !pTest || !resultArray) return resultArray;

    double psnr = frameforge::QualityEngine::computePsnr(pRef, pTest, width, height, refStride, testStride);
    double ssim = frameforge::QualityEngine::computeSsim(pRef, pTest, width, height, refStride, testStride);

    jdouble metrics[2] = { psnr, ssim };
    env->SetDoubleArrayRegion(resultArray, 0, 2, metrics);
    return resultArray;
}

JNIEXPORT void JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeYuvPlanesToRgba(
    JNIEnv* env,
    jobject /* thiz */,
    jobject yBuf,
    jint yRowStride,
    jint yPixelStride,
    jobject uBuf,
    jint uRowStride,
    jint uPixelStride,
    jobject vBuf,
    jint vRowStride,
    jint vPixelStride,
    jobject rgbaBuf,
    jint width,
    jint height,
    jint rgbaStride
) {
    auto* pY = static_cast<const uint8_t*>(env->GetDirectBufferAddress(yBuf));
    auto* pU = static_cast<const uint8_t*>(env->GetDirectBufferAddress(uBuf));
    auto* pV = static_cast<const uint8_t*>(env->GetDirectBufferAddress(vBuf));
    auto* pRgba = static_cast<uint8_t*>(env->GetDirectBufferAddress(rgbaBuf));
    if (pY && pU && pV && pRgba) {
        frameforge::ColorConverter::yuvPlanesToRgba(
            pY, yRowStride, yPixelStride,
            pU, uRowStride, uPixelStride,
            pV, vRowStride, vPixelStride,
            pRgba, width, height, rgbaStride
        );
    }
}

JNIEXPORT void JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeYuvToRgba(
    JNIEnv* env,
    jobject /* thiz */,
    jobject yuvBuf,
    jobject rgbaBuf,
    jint width,
    jint height,
    jint rgbaStride,
    jboolean isNv21
) {
    auto* pYuv = static_cast<uint8_t*>(env->GetDirectBufferAddress(yuvBuf));
    auto* pRgba = static_cast<uint8_t*>(env->GetDirectBufferAddress(rgbaBuf));
    if (pYuv && pRgba) {
        frameforge::ColorConverter::yuv420spToRgba(pYuv, width, height, pRgba, rgbaStride, isNv21 == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_frameforge_ai_inference_NativeEngine_nativeRgbaToYuv(
    JNIEnv* env,
    jobject /* thiz */,
    jobject rgbaBuf,
    jobject yuvBuf,
    jint width,
    jint height,
    jint rgbaStride,
    jint formatMode
) {
    auto* pRgba = static_cast<uint8_t*>(env->GetDirectBufferAddress(rgbaBuf));
    auto* pYuv = static_cast<uint8_t*>(env->GetDirectBufferAddress(yuvBuf));
    if (pRgba && pYuv) {
        frameforge::ColorConverter::rgbaToYuv(pRgba, width, height, rgbaStride, pYuv, formatMode);
    }
}

} // extern "C"
