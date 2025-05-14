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
        val exportDialog = ExportDialog(this, scanDatabase, this)
        exportDialog.show()
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
                }
                isScanning.set(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
                Toast.makeText(this@ARScanActivity, "Error stopping scan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFrameCapture() {
        lifecycleScope.launch {
            while (isScanning.get() && arSession != null) {
                try {
                    val frame = arSession?.update() ?: continue
                    
                    // Check tracking state and update UI accordingly
                    updateTrackingStatus(frame.camera.trackingState)
                    
                    // Only capture frames when tracking is good
                    if (frame.camera.trackingState == TrackingState.TRACKING) {
                        captureFrame(frame)
                    }
                    
                    // Add a small delay to avoid overwhelming the system
                    kotlinx.coroutines.delay(100)
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing frame", e)
                    statusText.text = "Error capturing frame: ${e.message}"
                }
            }
        }
    }

    private fun updateTrackingStatus(trackingState: TrackingState) {
        runOnUiThread {
            when (trackingState) {
                TrackingState.TRACKING -> {
                    // Normal tracking - clear any warning messages
                    if (!statusText.text.toString().startsWith("Frames captured:")) {
                        statusText.text = "Tracking OK" + (if (frameCount > 0) " - Frames: $frameCount" else "")
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

    private suspend fun captureFrame(arFrame: com.google.ar.core.Frame) {
        try {
            val camera = arFrame.camera
            val pose = camera.displayOrientedPose
            val poseMatrix = FloatArray(16)
            pose.toMatrix(poseMatrix, 0)

            // Capture image
            try {
                val image = arFrame.acquireCameraImage()
                saveImageToFile(image)
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring camera image", e)
            }

            val frameData = com.example.arcore_scanner.data.models.Frame(
                frameId = "frame_${frameCount.toString().padStart(3, '0')}",
                sessionId = currentScanSession?.scanId ?: "",
                localPose = pose,
                poseMatrix = poseMatrix,
                gps = getCurrentGpsLocation(),
                imu = getImuData(),
                poseConfidence = camera.trackingState.ordinal.toFloat(),
                exposureInfo = getExposureInfo()
            )

            scanDatabase.frameDao().insertFrame(frameData)
            frameCount++

            // Update UI with frame count
            runOnUiThread {
                statusText.text = "Frames captured: $frameCount"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            throw e
        }
    }

    private fun saveImageToFile(image: Image): String {
        val frameId = "frame_${frameCount.toString().padStart(3, '0')}"
        val file = File(getExternalFilesDir(null), "$frameId.jpg")
        
        try {
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

                // Convert YUV to JPEG
                val out = FileOutputStream(file)
                yuvImage.compressToJpeg(
                    android.graphics.Rect(0, 0, image.width, image.height),
                    100,
                    out
                )
                out.close()
            } else {
                // If we can't process YUV, try direct buffer copy
                try {
                    bitmap.copyPixelsFromBuffer(buffer)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
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
                }
            }

            bitmap.recycle()
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            return ""
        } finally {
            image.close()
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
} 