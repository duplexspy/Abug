package com.frameforge.ai.core

import android.graphics.Bitmap
import java.io.File

/**
 * Model architecture descriptor for Real-ESRGAN and RIFE models.
 */
enum class UpscaleModelType(val displayName: String, val scale: Int, val description: String, val nativeId: Int) {
    REAL_ESRGAN_X4_PLUS("Real-ESRGAN 4x Plus", 4, "State-of-the-art 4x super-resolution", 0),
    REAL_ESRGAN_X2_PLUS("Real-ESRGAN 2x Plus", 2, "High fidelity 2x upscaling, sharp textures", 1),
    REAL_ESRGAN_GENERAL_X4V3("Real-ESRGAN General v3", 4, "Compact general video model with noise reduction", 2),
    REAL_ESRGAN_ANIME_6B("Real-ESRGAN Anime 6B", 4, "Optimized for anime, cartoons, and cell-shaded art", 3),
    REAL_ESRGAN_ANIME_VIDEO_V3("Real-ESRGAN AnimeVideo v3", 4, "Temporal-stable anime video model", 4)
}

enum class InterpolationModelType(val displayName: String, val description: String) {
    RIFE_V4_6("RIFE v4.6 (Fast)", "Real-time Intermediate Flow Estimation, optimal for mobile"),
    RIFE_V4_26("RIFE v4.26 (Quality)", "Enhanced optical flow with superior occlusion handling"),
    RIFE_ANIME("RIFE Anime", "Fine-tuned for 2D animation, hand-drawn lines, and fast cuts")
}

enum class PipelineOrder(val displayName: String, val description: String) {
    INTERPOLATION_FIRST("Interpolate -> Upscale", "Faster: interpolates at lower resolution, then upscales"),
    UPSCALING_FIRST("Upscale -> Interpolate", "Max Quality: upscales first, then calculates optical flow on high-res"),
    BALANCED("Balanced Mode", "Dynamic tile-based flow estimation with resolution downsampling"),
    UPSCALE_ONLY("Upscale Only", "Super-resolution without altering frame rate"),
    INTERPOLATE_ONLY("Interpolate Only", "Twixtor-style frame interpolation without resolution change"),
    RESTORATION_ONLY("Restoration Only", "Denoising, deblocking, and sharpening at native resolution")
}

/**
 * High-level Video Processing Mode selection
 */
enum class VideoProcessingMode(val displayName: String, val subtitle: String) {
    UPSCALE_ONLY("Upscale Quality Only", "AI Super-Resolution up to 4K/8K without altering frame rate"),
    TWIXTOR_ONLY("Twixtor Slow-Mo Only", "RIFE optical flow FPS boost & slow-motion at native resolution"),
    UPSCALE_AND_TWIXTOR("Upscale + Twixtor", "Full studio pipeline: both AI super-resolution and optical flow interpolation")
}

/**
 * Hardware Multi-Core Concurrency Modes
 */
enum class RenderEngineMode(val displayName: String, val description: String, val badge: String) {
    STANDARD("Standard (1 Frame)", "Sequential single-frame pipeline with lowest memory footprint", "Eco Mode"),
    FAST("Fast Mode (4 Frames)", "Concurrent 4-frame batch pipeline for high-speed rendering", "High Speed"),
    ULTRA("Ultra Mode (Multi-Core Max)", "Hardware-scanned maximum parallel frames running simultaneously", "Maximum Multi-Core")
}

enum class SceneCutAction(val displayName: String) {
    FRAME_BLEND("Cosine Blend"),
    DUPLICATE("Duplicate Prior Frame"),
    NEAREST("Nearest Neighbor")
}

enum class PrecisionMode(val displayName: String) {
    FP16("FP16 (Half Precision - Recommended)"),
    FP32("FP32 (Full Precision - Reference)"),
    INT8("INT8 (Quantized - Maximum Speed)")
}

enum class BackendMode(val displayName: String) {
    AUTO("Auto (Vulkan GPU -> CPU Fallback)"),
    VULKAN("Vulkan GPU Acceleration"),
    CPU("Optimized Multi-thread CPU")
}

enum class ExportCodec(val mimeType: String, val displayName: String) {
    H264("video/avc", "H.264 / AVC (Universal Compatibility)"),
    H265("video/hevc", "H.265 / HEVC (High Efficiency, Smaller Size)")
}

/**
 * AI Face Enhancement (GFPGAN / CodeFormer facial reconstruction)
 */
enum class FaceRestorationMode(val displayName: String, val description: String) {
    OFF("Disabled", "No facial neural enhancement"),
    GFPGAN_V1_4("GFPGAN v1.4", "Generative facial prior for realistic eye/skin reconstruction"),
    CODEFORMER("CodeFormer", "Robust codebook lookup for heavily blurred/degraded faces")
}

