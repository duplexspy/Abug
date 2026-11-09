package com.frameforge.ai.projects

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ProjectRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val inputUri: String = "",
    val outputFilePath: String = "",
    val inputResolution: String = "1920x1080",
    val outputResolution: String = "3840x2160",
    val inputFps: Double = 30.0,
    val outputFps: Double = 60.0,
    val durationFormatted: String = "00:15.0",
    val upscaleModel: String = "Real-ESRGAN 4x Plus",
    val interpolationModel: String = "RIFE v4.6",
    val pipelineOrder: String = "Interpolate -> Upscale",
    val fileSizeBytes: Long = 48_500_000L,
    val psnrDb: Float = 39.42f,
    val ssim: Float = 0.9715f,
    val status: String = "COMPLETED"
) {
    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
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

class ProjectManager(private val context: Context) {
    private val projectsFile: File
        get() = File(context.filesDir, "projects.json")

    init {
        ensureSeeded()
    }

    private fun ensureSeeded() {
        if (!projectsFile.exists() || projectsFile.length() == 0L) {
            val initial = listOf(
                ProjectRecord(
                    id = "proj-4k-twixtor-demo",
                    name = "Cinematic Action Shot - 4K 60FPS",
                    timestamp = System.currentTimeMillis() - 86400000L * 2, // 2 days ago
                    inputUri = "file:///data/local/tmp/action_shot.mp4",
                    outputFilePath = "${context.filesDir}/exports/action_shot_4k60.mp4",
                    inputResolution = "1920x1080",
                    outputResolution = "3840x2160 (4K UHD)",
                    inputFps = 30.0,
                    outputFps = 60.0,
                    durationFormatted = "00:24.5",
                    upscaleModel = "Real-ESRGAN 4x Plus",
                    interpolationModel = "RIFE v4.6 (Fast)",
                    pipelineOrder = "Interpolate -> Upscale",
                    fileSizeBytes = 74_200_000L,
                    psnrDb = 41.25f,
                    ssim = 0.9820f,
                    status = "COMPLETED"
                ),
                ProjectRecord(
                    id = "proj-anime-120fps-demo",
                    name = "Anime Fight Scene - 120FPS Slow-Mo",
                    timestamp = System.currentTimeMillis() - 86400000L * 4, // 4 days ago
                    inputUri = "file:///data/local/tmp/anime_clip.mp4",
                    outputFilePath = "${context.filesDir}/exports/anime_clip_120fps.mp4",
                    inputResolution = "1280x720",
                    outputResolution = "2560x1440 (2K QHD)",
                    inputFps = 24.0,
                    outputFps = 120.0,
                    durationFormatted = "00:12.8",
                    upscaleModel = "Real-ESRGAN Anime 4x",
                    interpolationModel = "RIFE Anime",
                    pipelineOrder = "Balanced Mode",
                    fileSizeBytes = 52_600_000L,
                    psnrDb = 43.10f,
                    ssim = 0.9885f,
                    status = "COMPLETED"
                ),
                ProjectRecord(
                    id = "proj-restored-vintage-demo",
                    name = "Vintage Tape Restoration & 2x Upscale",
                    timestamp = System.currentTimeMillis() - 86400000L * 7, // 7 days ago
                    inputUri = "file:///data/local/tmp/family_vintage.mp4",
                    outputFilePath = "${context.filesDir}/exports/family_vintage_restored.mp4",
                    inputResolution = "640x480",
                    outputResolution = "1280x960",
                    inputFps = 29.97,
                    outputFps = 29.97,
                    durationFormatted = "01:05.2",
                    upscaleModel = "Real-ESRGAN 2x AnimeVideo v3",
                    interpolationModel = "RIFE v4 (None)",
                    pipelineOrder = "Restoration -> Upscale",
                    fileSizeBytes = 36_800_000L,
                    psnrDb = 38.80f,
                    ssim = 0.9640f,
                    status = "COMPLETED"
                )
            )
            saveProjects(initial)
        }
    }

    @Synchronized
    fun getAllProjects(): List<ProjectRecord> {
        val file = projectsFile
        if (!file.exists()) return emptyList()
        val list = mutableListOf<ProjectRecord>()
        try {
            val content = file.readText()
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProjectRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Untitled Project"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        inputUri = obj.optString("inputUri", ""),
                        outputFilePath = obj.optString("outputFilePath", ""),
                        inputResolution = obj.optString("inputResolution", "1920x1080"),
                        outputResolution = obj.optString("outputResolution", "3840x2160"),
                        inputFps = obj.optDouble("inputFps", 30.0),
                        outputFps = obj.optDouble("outputFps", 60.0),
                        durationFormatted = obj.optString("durationFormatted", "00:15.0"),
                        upscaleModel = obj.optString("upscaleModel", "Real-ESRGAN"),
                        interpolationModel = obj.optString("interpolationModel", "RIFE"),
                        pipelineOrder = obj.optString("pipelineOrder", "Interpolate -> Upscale"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        psnrDb = obj.optDouble("psnrDb", 40.0).toFloat(),
                        ssim = obj.optDouble("ssim", 0.97).toFloat(),
                        status = obj.optString("status", "COMPLETED")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun getProjects(): List<ProjectRecord> = getAllProjects()

    @Synchronized
    fun addProject(project: ProjectRecord) {
        val current = getAllProjects().toMutableList()
        current.removeAll { it.id == project.id }
        current.add(0, project)
        saveProjects(current)
    }

    fun saveProject(project: ProjectRecord) = addProject(project)

    @Synchronized
    fun deleteProject(id: String): Boolean {
        val current = getAllProjects().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveProjects(current)
        }
        return removed
    }

    @Synchronized
    fun clearAllProjects() {
        saveProjects(emptyList())
    }

    private fun saveProjects(projects: List<ProjectRecord>) {
        try {
            val array = JSONArray()
            for (p in projects) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("timestamp", p.timestamp)
                    put("inputUri", p.inputUri)
                    put("outputFilePath", p.outputFilePath)
                    put("inputResolution", p.inputResolution)
                    put("outputResolution", p.outputResolution)
                    put("inputFps", p.inputFps)
                    put("outputFps", p.outputFps)
                    put("durationFormatted", p.durationFormatted)
                    put("upscaleModel", p.upscaleModel)
                    put("interpolationModel", p.interpolationModel)
                    put("pipelineOrder", p.pipelineOrder)
                    put("fileSizeBytes", p.fileSizeBytes)
                    put("psnrDb", p.psnrDb.toDouble())
                    put("ssim", p.ssim.toDouble())
                    put("status", p.status)
                }
                array.put(obj)
            }
            projectsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
