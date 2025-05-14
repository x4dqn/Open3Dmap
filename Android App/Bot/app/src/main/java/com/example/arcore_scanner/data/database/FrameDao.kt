package com.example.arcore_scanner.data.database

import androidx.room.*
import com.example.arcore_scanner.data.models.Frame
import kotlinx.coroutines.flow.Flow

@Dao
interface FrameDao {
    @Query("SELECT * FROM frames WHERE sessionId = :scanId ORDER BY timestamp ASC")
    fun getFramesForScan(scanId: String): Flow<List<Frame>>

    @Query("SELECT * FROM frames WHERE frameId = :frameId")
    suspend fun getFrameById(frameId: String): Frame?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrame(frame: Frame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrames(frames: List<Frame>)

    @Update
    suspend fun updateFrame(frame: Frame)

    @Delete
    suspend fun deleteFrame(frame: Frame)

    @Query("DELETE FROM frames WHERE sessionId = :scanId")
    suspend fun deleteFramesForScan(scanId: String)

    @Query("SELECT * FROM frames WHERE sessionId = :scanId AND timestamp >= :startTime AND timestamp <= :endTime")
    fun getFramesInTimeRange(scanId: String, startTime: Long, endTime: Long): Flow<List<Frame>>
} 