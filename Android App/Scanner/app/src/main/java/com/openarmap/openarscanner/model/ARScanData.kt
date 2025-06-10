package com.openarmap.openarscanner.model

import java.util.Date

/**
 * ARScanData represents a complete AR scan record in the OpenARMap system.
 * 
 * This data class contains all information about a 3D scan captured by the AR camera,
 * including location data, user information, file references, and metadata.
 * 
 * The class supports different types of AR data (point clouds, meshes, images) and
 * includes comprehensive tracking for sync status with Firebase backend services.
 * 
 * Key Features:
 * - Location tracking with GPS coordinates and accuracy
 * - Support for multiple scan types (POINT_CLOUD, MESH, IMAGE)
 * - Firebase Storage integration for large file uploads
 * - Sync status tracking for offline/online functionality
 * - Extensible metadata system for future enhancements
 * 
 * @property id Firebase document ID - unique identifier for this scan
 * @property userId Firebase UID of the user who created this scan
 * @property userName Display name of the scan creator (cached for performance)
 * @property latitude GPS latitude coordinate where scan was captured
 * @property longitude GPS longitude coordinate where scan was captured  
 * @property altitude GPS altitude coordinate (meters above sea level)
 * @property accuracy GPS accuracy in meters (lower values = more accurate)
 * @property scanType Type of AR data captured ("POINT_CLOUD", "MESH", "IMAGE")
 * @property dataUrl Firebase Storage URL where the scan data is stored
 * @property dataSize Size of the scan data file in bytes
 * @property title User-provided title for the scan
 * @property description User-provided description of the scan
 * @property tags List of tags for categorizing and searching scans
 * @property createdAt When the scan was originally created (Unix timestamp)
 * @property updatedAt When the scan was last modified (Unix timestamp)
 * @property isSynced Whether the scan has been successfully uploaded to Firebase
 * @property lastSyncAttempt Date of the most recent sync attempt
 * @property syncError Error message if sync failed (null if successful)
 * @property metadata Additional flexible metadata as key-value pairs
 */
data class ARScanData(
    /** Firebase document ID - unique identifier for this scan in Firestore */
    val id: String = "",
    
    // === User Information ===
    /** Firebase UID of the user who created this scan */
    val userId: String = "",
    
    /** Display name of the scan creator (cached from user profile for performance) */
    val userName: String? = null,
    
    // === Location Data ===
    /** GPS latitude coordinate where the scan was captured (decimal degrees) */
    val latitude: Double = 0.0,
    
    /** GPS longitude coordinate where the scan was captured (decimal degrees) */
    val longitude: Double = 0.0,
    
    /** GPS altitude coordinate in meters above sea level */
    val altitude: Double = 0.0,
    
    /** GPS accuracy in meters - lower values indicate more precise location data */
    val accuracy: Float = 0f,
    
    // === AR Data ===
    /** Type of AR data captured: "POINT_CLOUD", "MESH", "IMAGE", or custom types */
    val scanType: String = "",
    
    /** Firebase Storage URL where the actual scan data file is stored */
    val dataUrl: String = "",
    
    /** Size of the scan data file in bytes - used for storage management and UI */
    val dataSize: Long = 0,
    
    // === User-Provided Metadata ===
    /** User-provided title for the scan - used in lists and search results */
    val title: String = "",
    
    /** User-provided description explaining what was scanned */
    val description: String = "",
    
    /** List of tags for categorizing and searching scans (e.g., "building", "outdoor") */
    val tags: List<String> = listOf(),
    
    // === Timestamps ===
    /** When the scan was originally created (Unix timestamp in milliseconds) */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** When the scan was last modified (Unix timestamp in milliseconds) */
    val updatedAt: Long = System.currentTimeMillis(),
    
    // === Sync Status ===
    /** Whether the scan has been successfully uploaded to Firebase backend */
    val isSynced: Boolean = false,
    
    /** Date of the most recent sync attempt - used for retry logic */
    val lastSyncAttempt: Date? = null,
    
    /** Error message if sync failed, null if successful - helps with debugging */
    val syncError: String? = null,
    
    // === Extensible Metadata ===
    /** Additional metadata as key-value pairs for future features and customization */
    val metadata: Map<String, String> = mapOf()
) 