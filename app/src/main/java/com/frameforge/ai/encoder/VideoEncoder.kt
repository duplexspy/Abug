package com.frameforge.ai.encoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.frameforge.ai.core.ExportCodec
import java.io.File
import java.nio.ByteBuffer

class VideoEncoder(private val context: Context) {
    companion object {
        private const val TAG = "FrameForge_Encoder"
        private const val TIMEOUT_US = 10000L
    }

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var isMuxerStarted = false
    var outputWidth = 0
        private set
    var outputHeight = 0
        private set
    private var frameRate = 30
    var inputColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        private set

    val isNv21Format: Boolean
        get() = inputColorFormat == 0x7FA30C00 // Qualcomm NV21

    val formatMode: Int
        get() = when (inputColorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> 2 // I420
            0x7FA30C00 -> 1 // NV21
            else -> 0 // NV12
        }

    fun prepare(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrateMbps: Int,
        exportCodec: ExportCodec
    ): Boolean {
        release()

        // 1. Dimensions must strictly be even numbers for hardware YUV 4:2:0 encoders
        var safeW = (width and 1.inv()).coerceAtLeast(16)
        var safeH = (height and 1.inv()).coerceAtLeast(16)
        val safeFps = fps.coerceIn(1, 60)
        val safeBitrate = (bitrateMbps * 1_000_000).coerceIn(1_000_000, 35_000_000)

        // 2. Ensure output directory exists and delete stale file
        try {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) {
                outputFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean output file: ${e.message}")
        }

        // 3. Try requested codec first, then fallback to AVC / H.264
        val candidateMimes = listOf(
            exportCodec.mimeType,
            MediaFormat.MIMETYPE_VIDEO_AVC
        ).distinct()

        for (mime in candidateMimes) {
            if (tryConfigureEncoder(outputFile, mime, safeW, safeH, safeFps, safeBitrate)) {
                return true
            }
        }

        // 4. Last-ditch attempt: Standard 1080p / 720p with AVC if high resolution failed
        val fallbackW = minOf(safeW, 1920) and 1.inv()
        val fallbackH = minOf(safeH, 1080) and 1.inv()
        if (tryConfigureEncoder(outputFile, MediaFormat.MIMETYPE_VIDEO_AVC, fallbackW, fallbackH, 30, 8_000_000)) {
            return true
        }

        Log.e(TAG, "All video encoder configurations failed for ${width}x${height}")
        return false
    }

