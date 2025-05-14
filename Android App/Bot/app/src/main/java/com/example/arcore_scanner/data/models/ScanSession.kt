package com.example.arcore_scanner.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.TypeConverters
import com.example.arcore_scanner.data.database.Converters
import java.util.UUID
import com.google.ar.core.Pose
import java.time.Instant

@Entity(tableName = "scan_sessions")
@TypeConverters(Converters::class)
data class ScanSession(
    @PrimaryKey
    val scanId: String = UUID.randomUUID().toString(),
    val startTime: Long = Instant.now().toEpochMilli(),
    var endTime: Long? = null,
    val deviceId: String,
    val deviceModel: String,
    val appVersion: String,
    val contributorId: String? = null,
    @Embedded
    val anchorGps: GpsLocation,
    val originPose: Pose,
    val localToWorldMatrix: FloatArray,
    val privacyFlags: Int = 0,
    val scanNotes: String? = null,
    val dataLicense: String = "CC-BY",
    val estimatedAreaCoveredM2: Float? = null,
    val scanType: ScanType,
    @Embedded
    val cameraIntrinsics: CameraIntrinsics? = null
) {
    enum class ScanType {
        WALK_THROUGH,
        STATIC_TRIPOD,
        ROOM,
        STREET
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScanSession

        if (!localToWorldMatrix.contentEquals(other.localToWorldMatrix)) return false

        return true
    }

    override fun hashCode(): Int {
        return localToWorldMatrix.contentHashCode()
    }
}

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float
)

data class CameraIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val width: Int,
    val height: Int
) 