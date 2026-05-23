package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FileMetadata(
    val fileId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val senderDeviceName: String,
    val senderDeviceId: String,
    val timestamp: Long,
    val isBase64: Boolean = false
)