/**
 * Cinematic Shutter Angle & Motion Blur synthesis
 */
enum class ShutterAngle(val degrees: Int, val displayName: String, val description: String) {
    OFF(0, "Shutter Off (Crisp)", "Raw interpolated intermediate frames without optical blur"),
    DEGREES_90(90, "90° (Sharp Action)", "Tight cinematic shutter for fast sports and action"),
    DEGREES_180(180, "180° (Cinematic Standard)", "Natural Hollywood motion blur matched to target FPS"),
    DEGREES_270(270, "270° (Smooth Dreamy)", "Extended exposure blur for fluid panning"),
    DEGREES_360(360, "360° (Maximum Fluidity)", "Full continuous shutter exposure simulation")
}

/**
 * Twixtor Speed Ramping Interpolation Curve
 */
enum class TwixtorRampingMode(val displayName: String, val description: String) {
    CONSTANT("Constant Slow-Mo", "Even temporal distribution across all source frames"),
    EXPONENTIAL_SLOW("Exponential Slowdown", "Gradual deceleration into hyper slow-motion"),
    S_CURVE("S-Curve Dynamic Ramp", "Smooth cinematic acceleration and deceleration"),
    RAMP_UP_DOWN("Peak Slowdown (Hero Point)", "Slows down to 5% at center of clip, ramps back to 100%")
}

/**
 * HDR Tone Mapping & Dynamic Color Grade
 */
enum class ColorGradingMode(val displayName: String, val description: String) {
    OFF("Standard (Rec.709)", "Preserve source video colors and contrast"),
    ACES_FILMIC("ACES Filmic Tone Map", "Cinematic highlight roll-off and rich shadow depth"),
    REINHARD_HDR("Reinhard HDR Compression", "Preserves specular highlights without clipping"),
    VIVID_ENHANCE("Vivid Dynamic Boost", "Intelligent saturation, vibrance, and local contrast boost"),
    NEUTRAL_DENOISE("Studio Neutral Denoise", "Clean uniform color rendition for archival footage")
}

/**
 * Target Resolution Presets for Upscaling
 */
enum class UpscalePreset(val displayName: String, val scale: Float, val targetWidth: Int, val targetHeight: Int) {
    SCALE_1_5X("1.5x Upscale", 1.5f, 0, 0),
    SCALE_2X("2x Super-Resolution", 2.0f, 0, 0),
    SCALE_3X("3x Super-Resolution", 3.0f, 0, 0),
    SCALE_4X("4x Ultra Super-Resolution", 4.0f, 0, 0),
    TARGET_1080P("Full HD 1080p (1920x1080)", 0f, 1920, 1080),
    TARGET_1440P_2K("2K QHD (2560x1440)", 0f, 2560, 1440),
    TARGET_2160P_4K("4K UHD (3840x2160)", 0f, 3840, 2160),
    TARGET_4320P_8K("8K UHD (7680x4320)", 0f, 7680, 4320)
}

/**
 * Metadata extracted from a video source.
 */
data class VideoMetadata(
    val uri: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val frameRate: Double = 30.0,
    val frameCount: Int = 0,
    val bitrate: Long = 0L,
    val mimeType: String = "video/mp4",
    val rotation: Int = 0,
    val hasAudio: Boolean = false,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0
) {
    val resolutionString: String get() = "${width}x${height}"
    val durationSeconds: Double get() = durationMs / 1000.0
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = durationMs % 1000 / 100
            return String.format("%02d:%02d.%d", min, sec, ms)
        }
    val fileSizeFormatted: String
        get() {
            return when {
                fileSizeBytes >= 1_000_000_000L -> String.format("%.2f GB", fileSizeBytes / 1_000_000_000.0)
                fileSizeBytes >= 1_000_000L -> String.format("%.1f MB", fileSizeBytes / 1_000_000.0)
                fileSizeBytes >= 1_000L -> String.format("%.1f KB", fileSizeBytes / 1_000.0)
                else -> "$fileSizeBytes B"
            }
        }
}

/**
 * Metadata extracted from a single photo / still source.
 */
data class ImageMetadata(
    val uri: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "image/png"
) {
    val resolutionString: String get() = "${width}x${height}"
    val fileSizeFormatted: String
        get() {
            return when {
                fileSizeBytes >= 1_000_000_000L -> String.format("%.2f GB", fileSizeBytes / 1_000_000_000.0)
                fileSizeBytes >= 1_000_000L -> String.format("%.1f MB", fileSizeBytes / 1_000_000.0)
                fileSizeBytes >= 1_000L -> String.format("%.1f KB", fileSizeBytes / 1_000.0)
                else -> "$fileSizeBytes B"
            }
        }
}

