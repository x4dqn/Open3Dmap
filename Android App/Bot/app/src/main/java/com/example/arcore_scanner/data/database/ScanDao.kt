package com.example.arcore_scanner.data.database

import androidx.room.*
import com.example.arcore_scanner.data.models.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC")
    fun getAllScans(): Flow<List<ScanSession>>

    @Query("SELECT * FROM scan_sessions WHERE scanId = :scanId")
    suspend fun getScanById(scanId: String): ScanSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanSession)

    @Update
    suspend fun updateScan(scan: ScanSession)

    @Delete
    suspend fun deleteScan(scan: ScanSession)

    @Query("SELECT * FROM scan_sessions WHERE startTime >= :startTime AND startTime <= :endTime")
    fun getScansInTimeRange(startTime: Long, endTime: Long): Flow<List<ScanSession>>
} 