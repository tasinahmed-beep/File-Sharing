package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.TransferRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRecordDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TransferRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TransferRecord)

    @Query("SELECT * FROM transfer_history WHERE fileId = :fileId LIMIT 1")
    suspend fun getRecordById(fileId: String): TransferRecord?

    @Query("UPDATE transfer_history SET status = :status, savedPath = :savedPath, progress = :progress WHERE fileId = :fileId")
    suspend fun updateTransferProgress(fileId: String, status: String, savedPath: String, progress: Float)

    @Query("DELETE FROM transfer_history")
    suspend fun clearHistory()
}
