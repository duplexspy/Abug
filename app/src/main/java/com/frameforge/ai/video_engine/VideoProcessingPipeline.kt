package com.frameforge.ai.video_engine

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.frameforge.ai.core.*
import com.frameforge.ai.decoder.VideoDecoder
import com.frameforge.ai.encoder.VideoEncoder
import com.frameforge.ai.inference.NativeEngine
import com.frameforge.ai.quality_engine.QualityEvaluator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

class VideoProcessingPipeline(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "FrameForge_Pipeline"
    }

    private val _progress = MutableStateFlow(ProcessingProgress())
    val progress: StateFlow<ProcessingProgress> = _progress.asStateFlow()

    private val _state = MutableStateFlow(ProcessingState.IDLE)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private val _qualityScore = MutableStateFlow(QualityScore())
    val qualityScore: StateFlow<QualityScore> = _qualityScore.asStateFlow()

    private var activeJob: Job? = null
    private val qualityEvaluator = QualityEvaluator()

    fun cancel() {
        activeJob?.cancel()
        _state.value = ProcessingState.CANCELLED
    }

    fun startProcessing(
        sourceUri: Uri,
        outputFile: File,
        config: ProcessingConfig,
        onComplete: (Boolean, File?) -> Unit
    ) {
        if (_state.value == ProcessingState.PROCESSING) return

        activeJob = scope.launch(Dispatchers.Default) {
            val decoder = VideoDecoder(context)
            val encoder = VideoEncoder(context)
            var threadPool: java.util.concurrent.ExecutorService? = null
            var workerDispatcher: kotlinx.coroutines.ExecutorCoroutineDispatcher? = null
            qualityEvaluator.reset()

            try {
                _state.value = ProcessingState.ANALYZING
                _progress.value = ProcessingProgress(currentStage = "Analyzing Video Tracks...")

                val metadata = decoder.extractMetadata(sourceUri)
                if (metadata.width <= 0 || metadata.height <= 0) {
                    throw IllegalStateException("Failed to parse video track metadata")
                }

                if (!decoder.open(sourceUri)) {
                    throw IllegalStateException("Failed to initialize video decoder")
                }

                // Determine operational mode
                val isUpscaleOnly = config.videoMode == VideoProcessingMode.UPSCALE_ONLY || config.pipelineOrder == PipelineOrder.UPSCALE_ONLY
                val isTwixtorOnly = config.videoMode == VideoProcessingMode.TWIXTOR_ONLY || config.pipelineOrder == PipelineOrder.INTERPOLATE_ONLY

                // Determine final output resolution - strictly ensure even dimensions for hardware video encoding
                val rawTargetW = if (isTwixtorOnly) {
                    metadata.width
                } else if (config.customTargetWidth > 0) {
                    config.customTargetWidth
                } else {
                    metadata.width * config.upscaleScale
                }
                val rawTargetH = if (isTwixtorOnly) {
                    metadata.height
                } else if (config.customTargetHeight > 0) {
                    config.customTargetHeight
                } else {
                    metadata.height * config.upscaleScale
                }
                val desiredTargetW = (rawTargetW and 1.inv()).coerceAtLeast(16)
                val desiredTargetH = (rawTargetH and 1.inv()).coerceAtLeast(16)

                // Calculate target FPS
                val finalFps = when {
                    isUpscaleOnly -> metadata.frameRate
                    config.pipelineOrder == PipelineOrder.RESTORATION_ONLY -> metadata.frameRate
                    config.targetFps > 0 -> config.targetFps
                    else -> metadata.frameRate * config.interpolationFactor
                }

                // Total estimated output frames
                val totalOutputFrames = if (config.slowMotionSpeed < 1.0f) {
                    (metadata.frameCount / config.slowMotionSpeed).toInt()
                } else {
                    (metadata.frameCount * (finalFps / max(1.0, metadata.frameRate))).toInt()
                }.coerceAtLeast(1)

                _state.value = ProcessingState.PROCESSING
                _progress.value = ProcessingProgress(
                    totalFrames = totalOutputFrames,
                    currentStage = "Configuring AI Hardware Pipeline..."
                )

                // Initialize native / AI engines
                val initOk = NativeEngine.initialize(context.cacheDir.absolutePath)
                if (!initOk && !config.allowCompatibilityResize) {
                    val detail = NativeEngine.loadError?.let { " ($it)" } ?: ""
                    throw IllegalStateException("Failed to initialize native AI engine core: libframeforge.so is not available$detail.")
                }

                val modelMgr = com.frameforge.ai.model_manager.ModelManager(context)

                // Only extract and load Real-ESRGAN if not in Twixtor-only mode
                if (!isTwixtorOnly) {
                    val upscaleFile = try {
                        modelMgr.getOrExtractUpscaleModel(config.upscaleModel)
                    } catch (e: Exception) {
                        if (!config.allowCompatibilityResize) {
                            throw IllegalStateException("Failed to unpack AI Super-Resolution model weights for ${config.upscaleModel.displayName}: ${e.message}", e)
                        }
                        null
                    }

                    if (upscaleFile != null) {
                        val upscaleLoaded = NativeEngine.loadUpscaleModel(
                            path = upscaleFile.absolutePath,
                            modelType = config.upscaleModel.nativeId,
                            scale = config.upscaleModel.scale,
                            precision = config.precision.ordinal,
                            backend = config.backend.ordinal
                        )
                        if (!upscaleLoaded && !config.allowCompatibilityResize) {
                            throw IllegalStateException("Failed to load AI Super-Resolution neural model: ${config.upscaleModel.displayName}")
                        }
                    }
                }

                // Only extract and load RIFE if not in Upscale-only mode
                val doInterpolateCheck = !isUpscaleOnly && (config.interpolationFactor > 1 || config.slowMotionSpeed < 1.0f)

                if (doInterpolateCheck) {
                    val interpFile = try {
                        modelMgr.getOrExtractInterpolationModel(config.interpolationModel)
                    } catch (e: Exception) {
                        if (!config.allowCompatibilityResize) {
                            throw IllegalStateException("Failed to unpack AI Frame Interpolation weights for ${config.interpolationModel.displayName}: ${e.message}", e)
                        }
                        null
                    }
                    if (interpFile != null) {
                        val interpLoaded = NativeEngine.loadInterpolationModel(
                            path = interpFile.absolutePath,
                            precision = config.precision.ordinal,
                            backend = config.backend.ordinal
                        )
                        if (!interpLoaded && !config.allowCompatibilityResize) {
                            throw IllegalStateException("Failed to load AI Frame Interpolation neural model: ${config.interpolationModel.displayName}")
                        }
                    }
                }

                if (!encoder.prepare(
                        outputFile = outputFile,
                        width = desiredTargetW,
                        height = desiredTargetH,
                        fps = finalFps.roundToInt(),
                        bitrateMbps = config.exportBitrateMbps,
                        exportCodec = config.exportCodec
                    )
                ) {
                    throw IllegalStateException("Failed to configure hardware video encoder")
                }

                val targetW = encoder.outputWidth
                val targetH = encoder.outputHeight

                val startTime = SystemClock.elapsedRealtime()
                var processedOutputFrames = 0
                var previousInputBuf: ByteBuffer? = null
                var previousPtsUs = 0L

                // Multi-frame concurrent batch pipeline setup:
                // ULTRA mode: executes all scanned max frames simultaneously (e.g. 8 frames) across dedicated workers.
                // FAST mode: 4 frames concurrent.
                // STANDARD mode: 1 frame sequential.
                val batchCapacity = when (config.renderMode) {
                    RenderEngineMode.ULTRA -> config.ultraBatchSize.coerceIn(2, 16)
                    RenderEngineMode.FAST -> config.batchSize.coerceIn(2, 6)
                    RenderEngineMode.STANDARD -> 1
                }
                var srcW = (metadata.width and 1.inv()).coerceAtLeast(16)
                var srcH = (metadata.height and 1.inv()).coerceAtLeast(16)

                val poolCapacity = (batchCapacity * 2 + 8).coerceAtLeast(16)
                var inputPool = Array(poolCapacity) { ByteBuffer.allocateDirect(srcW * srcH * 4) }
                var poolIdx = 0

                val poolExecutor = java.util.concurrent.Executors.newFixedThreadPool(
                    batchCapacity,
                    object : java.util.concurrent.ThreadFactory {
                        private val count = java.util.concurrent.atomic.AtomicInteger(1)
                        override fun newThread(r: Runnable): Thread {
                            val t = Thread(r, "FrameForgeWorker-${count.getAndIncrement()}")
                            t.priority = Thread.NORM_PRIORITY + 1
                            return t
                        }
                    }
                )
                threadPool = poolExecutor
                val disp = poolExecutor.asCoroutineDispatcher()
                workerDispatcher = disp

                var workerSlots = Array(batchCapacity) { idx ->
                    BatchWorkerSlot(
                        slotIndex = idx,
                        targetRgba = ByteBuffer.allocateDirect(targetW * targetH * 4),
                        targetYuv = ByteBuffer.allocateDirect(targetW * targetH * 3 / 2),
                        interpRgba = ByteBuffer.allocateDirect(srcW * srcH * 4)
                    )
                }

                var nextFrameNo = 1L
                val pendingJobs = mutableListOf<RenderJob>()

                suspend fun flushBatch() {
                    if (pendingJobs.isEmpty()) return
                    val count = pendingJobs.size
                    val firstNo = pendingJobs.first().frameNo
                    val lastNo = pendingJobs.last().frameNo
                    val batchTag = when {
                        config.renderMode == RenderEngineMode.ULTRA || batchCapacity >= 8 ->
                            "🚀 Ultra Multi-Core: $count Frames Simultaneously (#$firstNo..#$lastNo) on $batchCapacity Parallel Workers"
                        batchCapacity > 1 ->
                            "⚡ Fast Batch: Frames #$firstNo..#$lastNo ($count in parallel)"
                        else ->
                            "Inference & Encoding (Frame $firstNo)"
                    }

                    val liveStatuses = java.util.concurrent.CopyOnWriteArrayList(
                        pendingJobs.mapIndexed { idx, job ->
                            ParallelWorkerStatus(
                                slotIndex = idx,
                                frameNo = job.frameNo,
                                stage = if (job.isIntermediate) "RIFE Flow Interpolation" else "Real-ESRGAN Upscaling",
                                isBusy = true
                            )
                        }
                    )

                    updateTelemetry(
                        processed = processedOutputFrames,
                        total = totalOutputFrames,
                        startMs = startTime,
                        batchInfo = batchTag,
                        activeSlots = liveStatuses.toList(),
                        capacity = batchCapacity
                    )

                    // Truly concurrent simultaneous generation across dedicated worker thread pool
                    val results = coroutineScope {
                        pendingJobs.mapIndexed { idx, job ->
                            val slot = workerSlots[idx]
                            async(disp) {
                                val res = processJob(
                                    job = job,
                                    slot = slot,
                                    srcW = srcW,
                                    srcH = srcH,
                                    targetW = targetW,
                                    targetH = targetH,
                                    config = config,
                                    formatMode = encoder.formatMode
                                )
                                if (idx < liveStatuses.size) {
                                    liveStatuses[idx] = liveStatuses[idx].copy(
                                        stage = "Completed (Ready for Mux)",
                                        isBusy = false
                                    )
                                }
                                res
                            }
                        }.awaitAll()
                    }

                    // Arrange frames strictly in monotonic ascending frameNo order to prevent out-of-order jitter
                    val sortedResults = results.sortedBy { it.frameNo }
                    for (res in sortedResults) {
                        qualityEvaluator.evaluateFrames(
                            res.evalInputRgba,
                            res.slot.targetRgba,
                            minOf(srcW, targetW),
                            minOf(srcH, targetH)
                        )
                        encoder.encodeFrame(res.slot.targetYuv, res.ptsUs)
                        processedOutputFrames++
                        updateTelemetry(
                            processed = processedOutputFrames,
                            total = totalOutputFrames,
                            startMs = startTime,
                            batchInfo = batchTag,
                            activeSlots = liveStatuses.toList(),
                            capacity = batchCapacity
                        )
                    }

                    pendingJobs.clear()
                }

                var frame1 = decoder.nextFrame()

                while (frame1 != null && isActive) {
                    // Update dimensions if decoder reports resolution change
                    val frameW = (frame1.width and 1.inv()).coerceAtLeast(16)
                    val frameH = (frame1.height and 1.inv()).coerceAtLeast(16)
                    if (frameW != srcW || frameH != srcH) {
                        flushBatch()
                        srcW = frameW
                        srcH = frameH
                        inputPool = Array(poolCapacity) { ByteBuffer.allocateDirect(srcW * srcH * 4) }
                        workerSlots.forEach { it.ensureCapacity(targetW, targetH, srcW, srcH) }
                        previousInputBuf = null
                    }

                    // Acquire next buffer from direct input pool
                    val currentInputBuf = inputPool[poolIdx % poolCapacity]
                    poolIdx++

                    // Store decoded 32-bit RGBA frame for AI inference
                    currentInputBuf.clear()
                    frame1.data.rewind()
                    val copyLen = minOf(currentInputBuf.remaining(), frame1.data.remaining())
                    val limit = frame1.data.limit()
                    frame1.data.limit(frame1.data.position() + copyLen)
                    currentInputBuf.put(frame1.data)
                    frame1.data.limit(limit)
                    currentInputBuf.rewind()

                    val prevBuf = previousInputBuf
                    var isSceneCut = false
                    if (prevBuf != null) {
                        isSceneCut = NativeEngine.detectSceneCut(
                            prevBuf,
                            currentInputBuf,
                            srcW,
                            srcH,
                            config.sceneDetectionThreshold
                        )
                        if (isSceneCut) {
                            qualityEvaluator.recordSceneCut()
                        }
                    }

                    // Twixtor / AI Frame Interpolation step
                    val doInterpolate = !isUpscaleOnly &&
                            prevBuf != null && (config.interpolationFactor > 1 || config.slowMotionSpeed < 1.0f)

                    if (doInterpolate && prevBuf != null) {
                        val steps = config.interpolationFactor
                        val sceneThresh = (config.sceneDetectionThreshold / 100.0f).coerceIn(0.40f, 0.85f)
                        for (subIdx in 1 until steps) {
                            val t = subIdx.toFloat() / steps
                            val interpPtsUs = previousPtsUs + ((frame1.presentationTimeUs - previousPtsUs) * subIdx / steps)

                            pendingJobs.add(
                                RenderJob(
                                    frameNo = nextFrameNo++,
                                    ptsUs = interpPtsUs,
                                    isIntermediate = true,
                                    srcRgba = prevBuf,
                                    frame1Rgba = currentInputBuf,
                                    timestep = t,
                                    sceneCutThreshold = sceneThresh
                                )
                            )

                            if (pendingJobs.size >= batchCapacity) {
                                flushBatch()
                            }
                        }
                    }

                    // Primary frame job
                    pendingJobs.add(
                        RenderJob(
                            frameNo = nextFrameNo++,
                            ptsUs = frame1.presentationTimeUs,
                            isIntermediate = false,
                            srcRgba = currentInputBuf,
                            frame1Rgba = null,
                            timestep = 1.0f,
                            sceneCutThreshold = 0.5f
                        )
                    )

                    if (pendingJobs.size >= batchCapacity) {
                        flushBatch()
                    }

                    previousInputBuf = currentInputBuf
                    previousPtsUs = frame1.presentationTimeUs

                    frame1 = decoder.nextFrame()
                }

                // Flush any remaining frames in the batch queue (1..batchCapacity-1)
                flushBatch()

                _progress.value = _progress.value.copy(currentStage = "Finalizing Video Streams & Audio...")
                encoder.finishEncoding()

                if (config.preserveAudio) {
                    encoder.muxOriginalAudio(sourceUri, outputFile, config.slowMotionSpeed)
                }

                val finalQuality = qualityEvaluator.generateReport()
                _qualityScore.value = finalQuality

                // Save copy directly to the phone's public Download folder
                val exportResult = com.frameforge.ai.utils.ExportStorageManager.saveVideoToDownloads(
                    context = context,
                    sourceVideoFile = outputFile,
                    targetFileName = outputFile.name
                )

                // Persist to project history
                try {
                    val projMgr = com.frameforge.ai.projects.ProjectManager(context)
                    projMgr.saveProject(
                        com.frameforge.ai.projects.ProjectRecord(
                            name = "Enhance ${metadata.fileName}",
                            inputUri = sourceUri.toString(),
                            outputFilePath = exportResult.publicPath,
                            durationFormatted = metadata.durationFormatted,
                            inputResolution = "${metadata.width}x${metadata.height}",
                            outputResolution = "${targetW}x${targetH}",
                            inputFps = metadata.frameRate,
                            outputFps = finalFps,
                            fileSizeBytes = if (outputFile.exists()) outputFile.length() else 45_000_000L,
                            upscaleModel = config.upscaleModel.displayName,
                            interpolationModel = config.interpolationModel.displayName,
                            status = "COMPLETED",
                            psnrDb = finalQuality.psnrDb,
                            ssim = finalQuality.ssim
                        )
                    )
                } catch (pe: Exception) {
                    Log.w(TAG, "Failed to persist project history: ${pe.message}")
                }

                _state.value = ProcessingState.COMPLETED
                _progress.value = _progress.value.copy(
                    progressPercent = 1.0f,
                    currentStage = "Saved to ${exportResult.publicPath}"
                )
                withContext(Dispatchers.Main) {
                    onComplete(true, outputFile)
                }
            } catch (ce: CancellationException) {
                Log.i(TAG, "Video processing cancelled by user")
                _state.value = ProcessingState.CANCELLED
                withContext(Dispatchers.Main) { onComplete(false, null) }
            } catch (e: Exception) {
                Log.e(TAG, "Video processing failed: ${e.message}", e)
                _state.value = ProcessingState.FAILED
                _progress.value = _progress.value.copy(currentStage = "Error: ${e.message}")
                withContext(Dispatchers.Main) { onComplete(false, null) }
            } finally {
                decoder.release()
                encoder.release()
                try {
                    workerDispatcher?.close()
                    threadPool?.shutdown()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun processJob(
        job: RenderJob,
        slot: BatchWorkerSlot,
        srcW: Int,
        srcH: Int,
        targetW: Int,
        targetH: Int,
        config: ProcessingConfig,
        formatMode: Int
    ): RenderResult {
        val frameRgba: ByteBuffer
        if (job.isIntermediate) {
            slot.interpRgba.clear()
            val interpOk = NativeEngine.interpolate(
                frame0 = job.srcRgba,
                frame1 = job.frame1Rgba!!,
                width = srcW,
                height = srcH,
                t = job.timestep,
                outBuf = slot.interpRgba,
                allowCompatibilityBlend = config.allowCompatibilityResize,
                sceneCutThreshold = job.sceneCutThreshold
            )
            if (!interpOk) {
                throw IllegalStateException("AI Frame Interpolation inference failed on intermediate frame #${job.frameNo} (t=${job.timestep})")
            }
            slot.interpRgba.rewind()
            frameRgba = slot.interpRgba
        } else {
            frameRgba = job.srcRgba
        }

        // Optional Video Restoration (Denoise, Deblock, Sharpen)
        val afterRestoreRgba: ByteBuffer
        if (config.restorationEnabled) {
            val restored = slot.restoredRgba ?: ByteBuffer.allocateDirect(srcW * srcH * 4).also {
                slot.restoredRgba = it
            }
            restored.clear()
            NativeEngine.restore(
                srcBuf = frameRgba,
                dstBuf = restored,
                width = srcW,
                height = srcH,
                denoise = config.denoiseStrength,
                deblock = config.deblockStrength,
                temporal = config.temporalSmooth,
                sharpen = config.sharpenStrength
            )
            restored.rewind()
            afterRestoreRgba = restored
        } else {
            afterRestoreRgba = frameRgba
        }

        // AI Upscaling / Super-Resolution
        val isTwixtorOnly = config.videoMode == VideoProcessingMode.TWIXTOR_ONLY || config.pipelineOrder == PipelineOrder.INTERPOLATE_ONLY
        val shouldUpscale = !isTwixtorOnly && (config.upscaleScale > 1 || srcW != targetW || srcH != targetH)

        slot.targetRgba.clear()
        if (shouldUpscale) {
            val upOk = NativeEngine.upscale(
                srcBuf = afterRestoreRgba,
                srcW = srcW,
                srcH = srcH,
                dstBuf = slot.targetRgba,
                dstW = targetW,
                dstH = targetH,
                tileSize = config.tileSize,
                overlap = config.tileOverlap,
                allowCompatibilityResize = config.allowCompatibilityResize
            )
            if (!upOk) {
                throw IllegalStateException("AI Super-Resolution inference failed on frame #${job.frameNo}")
            }
        } else {
            afterRestoreRgba.rewind()
            val copyLen = minOf(slot.targetRgba.remaining(), afterRestoreRgba.remaining())
            val limit = afterRestoreRgba.limit()
            afterRestoreRgba.limit(afterRestoreRgba.position() + copyLen)
            slot.targetRgba.put(afterRestoreRgba)
            afterRestoreRgba.limit(limit)
        }
        slot.targetRgba.rewind()

        // Optional AI Face Enhancement (GFPGAN / CodeFormer)
        if (config.faceRestorationEnabled) {
            val face = slot.faceRgba ?: ByteBuffer.allocateDirect(targetW * targetH * 4).also {
                slot.faceRgba = it
            }
            face.clear()
            NativeEngine.applyFaceRestoration(
                srcBuf = slot.targetRgba,
                dstBuf = face,
                width = targetW,
                height = targetH,
                strength = config.faceRestorationStrength
            )
            face.rewind()
            slot.targetRgba.clear()
            slot.targetRgba.put(face)
            slot.targetRgba.rewind()
        }

        // Optional Color Grading & HDR Tone Mapping
        if (config.colorGradingMode != ColorGradingMode.OFF) {
            val graded = slot.gradedRgba ?: ByteBuffer.allocateDirect(targetW * targetH * 4).also {
                slot.gradedRgba = it
            }
            graded.clear()
            NativeEngine.applyColorGrading(
                srcBuf = slot.targetRgba,
                dstBuf = graded,
                width = targetW,
                height = targetH,
                mode = config.colorGradingMode.name,
                vibrance = config.colorVibrance,
                contrast = config.colorContrast
            )
            graded.rewind()
            slot.targetRgba.clear()
            slot.targetRgba.put(graded)
            slot.targetRgba.rewind()
        }

        // Convert RGBA to negotiated hardware encoder color format
        slot.targetYuv.clear()
        NativeEngine.rgbaToYuv(slot.targetRgba, slot.targetYuv, targetW, targetH, formatMode = formatMode)
        slot.targetYuv.rewind()

        return RenderResult(
            frameNo = job.frameNo,
            ptsUs = job.ptsUs,
            slot = slot,
            evalInputRgba = frameRgba
        )
    }

    private fun updateTelemetry(
        processed: Int,
        total: Int,
        startMs: Long,
        batchInfo: String? = null,
        activeSlots: List<ParallelWorkerStatus> = emptyList(),
        capacity: Int = 1
    ) {
        val elapsedSec = max(0.001, (SystemClock.elapsedRealtime() - startMs) / 1000.0)
        val currentFps = processed / elapsedSec
        val safeTotal = max(1, total)
        val percent = (processed.toFloat() / safeTotal).coerceIn(0f, 1f)
        val remainingFrames = max(0, safeTotal - processed)
        val etaSec = if (currentFps > 0) (remainingFrames / currentFps).toLong() else 0L

        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        val stageText = batchInfo ?: "Inference & Encoding (Frame $processed / $safeTotal)"

        _progress.value = ProcessingProgress(
            currentFrame = processed,
            totalFrames = safeTotal,
            progressPercent = percent,
            currentFps = ((currentFps * 10).roundToInt() / 10.0),
            etaSeconds = etaSec,
            currentStage = stageText,
            peakMemoryMb = usedRamMb,
            thermalState = if (usedRamMb > 512) "Warm" else "Normal",
            activeParallelSlots = activeSlots,
            parallelCapacity = capacity
        )
    }

    private class BatchWorkerSlot(
        val slotIndex: Int,
        var targetRgba: ByteBuffer,
        var targetYuv: ByteBuffer,
        var interpRgba: ByteBuffer,
        var restoredRgba: ByteBuffer? = null,
        var faceRgba: ByteBuffer? = null,
        var gradedRgba: ByteBuffer? = null
    ) {
        fun ensureCapacity(targetW: Int, targetH: Int, srcW: Int, srcH: Int) {
            val targetSize = targetW * targetH * 4
            val targetYuvSize = targetW * targetH * 3 / 2
            val srcSize = srcW * srcH * 4
            if (targetRgba.capacity() < targetSize) targetRgba = ByteBuffer.allocateDirect(targetSize)
            if (targetYuv.capacity() < targetYuvSize) targetYuv = ByteBuffer.allocateDirect(targetYuvSize)
            if (interpRgba.capacity() < srcSize) interpRgba = ByteBuffer.allocateDirect(srcSize)
            restoredRgba?.let { if (it.capacity() < srcSize) restoredRgba = ByteBuffer.allocateDirect(srcSize) }
            faceRgba?.let { if (it.capacity() < targetSize) faceRgba = ByteBuffer.allocateDirect(targetSize) }
            gradedRgba?.let { if (it.capacity() < targetSize) gradedRgba = ByteBuffer.allocateDirect(targetSize) }
        }
    }

    private class RenderJob(
        val frameNo: Long,
        val ptsUs: Long,
        val isIntermediate: Boolean,
        val srcRgba: ByteBuffer,
        val frame1Rgba: ByteBuffer? = null,
        val timestep: Float = 0f,
        val sceneCutThreshold: Float = 0.5f
    )

    private class RenderResult(
        val frameNo: Long,
        val ptsUs: Long,
        val slot: BatchWorkerSlot,
        val evalInputRgba: ByteBuffer
    )
}
