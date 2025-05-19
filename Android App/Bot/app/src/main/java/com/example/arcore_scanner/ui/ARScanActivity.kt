package com.example.arcore_scanner.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.app.AlertDialog
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.arcore_scanner.R
import com.example.arcore_scanner.data.database.ScanDatabase
import com.example.arcore_scanner.data.models.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.ArSceneView
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject
import android.widget.ImageButton
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

class ARScanActivity : AppCompatActivity() {
    private var arSession: Session? = null
    private lateinit var sceneView: ArSceneView
    private lateinit var scanDatabase: ScanDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var statusText: TextView
    private lateinit var scanButton: MaterialButton
    private lateinit var settingsButton: FloatingActionButton
    private lateinit var exportButton: FloatingActionButton
    
    private var currentScanSession: ScanSession? = null
    private var frameCount = 0
    private val isScanning = AtomicBoolean(false)
    private var lastKnownLocation: GpsLocation? = null
    private var lastPose: Pose? = null
    private var lastTimestamp: Long = 0

    companion object {
        private const val TAG = "ARScanActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2
        private const val LOCATION_UPDATE_INTERVAL = 1000L // 1 second
        private const val STORAGE_PERMISSION_REQUEST_CODE = 3
        private const val MANAGE_STORAGE_PERMISSION_REQUEST_CODE = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_scan)
        
        // Initialize views
        sceneView = findViewById(R.id.sceneView)
        statusText = findViewById(R.id.statusText)
        scanButton = findViewById(R.id.scanButton)
        settingsButton = findViewById(R.id.settingsButton)
        exportButton = findViewById(R.id.exportButton)
        
        // Initialize database
        scanDatabase = ScanDatabase.getDatabase(this)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Set up UI listeners
        setupUIListeners()

        // Request permissions
        requestPermissions()

        // Start location updates
        startLocationUpdates()
        
        // Disable scan button until ARCore is initialized
        scanButton.isEnabled = false
        
