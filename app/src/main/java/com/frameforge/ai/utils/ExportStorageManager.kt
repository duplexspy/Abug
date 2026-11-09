package com.frameforge.ai.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ExportResult(
    val file: File?,
    val uri: Uri?,
    val publicPath: String,
    val isPublicDownload: Boolean
)

object ExportStorageManager {
    private const val TAG = "abug_Storage"
    private const val SUB_DIR = "abug"

    /**
     * Saves a high-res image (PNG) directly to the phone's Download folder.
     */
    fun saveImageToDownloads(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "frameforge_photo_${System.currentTimeMillis()}.png"
    ): ExportResult {
        // Always save a local copy in filesDir/exports for reliable internal preview and caching
        val localFile = saveBitmapToLocalFiles(context, bitmap, fileName)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUB_DIR")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    val publicPath = "Downloads/$SUB_DIR/$fileName"
                    Log.i(TAG, "Saved image to Downloads MediaStore: $publicPath (Uri: $uri)")
                    return ExportResult(
                        file = localFile,
                        uri = uri,
                        publicPath = publicPath,
                        isPublicDownload = true
                    )
                }
            }

            // Fallback for Android 9 or below, or if MediaStore insert failed
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUB_DIR)
            pubDir.mkdirs()
            val destFile = File(pubDir, fileName)
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("image/png"), null)
            val publicPath = destFile.absolutePath
            Log.i(TAG, "Saved image to Downloads direct: $publicPath")
            return ExportResult(
                file = destFile,
                uri = Uri.fromFile(destFile),
                publicPath = "Downloads/$SUB_DIR/$fileName",
                isPublicDownload = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to Downloads: ${e.message}", e)
            return ExportResult(
                file = localFile,
                uri = Uri.fromFile(localFile),
                publicPath = localFile.absolutePath,
                isPublicDownload = false
            )
        }
    }

    /**
     * Saves a video file (.mp4) directly to the phone's Download folder.
     */
    fun saveVideoToDownloads(
        context: Context,
        sourceVideoFile: File,
        targetFileName: String = sourceVideoFile.name
    ): ExportResult {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUB_DIR")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(sourceVideoFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    val publicPath = "Downloads/$SUB_DIR/$targetFileName"
                    Log.i(TAG, "Saved video to Downloads MediaStore: $publicPath (Uri: $uri)")
                    return ExportResult(
                        file = sourceVideoFile,
                        uri = uri,
                        publicPath = publicPath,
                        isPublicDownload = true
                    )
                }
            }

            // Fallback for Android 9 or below, or if MediaStore insert failed
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUB_DIR)
            pubDir.mkdirs()
            val destFile = File(pubDir, targetFileName)
            FileInputStream(sourceVideoFile).use { inStream ->
                FileOutputStream(destFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null)
            val publicPath = destFile.absolutePath
            Log.i(TAG, "Saved video to Downloads direct: $publicPath")
            return ExportResult(
                file = destFile,
                uri = Uri.fromFile(destFile),
                publicPath = "Downloads/$SUB_DIR/$targetFileName",
                isPublicDownload = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to Downloads: ${e.message}", e)
            return ExportResult(
                file = sourceVideoFile,
                uri = Uri.fromFile(sourceVideoFile),
                publicPath = sourceVideoFile.absolutePath,
                isPublicDownload = false
            )
        }
    }

    private fun saveBitmapToLocalFiles(context: Context, bitmap: Bitmap, fileName: String): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val f = File(dir, fileName)
        FileOutputStream(f).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return f
    }
}
