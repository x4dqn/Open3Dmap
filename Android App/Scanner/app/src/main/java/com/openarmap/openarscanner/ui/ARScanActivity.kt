package com.openarmap.openarscanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.ModelRenderable
import com.openarmap.openarscanner.R
import com.openarmap.openarscanner.databinding.ActivityArScanBinding
import com.openarmap.openarscanner.viewmodel.ARScanViewModel
import com.openarmap.openarscanner.data.model.ARScanData
import com.openarmap.openarscanner.data.model.GpsLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import android.util.Log
import com.google.android.gms.location.*
import android.os.Looper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class ARScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArScanBinding
    private lateinit var viewModel: ARScanViewModel
    private lateinit var arSceneView: ArSceneView
    private var isScanning = false
    private var currentSession: Session? = null
    private var frameCount = 0
    private var scanDirectory: File? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: GpsLocation? = null
    private var lastPose: com.google.ar.core.Pose? = null
    private var lastTimestamp: Long = 0
    private var currentScanSession: ARScanData? = null
    private var isDestroyed = false
    private var scanStartTime: Long = 0
    private var currentStorageType: String = "Not Selected"
    private var durationUpdateJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val TAG = "ARScanActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val LOCATION_PERMISSION_REQUEST_CODE = 101
        private const val LOCATION_UPDATE_INTERVAL = 1000L // 1 second
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        // WRITE_EXTERNAL_STORAGE is only needed on Android 9 and below
        private val STORAGE_PERMISSION = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ARScanViewModel::class.java]
        
        // Initialize AR scene view directly
        arSceneView = binding.arFragment as ArSceneView
        
        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Test Firebase connectivity
        testFirebaseConnection()
        
        setupUI()
        
        // Check permissions first
        Log.d(TAG, "onCreate: Checking permissions")
        if (checkPermissions()) {
            Log.d(TAG, "onCreate: Permissions granted, initializing AR")
            initializeAR()
        } else {
            Log.d(TAG, "onCreate: Permissions not granted, requesting permissions")
            requestPermissions()
        }

        observeScanState()
    }

    private fun testFirebaseConnection() {
        lifecycleScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser
                Log.d(TAG, "Firebase Auth - Current user: ${currentUser?.uid ?: "Not authenticated"}")
                
                if (currentUser != null) {
                    Log.d(TAG, "Firebase connection test successful")
                } else {
                    Log.w(TAG, "Firebase user not authenticated")
                }
                
                // Test Firebase Storage connectivity
                try {
                    val storage = FirebaseStorage.getInstance("gs://openarmap.firebasestorage.app")
                    val testRef = storage.reference.child("connectivity_test_${System.currentTimeMillis()}")
                    Log.d(TAG, "Firebase Storage test - Bucket: ${storage.app.options.storageBucket}")
                    Log.d(TAG, "Firebase Storage test - Reference: ${testRef.path}")
                    Log.d(TAG, "Firebase Storage test - Reference bucket: ${testRef.bucket}")
                    
                    // Try to create a small test upload to verify write permissions
                    val testData = "test".toByteArray()
                    try {
                        val uploadTask = testRef.putBytes(testData).await()
                        Log.d(TAG, "Firebase Storage test upload successful: ${uploadTask.bytesTransferred} bytes")
                        
                        // Clean up test file
                        try {
                            testRef.delete().await()
                            Log.d(TAG, "Firebase Storage test file cleaned up")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to clean up test file", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Firebase Storage test upload failed", e)
                        if (e is com.google.firebase.storage.StorageException) {
                            Log.e(TAG, "Storage error code: ${e.errorCode}")
                            Log.e(TAG, "Storage HTTP result: ${e.httpResultCode}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase Storage initialization failed", e)
                    // Try with default storage
                    try {
                        val defaultStorage = FirebaseStorage.getInstance()
                        Log.w(TAG, "Trying default Firebase Storage")
                        Log.w(TAG, "Default storage bucket: ${defaultStorage.app.options.storageBucket}")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Default Firebase Storage also failed", e2)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase connection test failed", e)
            }
        }
    }

    private fun setupUI() {
        binding.scanButton.setOnClickListener {
            if (!isScanning) {
                showStorageChoiceDialog()
            } else {
                stopScanning()
            }
        }
        
        binding.infoButton.setOnClickListener {
            toggleInfoModal()
        }
        
        binding.closeInfoButton.setOnClickListener {
            hideInfoModal()
        }
    }

    private fun toggleInfoModal() {
        if (binding.scanInfoModal.visibility == View.VISIBLE) {
            hideInfoModal()
        } else {
            showInfoModal()
        }
    }

    private fun showInfoModal() {
        binding.scanInfoModal.visibility = View.VISIBLE
        updateInfoModal()
    }

    private fun hideInfoModal() {
        binding.scanInfoModal.visibility = View.GONE
    }

    private fun updateInfoModal() {
        binding.frameCountText.text = frameCount.toString()
        binding.storageTypeText.text = currentStorageType
        
        // Update tracking state
        currentSession?.let { session ->
            try {
                val frame = session.update()
                val trackingState = frame.camera.trackingState
                binding.trackingStateText.text = when (trackingState) {
                    TrackingState.TRACKING -> "Tracking"
                    TrackingState.PAUSED -> "Paused"
                    TrackingState.STOPPED -> "Stopped"
                    else -> "Unknown"
                }
            } catch (e: Exception) {
                binding.trackingStateText.text = "Error"
            }
        } ?: run {
            binding.trackingStateText.text = "Not Initialized"
        }
        
        // Update duration
        if (isScanning && scanStartTime > 0) {
            val duration = (System.currentTimeMillis() - scanStartTime) / 1000
            val minutes = duration / 60
            val seconds = duration % 60
            binding.scanDurationText.text = String.format("%02d:%02d", minutes, seconds)
        } else {
            binding.scanDurationText.text = "00:00"
        }
    }

    private fun startDurationUpdates() {
        durationUpdateJob?.cancel()
        durationUpdateJob = lifecycleScope.launch {
            while (isScanning) {
                updateInfoModal()
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }

    private fun stopDurationUpdates() {
        durationUpdateJob?.cancel()
        durationUpdateJob = null
    }

    private fun showStorageChoiceDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Storage Location")
            .setItems(arrayOf("Local Storage", "Firebase Cloud")) { _, which ->
                when (which) {
                    0 -> {
                        currentStorageType = "Local Storage"
                        startLocalScanning()
                    }
                    1 -> {
                        currentStorageType = "Firebase Cloud"
                        startCloudScanning()
                    }
                }
            }
            .show()
    }

    private fun startLocalScanning() {
        isScanning = true
        scanStartTime = System.currentTimeMillis()
        frameCount = 0
        binding.scanButton.text = "End Scan"
        binding.scanButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        Toast.makeText(this, "Local scanning started", Toast.LENGTH_SHORT).show()
        
        // Create directory for local storage
        scanDirectory = File(getExternalFilesDir(null), "ARScans/${System.currentTimeMillis()}")
        scanDirectory?.mkdirs()
        
        startScanWithName("Local Scan ${System.currentTimeMillis()}")
        startDurationUpdates()
    }

    private fun startCloudScanning() {
        isScanning = true
        scanStartTime = System.currentTimeMillis()
        frameCount = 0
        binding.scanButton.text = "End Scan"
        binding.scanButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        Toast.makeText(this, "Cloud scanning started", Toast.LENGTH_SHORT).show()
        
        startScanWithName("Cloud Scan ${System.currentTimeMillis()}")
        startDurationUpdates()
    }

    private fun startScanWithName(scanName: String) {
        lifecycleScope.launch {
            try {
                // Get device info
                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                // Get current GPS location
                val gpsLocation = getCurrentGpsLocation()

                // Create new scan session
                currentScanSession = ARScanData(
                    title = scanName,
                    deviceId = deviceId,
                    deviceModel = android.os.Build.MODEL,
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName,
                    anchorGps = gpsLocation,
                    scanType = ARScanData.ScanType.WALK_THROUGH
                )

                // Start frame capture
                startFrameCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting scan", e)
                Toast.makeText(this@ARScanActivity, "Error starting scan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFrameCapture() {
        lifecycleScope.launch {
            var lastCaptureTime = System.currentTimeMillis()
            val captureInterval = 1000L // Capture every 1 second instead of every 200ms
            val capturedFrames = mutableListOf<ByteArray>()
            
            while (isScanning && currentSession != null) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val frame = currentSession?.update() ?: continue
                    
                    // Check tracking state
                    if (frame.camera.trackingState != TrackingState.TRACKING) {
                        continue
                    }
                    
                    // Only capture frames when enough time has passed
                    if ((currentTime - lastCaptureTime) >= captureInterval) {
                        captureFrame(frame, capturedFrames)
                        lastCaptureTime = currentTime
                        
                        // Update UI with frame count on main thread
                        runOnUiThread {
                            Log.d(TAG, "Captured frame $frameCount")
                            // Update info modal if it's visible
                            if (binding.scanInfoModal.visibility == View.VISIBLE) {
                                binding.frameCountText.text = frameCount.toString()
                            }
                        }
                    }
                    
                    // Add a small delay
                    kotlinx.coroutines.delay(16)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in frame capture loop", e)
                    break
                }
            }
            
            // Save all captured frames when scanning stops
            if (capturedFrames.isNotEmpty()) {
                saveCapturedFrames(capturedFrames)
            }
        }
    }

    private suspend fun captureFrame(frame: com.google.ar.core.Frame, capturedFrames: MutableList<ByteArray>) {
        try {
            val image = frame.acquireCameraImage()
            
            try {
                // Convert YUV image to JPEG with proper rotation
                val width = image.width
                val height = image.height
                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 80, out)
                
                // Create bitmap and rotate it to correct orientation
                val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                val rotatedBitmap = rotateBitmap(bitmap, 90f) // Rotate 90 degrees clockwise to correct orientation
                
                // Convert rotated bitmap back to byte array
                val rotatedOut = ByteArrayOutputStream()
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, rotatedOut)
                val imageBytes = rotatedOut.toByteArray()
                
                // Clean up bitmaps
                bitmap.recycle()
                rotatedBitmap.recycle()

                if (isScanning) {
                    if (scanDirectory != null) {
                        // Save locally
                        val imageFile = File(scanDirectory, "frame_${frameCount++}.jpg")
                        FileOutputStream(imageFile).use { it.write(imageBytes) }
                    } else {
                        // Store in memory for batch upload
                        capturedFrames.add(imageBytes)
                        frameCount++
                    }
                }
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing frame", e)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun saveCapturedFrames(capturedFrames: List<ByteArray>) {
        try {
            Log.d(TAG, "Saving ${capturedFrames.size} captured frames")
            currentScanSession?.let { session ->
                // Limit the number of frames to upload (max 10 frames)
                val framesToUpload = capturedFrames.take(10)
                viewModel.saveScanData(session, framesToUpload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving captured frames", e)
            runOnUiThread {
                Toast.makeText(this@ARScanActivity, "Error saving scan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL)
                .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                lastKnownLocation = GpsLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    accuracy = location.accuracy
                )
            }
        }
    }

    private suspend fun getCurrentGpsLocation(): GpsLocation {
        return lastKnownLocation ?: GpsLocation()
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            // Android 9 and below - include storage permission
            REQUIRED_PERMISSIONS + STORAGE_PERMISSION
        } else {
            // Android 10 and above - storage permission not needed for app-specific directories
            REQUIRED_PERMISSIONS
        }
    }

    private fun checkPermissions(): Boolean {
        val permissionsToCheck = getRequiredPermissions()
        val permissionResults = permissionsToCheck.map { permission ->
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: $granted")
            granted
        }
        val allGranted = permissionResults.all { it }
        Log.d(TAG, "All permissions granted: $allGranted")
        return allGranted
    }

    private fun requestPermissions() {
        val permissionsToRequest = getRequiredPermissions()
        Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest,
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun initializeAR() {
        try {
            if (isDestroyed) return
            
            Log.d(TAG, "Initializing AR session")
            
            // Create AR session
            currentSession = Session(this)
            val config = Config(currentSession)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode = Config.FocusMode.AUTO
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            
            // Configure session
            currentSession?.configure(config)
            
            // Setup scene view
            arSceneView.setupSession(currentSession)
            
            // Start location updates
            startLocationUpdates()
            
            // Resume AR session
            currentSession?.resume()
            
            Log.d(TAG, "AR session initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AR", e)
            Toast.makeText(this, "Failed to initialize AR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeScanState() {
        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.Saving -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.scanButton.isEnabled = false
                }
                is ScanState.Saved -> {
                    binding.progressBar.visibility = View.GONE
                    binding.scanButton.isEnabled = true
                    Toast.makeText(this, "Scan saved successfully", Toast.LENGTH_SHORT).show()
                }
                is ScanState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.scanButton.isEnabled = true
                    Toast.makeText(this, "Error: ${state.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.scanButton.isEnabled = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isDestroyed) return
        
        Log.d(TAG, "onResume: Checking permissions")
        try {
            if (checkPermissions()) {
                Log.d(TAG, "onResume: Permissions granted")
                if (currentSession == null) {
                    Log.d(TAG, "onResume: No current session, initializing AR")
                    initializeAR()
                } else {
                    Log.d(TAG, "onResume: Resuming existing session")
                    currentSession?.resume()
                    arSceneView.resume()
                }
            } else {
                Log.w(TAG, "onResume: Permissions not granted")
                // Don't request permissions again in onResume to avoid loops
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume AR session", e)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isDestroyed) return
        
        try {
            arSceneView.pause()
            currentSession?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause AR session", e)
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        try {
            stopScanning()
            arSceneView.destroy()
            currentSession?.close()
            currentSession = null
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy AR session", e)
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode, permissions=${permissions.joinToString()}, grantResults=${grantResults.joinToString()}")
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "All permissions granted, initializing AR")
                    initializeAR()
                } else {
                    Log.e(TAG, "Camera permission denied")
                    Toast.makeText(this, "Camera permission is required for AR", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "Location permissions granted")
                    startLocationUpdates()
                } else {
                    Log.w(TAG, "Location permission denied")
                    Toast.makeText(this, "Location permission is recommended for better AR experience", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopScanning() {
        if (!isScanning) return

        Log.d(TAG, "Stopping scan")
        isScanning = false
        stopDurationUpdates()
        
        lifecycleScope.launch {
            try {
                currentScanSession?.let { session ->
                    session.endTime = System.currentTimeMillis()
                }
                
                runOnUiThread {
                    binding.scanButton.text = "Start Scan"
                    binding.scanButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this@ARScanActivity, R.color.purple_500)
                    )
                    binding.progressBar.visibility = View.GONE
                    currentStorageType = "Not Selected"
                    updateInfoModal()
                    Toast.makeText(this@ARScanActivity, "Scan stopped - $frameCount frames captured", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
                runOnUiThread {
                    Toast.makeText(this@ARScanActivity, "Error stopping scan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
} 