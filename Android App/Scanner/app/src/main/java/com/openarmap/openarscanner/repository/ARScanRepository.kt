package com.openarmap.openarscanner.repository

import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.openarmap.openarscanner.data.model.ARScanData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Date
import java.util.UUID

/**
 * ARScanRepository manages all AR scan data operations with Firebase backend services.
 * 
 * This repository is the central hub for AR scan data management, handling the complex
 * workflow of uploading photos to Firebase Storage and saving metadata to Firestore.
 * It provides a clean abstraction layer between the application logic and Firebase services.
 * 
 * Key Responsibilities:
 * - Upload AR scan photos to Firebase Storage with user-organized folder structure
 * - Save scan metadata to Firestore with proper user association
 * - Retrieve user's scans with proper filtering and ordering
 * - Handle scan updates and deletions with cleanup of associated files
 * - Provide robust error handling and logging for debugging
 * - Manage Firebase Storage bucket configuration with fallback mechanisms
 * 
 * Architecture Features:
 * - User-organized storage: ar_scans/{userId}/{scanId}/{photos}
 * - Concurrent photo uploads for optimal performance
 * - Comprehensive error handling and recovery
 * - Detailed logging for troubleshooting
 * - Atomic operations to prevent data inconsistency
 * - Support for both explicit and default Firebase Storage buckets
 * 
 * Storage Organization:
 * ```
 * Firebase Storage Structure:
 * ar_scans/
 * ├── {userId1}/
 * │   ├── {scanId1}/
 * │   │   ├── scan_{scanId}_photo_0_{photoId}.jpg
 * │   │   └── scan_{scanId}_photo_1_{photoId}.jpg
 * │   └── {scanId2}/
 * │       └── scan_{scanId}_photo_0_{photoId}.jpg
 * └── {userId2}/
 *     └── {scanId3}/
 *         └── scan_{scanId}_photo_0_{photoId}.jpg
 * ```
 * 
 * Thread Safety:
 * - All operations use Dispatchers.IO for background execution
 * - Concurrent photo uploads with proper synchronization
 * - Safe to call from any coroutine context
 */
class ARScanRepository {
    
    /** Firebase Firestore database instance for metadata storage */
    private val db = Firebase.firestore
    
    /**
     * Firebase Storage instance with fallback mechanism.
     * 
     * Attempts to use the explicit OpenARMap storage bucket first,
     * then falls back to the default Firebase Storage if initialization fails.
     * This ensures the app can function even with configuration issues.
     */
    private val storage = try {
        Log.d(TAG, "Attempting to initialize Firebase Storage with explicit bucket")
        FirebaseStorage.getInstance("gs://openarmap.firebasestorage.app")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize with explicit bucket, using default", e)
        Firebase.storage
    }
    
    /** Reference to the ar_scans collection in Firestore */
    private val scansCollection = db.collection("ar_scans")
    
    companion object {
        /** Logging tag for this repository */
        private const val TAG = "ARScanRepository"
    }

    /**
     * Initializes the repository and logs configuration details.
     * 
     * This initialization block provides valuable debugging information
     * about the Firebase Storage configuration being used.
     */
    init {
        Log.d(TAG, "ARScanRepository initialized")
        Log.d(TAG, "Storage bucket: ${storage.app.options.storageBucket}")
        Log.d(TAG, "Storage app name: ${storage.app.name}")
    }

    data class UploadConfig(
        val maxConcurrentUploads: Int = 6,
        val targetJpegQuality: Int = 92,
        val minJpegQuality: Int = 80,
        val maxBytesPerFile: Int = 15 * 1024 * 1024
    )

    /**
     * Saves AR scan data with associated images using direct uploads with progress tracking.
     * 
     * This method uploads photos to Firebase Storage and saves scan metadata to Firestore.
     * It provides real-time progress updates through the progressCallback parameter.
     * 
     * The method handles:
     * - User authentication validation
     * - Concurrent photo uploads with progress tracking
     * - Firestore metadata storage
     * - Comprehensive error handling and logging
     * 
     * @param scanData AR scan metadata to save
     * @param photos List of photo data as byte arrays
     * @param progressCallback Callback function for upload progress updates (current, total)
     * @return Result<ARScanData> containing saved scan with photo URLs or error
     * 
     * @throws Exception if user ID is empty or Firebase services are unavailable
     */
    suspend fun saveScanWithProgress(
        scanData: ARScanData, 
        photos: List<ByteArray>,
        progressCallback: (current: Int, total: Int) -> Unit,
        config: UploadConfig = UploadConfig()
    ): Result<ARScanData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to save scan with progress tracking - ${photos.size} photos")
            Log.d(TAG, "Using storage bucket: ${storage.app.options.storageBucket}")
            Log.d(TAG, "Saving scan for user: ${scanData.userId}")
            
