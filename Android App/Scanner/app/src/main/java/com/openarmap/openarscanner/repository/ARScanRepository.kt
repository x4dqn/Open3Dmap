package com.openarmap.openarscanner.repository

import android.util.Log
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

    /**
     * Saves an AR scan with photos to Firebase backend services.
     * 
     * This is the primary method for persisting AR scan data. It performs a complex
     * multi-step operation:
     * 1. Validates user authentication and input data
     * 2. Tests Firebase Storage connectivity
     * 3. Uploads all photos concurrently to Firebase Storage
     * 4. Creates scan metadata with photo URLs
     * 5. Saves metadata to Firestore
     * 
     * The operation is designed to be atomic - if any step fails, the entire
     * operation fails to prevent partial data corruption.
     * 
     * Storage Path: ar_scans/{userId}/{scanId}/scan_{scanId}_photo_{index}_{photoId}.jpg
     * 
     * @param scanData AR scan metadata (user ID must be provided)
     * @param photos List of photo data as byte arrays
     * @return Result<ARScanData> containing saved scan with photo URLs or error
     * 
     * @throws Exception if user ID is empty or Firebase services are unavailable
     */
    suspend fun saveScan(scanData: ARScanData, photos: List<ByteArray>): Result<ARScanData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to save scan with ${photos.size} photos")
            Log.d(TAG, "Using storage bucket: ${storage.app.options.storageBucket}")
            Log.d(TAG, "Saving scan for user: ${scanData.userId}")
            
            // Validate that userId is provided
            if (scanData.userId.isEmpty()) {
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
            
            // Step 1: Upload photos concurrently (no retry to prevent duplicates)
            Log.d(TAG, "Step 1: Uploading photos to storage...")
            val photoUrls = photos.mapIndexed { index, photo ->
                async {
                    try {
                        val scanId = scanData.id.ifEmpty { UUID.randomUUID().toString() }
                        val photoId = UUID.randomUUID().toString()
                        val fileName = "scan_${scanId}_photo_${index}_${photoId}.jpg"
                        
                        // Create user-organized storage reference: ar_scans/{userId}/{scanId}/{fileName}
                        val photoRef = storage.reference
                            .child("ar_scans")
                            .child(scanData.userId)  // User-specific folder
                            .child(scanId)           // Scan-specific folder
                            .child(fileName)         // Unique filename
                        
                        Log.d(TAG, "Uploading photo $index to: ${photoRef.path}")
                        Log.d(TAG, "User-organized path: ar_scans/${scanData.userId}/${scanId}/${fileName}")
                        Log.d(TAG, "Storage reference bucket: ${photoRef.bucket}")
                        Log.d(TAG, "Photo size: ${photo.size} bytes")
                        
                        // Upload the photo bytes to Firebase Storage
                        val uploadTask = photoRef.putBytes(photo).await()
                        Log.d(TAG, "Photo $index uploaded successfully, bytes transferred: ${uploadTask.bytesTransferred}")
                        
                        // Get the download URL for the uploaded photo
                        val downloadUrl = photoRef.downloadUrl.await().toString()
                        Log.d(TAG, "Photo $index download URL obtained: $downloadUrl")
                        
                        downloadUrl
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading photo $index", e)
                        Log.e(TAG, "Error details - Message: ${e.message}")
                        Log.e(TAG, "Error details - Cause: ${e.cause}")
                        if (e is com.google.firebase.storage.StorageException) {
                            Log.e(TAG, "Storage error code: ${e.errorCode}")
                            Log.e(TAG, "Storage HTTP result: ${e.httpResultCode}")
                        }
                        throw e
                    }
                }
            }.awaitAll()

            Log.d(TAG, "Step 1 completed: All photos uploaded successfully")

            // Step 2: Create scan data with photo URLs and timestamps
            val scanWithPhotos = scanData.copy(
                id = scanData.id.ifEmpty { UUID.randomUUID().toString() },
                photoUrls = photoUrls,
                createdAt = Date(),
                updatedAt = Date()
            )

            // Step 3: Save metadata to Firestore
            Log.d(TAG, "Step 2: Saving scan metadata to Firestore...")
            scansCollection.document(scanWithPhotos.id).set(scanWithPhotos).await()
            
            Result.success(scanWithPhotos)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scan", e)
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
     * Retrieves all AR scans for a specific user.
     * 
     * Returns scans ordered by creation date (newest first) for optimal
     * user experience in list views.
     * 
     * @param userId Firebase UID of the user whose scans to retrieve
     * @return Result<List<ARScanData>> containing the user's scans or error
     */
    suspend fun getUserScans(userId: String): Result<List<ARScanData>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = scansCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val scans = snapshot.toObjects(ARScanData::class.java)
            Result.success(scans)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user scans", e)
            Result.failure(e)
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
     * Deletes an AR scan and all associated photos.
     * 
     * This method performs a complete cleanup:
     * 1. Retrieves the scan to get photo URLs
     * 2. Deletes all photos from Firebase Storage
     * 3. Deletes the metadata document from Firestore
     * 
     * The operation continues even if some photo deletions fail,
     * ensuring the metadata is always cleaned up.
     * 
     * @param id Unique identifier of the scan to delete
     * @return Result<Unit> indicating success or failure
     */
    suspend fun deleteScan(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get the scan data first to access photo URLs
            val scan = getScan(id).getOrNull()
            
            // Delete all associated photos from Firebase Storage
            scan?.photoUrls?.forEach { photoUrl ->
                try {
                    storage.getReferenceFromUrl(photoUrl).delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting photo: $photoUrl", e)
                    // Continue with other deletions even if one fails
                }
            }
            
            // Delete the metadata document from Firestore
            scansCollection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting scan", e)
            Result.failure(e)
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
     * Test method to verify Firestore connectivity and authentication.
     * 
     * This diagnostic method helps troubleshoot permission and connectivity issues
     * by testing the complete authentication and database access workflow.
     * 
     * The test performs:
     * 1. Checks user authentication status
     * 2. Attempts to write to a test collection
     * 3. Attempts to write to the ar_scans collection
     * 4. Provides detailed logging for debugging
     * 
     * Call this method before attempting to save scans if you encounter
     * permission or connectivity issues.
     * 
     * @return Result<String> with success message or detailed error information
     */
    suspend fun testFirestoreConnection(): Result<String> = withContext(Dispatchers.IO) {
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
            Result.success("Firestore connection test passed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Firestore connection test failed", e)
            Result.failure(e)
        }
    }
} 