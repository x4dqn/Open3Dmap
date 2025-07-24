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
    val dataLicense: String = "CC-BY",

    // === Legacy/Additional Fields Restored ===
    /** Display name of the scan creator (cached from user profile for performance) */
    val userName: String? = null,

    /** GPS latitude coordinate where the scan was captured (decimal degrees) */
    val latitude: Double? = null,
    /** GPS longitude coordinate where the scan was captured (decimal degrees) */
    val longitude: Double? = null,
    /** GPS altitude coordinate in meters above sea level */
    val altitude: Double? = null,
    /** GPS accuracy in meters - lower values indicate more precise location data */
    val accuracy: Float? = null,

    /** Type of AR data captured: "POINT_CLOUD", "MESH", "IMAGE", or custom types */
    val legacyScanType: String? = null,
    /** Firebase Storage URL where the actual scan data file is stored */
    val dataUrl: String? = null,
    /** Size of the scan data file in bytes - used for storage management and UI */
    val dataSize: Long? = null,

    /** List of uploaded file paths (for COLMAP or other processing) */
    val uploadedFiles: List<String>? = null,
    /** Path to COLMAP database file */
    val databasePath: String? = null,
    /** Path to images directory for COLMAP */
    val imagesDir: String? = null,
    /** Path to sparse directory for COLMAP */
    val sparseDir: String? = null,
    /** COLMAP results or other processing results (as a map) */
    val colmapResults: Map<String, Any?>? = null,

    // === Extensible Metadata ===
    /** Additional metadata as key-value pairs for future features and customization */
    val metadata: Map<String, String>? = null
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