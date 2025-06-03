package com.openarmap.openarscanner.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.openarmap.openarscanner.data.model.ARScanData
import com.openarmap.openarscanner.repository.ARScanRepository
import com.openarmap.openarscanner.ui.ScanState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ARScanViewModel : ViewModel() {
    private val repository = ARScanRepository()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = try {
        Log.d(TAG, "ViewModel: Attempting to initialize Firebase Storage with explicit bucket")
        FirebaseStorage.getInstance("gs://openarmap.firebasestorage.app")
    } catch (e: Exception) {
        Log.w(TAG, "ViewModel: Failed to initialize with explicit bucket, using default", e)
        FirebaseStorage.getInstance()
    }
    private val auth = FirebaseAuth.getInstance()
    
    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState
    
    private val _userScans = MutableLiveData<List<ARScanData>>()
    val userScans: LiveData<List<ARScanData>> = _userScans
    
    companion object {
        private const val TAG = "ARScanViewModel"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    fun saveScanData(scanData: ARScanData, images: List<ByteArray>) {
        viewModelScope.launch {
            try {
                _scanState.value = ScanState.Saving
                Log.d(TAG, "Starting to save scan data with ${images.size} images")

                // Get current user
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e(TAG, "User not authenticated")
                    _scanState.value = ScanState.Error("User not authenticated. Please sign in and try again.")
                    return@launch
                }

                // Update user ID
                val updatedScanData = scanData.copy(userId = currentUser.uid)
                
                // Validate images
                if (images.isEmpty()) {
                    Log.w(TAG, "No images to upload")
                    _scanState.value = ScanState.Error("No images captured. Please try scanning again.")
                    return@launch
                }
                
                // Check image sizes
                val oversizedImages = images.filter { it.size > 10 * 1024 * 1024 } // 10MB limit
                if (oversizedImages.isNotEmpty()) {
                    Log.w(TAG, "Some images are too large: ${oversizedImages.size} images exceed 10MB")
                    _scanState.value = ScanState.Error("Some images are too large. Please try again with smaller images.")
                    return@launch
                }

                // Test storage connectivity before attempting upload
                try {
                    Log.d(TAG, "Testing storage connectivity...")
                    val testRef = storage.reference.child("test_connectivity_${System.currentTimeMillis()}")
                    Log.d(TAG, "Storage test reference created: ${testRef.path}")
                    Log.d(TAG, "Storage bucket: ${testRef.bucket}")
                } catch (e: Exception) {
                    Log.e(TAG, "Storage connectivity test failed", e)
                    _scanState.value = ScanState.Error("Storage service unavailable. Please check your internet connection and try again.")
                    return@launch
                }

                // Retry logic for saving scan
                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                    try {
                        Log.d(TAG, "Save attempt $attempt of $MAX_RETRY_ATTEMPTS")
                        val result = repository.saveScan(updatedScanData, images)
                        
                        if (result.isSuccess) {
                            Log.d(TAG, "Scan saved successfully on attempt $attempt")
                            _scanState.value = ScanState.Saved
                            loadUserScans() // Refresh the list
                            return@launch
                        } else {
                            lastException = result.exceptionOrNull() as? Exception
                            Log.w(TAG, "Save attempt $attempt failed", lastException)
                        }
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "Save attempt $attempt failed with exception", e)
                    }
                    
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        Log.d(TAG, "Waiting ${RETRY_DELAY_MS}ms before retry...")
                        delay(RETRY_DELAY_MS)
                    }
                }
                
                // All attempts failed, provide specific error message
                val errorMessage = when {
                    lastException?.message?.contains("404") == true || 
                    lastException?.message?.contains("Object does not exist") == true -> 
                        "Firebase Storage bucket not found or incorrectly configured. Please check your Firebase project setup."
                    lastException?.message?.contains("403") == true -> 
                        "Permission denied. Please check your Firebase security rules."
                    lastException?.message?.contains("network") == true -> 
                        "Network error. Please check your internet connection and try again."
                    lastException?.message?.contains("quota") == true -> 
                        "Storage quota exceeded. Please contact support."
                    else -> 
                        "Failed to save scan after $MAX_RETRY_ATTEMPTS attempts. Please try again later."
                }
                
                Log.e(TAG, "All save attempts failed. Final error: $errorMessage", lastException)
                _scanState.value = ScanState.Error(errorMessage)
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in saveScanData", e)
                _scanState.value = ScanState.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    fun loadUserScans() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.w(TAG, "User not authenticated, cannot load scans")
                    _userScans.value = emptyList()
                    return@launch
                }

                val result = repository.getUserScans(currentUser.uid)
                if (result.isSuccess) {
                    _userScans.value = result.getOrThrow()
                } else {
                    Log.e(TAG, "Failed to load user scans", result.exceptionOrNull())
                    _userScans.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user scans", e)
                _userScans.value = emptyList()
            }
        }
    }

    fun deleteScan(scanId: String) {
        viewModelScope.launch {
            try {
                val result = repository.deleteScan(scanId)
                if (result.isSuccess) {
                    Log.d(TAG, "Scan deleted successfully")
                    // Reload scans to update the list
                    loadUserScans()
                } else {
                    Log.e(TAG, "Failed to delete scan", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting scan", e)
            }
        }
    }

    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }
} 