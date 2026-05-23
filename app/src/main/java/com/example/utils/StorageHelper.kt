package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object StorageHelper {

    fun getMimeType(fileName: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
            ?: fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }

    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")
    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

    fun saveFileToPublicDirectory(
        context: Context,
        tempFile: File,
        fileName: String,
        mimeType: String,
        customFolder: String = "GroupDrop"
    ): String {
        val contentResolver = context.contentResolver
        var outputStream: OutputStream? = null
        var targetUri: Uri? = null
        var displayPath = ""

        try {
            val finalFolder = if (customFolder.trim().isBlank()) "GroupDrop" else customFolder.trim()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                }

                if (isImage(mimeType)) {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$finalFolder")
                    targetUri = contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                } else if (isVideo(mimeType)) {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/$finalFolder")
                    targetUri = contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                } else {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$finalFolder")
                    targetUri = contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                }

                if (targetUri != null) {
                    outputStream = contentResolver.openOutputStream(targetUri)
                    displayPath = targetUri.toString()
                }
            } else {
                // Fallback for Android PIE (API 28) and below
                val targetDir = when {
                    isImage(mimeType) -> File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        finalFolder
                    )
                    isVideo(mimeType) -> File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        finalFolder
                    )
                    else -> File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        finalFolder
                    )
                }

                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val targetFile = File(targetDir, fileName)
                outputStream = FileOutputStream(targetFile)
                displayPath = targetFile.absolutePath

                targetUri = Uri.fromFile(targetFile)
            }

            if (outputStream != null) {
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                // Scan the files to make them visible in standard index apps
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetUri != null) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    contentResolver.update(targetUri, values, null, null)
                } else {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(displayPath),
                        arrayOf(mimeType),
                        null
                    )
                }
                return displayPath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream?.close()
        }
        return ""
    }

    // Helper to read all content from content URIs (e.g. from File Picker or Camera) and store to temp file
    fun createTempFileFromUri(context: Context, uri: Uri): File? {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
            
            val tempFile = File.createTempFile("gd_send_", ".$extension", context.cacheDir)
            contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) return null
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "picked_file"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (name == "picked_file" || name.isEmpty()) {
            val path = uri.path
            if (path != null) {
                val lastSlash = path.lastIndexOf('/')
                if (lastSlash != -1) {
                    name = path.substring(lastSlash + 1)
                }
            }
        }
        return name
    }
}