            // CRITICAL: Validate user authentication BEFORE attempting any uploads
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "AUTHENTICATION ERROR: No authenticated user found")
                Log.e(TAG, "Cannot upload to Firebase Storage without authentication")
                throw Exception("User not authenticated. Please sign in and try again.")
            }
            
            Log.d(TAG, "Authentication validated - User: ${currentUser.uid}")
            Log.d(TAG, "User email: ${currentUser.email}")
            Log.d(TAG, "User is anonymous: ${currentUser.isAnonymous}")
            
            // Ensure user ID matches authenticated user
            val updatedScanData = if (scanData.userId != currentUser.uid) {
                Log.e(TAG, "USER ID MISMATCH: scanData.userId=${scanData.userId}, auth.uid=${currentUser.uid}")
                Log.e(TAG, "Updating scanData.userId to match authenticated user")
                // Update scanData with correct user ID
                scanData.copy(userId = currentUser.uid)
            } else {
                scanData
            }
            
            // Validate that userId is provided
            if (updatedScanData.userId.isEmpty()) {
                Log.e(TAG, "User ID is empty - cannot save scan without user context")
                throw Exception("User ID is required to save scan")
            }
            
            // Test storage connectivity first
            try {
                val testRef = storage.reference.child("test_${System.currentTimeMillis()}")
                Log.d(TAG, "Storage test reference created successfully: ${testRef.path}")
                Log.d(TAG, "Storage reference bucket: ${testRef.bucket}")
            } catch (e: Exception) {
                Log.e(TAG, "Storage connectivity test failed", e)
                throw Exception("Firebase Storage is not accessible: ${e.message}", e)
            }
            
            // Step 1: Upload photos with progress tracking
            Log.d(TAG, "Step 1: Uploading photos to storage with progress tracking...")
            // Use bounded parallelism for faster uploads without overwhelming the device/network
            val semaphore = Semaphore(config.maxConcurrentUploads)
            val totalPhotos = photos.size
            val scanId = updatedScanData.id.ifEmpty { UUID.randomUUID().toString() }
            val progressCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val photoUrls = MutableList(totalPhotos) { "" }

            kotlinx.coroutines.coroutineScope {
                photos.mapIndexed { index, photo ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val preparedBytes = ensureUnderMaxBytes(
                                    original = photo,
                                    maxBytes = config.maxBytesPerFile,
                                    targetQuality = config.targetJpegQuality,
                                    minQuality = config.minJpegQuality
                                )
                                val photoId = UUID.randomUUID().toString()
                                val fileName = "scan_${scanId}_photo_${index}_${photoId}.jpg"

                                val photoRef = storage.reference
                                    .child("ar_scans")
                                    .child(updatedScanData.userId)
                                    .child(scanId)
                                    .child(fileName)

                                Log.d(TAG, "Uploading photo $index to: ${photoRef.path}")
                                Log.d(TAG, "User-organized path: ar_scans/${updatedScanData.userId}/${scanId}/${fileName}")
                                Log.d(TAG, "Storage reference bucket: ${photoRef.bucket}")
                                Log.d(TAG, "Photo size (pre-upload): ${preparedBytes.size} bytes")

                                val uploadTask = photoRef.putBytes(preparedBytes).await()
                                Log.d(TAG, "Photo $index uploaded successfully, bytes transferred: ${uploadTask.bytesTransferred}")

                                // Small delay to avoid immediate metadata fetch race in some networks
                                kotlinx.coroutines.delay(50)

                                val downloadUrl = photoRef.downloadUrl.await().toString()
                                Log.d(TAG, "Photo $index download URL obtained successfully")

                                photoUrls[index] = downloadUrl

                                val current = progressCounter.incrementAndGet()
                                progressCallback(current, totalPhotos)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error uploading photo $index", e)
                                if (e is com.google.firebase.storage.StorageException) {
                                    Log.e(TAG, "Storage error code: ${e.errorCode}")
                                    Log.e(TAG, "Storage HTTP result: ${e.httpResultCode}")
                                }
                                throw e
                            }
                        }
                    }
                }.awaitAll()
            }

            Log.d(TAG, "Step 1 completed: All photos uploaded successfully")

            // Step 2: Create scan data with photo URLs and timestamps
            val scanWithPhotos = ARScanData(
                id = updatedScanData.id.ifEmpty { UUID.randomUUID().toString() },
                userId = updatedScanData.userId,
                title = updatedScanData.title,
                description = updatedScanData.description,
                tags = updatedScanData.tags,
                photoUrls = photoUrls,
                createdAt = Date(),
                updatedAt = Date(),
                deviceId = updatedScanData.deviceId,
                deviceModel = updatedScanData.deviceModel,
                appVersion = updatedScanData.appVersion,
                scanType = updatedScanData.scanType,
                startTime = updatedScanData.startTime,
                endTime = updatedScanData.endTime,
                anchorGps = updatedScanData.anchorGps,
                cameraIntrinsics = updatedScanData.cameraIntrinsics,
                estimatedAreaCoveredM2 = updatedScanData.estimatedAreaCoveredM2,
                privacyFlags = updatedScanData.privacyFlags,
                scanNotes = updatedScanData.scanNotes,
                dataLicense = updatedScanData.dataLicense,
                // Legacy/compat fields:
                userName = updatedScanData.userName,
                latitude = updatedScanData.latitude,
                longitude = updatedScanData.longitude,
                altitude = updatedScanData.altitude,
                accuracy = updatedScanData.accuracy,
                legacyScanType = updatedScanData.legacyScanType,
                dataUrl = updatedScanData.dataUrl,
                dataSize = updatedScanData.dataSize,
                uploadedFiles = updatedScanData.uploadedFiles ?: emptyList(),
                databasePath = updatedScanData.databasePath,
                imagesDir = updatedScanData.imagesDir,
                sparseDir = updatedScanData.sparseDir,
                colmapResults = updatedScanData.colmapResults ?: emptyMap(),
                metadata = updatedScanData.metadata ?: emptyMap()
            )

            // Step 3: Save scan metadata to Firestore
            Log.d(TAG, "Step 2: Saving scan metadata to Firestore...")
            val docRef = scansCollection.document(scanWithPhotos.id)
            docRef.set(scanWithPhotos).await()
            Log.d(TAG, "Step 2 completed: Scan metadata saved to Firestore")

            Log.d(TAG, "=== UPLOAD SUMMARY ===")
            Log.d(TAG, "Scan ID: ${scanWithPhotos.id}")
            Log.d(TAG, "User ID: ${scanWithPhotos.userId}")
            Log.d(TAG, "Photos uploaded: ${photoUrls.size}")
            Log.d(TAG, "Total data size: ${photos.sumOf { it.size }} bytes")
            Log.d(TAG, "Firestore document: ${docRef.path}")
            Log.d(TAG, "========================")

            Result.success(scanWithPhotos)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving scan with progress", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves a single AR scan by its ID.
     * 
     * @param id Unique identifier of the scan to retrieve
     * @return Result<ARScanData?> containing the scan data or null if not found
     */
    suspend fun getScan(id: String): Result<ARScanData?> = withContext(Dispatchers.IO) {
        try {
            val document = scansCollection.document(id).get().await()
            val scan = document.toObject(ARScanData::class.java)
            Result.success(scan)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting scan", e)
            Result.failure(e)
        }
    }

    /**
     * Gets all scans for a specific user.
     * 
     * @param userId The user ID to get scans for
     * @return List of ARScanData for the user, ordered by creation date (newest first)
     */
    suspend fun getUserScans(userId: String): List<ARScanData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading scans for user: $userId")
            
            val snapshot = scansCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val scans = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ARScanData::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing scan document ${doc.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Loaded ${scans.size} scans for user $userId")
            scans
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user scans for user $userId", e)
            emptyList()
        }
    }

    /**
     * Updates an existing AR scan's metadata.
     * 
     * This method updates the scan metadata in Firestore while preserving
     * the existing photo URLs. The updatedAt timestamp is automatically set.
     * 
     * Note: This method does not handle photo updates - use saveScan for
     * operations that involve changing photos.
     * 
     * @param scanData Updated scan data with the same ID
     * @return Result<ARScanData> containing the updated scan or error
     */
    suspend fun updateScan(scanData: ARScanData): Result<ARScanData> = withContext(Dispatchers.IO) {
        try {
            val scanWithTimestamp = scanData.copy(
                updatedAt = Date()
            )
            scansCollection.document(scanData.id).set(scanWithTimestamp).await()
            Result.success(scanWithTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scan", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a scan and all associated data for a specific user.
     * 
     * @param scanId The scan ID to delete
     * @param userId The user ID (for security validation)
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteScan(scanId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting scan $scanId for user $userId")
            
            // First, verify the scan belongs to the user
            val scanDoc = scansCollection.document(scanId).get().await()
            if (!scanDoc.exists()) {
                Log.w(TAG, "Scan $scanId does not exist")
                false
            } else {
                val scanData = scanDoc.toObject(ARScanData::class.java)
                if (scanData?.userId != userId) {
                    Log.w(TAG, "Scan $scanId does not belong to user $userId")
                    false
                } else {
                    // Delete photos from Firebase Storage
                    scanData.photoUrls.forEach { photoUrl ->
                        try {
                            // Extract storage path from download URL
                            val storagePath = extractStoragePathFromUrl(photoUrl)
                            if (storagePath != null) {
                                val photoRef = storage.reference.child(storagePath)
                                photoRef.delete().await()
                                Log.d(TAG, "Deleted photo: $storagePath")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete photo: $photoUrl", e)
                            // Continue with other deletions even if one fails
                        }
                    }
                    
                    // Delete the scan document from Firestore
                    scansCollection.document(scanId).delete().await()
                    Log.d(TAG, "Deleted scan document: $scanId")
                    
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting scan $scanId", e)
            false
        }
    }

    /**
     * Updates scan metadata for a specific user.
     * 
     * @param scanId The scan ID to update
     * @param userId The user ID (for security validation)
     * @param title New title for the scan
     * @param description New description for the scan
     * @param tags New tags for the scan
     * @return true if update was successful, false otherwise
     */
    suspend fun updateScanMetadata(
        scanId: String, 
        userId: String, 
        title: String, 
        description: String, 
        tags: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating metadata for scan $scanId")
            
            // First, verify the scan belongs to the user
            val scanDoc = scansCollection.document(scanId).get().await()
            if (!scanDoc.exists()) {
                Log.w(TAG, "Scan $scanId does not exist")
                false
            } else {
                val scanData = scanDoc.toObject(ARScanData::class.java)
                if (scanData?.userId != userId) {
                    Log.w(TAG, "Scan $scanId does not belong to user $userId")
                    false
                } else {
                    // Update the scan metadata
                    val updates = mapOf(
                        "title" to title,
                        "description" to description,
                        "tags" to tags,
                        "updatedAt" to Date()
                    )
                    
                    scansCollection.document(scanId).update(updates).await()
                    Log.d(TAG, "Updated scan metadata: $scanId")
                    
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scan metadata $scanId", e)
            false
        }
    }

    /**
     * Helper method to get the storage path for a user's scans.
     * 
     * This method is useful for web platform integration and provides
     * a consistent way to reference user storage locations.
     * 
     * @param userId Firebase UID of the user
     * @return Storage path in format "ar_scans/{userId}/"
     */
    fun getUserStoragePath(userId: String): String {
        return "ar_scans/$userId/"
    }

    /**
     * Helper method to get the full storage path for a specific scan.
     * 
     * This method is useful for web platform integration and provides
     * a consistent way to reference specific scan storage locations.
     * 
     * @param userId Firebase UID of the user
     * @param scanId Unique identifier of the scan
     * @return Storage path in format "ar_scans/{userId}/{scanId}/"
     */
    fun getScanStoragePath(userId: String, scanId: String): String {
        return "ar_scans/$userId/$scanId/"
    }

    /**
     * Enhanced test method to verify Firebase Storage and Firestore connectivity and authentication.
     * 
     * This comprehensive diagnostic method helps troubleshoot permission and connectivity issues
     * by testing the complete authentication, storage, and database access workflow.
     * 
     * The test performs:
     * 1. Checks user authentication status with detailed logging
     * 2. Tests Firebase Storage connectivity and upload permissions
     * 3. Attempts to write to a test collection in Firestore
     * 4. Attempts to write to the ar_scans collection
     * 5. Tests both storage upload and download capabilities
     * 6. Provides detailed logging for debugging
     * 
     * Call this method before attempting to save scans if you encounter
     * permission or connectivity issues.
     * 
     * @return Result<String> with success message or detailed error information
     */
    suspend fun testFirebaseConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            Log.d(TAG, "=== Firestore Connection Test ===")
            Log.d(TAG, "Current user: ${currentUser?.uid}")
            Log.d(TAG, "User email: ${currentUser?.email}")
            Log.d(TAG, "User is anonymous: ${currentUser?.isAnonymous}")
            Log.d(TAG, "Auth token available: ${currentUser != null}")
            
            if (currentUser == null) {
                Log.e(TAG, "ERROR: No authenticated user found!")
                return@withContext Result.failure(Exception("User not authenticated"))
            }
            
            // Test 1: Write to test collection
            val testData = mapOf(
                "userId" to currentUser.uid,
                "timestamp" to System.currentTimeMillis(),
                "message" to "Test connection"
            )
            
            Log.d(TAG, "Attempting to write test document...")
            db.collection("test").document("connection_test_${System.currentTimeMillis()}")
                .set(testData).await()
            
            Log.d(TAG, "Test document created successfully!")
            
            // Test 2: Write to ar_scans collection with minimal data
            val testScanData = mapOf(
                "id" to "test_${System.currentTimeMillis()}",
                "userId" to currentUser.uid,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "title" to "Test Scan"
            )
            
            Log.d(TAG, "Attempting to write test scan document...")
            db.collection("ar_scans").document("test_${System.currentTimeMillis()}")
                .set(testScanData).await()
            
            Log.d(TAG, "Test scan document created successfully!")
            
            // Test 3: Test Firebase Storage upload
            Log.d(TAG, "Testing Firebase Storage upload...")
            val testFileName = "storage_test_${System.currentTimeMillis()}.txt"
            val testContent = "Firebase Storage test content - ${System.currentTimeMillis()}"
            val testBytes = testContent.toByteArray()
            
            val storageRef = storage.reference
                .child("test_uploads")
                .child(currentUser.uid)
                .child(testFileName)
            
            Log.d(TAG, "Uploading test file to: ${storageRef.path}")
            Log.d(TAG, "Storage bucket: ${storageRef.bucket}")
            val uploadTask = storageRef.putBytes(testBytes).await()
            Log.d(TAG, "Storage upload successful, bytes transferred: ${uploadTask.bytesTransferred}")
            
            // Test 4: Test Firebase Storage download URL
            Log.d(TAG, "Testing Firebase Storage download URL...")
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "Download URL obtained successfully: $downloadUrl")
            
            // Test 5: Clean up test file
            Log.d(TAG, "Cleaning up test storage file...")
            try {
                storageRef.delete().await()
                Log.d(TAG, "Test storage file deleted successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete test storage file (non-critical)", e)
            }
            
            Result.success("ALL TESTS PASSED! Authentication, Firestore, and Storage are working correctly.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Firestore connection test failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts the Firebase Storage path from a download URL.
     * 
     * @param downloadUrl The Firebase Storage download URL
     * @return The storage path or null if extraction fails
     */
    private fun extractStoragePathFromUrl(downloadUrl: String): String? {
        return try {
            val url = java.net.URL(downloadUrl)
            val path = url.path
            if (path.startsWith("/o/")) {
                val encodedPath = path.substring(3)
                java.net.URLDecoder.decode(encodedPath, "UTF-8")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract storage path from URL: $downloadUrl", e)
            null
        }
    }

    private fun ensureUnderMaxBytes(
        original: ByteArray,
        maxBytes: Int,
        targetQuality: Int,
        minQuality: Int
    ): ByteArray {
        if (original.size <= maxBytes) return original

        // First try quality-only reduction without resizing
        var quality = targetQuality
        var currentBytes = recompress(original, quality)
        while (currentBytes.size > maxBytes && quality > minQuality) {
            quality -= 4
            currentBytes = recompress(original, quality)
        }
        if (currentBytes.size <= maxBytes) return currentBytes

        // Need to downscale. Decode with sampling and iteratively increase sampling.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, boundsOptions)
        var inSampleSize = 2

        while (true) {
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size, decodeOptions)
                ?: return currentBytes // fallback to best we have
            try {
                quality = targetQuality
                var out = toJpeg(bitmap, quality)
                while (out.size > maxBytes && quality > minQuality) {
                    quality -= 4
                    out = toJpeg(bitmap, quality)
                }
                if (out.size <= maxBytes) return out
            } finally {
                bitmap.recycle()
            }
            // Increase downscale factor and try again, cap to prevent infinite loop
            if (inSampleSize >= 16) return currentBytes
            inSampleSize *= 2
        }
    }

    private fun recompress(sourceJpeg: ByteArray, quality: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(sourceJpeg, 0, sourceJpeg.size)
            ?: return sourceJpeg
        return try { toJpeg(bitmap, quality) } finally { bitmap.recycle() }
    }

    private fun toJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), baos)
        return baos.toByteArray()
    }
} 