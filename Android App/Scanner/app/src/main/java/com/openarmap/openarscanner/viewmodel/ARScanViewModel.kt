package com.openarmap.openarscanner.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.openarmap.openarscanner.data.model.ARScanData
import com.openarmap.openarscanner.repository.ARScanRepository
import com.openarmap.openarscanner.ui.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

/**
 * ARScanViewModel manages AR scan data and upload operations.
 * 
 * This ViewModel provides a clean interface for:
 * - Capturing and managing AR scan data
 * - Direct upload of scan data with progress tracking
 * - Loading and managing user's scan history
 * - Handling authentication and error states
 * 
 * The ViewModel uses direct uploads instead of background uploads to avoid
 * WorkManager's 10KB input data limitation, allowing large dataset uploads.
 */
class ARScanViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "ARScanViewModel"
    }
    
    private val auth = FirebaseAuth.getInstance()
    private val repository = ARScanRepository()
    
    // Scan state management
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState
    
    // Upload progress tracking
    private val _uploadProgress = MutableStateFlow<UploadProgress>(UploadProgress.Idle)
    val uploadProgress: StateFlow<UploadProgress> = _uploadProgress
    
    // User scans management
    private val _userScans = MutableLiveData<List<ARScanData>>()
    val userScans: LiveData<List<ARScanData>> = _userScans
    
    /**
     * Represents upload progress for direct uploads
     */
    sealed class UploadProgress {
        object Idle : UploadProgress()
        data class Uploading(
            val current: Int,
            val total: Int,
            val scanId: String
        ) : UploadProgress()
        data class Success(
            val scanId: String,
            val photoCount: Int
        ) : UploadProgress()
        data class Failed(
            val scanId: String,
            val error: String
        ) : UploadProgress()
    }
    
    /**
     * Saves AR scan data with associated images using direct uploads.
     * 
     * This is the primary method for persisting AR scan data. It uses direct
     * uploads with progress tracking to avoid WorkManager's 10KB limitation.
     * 
     * The method handles:
     * - User authentication validation
     * - Image size and count validation
     * - Direct upload with progress tracking
     * - Real-time progress updates to UI
     * 
     * @param scanData AR scan metadata to save
     * @param images List of image data as byte arrays
     */
    fun saveScanData(
        scanData: ARScanData,
        images: List<ByteArray>,
        config: com.openarmap.openarscanner.repository.ARScanRepository.UploadConfig? = null
    ) {
        viewModelScope.launch {
            try {
                _scanState.value = ScanState.Saving
                _uploadProgress.value = UploadProgress.Idle
                
                Log.d(TAG, "Starting direct upload with ${images.size} images")

                // Validate user authentication
                val currentUser = auth.currentUser
                Log.d(TAG, "=== AUTHENTICATION STATE CHECK ===")
                Log.d(TAG, "Current user: ${currentUser?.uid ?: "NULL"}")
                Log.d(TAG, "User email: ${currentUser?.email ?: "N/A"}")
                Log.d(TAG, "User display name: ${currentUser?.displayName ?: "N/A"}")
                Log.d(TAG, "User is anonymous: ${currentUser?.isAnonymous ?: "N/A"}")
                Log.d(TAG, "User is email verified: ${currentUser?.isEmailVerified ?: "N/A"}")
                Log.d(TAG, "Auth token valid: ${currentUser != null}")
                
                if (currentUser == null) {
                    Log.e(TAG, "CRITICAL: User not authenticated - cannot upload to Firebase Storage")
                    Log.e(TAG, "Please ensure user is signed in before attempting to save scan data")
                    _scanState.value = ScanState.Error("User not authenticated. Please sign in and try again.")
                    _uploadProgress.value = UploadProgress.Failed("", "User not authenticated")
                    return@launch
                }
                
                Log.d(TAG, "Authentication validation PASSED - proceeding with direct upload")
                Log.d(TAG, "======================================")

                // Update scan data with current user ID
                val updatedScanData = scanData.copy(userId = currentUser.uid)
                
                // Validate that images were captured
                if (images.isEmpty()) {
                    Log.w(TAG, "No images to upload")
                    _scanState.value = ScanState.Error("No images captured. Please try scanning again.")
                    _uploadProgress.value = UploadProgress.Failed("", "No images captured")
                    return@launch
                }
                
                // Check for oversized images (10MB limit per image)
                val oversizedImages = images.filter { it.size > 10 * 1024 * 1024 }
                if (oversizedImages.isNotEmpty()) {
                    Log.w(TAG, "Some images are too large: ${oversizedImages.size} images exceed 10MB")
                    _scanState.value = ScanState.Error("Some images are too large. Please try again with smaller images.")
                    _uploadProgress.value = UploadProgress.Failed("", "Images too large")
                    return@launch
                }

                // Start direct upload with progress tracking
                Log.d(TAG, "Starting direct upload...")
                _uploadProgress.value = UploadProgress.Uploading(0, images.size, updatedScanData.id)
                
                // Perform direct upload with progress updates
                val result = if (config != null) {
                    repository.saveScanWithProgress(
                        scanData = updatedScanData,
                        photos = images,
                        progressCallback = { current: Int, total: Int ->
                            _uploadProgress.value = UploadProgress.Uploading(current, total, updatedScanData.id)
                        },
                        config = config
                    )
                } else {
                    repository.saveScanWithProgress(
                        scanData = updatedScanData,
                        photos = images,
                        progressCallback = { current: Int, total: Int ->
                            _uploadProgress.value = UploadProgress.Uploading(current, total, updatedScanData.id)
                        }
                    )
                }
                
                when {
                    result.isSuccess -> {
                        val savedScan = result.getOrNull()!!
                        Log.d(TAG, "Direct upload completed successfully: ${savedScan.id}")
                        _scanState.value = ScanState.Saved
                        _uploadProgress.value = UploadProgress.Success(savedScan.id, images.size)
                    }
                    result.isFailure -> {
                        val error = result.exceptionOrNull()?.message ?: "Unknown upload error"
                        Log.e(TAG, "Direct upload failed: $error")
                        _scanState.value = ScanState.Error("Upload failed: $error")
                        _uploadProgress.value = UploadProgress.Failed(updatedScanData.id, error)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during direct upload", e)
                _scanState.value = ScanState.Error("Failed to upload: ${e.message}")
                _uploadProgress.value = UploadProgress.Failed("", e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Loads all scans for the currently authenticated user.
     * 
     * This method retrieves the user's scan history from Firestore and updates
     * the userScans LiveData. The scans are automatically ordered by creation
     * date (newest first) by the repository.
     * 
     * The method handles:
     * - User authentication validation
     * - Coordinating with ARScanRepository for data retrieval
     * - Updating the userScans LiveData with results
     * - Graceful error handling with empty list fallback
     */
    fun loadUserScans() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.w(TAG, "User not authenticated, cannot load scans")
                    _userScans.value = emptyList()
                    return@launch
                }

                Log.d(TAG, "Loading scans for user: ${currentUser.uid}")
                val scans = repository.getUserScans(currentUser.uid)
                _userScans.value = scans
                Log.d(TAG, "Loaded ${scans.size} scans for user")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading user scans", e)
                _userScans.value = emptyList()
            }
        }
    }

    /**
     * Deletes a specific scan for the authenticated user.
     * 
     * This method removes the scan from Firestore and cleans up associated
     * Firebase Storage files. It handles:
     * - User authentication validation
     * - Coordinating with ARScanRepository for deletion
     * - Updating the userScans LiveData after successful deletion
     * - Graceful error handling
     * 
     * @param scanId The ID of the scan to delete
     */
    fun deleteScan(scanId: String) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.w(TAG, "User not authenticated, cannot delete scan")
                    return@launch
                }

                Log.d(TAG, "Deleting scan: $scanId")
                val success = repository.deleteScan(scanId, currentUser.uid)
                
                if (success) {
                    Log.d(TAG, "Scan deleted successfully: $scanId")
                    // Reload user scans to update the list
                    loadUserScans()
                } else {
                    Log.e(TAG, "Failed to delete scan: $scanId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting scan: $scanId", e)
            }
        }
    }

    /**
     * Updates scan metadata (title, description, tags) for an existing scan.
     * 
     * This method allows users to modify the metadata of their scans without
     * re-uploading the entire scan data. It handles:
     * - User authentication validation
     * - Coordinating with ARScanRepository for updates
     * - Updating the userScans LiveData after successful update
     * - Graceful error handling
     * 
     * @param scanId The ID of the scan to update
     * @param title New title for the scan
     * @param description New description for the scan
     * @param tags New tags for the scan
     */
    fun updateScanMetadata(scanId: String, title: String, description: String, tags: List<String>) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.w(TAG, "User not authenticated, cannot update scan")
                    return@launch
                }

                Log.d(TAG, "Updating metadata for scan: $scanId")
                val success = repository.updateScanMetadata(scanId, currentUser.uid, title, description, tags)
                
                if (success) {
                    Log.d(TAG, "Scan metadata updated successfully: $scanId")
                    // Reload user scans to update the list
                    loadUserScans()
                } else {
                    Log.e(TAG, "Failed to update scan metadata: $scanId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating scan metadata: $scanId", e)
            }
        }
    }

    /**
     * Resets the scan state to idle.
     * 
     * This method is called when the user starts a new scan or when
     * the scan state needs to be cleared.
     */
    fun resetScanState() {
        _scanState.value = ScanState.Idle
        _uploadProgress.value = UploadProgress.Idle
    }

    /**
     * Gets the current upload progress as a human-readable string.
     * 
     * @return String representation of current upload progress
     */
    fun getUploadProgressText(): String {
        return when (val progress = _uploadProgress.value) {
            is UploadProgress.Idle -> "No upload in progress"
            is UploadProgress.Uploading -> "Uploading scan ${progress.scanId}: ${progress.current}/${progress.total} photos"
            is UploadProgress.Success -> "Upload completed: ${progress.photoCount} photos uploaded"
            is UploadProgress.Failed -> "Upload failed: ${progress.error}"
        }
    }
} 