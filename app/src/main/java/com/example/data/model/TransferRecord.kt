package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferRecord(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val senderReceiverName: String,
    val status: String, // "SENT", "RECEIVED", "FAILED", "SENDING", "RECEIVING"
    val timestamp: Long,
    val savedPath: String,
    val isOutgoing: Boolean,
    val progress: Float = 1.0f
)