/**
 * Result of Single Image Super-Resolution processing.
 */
data class ImageProcessingResult(
    val outputFile: File? = null,
    val outputBitmap: Bitmap? = null,
    val inputBitmap: Bitmap? = null,
    val publicDownloadPath: String = "",
    val outputUriString: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val elapsedMs: Long = 0L,
    val psnrDb: Float = 0f,
    val ssim: Float = 0f,
    val success: Boolean = true,
    val errorMessage: String = ""
)

/**
 * Full configuration for video processing task.
 */
data class ProcessingConfig(
    val videoMode: VideoProcessingMode = VideoProcessingMode.UPSCALE_AND_TWIXTOR,
    val renderMode: RenderEngineMode = RenderEngineMode.ULTRA,
    val fastRenderMode: Boolean = true,
    val batchSize: Int = 4, // Concurrent frames processed in parallel in Fast Mode
    val ultraBatchSize: Int = 8, // Hardware scanned frames in Ultra Mode
    val scannedMaxParallelFrames: Int = 8, // Hardware scanned device maximum
    val upscaleModel: UpscaleModelType = UpscaleModelType.REAL_ESRGAN_X2_PLUS,
    val upscalePreset: UpscalePreset = UpscalePreset.SCALE_2X,
    val upscaleScale: Int = 2,
    val customTargetWidth: Int = 0,
    val customTargetHeight: Int = 0,
    val tileSize: Int = 0, // 0 = auto
    val tileOverlap: Int = 16,
    val interpolationModel: InterpolationModelType = InterpolationModelType.RIFE_V4_6,
    val interpolationFactor: Int = 2, // 2x, 4x, 8x
    val targetFps: Double = 60.0,
    val slowMotionSpeed: Float = 1.0f, // 1.0 = normal, 0.5 = 50% slow-mo, 0.25 = 25% slow-mo
    val twixtorRampingMode: TwixtorRampingMode = TwixtorRampingMode.CONSTANT,
    val sceneDetectionThreshold: Float = 30.0f,
    val sceneCutAction: SceneCutAction = SceneCutAction.FRAME_BLEND,
    val restorationEnabled: Boolean = false,
    val denoiseStrength: Float = 0.25f,
    val deblockStrength: Float = 0.30f,
    val temporalSmooth: Float = 0.20f,
    val sharpenStrength: Float = 0.15f,
    val faceRestorationEnabled: Boolean = false,
    val faceRestorationMode: FaceRestorationMode = FaceRestorationMode.GFPGAN_V1_4,
    val faceRestorationStrength: Float = 0.60f,
    val motionBlurEnabled: Boolean = false,
    val shutterAngle: ShutterAngle = ShutterAngle.DEGREES_180,
    val motionBlurIntensity: Float = 0.50f,
    val colorGradingMode: ColorGradingMode = ColorGradingMode.OFF,
    val colorVibrance: Float = 1.10f,
    val colorContrast: Float = 1.05f,
    val pipelineOrder: PipelineOrder = PipelineOrder.INTERPOLATION_FIRST,
    val exportCodec: ExportCodec = ExportCodec.H264,
    val exportBitrateMbps: Int = 25,
    val preserveAudio: Boolean = true,
    val precision: PrecisionMode = PrecisionMode.FP16,
    val backend: BackendMode = BackendMode.AUTO,
    val allowCompatibilityResize: Boolean = false
)

/**
 * Quality metrics evaluated for output frame comparison.
 */
data class QualityScore(
    val psnrDb: Float = 0.0f,
    val ssim: Float = 0.0f,
    val temporalFlicker: Float = 0.0f,
    val sceneCutsCount: Int = 0,
    val framesAnalyzed: Int = 0
)

/**
 * Individual worker status for parallel batch processing slots.
 */
data class ParallelWorkerStatus(
    val slotIndex: Int,
    val frameNo: Long,
    val stage: String,
    val isBusy: Boolean
)

/**
 * Real-time progress report.
 */
data class ProcessingProgress(
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val progressPercent: Float = 0.0f,
    val currentFps: Double = 0.0,
    val etaSeconds: Long = 0L,
    val currentStage: String = "Idle",
    val peakMemoryMb: Long = 0L,
    val thermalState: String = "Normal",
    val activeParallelSlots: List<ParallelWorkerStatus> = emptyList(),
    val parallelCapacity: Int = 1
)

enum class ProcessingState {
    IDLE,
    ANALYZING,
    PROCESSING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
