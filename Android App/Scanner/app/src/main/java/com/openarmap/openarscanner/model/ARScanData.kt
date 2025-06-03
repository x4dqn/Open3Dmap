package com.openarmap.openarscanner.model

import java.util.Date

data class ARScanData(
    val id: String = "", // Firebase document ID
    
    // User information
    val userId: String = "", // Firebase UID
    val userName: String? = null,
    
    // Location data
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    
    // AR data
    val scanType: String = "", // e.g., "POINT_CLOUD", "MESH", "IMAGE"
    val dataUrl: String = "", // URL to stored data (could be Firebase Storage URL)
    val dataSize: Long = 0, // Size in bytes
    
    // Metadata
    val title: String = "",
    val description: String = "",
    val tags: List<String> = listOf(),
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Sync status
    val isSynced: Boolean = false,
    val lastSyncAttempt: Date? = null,
    val syncError: String? = null,
    
    // Additional metadata as key-value pairs
    val metadata: Map<String, String> = mapOf()
) 