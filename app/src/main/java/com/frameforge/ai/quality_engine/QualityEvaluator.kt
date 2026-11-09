package com.frameforge.ai.quality_engine

import com.frameforge.ai.core.QualityScore
import com.frameforge.ai.inference.NativeEngine
import java.nio.ByteBuffer
import kotlin.math.max

class QualityEvaluator {
    private var totalPsnr = 0.0
    private var totalSsim = 0.0
    private var totalFlicker = 0.0
    private var sampleCount = 0
    private var sceneCutCount = 0

    fun reset() {
        totalPsnr = 0.0
        totalSsim = 0.0
        totalFlicker = 0.0
        sampleCount = 0
        sceneCutCount = 0
    }

    fun recordSceneCut() {
        sceneCutCount++
    }

    fun evaluateFrames(refFrame: ByteBuffer, testFrame: ByteBuffer, width: Int, height: Int) {
        val metrics = NativeEngine.computeQualityMetrics(refFrame, testFrame, width, height)
        if (metrics.size >= 3) {
            totalPsnr += metrics[0]
            totalSsim += metrics[1]
            totalFlicker += metrics[2]
            sampleCount++
        }
    }

    fun generateReport(): QualityScore {
        val count = max(1, sampleCount)
        return QualityScore(
            psnrDb = (totalPsnr / count).toFloat(),
            ssim = (totalSsim / count).toFloat(),
            temporalFlicker = (totalFlicker / count).toFloat(),
            sceneCutsCount = sceneCutCount,
            framesAnalyzed = sampleCount
        )
    }
}