        // Initialize ARCore when ready
        initializeARCore()
    }
    
    private fun initializeARCore() {
        // Check if ARCore is installed and up to date
        statusText.text = "Checking ARCore availability..."
        
        try {
            when (ArCoreApk.getInstance().checkAvailability(this)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    // ARCore is installed and ready to use
                    setupARSession()
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD, 
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    // ARCore is supported but not installed or needs to be updated
                    try {
                        // Request ARCore installation or update
                        val requestInstallStatus = ArCoreApk.getInstance().requestInstall(
                            this,
                            true
                        )
                        
                        if (requestInstallStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                            // ARCore installation has been requested
                            statusText.text = "ARCore installation requested. Please restart the app after installation."
                            return
                        } else {
                            // ARCore is being installed, continue with session creation
                            setupARSession()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ARCore installation failed", e)
                        statusText.text = "Failed to install ARCore: ${e.message}"
                    }
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    // This device is not capable of running AR
                    statusText.text = "This device does not support ARCore"
                    Toast.makeText(this, "This device does not support ARCore", Toast.LENGTH_LONG).show()
                }
                else -> {
                    // Handle other states
                    statusText.text = "ARCore is not available"
                    Toast.makeText(this, "ARCore is not available", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ARCore availability check failed", e)
            statusText.text = "Error checking ARCore availability: ${e.message}"
        }
    }
    
    private fun setupARSession() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Camera permission required"
            return
        }
        
        try {
            // Create new AR session
            arSession = Session(this)
            
            // Configure session
            val config = Config(arSession)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode = Config.FocusMode.AUTO
            
            // Enable depth if supported
            if (arSession?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true) {
                config.depthMode = Config.DepthMode.AUTOMATIC
                Log.d(TAG, "Depth mode AUTOMATIC is supported")
                
                // Set depth to be prioritized during scanning
                // This may help with the depth measurement errors
                config.setFocusMode(Config.FocusMode.AUTO)
                
                // Try to enable raw depth if available on the device
                try {
                    // This requires ARCore version 1.24.0 or higher
                    val instantPlacementMode = Config.InstantPlacementMode.DISABLED
                    config.instantPlacementMode = instantPlacementMode
                } catch (e: Exception) {
                    Log.d(TAG, "InstantPlacementMode configuration not available: ${e.message}")
                }
            } else {
                Log.d(TAG, "Depth mode is not supported on this device")
            }
            
            // Apply configuration
            arSession?.configure(config)
            
            // Set up AR scene view
            sceneView.setupSession(arSession)
            
            // Enable scan button
            scanButton.isEnabled = true
            statusText.text = "ARCore initialized. Ready to scan."
            
        } catch (e: UnavailableException) {
            handleARCoreException(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AR session", e)
            statusText.text = "Error initializing ARCore: ${e.message}"
        }
    }
    
    private fun handleARCoreException(exception: UnavailableException) {
        val message = when (exception) {
            is UnavailableArcoreNotInstalledException -> 
                "Please install ARCore"
            is UnavailableApkTooOldException -> 
                "Please update ARCore"
            is UnavailableSdkTooOldException -> 
                "Please update this app"
            is UnavailableDeviceNotCompatibleException -> 
                "This device does not support AR"
            is UnavailableUserDeclinedInstallationException -> 
                "Please install ARCore"
            else -> 
                "ARCore is not available: ${exception.message}"
        }
        
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, "ARCore error", exception)
    }

    private fun setupUIListeners() {
        scanButton.setOnClickListener {
            if (isScanning.get()) {
                stopScanning()
                scanButton.text = "Start Scan"
                statusText.text = "Scan stopped"
            } else {
                startScanning()
                scanButton.text = "Stop Scan"
                statusText.text = "Scanning in progress..."
            }
        }

        settingsButton.setOnClickListener {
            // TODO: Implement settings dialog
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }
        
        exportButton.setOnClickListener {
            if (isScanning.get()) {
                Toast.makeText(this, "Please stop scanning before exporting", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "Export button clicked")
            showExportDialog()
        }
    }

    private fun showExportDialog() {
        // For Android 11+ (API 30+), we need special handling for storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Request the MANAGE_EXTERNAL_STORAGE permission
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    
                    Toast.makeText(
                        this,
                        "Please grant permission to access all files for exporting data",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                } catch (e: Exception) {
                    // If the specific permission screen fails to open, try the general storage settings
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error opening storage permission settings", ex)
                        Toast.makeText(
                            this,
                            "Please enable 'Allow access to manage all files' permission in Settings",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // For Android 10 and below, request standard storage permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                return
            }
        }
        
        // Show the export dialog
        lifecycleScope.launch {
            try {
                scanDatabase.scanDao().getAllScans().collect { scanList: List<ScanSession> ->
                    if (scanList.isEmpty()) {
                        Toast.makeText(this@ARScanActivity, "No scans available to export", Toast.LENGTH_SHORT).show()
                        return@collect
                    }

                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    
                    // Create a custom adapter for the list
                    val adapter = object : BaseAdapter() {
                        override fun getCount(): Int = scanList.size
                        override fun getItem(position: Int): Any = scanList[position]
                        override fun getItemId(position: Int): Long = position.toLong()
                        
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = convertView ?: layoutInflater.inflate(R.layout.scan_list_item, parent, false)
                            val scan = scanList[position]
                            
                            val scanNameText = view.findViewById<TextView>(R.id.scanNameText)
                            val deleteButton = view.findViewById<ImageButton>(R.id.deleteButton)
                            val renameButton = view.findViewById<ImageButton>(R.id.renameButton)
                            
                            // Set the scan name and timestamp
                            scanNameText.text = scan.name ?: "Scan ${scan.scanId.take(8)} - ${dateFormat.format(java.util.Date(scan.startTime))}"
                            
                            // Set up delete button
                            deleteButton.setOnClickListener {
                                showDeleteConfirmationDialog(scan)
                            }
                            
                            // Set up rename button
                            renameButton.setOnClickListener {
                                showRenameDialog(scan)
                            }
                            
                            // Set up long press listener for the entire item
                            view.setOnLongClickListener {
                                showScanOptionsDialog(scan)
                                true
                            }
                            
                            // Set up click listener for the entire item
                            view.setOnClickListener {
                                lifecycleScope.launch {
                                    exportMetadata(scan)
                                }
                            }
                            
                            return view
                        }
                    }

                    AlertDialog.Builder(this@ARScanActivity)
                        .setTitle("Select Scan to Export")
                        .setAdapter(adapter, null)
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing export dialog", e)
                Toast.makeText(this@ARScanActivity, "Error loading scans: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmationDialog(scan: ScanSession) {
        AlertDialog.Builder(this)
            .setTitle("Delete Scan")
            .setMessage("Are you sure you want to delete this scan? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // Delete the scan from the database
                        scanDatabase.scanDao().deleteScan(scan)
                        
                        // Delete the exported files if they exist
                        val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "OpenARMaps/Exports")
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.getDefault())
                        val timestamp = dateFormat.format(java.util.Date(scan.startTime))
                        val scanName = scan.name?.takeIf { it.isNotEmpty() } ?: "Unnamed_Scan"
                        val exportDir = File(baseDir, "${scanName}_${timestamp}")
                        
                        if (exportDir.exists()) {
                            exportDir.deleteRecursively()
                        }
                        
                        Toast.makeText(this@ARScanActivity, "Scan deleted successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting scan", e)
                        Toast.makeText(this@ARScanActivity, "Error deleting scan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(scan: ScanSession) {
        val input = EditText(this)
        input.setText(scan.name ?: "")
        input.hint = "Enter new scan name"
        
        AlertDialog.Builder(this)
            .setTitle("Rename Scan")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val newName = input.text.toString().trim()
                        scan.name = newName
                        scanDatabase.scanDao().updateScan(scan)
                        Toast.makeText(this@ARScanActivity, "Scan renamed successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error renaming scan", e)
                        Toast.makeText(this@ARScanActivity, "Error renaming scan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showScanOptionsDialog(scan: ScanSession) {
        val options = arrayOf("Rename", "Delete")
        
        AlertDialog.Builder(this)
            .setTitle("Scan Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(scan)
                    1 -> showDeleteConfirmationDialog(scan)
                }
            }
            .show()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            
            val locationRequest = LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
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

    private suspend fun getCurrentGpsLocation(): GpsLocation = suspendCancellableCoroutine { continuation ->
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            continuation.resume(GpsLocation(0.0, 0.0, 0.0, 0.0f))
            return@suspendCancellableCoroutine
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val gpsLocation = GpsLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude,
                            accuracy = location.accuracy
                        )
                        lastKnownLocation = gpsLocation
                        continuation.resume(gpsLocation)
                    } else {
                        continuation.resume(GpsLocation(0.0, 0.0, 0.0, 0.0f))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error getting location", e)
                    continuation.resume(GpsLocation(0.0, 0.0, 0.0, 0.0f))
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            continuation.resume(GpsLocation(0.0, 0.0, 0.0, 0.0f))
        }
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
        
        // Request storage permissions based on Android version
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED || 
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33) or higher - request new media permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    ),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun startScanning() {
        if (isScanning.get() || arSession == null) return

        // Check if we have good tracking before starting
        val frame = arSession?.update()
        if (frame?.camera?.trackingState != TrackingState.TRACKING) {
            Toast.makeText(this, 
                "Cannot start scan: Poor tracking. Please move device slowly in a well-lit area.", 
                Toast.LENGTH_LONG).show()
            statusText.text = "Move device to improve tracking before scanning"
            return
        }

        // Show name input dialog
        val input = EditText(this)
        input.hint = "Enter scan name (optional)"
        
        AlertDialog.Builder(this)
            .setTitle("New Scan")
            .setView(input)
            .setPositiveButton("Start") { dialog, _ ->
                val scanName = input.text.toString().trim()
                startScanWithName(scanName)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startScanWithName(scanName: String) {
        lifecycleScope.launch {
            try {
                // Show scanning tips
                runOnUiThread {
                    Toast.makeText(this@ARScanActivity,
                        "For best results: Move slowly, keep textured surfaces in view, ensure good lighting",
                        Toast.LENGTH_LONG).show()
                }
                
                // Create new scan session
                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                // Get the latest frame to access camera pose
                val frame = arSession?.update() ?: throw IllegalStateException("ARCore session not ready")
                val cameraPose = frame.camera.displayOrientedPose
                val worldMatrix = FloatArray(16)
                cameraPose.toMatrix(worldMatrix, 0)

                currentScanSession = ScanSession(
                    deviceId = deviceId,
                    deviceModel = android.os.Build.MODEL,
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName,
                    name = scanName.takeIf { it.isNotEmpty() },
                    anchorGps = getCurrentGpsLocation(),
                    originPose = cameraPose,
                    localToWorldMatrix = worldMatrix,
                    scanType = ScanSession.ScanType.WALK_THROUGH
                )

                // Save session to database
                currentScanSession?.let { scanDatabase.scanDao().insertScan(it) }
                isScanning.set(true)
                frameCount = 0

                // Start frame capture loop
                startFrameCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting scan", e)
                Toast.makeText(this@ARScanActivity, "Error starting scan: ${e.message}", Toast.LENGTH_SHORT).show()
                statusText.text = "Error: ${e.message}"
            }
        }
    }

    private fun stopScanning() {
        if (!isScanning.get()) return

        lifecycleScope.launch {
            try {
                currentScanSession?.let { session ->
                    session.endTime = System.currentTimeMillis()
                    scanDatabase.scanDao().updateScan(session)
                    Log.d(TAG, "Scan session ${session.scanId} ended with $frameCount frames")
                }
                isScanning.set(false)
                runOnUiThread {
                    scanButton.text = "Start Scan"
                    statusText.text = "Scan stopped"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
                Toast.makeText(this@ARScanActivity, "Error stopping scan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFrameCapture() {
        lifecycleScope.launch {
            var lastCaptureTime = System.currentTimeMillis()
            val captureInterval = 200L // 5 frames per second (1000ms / 5)
            
            Log.d(TAG, "Starting frame capture loop")
            
            while (isScanning.get() && arSession != null) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val frame = arSession?.update() ?: continue
                    
                    // Check tracking state and update UI accordingly
                    updateTrackingStatus(frame.camera.trackingState)
                    
                    // Log tracking state and quality
                    val trackingState = frame.camera.trackingState
                    val trackingQuality = when (trackingState) {
                        TrackingState.TRACKING -> 1.0f
                        TrackingState.PAUSED -> 0.5f
                        else -> 0.0f
                    }
                    
                    // Get motion data for quality assessment
                    val motionData = getMotionData(frame)
                    val motionQuality = calculateMotionQuality(motionData)
                    
                    Log.d(TAG, "Frame check - Tracking: $trackingState, Quality: ${(motionQuality * 100).toInt()}%, Time since last: ${currentTime - lastCaptureTime}ms")
                    
                    // Only capture frames when tracking is good and enough time has passed
                    if (trackingState == TrackingState.TRACKING && 
                        (currentTime - lastCaptureTime) >= captureInterval &&
                        motionQuality > 0.3f) {
                        
                        Log.d(TAG, "Attempting to capture frame")
                        captureFrame(frame, trackingQuality, motionQuality)
                        lastCaptureTime = currentTime
                        
                        // Update UI with frame count
                        runOnUiThread {
                            statusText.text = "Frames: $frameCount (Quality: ${(motionQuality * 100).toInt()}%)"
                        }
                    }
                    
                    // Add a small delay to avoid overwhelming the system
                    kotlinx.coroutines.delay(16) // ~60fps for UI updates
                } catch (e: Exception) {
                    Log.e(TAG, "Error in frame capture loop", e)
                    statusText.text = "Error capturing frame: ${e.message}"
                }
            }
            Log.d(TAG, "Frame capture loop ended")
        }
    }

    private fun updateTrackingStatus(trackingState: TrackingState) {
        runOnUiThread {
            when (trackingState) {
                TrackingState.TRACKING -> {
                    // Only update if we're not already showing frame count
                    if (!statusText.text.toString().startsWith("Frames:")) {
                        statusText.text = "Frames: $frameCount"
                    }
                    scanButton.isEnabled = true
                }
                TrackingState.PAUSED -> {
                    // Get the current frame to check tracking failure reason
                    val frame = arSession?.update()
                    val reason = when (frame?.camera?.trackingFailureReason) {
                        TrackingFailureReason.NONE -> "Unknown"
                        TrackingFailureReason.BAD_STATE -> "Bad state"
                        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Insufficient light"
                        TrackingFailureReason.EXCESSIVE_MOTION -> "Excessive motion"
                        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Insufficient features"
                        else -> "Unknown"
                    }
                    statusText.text = "Tracking paused: $reason. Move device slowly."
                    // Keep scan button enabled to allow users to stop a scan
                }
                TrackingState.STOPPED -> {
                    statusText.text = "Tracking lost. Try restarting the scan."
                    // Consider stopping the scan automatically here
                    if (isScanning.get()) {
                        stopScanning()
                        scanButton.text = "Start Scan"
                        Toast.makeText(this@ARScanActivity, 
                            "Scan stopped due to tracking loss", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun captureFrame(arFrame: com.google.ar.core.Frame, trackingQuality: Float, motionQuality: Float) {
        try {
            val camera = arFrame.camera
            val pose = camera.displayOrientedPose
            val poseMatrix = FloatArray(16)
            pose.toMatrix(poseMatrix, 0)

            // Get camera intrinsics
            val intrinsics = camera.imageIntrinsics
            val image = arFrame.acquireCameraImage()
            
            try {
                // Log overall frame quality metrics
                Log.d(TAG, """
                    Frame Quality Assessment:
                    - Tracking State: ${camera.trackingState}
                    - Tracking Quality: ${(trackingQuality * 100).toInt()}%
                    - Motion Quality: ${(motionQuality * 100).toInt()}%
                    - Combined Quality: ${(((trackingQuality + motionQuality) / 2.0f) * 100).toInt()}%
                """.trimIndent())

                // Capture image
                val imagePath = saveImageToFile(image)
                Log.d(TAG, "Saved image to: $imagePath")
                
                if (imagePath.isEmpty()) {
                    Log.e(TAG, "Failed to save image to file")
                    return
                }
                
                // Get current GPS location
                val gpsLocation = getCurrentGpsLocation()
                
                // Create frame data
                val frameData = com.example.arcore_scanner.data.models.Frame(
                    frameId = "frame_${frameCount.toString().padStart(3, '0')}",
                    sessionId = currentScanSession?.scanId ?: throw IllegalStateException("No active scan session"),
                    localPose = pose,
                    poseMatrix = poseMatrix,
                    gps = gpsLocation,
                    imu = getImuData(),
                    poseConfidence = trackingQuality,
                    frameQualityScore = motionQuality,
                    exposureInfo = getExposureInfo(),
                    manualTags = emptyList(),
                    timestamp = System.currentTimeMillis()
                )

                // Save frame to database
                try {
                    scanDatabase.frameDao().insertFrame(frameData)
                    frameCount++
                    Log.d(TAG, "Frame $frameCount saved to database with ID: ${frameData.frameId}")

                    // Update UI with frame count and quality
                    runOnUiThread {
                        statusText.text = "Frames: $frameCount (Quality: ${(motionQuality * 100).toInt()}%)"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving frame to database", e)
                    throw e
                }
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing frame", e)
        }
    }

    private fun getMotionData(frame: com.google.ar.core.Frame): MotionData {
        val camera = frame.camera
        val currentPose = camera.displayOrientedPose
        val currentTimestamp = frame.timestamp
        
        // Calculate relative motion if we have a previous pose
        val translation = FloatArray(3)
        val rotation = FloatArray(4)
        
        if (lastPose != null && lastTimestamp > 0) {
            // Get current and last positions
            val currentPosition = FloatArray(3)
            val lastPosition = FloatArray(3)
            currentPose.getTranslation(currentPosition, 0)
            lastPose!!.getTranslation(lastPosition, 0)
            
            // Calculate translation as the difference between positions
            translation[0] = currentPosition[0] - lastPosition[0]
            translation[1] = currentPosition[1] - lastPosition[1]
            translation[2] = currentPosition[2] - lastPosition[2]
            
            // Get current and last rotations
            val currentRotation = FloatArray(4)
            val lastRotation = FloatArray(4)
            currentPose.getRotationQuaternion(currentRotation, 0)
            lastPose!!.getRotationQuaternion(lastRotation, 0)
            
            // Calculate relative rotation: current * inverse(last)
            // For quaternions, inverse is [w, -x, -y, -z]
            val lastInverse = floatArrayOf(
                lastRotation[0],  // w
                -lastRotation[1], // -x
                -lastRotation[2], // -y
                -lastRotation[3]  // -z
            )
            
            // Quaternion multiplication: current * lastInverse
            rotation[0] = currentRotation[0] * lastInverse[0] - currentRotation[1] * lastInverse[1] -
                         currentRotation[2] * lastInverse[2] - currentRotation[3] * lastInverse[3]
            rotation[1] = currentRotation[0] * lastInverse[1] + currentRotation[1] * lastInverse[0] +
                         currentRotation[2] * lastInverse[3] - currentRotation[3] * lastInverse[2]
            rotation[2] = currentRotation[0] * lastInverse[2] - currentRotation[1] * lastInverse[3] +
                         currentRotation[2] * lastInverse[0] + currentRotation[3] * lastInverse[1]
            rotation[3] = currentRotation[0] * lastInverse[3] + currentRotation[1] * lastInverse[2] -
                         currentRotation[2] * lastInverse[1] + currentRotation[3] * lastInverse[0]
            
            // Normalize the resulting quaternion
            val magnitude = kotlin.math.sqrt(
                (rotation[0] * rotation[0] + rotation[1] * rotation[1] +
                rotation[2] * rotation[2] + rotation[3] * rotation[3]).toDouble()
            ).toFloat()
            
            if (magnitude > 0) {
                rotation[0] /= magnitude
                rotation[1] /= magnitude
                rotation[2] /= magnitude
                rotation[3] /= magnitude
            }
            
            // Log raw motion data
            Log.d(TAG, """
                Raw Motion Data:
                - Current Position: ${currentPosition[0]}, ${currentPosition[1]}, ${currentPosition[2]}
                - Last Position: ${lastPosition[0]}, ${lastPosition[1]}, ${lastPosition[2]}
                - Relative Translation: ${translation[0]}, ${translation[1]}, ${translation[2]}
                - Current Rotation: ${currentRotation[0]}, ${currentRotation[1]}, ${currentRotation[2]}, ${currentRotation[3]}
                - Last Rotation: ${lastRotation[0]}, ${lastRotation[1]}, ${lastRotation[2]}, ${lastRotation[3]}
                - Relative Rotation: ${rotation[0]}, ${rotation[1]}, ${rotation[2]}, ${rotation[3]}
                - Time Delta: ${(currentTimestamp - lastTimestamp) / 1000000.0}ms
            """.trimIndent())
        } else {
            // First frame, use zero motion
            translation.fill(0f)
            rotation[0] = 1f  // w component of identity quaternion
            rotation[1] = 0f
            rotation[2] = 0f
            rotation[3] = 0f
        }
        
        // Update last pose and timestamp
        lastPose = currentPose
        lastTimestamp = currentTimestamp
        
        return MotionData(
            translation = translation,
            rotation = rotation,
            timestamp = currentTimestamp
        )
    }

    private fun calculateMotionQuality(motionData: MotionData): Float {
        // Calculate translation magnitude in meters
        val translationMagnitude = kotlin.math.sqrt(
            (motionData.translation[0] * motionData.translation[0] +
            motionData.translation[1] * motionData.translation[1] +
            motionData.translation[2] * motionData.translation[2]).toDouble()
        ).toFloat()
        
        // Calculate rotation magnitude in degrees using quaternion
        val q = motionData.rotation
        val w = kotlin.math.abs(q[0])
        val x = q[1]
        val y = q[2]
        val z = q[3]
        
        // Calculate rotation angle in degrees
        val rotationMagnitudeDegrees = if (w >= 1.0f) {
            0.0f  // No rotation
        } else {
            (2.0 * kotlin.math.acos(w) * 180.0 / Math.PI).toFloat()
        }
        
        // Quality ranges for optimal scanning at 5fps (200ms per frame):
        // Translation: 0.2cm to 2cm per frame (0.002m to 0.02m)
        // This means 1-10cm per second, which is a reasonable walking speed
        // Rotation: 0.1° to 1° per frame
        // This means 0.5-5° per second, which is a reasonable turning rate
        val idealTranslationRange = 0.002f..0.02f
        val idealRotationRange = 0.1f..1.0f
        
        // Calculate translation quality score (0.0 to 1.0)
        val translationQuality = when {
            translationMagnitude in idealTranslationRange -> 1.0f
            translationMagnitude < idealTranslationRange.start -> {
                // Too little movement - score based on how close to minimum
                translationMagnitude / idealTranslationRange.start
            }
            else -> {
                // Too much movement - score based on how close to maximum
                idealTranslationRange.endInclusive / translationMagnitude
            }
        }
        
        // Calculate rotation quality score (0.0 to 1.0)
        val rotationQuality = when {
            rotationMagnitudeDegrees in idealRotationRange -> 1.0f
            rotationMagnitudeDegrees < idealRotationRange.start -> {
                // Too little rotation - score based on how close to minimum
                rotationMagnitudeDegrees / idealRotationRange.start
            }
            else -> {
                // Too much rotation - score based on how close to maximum
                idealRotationRange.endInclusive / rotationMagnitudeDegrees
            }
        }
        
        // Log detailed quality metrics
        Log.d(TAG, """
            Motion Quality Metrics:
            - Translation: ${(translationMagnitude * 100).toInt()}cm (Score: ${(translationQuality * 100).toInt()}%)
            - Rotation: ${rotationMagnitudeDegrees.toInt()}° (Score: ${(rotationQuality * 100).toInt()}%)
            - Combined Score: ${(((translationQuality + rotationQuality) / 2.0f) * 100).toInt()}%
        """.trimIndent())
        
        // Return average of translation and rotation quality
        return (translationQuality + rotationQuality) / 2.0f
    }

    private fun quaternionToEuler(q: FloatArray): FloatArray {
        // Convert quaternion to euler angles (in degrees)
        // Using the correct conversion formula for ARCore's coordinate system
        val roll = Math.atan2(2.0 * (q[0] * q[1] + q[2] * q[3]), 1.0 - 2.0 * (q[1] * q[1] + q[2] * q[2]))
        val pitch = Math.asin(2.0 * (q[0] * q[2] - q[3] * q[1]))
        val yaw = Math.atan2(2.0 * (q[0] * q[3] + q[1] * q[2]), 1.0 - 2.0 * (q[2] * q[2] + q[3] * q[3]))
        
        // Convert to degrees and normalize to -180 to 180
        val rollDegrees = (roll * 180.0 / Math.PI).toFloat()
        val pitchDegrees = (pitch * 180.0 / Math.PI).toFloat()
        val yawDegrees = (yaw * 180.0 / Math.PI).toFloat()
        
        // Normalize angles to -180 to 180 degrees
        fun normalizeAngle(angle: Float): Float {
            var normalized = angle
            while (normalized > 180f) normalized -= 360f
            while (normalized < -180f) normalized += 360f
            return normalized
        }
        
        return floatArrayOf(
            normalizeAngle(rollDegrees),
            normalizeAngle(pitchDegrees),
            normalizeAngle(yawDegrees)
        )
    }

    data class MotionData(
        val translation: FloatArray,
        val rotation: FloatArray,
        val timestamp: Long
    )

    data class CameraIntrinsics(
        val focalLength: Float,
        val principalPoint: FloatArray,
        val imageWidth: Int,
        val imageHeight: Int
    )

    private fun saveImageToFile(image: Image): String {
        val frameId = "frame_${frameCount.toString().padStart(3, '0')}"
        val file = File(getExternalFilesDir(null), "$frameId.jpg")
        
        try {
            Log.d(TAG, "Attempting to save image to: ${file.absolutePath}")
            
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            // Create a bitmap with the correct dimensions
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )

            // For YUV_420_888 format
            if (planes.size >= 3) {
                val yBuffer = planes[0].buffer
                val uBuffer = planes[1].buffer
                val vBuffer = planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                // U and V are swapped
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = android.graphics.YuvImage(
                    nv21,
                    android.graphics.ImageFormat.NV21,
                    image.width,
                    image.height,
                    null
                )

                // Convert YUV to JPEG with proper orientation
                val out = FileOutputStream(file)
                yuvImage.compressToJpeg(
                    android.graphics.Rect(0, 0, image.width, image.height),
                    100,
                    out
                )
                out.close()

                // Read the saved JPEG and rotate it to portrait
                val savedBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                val rotatedBitmap = if (savedBitmap.width > savedBitmap.height) {
                    // Landscape image, rotate to portrait
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(90f)
                    Bitmap.createBitmap(
                        savedBitmap,
                        0,
                        0,
                        savedBitmap.width,
                        savedBitmap.height,
                        matrix,
                        true
                    )
                } else {
                    savedBitmap
                }

                // Save the rotated image
                FileOutputStream(file).use { out ->
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }

                // Clean up bitmaps
                savedBitmap.recycle()
                if (rotatedBitmap != savedBitmap) {
                    rotatedBitmap.recycle()
                }

                Log.d(TAG, "Successfully saved YUV image with portrait orientation")
            } else {
                // If we can't process YUV, try direct buffer copy
                try {
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    // Rotate the bitmap to portrait if needed
                    val rotatedBitmap = if (bitmap.width > bitmap.height) {
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(90f)
                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true
                        )
                    } else {
                        bitmap
                    }

                    FileOutputStream(file).use { out ->
                        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    // Clean up bitmaps
                    if (rotatedBitmap != bitmap) {
                        rotatedBitmap.recycle()
                    }

                    Log.d(TAG, "Successfully saved direct buffer image with portrait orientation")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing image buffer", e)
                    // Create a placeholder frame with error information
                    val canvas = android.graphics.Canvas(bitmap)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                        style = android.graphics.Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, image.width.toFloat(), image.height.toFloat(), paint)
                    
                    // Add error information
                    paint.color = android.graphics.Color.WHITE
                    paint.textSize = 50f
                    canvas.drawText("Frame: $frameCount", 50f, 100f, paint)
                    canvas.drawText("Error: ${e.message}", 50f, 150f, paint)
                    
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    Log.d(TAG, "Saved error placeholder image")
                }
            }

            bitmap.recycle()
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            return ""
        }
    }

    private fun getImuData(): com.example.arcore_scanner.data.models.Frame.ImuData {
        // In a real app, you would register SensorEventListener and get real values
        // For now, we'll just use default values
        return com.example.arcore_scanner.data.models.Frame.ImuData(
            accelerometer = FloatArray(3),
            gyroscope = FloatArray(3)
        )
    }

    private fun getExposureInfo(): com.example.arcore_scanner.data.models.Frame.ExposureInfo {
        // Since we're having issues with the camera2 API constants,
        // we'll use default values for now to avoid compilation errors
        return com.example.arcore_scanner.data.models.Frame.ExposureInfo(
            iso = 100, // Default ISO value
            shutterSpeed = 0.033f, // Default shutter speed (30fps)
            brightness = 0.5f // Default brightness
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Camera permission granted, initialize ARCore
                    initializeARCore()
                } else {
                    Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                    statusText.text = "Camera permission denied"
                    finish()
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                } else {
                    Toast.makeText(this, "Location permission is required for accurate scanning", Toast.LENGTH_LONG).show()
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showExportDialog()
                } else {
                    Toast.makeText(this, "Storage permission is required for exporting data", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Resume ARCore session if it exists
        if (arSession != null) {
            try {
                arSession?.resume()
                sceneView.resume()
            } catch (e: CameraNotAvailableException) {
                Log.e(TAG, "Camera not available during onResume", e)
                statusText.text = "Camera not available. Please restart the app."
                Toast.makeText(this, "Camera not available", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            // Try to initialize ARCore again
            initializeARCore()
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Pause ARCore session
        arSession?.let {
            sceneView.pause()
            it.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        arSession?.close()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == MANAGE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // Permission granted, show export dialog
                    showExportDialog()
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "Storage permission is required for exporting data",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun exportMetadata(session: ScanSession) {
        try {
            Log.d(TAG, "Starting metadata export for session: ${session.scanId}")
            
            // Create base directory for all exports in public Documents
            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "OpenARMaps/Exports")
            
            if (!baseDir.exists()) {
                val dirCreated = baseDir.mkdirs()
                Log.d(TAG, "Base directory creation result: $dirCreated")
                if (!dirCreated) {
                    throw Exception("Failed to create base directory at ${baseDir.absolutePath}")
                }
            }

            // Format the directory name using scan name and timestamp
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.getDefault())
            val timestamp = dateFormat.format(java.util.Date(session.startTime))
            val scanName = session.name?.takeIf { it.isNotEmpty() } ?: "Unnamed_Scan"
            val exportDirName = "${scanName}_${timestamp}"
            
            // Create session-specific directory
            val exportDir = File(baseDir, exportDirName)
            Log.d(TAG, "Attempting to create export directory at: ${exportDir.absolutePath}")
            if (!exportDir.exists()) {
                val dirCreated = exportDir.mkdirs()
                Log.d(TAG, "Export directory creation result: $dirCreated")
                if (!dirCreated) {
                    throw Exception("Failed to create export directory at ${exportDir.absolutePath}")
                }
            }

            // Create metadata subdirectory
            val metadataDir = File(exportDir, "metadata")
            if (!metadataDir.exists()) {
                val metadataDirCreated = metadataDir.mkdirs()
                Log.d(TAG, "Metadata directory creation result: $metadataDirCreated")
                if (!metadataDirCreated) {
                    throw Exception("Failed to create metadata directory at ${metadataDir.absolutePath}")
                }
            }

            // Get frames from database
            val frames = scanDatabase.frameDao().getFramesForScan(session.scanId)
            Log.d(TAG, "Retrieved frames from database for session: ${session.scanId}")
            
            frames.collect { frameList ->
                Log.d(TAG, "Processing ${frameList.size} frames for export")
                
                if (frameList.isEmpty()) {
                    throw Exception("No frames found for session ${session.scanId}")
                }

                // Copy images to export directory
                val imagesDir = File(exportDir, "images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }

                frameList.forEach { frame ->
                    val sourceFile = File(getExternalFilesDir(null), "${frame.frameId}.jpg")
                    if (sourceFile.exists()) {
                        val destFile = File(imagesDir, "${frame.frameId}.jpg")
                        sourceFile.copyTo(destFile, overwrite = true)
                        Log.d(TAG, "Copied image ${frame.frameId}.jpg to export directory")
                    } else {
                        Log.w(TAG, "Source image not found: ${sourceFile.absolutePath}")
                    }
                }

                // Create transforms.json in INRIA format
                val transforms = frameList.map { frame ->
                    mapOf(
                        "file_path" to "images/${frame.frameId}.jpg",
                        "transform_matrix" to frame.poseMatrix.toTypedArray().toList(),
                        "timestamp" to frame.timestamp,
                        "gps" to mapOf(
                            "latitude" to frame.gps?.latitude,
                            "longitude" to frame.gps?.longitude,
                            "altitude" to frame.gps?.altitude,
                            "accuracy" to frame.gps?.accuracy
                        ),
                        "imu" to mapOf(
                            "accelerometer" to frame.imu?.accelerometer?.toTypedArray()?.toList(),
                            "gyroscope" to frame.imu?.gyroscope?.toTypedArray()?.toList()
                        ),
                        "quality" to mapOf(
                            "pose_confidence" to frame.poseConfidence,
                            "frame_quality" to frame.frameQualityScore
                        )
                    )
                }

                // Write transforms.json
                val transformsFile = File(metadataDir, "transforms.json")
                try {
                    transformsFile.writeText(JSONObject(mapOf("frames" to transforms) as Map<String, Any>).toString(2))
                    Log.d(TAG, "Successfully wrote transforms.json to: ${transformsFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing transforms.json", e)
                    throw e
                }

                // Write session metadata
                val sessionMetadata = mapOf(
                    "session_id" to session.scanId,
                    "device_id" to session.deviceId,
                    "device_model" to session.deviceModel,
                    "app_version" to session.appVersion,
                    "scan_type" to session.scanType.toString(),
                    "start_time" to session.startTime,
                    "end_time" to session.endTime,
                    "frame_count" to frameList.size,
                    "name" to session.name
                )

                val sessionFile = File(metadataDir, "session_${session.scanId}.json")
                try {
                    sessionFile.writeText(JSONObject(sessionMetadata as Map<String, Any>).toString(2))
                    Log.d(TAG, "Successfully wrote session metadata to: ${sessionFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing session metadata", e)
                    throw e
                }

                runOnUiThread {
                    Toast.makeText(this@ARScanActivity, "Scan exported to: ${exportDir.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting metadata", e)
            runOnUiThread {
                Toast.makeText(this@ARScanActivity, "Error exporting scan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Extension function to convert FloatArray to List<Float>
    private fun FloatArray.toList(): List<Float> = this.toTypedArray().toList()
} 