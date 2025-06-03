package com.openarmap.openarscanner.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import java.util.UUID

data class ARScanData(
    @DocumentId
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
    val deviceId: String = "",
    val deviceModel: String = "",
    val appVersion: String = "",
    val scanType: ScanType = ScanType.WALK_THROUGH,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val anchorGps: GpsLocation? = null,
    val cameraIntrinsics: CameraIntrinsics? = null,
    val estimatedAreaCoveredM2: Float? = null,
    val privacyFlags: Int = 0,
    val scanNotes: String? = null,
    val dataLicense: String = "CC-BY"
) {
    enum class ScanType {
        WALK_THROUGH,
        STATIC_TRIPOD,
        ROOM,
        STREET
    }
}

data class GpsLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f
)

data class CameraIntrinsics(
    val fx: Float = 0.0f,
    val fy: Float = 0.0f,
    val cx: Float = 0.0f,
    val cy: Float = 0.0f,
    val width: Int = 0,
    val height: Int = 0
) 