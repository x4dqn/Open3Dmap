package com.openarmap.openarscanner.repository

import android.util.Log
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

class ARScanRepository {
    private val db = Firebase.firestore
    // Try explicit storage bucket first, fallback to default if needed
    private val storage = try {
        Log.d(TAG, "Attempting to initialize Firebase Storage with explicit bucket")
        FirebaseStorage.getInstance("gs://openarmap.firebasestorage.app")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize with explicit bucket, using default", e)
        Firebase.storage
    }
    private val scansCollection = db.collection("ar_scans")
    
    companion object {
        private const val TAG = "ARScanRepository"
    }

    init {
        Log.d(TAG, "ARScanRepository initialized")
        Log.d(TAG, "Storage bucket: ${storage.app.options.storageBucket}")
        Log.d(TAG, "Storage app name: ${storage.app.name}")
    }

    suspend fun saveScan(scanData: ARScanData, photos: List<ByteArray>): Result<ARScanData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to save scan with ${photos.size} photos")
            Log.d(TAG, "Using storage bucket: ${storage.app.options.storageBucket}")
            
            // Test storage connectivity first
            try {
                val testRef = storage.reference.child("test_${System.currentTimeMillis()}")
                Log.d(TAG, "Storage test reference created successfully: ${testRef.path}")
                Log.d(TAG, "Storage reference bucket: ${testRef.bucket}")
            } catch (e: Exception) {
                Log.e(TAG, "Storage connectivity test failed", e)
                throw Exception("Firebase Storage is not accessible: ${e.message}", e)
            }
            
            // Upload photos in parallel with unique references
            val photoUrls = photos.mapIndexed { index, photo ->
                async {
                    try {
                        val scanId = scanData.id.ifEmpty { UUID.randomUUID().toString() }
                        val photoId = UUID.randomUUID().toString()
                        val fileName = "scan_${scanId}_photo_${index}_${photoId}.jpg"
                        
                        // Create a unique storage reference for each photo - IMPORTANT: Use local variable
                        val photoRef = storage.reference
                            .child("ar_scans")
                            .child(scanId)
                            .child(fileName)
                        
                        Log.d(TAG, "Uploading photo $index to: ${photoRef.path}")
                        Log.d(TAG, "Storage reference bucket: ${photoRef.bucket}")
                        Log.d(TAG, "Photo size: ${photo.size} bytes")
                        
                        // Upload the photo with explicit reference
                        val uploadTask = photoRef.putBytes(photo).await()
                        Log.d(TAG, "Photo $index uploaded successfully, bytes transferred: ${uploadTask.bytesTransferred}")
                        
                        // Get download URL using the same reference
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

            Log.d(TAG, "All photos uploaded successfully. Creating scan data...")

            // Create scan data with photo URLs
            val scanWithPhotos = scanData.copy(
                id = scanData.id.ifEmpty { UUID.randomUUID().toString() },
                photoUrls = photoUrls,
                createdAt = Date(),
                updatedAt = Date()
            )

            // Save to Firestore
            Log.d(TAG, "Saving scan data to Firestore...")
            scansCollection.document(scanWithPhotos.id).set(scanWithPhotos).await()
            Log.d(TAG, "Scan saved successfully to Firestore")
            
            Result.success(scanWithPhotos)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scan", e)
            Result.failure(e)
        }
    }

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

    suspend fun deleteScan(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get the scan data first to get photo URLs
            val scan = getScan(id).getOrNull()
            
            // Delete photos from Storage
            scan?.photoUrls?.forEach { photoUrl ->
                try {
                    storage.getReferenceFromUrl(photoUrl).delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting photo: $photoUrl", e)
                    // Continue with other deletions even if one fails
                }
            }
            
            // Delete document from Firestore
            scansCollection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting scan", e)
            Result.failure(e)
        }
    }
} 