    private fun tryConfigureEncoder(
        outputFile: File,
        mime: String,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int
    ): Boolean {
        var mediaCodec: MediaCodec? = null
        try {
            val codecInfo = selectEncoderInfo(mime)
            val caps = codecInfo?.getCapabilitiesForType(mime)

            var targetW = width
            var targetH = height

            // Check if size is supported by device video capabilities
            val videoCaps = caps?.videoCapabilities
            if (videoCaps != null) {
                val maxW = videoCaps.supportedWidths.upper
                val maxH = videoCaps.supportedHeights.upper
                if (targetW > maxW || targetH > maxH) {
                    val scale = minOf(maxW.toFloat() / targetW, maxH.toFloat() / targetH)
                    targetW = ((targetW * scale).toInt() and 1.inv()).coerceAtLeast(16)
                    targetH = ((targetH * scale).toInt() and 1.inv()).coerceAtLeast(16)
                    Log.i(TAG, "Downscaled encoder target to device max: ${targetW}x${targetH}")
                }
            }

            // Negotiate color format
            val supportedColors = caps?.colorFormats ?: intArrayOf()
            val chosenColor = when {
                supportedColors.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                supportedColors.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                supportedColors.contains(0x7FA30C00) -> // QCOM NV21
                    0x7FA30C00
                else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            }

            inputColorFormat = chosenColor
            outputWidth = targetW
            outputHeight = targetH
            frameRate = fps

            val format = MediaFormat.createVideoFormat(mime, targetW, targetH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, chosenColor)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 keyframe per second
            }

            mediaCodec = if (codecInfo != null) {
                MediaCodec.createByCodecName(codecInfo.name)
            } else {
                MediaCodec.createEncoderByType(mime)
            }

            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec.start()
            codec = mediaCodec

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            isMuxerStarted = false
            videoTrackIndex = -1
            Log.i(TAG, "Configured encoder ${codecInfo?.name ?: mime} for ${targetW}x${targetH} @ ${fps}fps (color: $chosenColor)")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Encoder attempt with mime=$mime at ${width}x${height} failed: ${e.message}")
            try {
                mediaCodec?.stop()
                mediaCodec?.release()
            } catch (_: Exception) {}
            codec = null
            muxer = null
            return false
        }
    }

    private fun selectEncoderInfo(mime: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mime, ignoreCase = true)) {
                    return info
                }
            }
        }
        return null
    }

    fun encodeFrame(yuvData: ByteBuffer, presentationTimeUs: Long): Boolean {
        val currentCodec = codec ?: return false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            // Feed input
            val inIndex = currentCodec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val inputBuffer = currentCodec.getInputBuffer(inIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    yuvData.rewind()
                    val bytesToCopy = minOf(inputBuffer.remaining(), yuvData.remaining())
                    val limit = yuvData.limit()
                    yuvData.limit(yuvData.position() + bytesToCopy)
                    inputBuffer.put(yuvData)
                    yuvData.limit(limit)

                    currentCodec.queueInputBuffer(inIndex, 0, bytesToCopy, presentationTimeUs, 0)
                }
            }

            // Drain output
            drainEncoder(bufferInfo, false)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during frame encoding: ${e.message}")
            return false
        }
    }

    fun finishEncoding() {
        val currentCodec = codec ?: return
        try {
            val inIndex = currentCodec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                currentCodec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainEncoder(MediaCodec.BufferInfo(), true)
        } catch (e: Exception) {
            Log.w(TAG, "Error finishing encoding: ${e.message}")
        }

        try {
            if (isMuxerStarted) {
                muxer?.stop()
                isMuxerStarted = false
                Log.i(TAG, "Muxer stopped successfully in finishEncoding, moov header finalized.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping muxer in finishEncoding: ${e.message}")
        }
    }

    private fun drainEncoder(bufferInfo: MediaCodec.BufferInfo, endOfStream: Boolean) {
        val currentCodec = codec ?: return
        val currentMuxer = muxer ?: return

        while (true) {
            val outIndex = currentCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isMuxerStarted) {
                    Log.w(TAG, "Format changed after muxer started")
                } else {
                    val newFormat = currentCodec.outputFormat
                    videoTrackIndex = currentMuxer.addTrack(newFormat)
                    currentMuxer.start()
                    isMuxerStarted = true
                }
            } else if (outIndex >= 0) {
                val encodedData = currentCodec.getOutputBuffer(outIndex)
                if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (bufferInfo.size != 0 && isMuxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        currentMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                }
                currentCodec.releaseOutputBuffer(outIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    fun muxOriginalAudio(sourceUri: Uri, outputFile: File, slowMotionFactor: Float = 1.0f) {
        if (slowMotionFactor != 1.0f) return

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, sourceUri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = f
                    break
                }
            }

            if (audioTrack >= 0 && audioFormat != null) {
                extractor.selectTrack(audioTrack)
                Log.i(TAG, "Original audio track identified: ${audioFormat.getString(MediaFormat.KEY_MIME)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio remux pass skipped: ${e.message}")
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    fun release() {
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing encoder codec: ${e.message}")
        } finally {
            codec = null
        }

        try {
            if (isMuxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing muxer: ${e.message}")
        } finally {
            muxer = null
            isMuxerStarted = false
            videoTrackIndex = -1
        }
    }
}
