package com.example.data.repository

import com.example.data.database.TransferRecordDao
import com.example.data.model.TransferRecord
import kotlinx.coroutines.flow.Flow

class TransferRepository(private val dao: TransferRecordDao) {
    val allHistory: Flow<List<TransferRecord>> = dao.getAllHistory()

    suspend fun insertRecord(record: TransferRecord) {
        dao.insertRecord(record)
    }

    suspend fun getRecordById(fileId: String): TransferRecord? {
        return dao.getRecordById(fileId)
    }

    suspend fun updateTransferProgress(fileId: String, status: String, savedPath: String, progress: Float) {
        dao.updateTransferProgress(fileId, status, savedPath, progress)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }
}
