package com.example.arcore_scanner.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.TypeConverters
import androidx.room.ForeignKey
import com.example.arcore_scanner.data.database.Converters
import com.google.ar.core.Pose
import java.time.Instant

@Entity(
    tableName = "frames",
    foreignKeys = [
        ForeignKey(
            entity = ScanSession::class,
            parentColumns = ["scanId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(Converters::class)
data class Frame(
    @PrimaryKey
    val frameId: String,
    val sessionId: String, // Foreign key to ScanSession
    val timestamp: Long = Instant.now().toEpochMilli(),
    val localPose: Pose,
    val poseMatrix: FloatArray,
    @Embedded(prefix = "gps_")
    val gps: GpsLocation? = null,
    @Embedded(prefix = "imu_")
    val imu: ImuData? = null,
    val poseConfidence: Float,
    val imageBlurScore: Float? = null,
    @Embedded(prefix = "exposure_")
    val exposureInfo: ExposureInfo? = null,
    val manualTags: List<String> = emptyList(),
    val frameQualityScore: Float? = null
) {
    data class ImuData(
        val accelerometer: FloatArray,
        val gyroscope: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ImuData

            if (!accelerometer.contentEquals(other.accelerometer)) return false
            if (!gyroscope.contentEquals(other.gyroscope)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = accelerometer.contentHashCode()
            result = 31 * result + gyroscope.contentHashCode()
            return result
        }
    }

    data class ExposureInfo(
        val iso: Int,
        val shutterSpeed: Float,
        val brightness: Float
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        if (!poseMatrix.contentEquals(other.poseMatrix)) return false

        return true
    }

    override fun hashCode(): Int {
        return poseMatrix.contentHashCode()
    }
} 