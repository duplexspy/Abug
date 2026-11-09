package com.frameforge.ai.decoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.frameforge.ai.core.VideoMetadata
import java.io.File
import java.nio.ByteBuffer

data class DecodedFrame(
    val data: ByteBuffer,
    val width: Int,
    val height: Int,
    val presentationTimeUs: Long,
    val isKeyFrame: Boolean,
    val frameIndex: Int
)

class VideoDecoder(private val context: Context) {
    companion object {
        private const val TAG = "FrameForge_Decoder"
        private const val TIMEOUT_US = 10000L
    }

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var videoFormat: MediaFormat? = null
    private var width = 0
    private var height = 0
    private var frameRate = 30.0
    private var durationUs = 0L
    private var currentFrameIndex = 0

    val audioTrack: Int get() = audioTrackIndex

    fun extractMetadata(videoUri: Uri): VideoMetadata {
        val mediaExtractor = MediaExtractor()
        try {
            mediaExtractor.setDataSource(context, videoUri, null)
            var vidWidth = 0
            var vidHeight = 0
            var duration = 0L
            var fps = 30.0
            var bitrate = 0L
            var mime = "video/mp4"
            var rotation = 0
            var hasAudio = false
            var audioChannels = 0
            var audioSampleRate = 0

            val trackCount = mediaExtractor.trackCount
            for (i in 0 until trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("video/")) {
                    vidWidth = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
                    vidHeight = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                    duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                    }
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
                    mime = trackMime
                } else if (trackMime.startsWith("audio/")) {
                    hasAudio = true
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
            }

            var fileSize = 0L
            val path = videoUri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) fileSize = file.length()
            }

            val estimatedFrames = if (fps > 0 && duration > 0) ((duration / 1_000_000.0) * fps).toInt() else 0

            return VideoMetadata(
                uri = videoUri.toString(),
                fileName = videoUri.lastPathSegment ?: "video.mp4",
                fileSizeBytes = fileSize,
                width = vidWidth,
                height = vidHeight,
                durationMs = duration / 1000L,
                frameRate = fps,
                frameCount = estimatedFrames,
                bitrate = bitrate,
                mimeType = mime,
                rotation = rotation,
                hasAudio = hasAudio,
                audioChannels = audioChannels,
                audioSampleRate = audioSampleRate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata: ${e.message}")
            return VideoMetadata(uri = videoUri.toString())
        } finally {
            mediaExtractor.release()
        }
    }

    fun open(videoUri: Uri): Boolean {
        release()
        try {
            val mediaExtractor = MediaExtractor().also { extractor = it }
            mediaExtractor.setDataSource(context, videoUri, null)

            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = format
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    }
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) {
                Log.e(TAG, "No video track found")
                return false
            }

            mediaExtractor.selectTrack(videoTrackIndex)
            val mime = videoFormat!!.getString(MediaFormat.KEY_MIME)!!
            val mediaCodec = MediaCodec.createDecoderByType(mime)
            codec = mediaCodec
            mediaCodec.configure(videoFormat, null, null, 0)
            mediaCodec.start()
            currentFrameIndex = 0
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open video decoder: ${e.message}", e)
            release()
            return false
        }
    }

    fun nextFrame(): DecodedFrame? {
        val currentCodec = codec ?: return null
        val currentExtractor = extractor ?: return null
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false

        while (true) {
            if (!sawInputEOS) {
                val inIndex = currentCodec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inputBuffer = currentCodec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = currentExtractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            currentCodec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val presentationTime = currentExtractor.sampleTime
                            val flags = currentExtractor.sampleFlags
                            currentCodec.queueInputBuffer(inIndex, 0, sampleSize, presentationTime, flags)
                            currentExtractor.advance()
                        }
                    }
                }
            }

            val outIndex = currentCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outIndex >= 0) {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    currentCodec.releaseOutputBuffer(outIndex, false)
                    return null
                }

                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                val pts = bufferInfo.presentationTimeUs

                var image: android.media.Image? = null
                var frameBuffer: ByteBuffer? = null

                try {
                    image = currentCodec.getOutputImage(outIndex)
                } catch (e: Exception) {
                    Log.w(TAG, "getOutputImage failed: ${e.message}")
                }

                val safeW = (width and 1.inv()).coerceAtLeast(16)
                val safeH = (height and 1.inv()).coerceAtLeast(16)

                if (image != null) {
                    try {
                        val rgbaBuf = ByteBuffer.allocateDirect(safeW * safeH * 4)
                        val planes = image.planes
                        val yPlane = planes[0]
                        val uPlane = planes[1]
                        val vPlane = planes[2]

                        com.frameforge.ai.inference.NativeEngine.yuvPlanesToRgba(
                            yBuf = yPlane.buffer,
                            yRowStride = yPlane.rowStride,
                            yPixelStride = yPlane.pixelStride,
                            uBuf = uPlane.buffer,
                            uRowStride = uPlane.rowStride,
                            uPixelStride = uPlane.pixelStride,
                            vBuf = vPlane.buffer,
                            vRowStride = vPlane.rowStride,
                            vPixelStride = vPlane.pixelStride,
                            rgbaBuf = rgbaBuf,
                            width = safeW,
                            height = safeH
                        )
                        rgbaBuf.rewind()
                        frameBuffer = rgbaBuf
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting image planes to RGBA: ${e.message}", e)
                    } finally {
                        try { image.close() } catch (_: Exception) {}
                    }
                } else {
                    val outputBuffer = currentCodec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val rgbaBuf = ByteBuffer.allocateDirect(safeW * safeH * 4)
                        com.frameforge.ai.inference.NativeEngine.yuvToRgba(
                            yuvBuf = outputBuffer,
                            rgbaBuf = rgbaBuf,
                            width = safeW,
                            height = safeH,
                            isNv21 = false
                        )
                        rgbaBuf.rewind()
                        frameBuffer = rgbaBuf
                    }
                }

                currentCodec.releaseOutputBuffer(outIndex, false)

                if (frameBuffer != null) {
                    return DecodedFrame(
                        data = frameBuffer,
                        width = safeW,
                        height = safeH,
                        presentationTimeUs = pts,
                        isKeyFrame = isKey,
                        frameIndex = currentFrameIndex++
                    )
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = currentCodec.outputFormat
                if (newFormat.containsKey("crop-right") && newFormat.containsKey("crop-left")) {
                    val cropW = newFormat.getInteger("crop-right") - newFormat.getInteger("crop-left") + 1
                    if (cropW > 0) width = cropW
                } else if (newFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                    width = newFormat.getInteger(MediaFormat.KEY_WIDTH)
                }

                if (newFormat.containsKey("crop-bottom") && newFormat.containsKey("crop-top")) {
                    val cropH = newFormat.getInteger("crop-bottom") - newFormat.getInteger("crop-top") + 1
                    if (cropH > 0) height = cropH
                } else if (newFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
                    height = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
                }
            } else if (sawInputEOS && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return null
            }
        }
    }

    fun release() {
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing codec: ${e.message}")
        } finally {
            codec = null
        }

        try {
            extractor?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing extractor: ${e.message}")
        } finally {
            extractor = null
        }
        videoTrackIndex = -1
        audioTrackIndex = -1
        videoFormat = null
    }
}
