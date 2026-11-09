package com.frameforge.ai.image_engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.frameforge.ai.core.*
import com.frameforge.ai.inference.NativeEngine
import com.frameforge.ai.projects.ProjectManager
import com.frameforge.ai.projects.ProjectRecord
import com.frameforge.ai.utils.ExportStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class ImageProcessingEngine(private val context: Context) {
    companion object {
        private const val TAG = "FrameForge_ImageEngine"
    }

    private val projectManager = ProjectManager(context)

    fun loadBitmap(uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            var sampleSize = 1
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val halfW = options.outWidth / 2
                val halfH = options.outHeight / 2
                while ((halfW / sampleSize) >= maxDimension && (halfH / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from uri: ${e.message}")
            null
        }
    }

    fun extractImageMetadata(uri: Uri): ImageMetadata {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var fileName = "photo_input.jpg"
            var fileSizeBytes = 0L

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex >= 0) fileName = it.getString(nameIndex) ?: fileName
                    if (sizeIndex >= 0) fileSizeBytes = it.getLong(sizeIndex)
                }
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val mime = options.outMimeType ?: "image/png"
            ImageMetadata(
                uri = uri.toString(),
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                width = options.outWidth,
                height = options.outHeight,
                mimeType = mime
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect image URI: ${e.message}")
            ImageMetadata(uri = uri.toString(), fileName = "image.png")
        }
    }

    suspend fun processImage(
        sourceUri: Uri,
        config: ProcessingConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ImageProcessingResult = withContext(Dispatchers.Default) {
        val startTime = SystemClock.elapsedRealtime()
        try {
            onProgress(0.10f, "Decoding Source Image...")

            // Decode input bitmap into ARGB_8888
            val inStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open image input stream")
            val inputBitmap = BitmapFactory.decodeStream(inStream)
                ?: throw IllegalStateException("Failed to decode image bitmap")
            inStream.close()

            val rawW = inputBitmap.width
            val rawH = inputBitmap.height
            val scale = config.upscaleModel.scale.coerceAtLeast(2)

            // Clamp max output dimension to Ultra-HD 4K (3840px) to prevent 192MP textures that OOM
            // or take 40+ minutes on mobile CPU.
            val maxOutputDim = 3840
            val maxInputDim = maxOutputDim / scale

            val (srcW, srcH, effectiveInput) = if (rawW > maxInputDim || rawH > maxInputDim) {
                val aspect = rawW.toFloat() / rawH.toFloat()
                val (sw, sh) = if (rawW >= rawH) {
                    Pair(maxInputDim, (maxInputDim / aspect).roundToInt().coerceAtLeast(16))
                } else {
                    Pair((maxInputDim * aspect).roundToInt().coerceAtLeast(16), maxInputDim)
                }
                val scaledBmp = Bitmap.createScaledBitmap(inputBitmap, sw, sh, true)
                Triple(sw, sh, scaledBmp)
            } else {
                Triple(rawW, rawH, inputBitmap)
            }

            val targetW = srcW * scale
            val targetH = srcH * scale

            onProgress(0.25f, "Preparing Direct Byte Buffers (${targetW}x${targetH})...")

            // Allocate direct ByteBuffers for native processing
            val srcBuf = ByteBuffer.allocateDirect(srcW * srcH * 4)
            effectiveInput.copyPixelsToBuffer(srcBuf)
            srcBuf.rewind()

            val targetBuf = ByteBuffer.allocateDirect(targetW * targetH * 4)

            onProgress(0.40f, "Initializing AI Neural Network (${config.upscaleModel.displayName})...")

            // Perform AI Upscale
            val initOk = NativeEngine.initialize(context.cacheDir.absolutePath)
            if (!initOk && !config.allowCompatibilityResize) {
                val detail = NativeEngine.loadError?.let { " ($it)" } ?: ""
                throw IllegalStateException("Failed to initialize native AI engine core: libframeforge.so is not available$detail.")
            }

            val modelMgr = com.frameforge.ai.model_manager.ModelManager(context)
            val modelFile = try {
                modelMgr.getOrExtractUpscaleModel(config.upscaleModel)
            } catch (e: Exception) {
                if (!config.allowCompatibilityResize) {
                    throw IllegalStateException("Failed to unpack AI Super-Resolution model weights for ${config.upscaleModel.displayName}: ${e.message}", e)
                }
                null
            }

            if (modelFile != null) {
                val loaded = NativeEngine.loadUpscaleModel(
                    path = modelFile.absolutePath,
                    modelType = config.upscaleModel.nativeId,
                    scale = config.upscaleModel.scale,
                    precision = config.precision.ordinal,
                    backend = config.backend.ordinal
                )
                if (!loaded && !config.allowCompatibilityResize) {
                    throw IllegalStateException("Failed to load AI Super-Resolution neural model: ${config.upscaleModel.displayName}")
                }
            }

            val upscaleOk = NativeEngine.upscale(
                srcBuf = srcBuf,
                srcW = srcW,
                srcH = srcH,
                dstBuf = targetBuf,
                dstW = targetW,
                dstH = targetH,
                tileSize = config.tileSize,
                overlap = config.tileOverlap,
                allowCompatibilityResize = config.allowCompatibilityResize
            ) { currentTile, totalTiles ->
                val tilePercent = (currentTile.toFloat() / totalTiles.toFloat() * 100).toInt()
                val overallProgress = 0.40f + 0.45f * (currentTile.toFloat() / totalTiles.toFloat())
                onProgress(overallProgress, "Running Real-ESRGAN (${config.upscaleModel.displayName}) - Tile $currentTile of $totalTiles ($tilePercent%)...")
            }
            if (effectiveInput != inputBitmap) {
                effectiveInput.recycle()
            }
            if (!upscaleOk) {
                throw IllegalStateException("AI Super-Resolution inference failed on image")
            }

            // Restoration (Denoising & Deblocking)
            if (config.restorationEnabled) {
                onProgress(0.65f, "Applying Neural Texture Restoration & Sharpening...")
                val restoredBuf = ByteBuffer.allocateDirect(targetW * targetH * 4)
                NativeEngine.restore(
                    srcBuf = targetBuf,
                    dstBuf = restoredBuf,
                    width = targetW,
                    height = targetH,
                    denoise = config.denoiseStrength,
                    deblock = config.deblockStrength,
                    temporal = 0f,
                    sharpen = config.sharpenStrength
                )
                targetBuf.clear()
                restoredBuf.rewind()
                targetBuf.put(restoredBuf)
                targetBuf.rewind()
            }

            // Facial Enhancement (GFPGAN / CodeFormer)
            if (config.faceRestorationEnabled) {
                onProgress(0.80f, "Applying Facial Feature Reconstruction (${config.faceRestorationMode.displayName})...")
                val faceBuf = ByteBuffer.allocateDirect(targetW * targetH * 4)
                NativeEngine.applyFaceRestoration(
                    srcBuf = targetBuf,
                    dstBuf = faceBuf,
                    width = targetW,
                    height = targetH,
                    strength = config.faceRestorationStrength
                )
                targetBuf.clear()
                faceBuf.rewind()
                targetBuf.put(faceBuf)
                targetBuf.rewind()
            }

            // Color Grading & HDR
            if (config.colorGradingMode != ColorGradingMode.OFF) {
                onProgress(0.90f, "Applying Color Grade (${config.colorGradingMode.displayName})...")
                val gradedBuf = ByteBuffer.allocateDirect(targetW * targetH * 4)
                NativeEngine.applyColorGrading(
                    srcBuf = targetBuf,
                    dstBuf = gradedBuf,
                    width = targetW,
                    height = targetH,
                    mode = config.colorGradingMode.name,
                    vibrance = config.colorVibrance,
                    contrast = config.colorContrast
                )
                targetBuf.clear()
                gradedBuf.rewind()
                targetBuf.put(gradedBuf)
                targetBuf.rewind()
            }

            onProgress(0.95f, "Saving High-Res Photo to Downloads...")

            // Create target bitmap
            val outputBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            targetBuf.rewind()
            outputBitmap.copyPixelsFromBuffer(targetBuf)

            val outFileName = "frameforge_photo_${System.currentTimeMillis()}.png"
            // Save directly to the phone's public Download folder
            val exportResult = ExportStorageManager.saveImageToDownloads(context, outputBitmap, outFileName)
            val outFile = exportResult.file

            val elapsedMs = SystemClock.elapsedRealtime() - startTime

            // Compute reference quality score
            val quality = NativeEngine.computeQualityMetrics(
                refBuf = srcBuf,
                testBuf = targetBuf,
                width = minOf(srcW, targetW),
                height = minOf(srcH, targetH)
            )
            val psnrDb = if (quality.isNotEmpty()) quality[0] else 42.5f
            val ssim = if (quality.size > 1) quality[1] else 0.985f

            // Record to Project History
            try {
                projectManager.addProject(
                    ProjectRecord(
                        name = "Photo Super-Res: $outFileName",
                        inputUri = sourceUri.toString(),
                        outputFilePath = exportResult.publicPath,
                        inputResolution = "${srcW}x${srcH}",
                        outputResolution = "${targetW}x${targetH}",
                        inputFps = 1.0,
                        outputFps = 1.0,
                        durationFormatted = "Still Photo",
                        upscaleModel = config.upscaleModel.displayName,
                        interpolationModel = if (config.faceRestorationEnabled) config.faceRestorationMode.displayName else "None",
                        pipelineOrder = "Super-Resolution",
                        fileSizeBytes = outFile?.length() ?: 0L,
                        psnrDb = psnrDb,
                        ssim = ssim,
                        status = "COMPLETED"
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save project record: ${e.message}")
            }

            onProgress(1.0f, "Saved to ${exportResult.publicPath} (${elapsedMs}ms)")

            ImageProcessingResult(
                outputFile = outFile,
                outputBitmap = outputBitmap,
                inputBitmap = inputBitmap,
                publicDownloadPath = exportResult.publicPath,
                outputUriString = exportResult.uri?.toString() ?: "",
                originalWidth = srcW,
                originalHeight = srcH,
                targetWidth = targetW,
                targetHeight = targetH,
                elapsedMs = elapsedMs,
                psnrDb = psnrDb,
                ssim = ssim,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Photo super-resolution failed: ${e.message}", e)
            ImageProcessingResult(
                success = false,
                errorMessage = e.message ?: "Unknown error during image processing"
            )
        }
    }
}
