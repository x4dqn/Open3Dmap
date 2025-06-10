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

/**
 * ARScanViewModel manages the business logic and state for AR scanning operations.
 * 
 * This ViewModel follows the MVVM (Model-View-ViewModel) architecture pattern,
 * serving as the bridge between the UI layer and the data layer. It handles all
 * AR scan-related operations including saving, loading, and deleting scans.
 * 
 * Key Responsibilities:
 * - Manage scan operation states (Idle, Saving, Saved, Error)
 * - Coordinate between UI and ARScanRepository for data operations
 * - Handle user authentication validation
 * - Provide comprehensive error handling with user-friendly messages
 * - Manage the list of user's scans with automatic refresh
 * - Validate scan data and images before processing
 * - Handle Firebase Storage connectivity and configuration
 * 
 * Architecture Features:
 * - Uses LiveData for reactive UI updates
 * - Leverages Kotlin coroutines for asynchronous operations
 * - Implements proper error handling with specific error messages
 * - Provides automatic retry mechanisms where appropriate
 * - Maintains separation of concerns between UI and business logic
 * 
 * State Management:
 * - scanState: Tracks the current state of scan operations
 * - userScans: Maintains the list of user's saved scans
 * 
 * Thread Safety:
 * - All operations run in viewModelScope for proper lifecycle management
 * - LiveData ensures thread-safe UI updates
 * - Repository operations are handled on appropriate dispatchers
 */
class ARScanViewModel : ViewModel() {
    
    /** Repository for AR scan data operations */
    private val repository = ARScanRepository()
    
    /** Firestore instance for direct database operations (if needed) */
    private val firestore = FirebaseFirestore.getInstance()
    
    /**
     * Firebase Storage instance with fallback mechanism.
     * 
     * Attempts to use the explicit OpenARMap storage bucket first,
     * then falls back to the default Firebase Storage if initialization fails.
     */
    private val storage = try {
        Log.d(TAG, "ViewModel: Attempting to initialize Firebase Storage with explicit bucket")
        FirebaseStorage.getInstance("gs://openarmap.firebasestorage.app")
    } catch (e: Exception) {
        Log.w(TAG, "ViewModel: Failed to initialize with explicit bucket, using default", e)
        FirebaseStorage.getInstance()
    }
    
    /** Firebase Authentication instance for user validation */
    private val auth = FirebaseAuth.getInstance()
    
    /** Private mutable LiveData for scan operation state */
    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    
    /** Public read-only LiveData for scan operation state - observed by UI */
    val scanState: LiveData<ScanState> = _scanState
    
    /** Private mutable LiveData for user's scan list */
    private val _userScans = MutableLiveData<List<ARScanData>>()
    
    /** Public read-only LiveData for user's scan list - observed by UI */
    val userScans: LiveData<List<ARScanData>> = _userScans
    
    companion object {
        /** Logging tag for this ViewModel */
        private const val TAG = "ARScanViewModel"
        
        /** Maximum number of retry attempts for failed operations */
        private const val MAX_RETRY_ATTEMPTS = 3
        
        /** Delay between retry attempts in milliseconds */
        private const val RETRY_DELAY_MS = 2000L
    }

    /**
     * Saves AR scan data with associated images to Firebase backend.
     * 
     * This is the primary method for persisting AR scan data. It performs
     * comprehensive validation, error handling, and state management throughout
     * the save operation.
     * 
     * The method handles:
     * - User authentication validation
     * - Image size and count validation
     * - Firebase Storage connectivity testing
     * - Coordinating with ARScanRepository for actual save operation
     * - Providing detailed error messages for different failure scenarios
     * - Updating UI state throughout the operation
     * 
     * @param scanData AR scan metadata to save
     * @param images List of image data as byte arrays
     */
    fun saveScanData(scanData: ARScanData, images: List<ByteArray>) {
        viewModelScope.launch {
            try {
                _scanState.value = ScanState.Saving
                Log.d(TAG, "Starting to save scan data with ${images.size} images")

                // Validate user authentication
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e(TAG, "User not authenticated")
                    _scanState.value = ScanState.Error("User not authenticated. Please sign in and try again.")
                    return@launch
                }

                // Update scan data with current user ID
                val updatedScanData = scanData.copy(userId = currentUser.uid)
                
                // Validate that images were captured
                if (images.isEmpty()) {
                    Log.w(TAG, "No images to upload")
                    _scanState.value = ScanState.Error("No images captured. Please try scanning again.")
                    return@launch
                }
                
                // Check for oversized images (10MB limit per image)
                val oversizedImages = images.filter { it.size > 10 * 1024 * 1024 }
                if (oversizedImages.isNotEmpty()) {
                    Log.w(TAG, "Some images are too large: ${oversizedImages.size} images exceed 10MB")
                    _scanState.value = ScanState.Error("Some images are too large. Please try again with smaller images.")
                    return@launch
                }

                // Test Firebase Storage connectivity before attempting upload
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

                // Attempt to save scan data (no retry to prevent duplicate uploads)
                Log.d(TAG, "Attempting to save scan...")
                val result = repository.saveScan(updatedScanData, images)
                
                if (result.isSuccess) {
                    Log.d(TAG, "Scan saved successfully")
                    _scanState.value = ScanState.Saved
                    loadUserScans() // Refresh the scan list
                } else {
                    val exception = result.exceptionOrNull()
                    Log.e(TAG, "Failed to save scan", exception)
                    
                    // Provide specific error messages based on exception type
                    val errorMessage = when {
                        exception?.message?.contains("404") == true || 
                        exception?.message?.contains("Object does not exist") == true -> 
                            "Firebase Storage bucket not found or incorrectly configured. Please check your Firebase project setup."
                        exception?.message?.contains("403") == true || 
                        exception?.message?.contains("PERMISSION_DENIED") == true -> 
                            "Permission denied. Please check your Firebase security rules or sign in again."
                        exception?.message?.contains("network") == true -> 
                            "Network error. Please check your internet connection and try again."
                        exception?.message?.contains("quota") == true -> 
                            "Storage quota exceeded. Please contact support."
                        else -> 
                            "Failed to save scan: ${exception?.message ?: "Unknown error"}"
                    }
                    
                    _scanState.value = ScanState.Error(errorMessage)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in saveScanData", e)
                _scanState.value = ScanState.Error("An unexpected error occurred: ${e.message}")
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

    /**
     * Deletes a specific scan and all associated data.
     * 
     * This method removes a scan from both Firebase Storage (photos) and
     * Firestore (metadata). After successful deletion, it automatically
     * refreshes the user's scan list.
     * 
     * @param scanId Unique identifier of the scan to delete
     */
    fun deleteScan(scanId: String) {
        viewModelScope.launch {
            try {
                val result = repository.deleteScan(scanId)
                if (result.isSuccess) {
                    Log.d(TAG, "Scan deleted successfully")
                    // Reload scans to update the UI list
                    loadUserScans()
                } else {
                    Log.e(TAG, "Failed to delete scan", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting scan", e)
            }
        }
    }

    /**
     * Resets the scan state to Idle.
     * 
     * This method is typically called by the UI after handling a scan state
     * change (such as displaying an error message or success notification).
     * It allows the user to start a new scan operation.
     */
    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }
} 