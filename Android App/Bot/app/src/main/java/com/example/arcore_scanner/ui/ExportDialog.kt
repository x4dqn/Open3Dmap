package com.example.arcore_scanner.ui

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.arcore_scanner.R
import com.example.arcore_scanner.data.database.ScanDatabase
import com.example.arcore_scanner.data.models.ScanSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.AlertDialog

class ExportDialog(
    context: Context,
    private val scanDatabase: ScanDatabase,
    private val activity: ARScanActivity? = null
) : Dialog(context) {

    private lateinit var spinnerSessions: Spinner
    private lateinit var progressExport: ProgressBar
    private lateinit var textExportStatus: TextView
    private lateinit var btnExport: Button
    private lateinit var btnCancel: Button
    private lateinit var btnRename: Button
    private lateinit var btnDelete: Button
    
    private var sessions = listOf<ScanSession>()
    private val TAG = "ExportDialog"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_export)
        
        // Initialize views
        spinnerSessions = findViewById(R.id.spinnerSessions)
        progressExport = findViewById(R.id.progressExport)
        textExportStatus = findViewById(R.id.textExportStatus)
        btnExport = findViewById(R.id.btnExport)
        btnCancel = findViewById(R.id.btnCancel)
        btnRename = findViewById(R.id.btnRename)
        btnDelete = findViewById(R.id.btnDelete)
        
        // Load scan sessions
        loadScanSessions()
        
        // Set up button listeners
        btnCancel.setOnClickListener {
            dismiss()
        }
        
        btnExport.setOnClickListener {
            if (sessions.isNotEmpty()) {
                val selectedSession = sessions[spinnerSessions.selectedItemPosition]
                exportSession(selectedSession)
            } else {
                Toast.makeText(context, "No scan sessions available", Toast.LENGTH_SHORT).show()
            }
        }

        btnRename.setOnClickListener {
            if (sessions.isNotEmpty()) {
                val selectedSession = sessions[spinnerSessions.selectedItemPosition]
                showRenameDialog(selectedSession)
            }
        }

        btnDelete.setOnClickListener {
            if (sessions.isNotEmpty()) {
                val selectedSession = sessions[spinnerSessions.selectedItemPosition]
                showDeleteConfirmationDialog(selectedSession)
            }
        }
    }
    
    private fun loadScanSessions() {
        if (activity == null) {
            Log.e(TAG, "Activity reference is null")
            Toast.makeText(context, "Error: Cannot access activity", Toast.LENGTH_SHORT).show()
            btnExport.isEnabled = false
            btnRename.isEnabled = false
            btnDelete.isEnabled = false
            return
        }
        
        activity.lifecycleScope.launch {
            try {
                sessions = scanDatabase.scanDao().getAllScans().first()
                
                if (sessions.isEmpty()) {
                    Toast.makeText(context, "No scan sessions found", Toast.LENGTH_SHORT).show()
                    btnExport.isEnabled = false
                    btnRename.isEnabled = false
                    btnDelete.isEnabled = false
                } else {
                    // Create adapter with session names and timestamps
                    val sessionNames = sessions.map { session ->
                        val date = Date(session.startTime)
                        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        session.name ?: "Scan - ${dateFormat.format(date)}"
                    }.toTypedArray()
                    
                    val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, sessionNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerSessions.adapter = adapter
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading scan sessions", e)
                Toast.makeText(context, "Error loading scan sessions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRenameDialog(session: ScanSession) {
        val input = EditText(context)
        input.setText(session.name ?: "")
        input.hint = "Enter scan name"

        AlertDialog.Builder(context)
            .setTitle("Rename Scan")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    activity?.lifecycleScope?.launch {
                        try {
                            session.name = newName
                            scanDatabase.scanDao().updateScan(session)
                            loadScanSessions() // Reload the list
                            Toast.makeText(context, "Scan renamed successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error renaming scan", e)
                            Toast.makeText(context, "Error renaming scan: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(session: ScanSession) {
        AlertDialog.Builder(context)
            .setTitle("Delete Scan")
            .setMessage("Are you sure you want to delete this scan? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                activity?.lifecycleScope?.launch {
                    try {
                        // Delete frames first (cascade delete should handle this, but being explicit)
                        scanDatabase.frameDao().deleteFramesForScan(session.scanId)
                        // Delete the session
                        scanDatabase.scanDao().deleteScan(session)
                        loadScanSessions() // Reload the list
                        Toast.makeText(context, "Scan deleted successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting scan", e)
                        Toast.makeText(context, "Error deleting scan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun exportSession(session: ScanSession) {
        if (activity == null) {
            Log.e(TAG, "Activity reference is null")
            Toast.makeText(context, "Error: Cannot access activity", Toast.LENGTH_SHORT).show()
            return
        }
        
        activity.lifecycleScope.launch {
            try {
                btnExport.isEnabled = false
                progressExport.visibility = View.VISIBLE
                textExportStatus.visibility = View.VISIBLE
                textExportStatus.text = "Preparing export..."
                
                // Create timestamp for export folder
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

                // Determine appropriate export directory based on Android version
                val exportDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                    // Use public directory for Android 11+ with MANAGE_EXTERNAL_STORAGE permission
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), 
                        "OpenARMaps/exports/gaussian_${timestamp}")
                } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                    // For Android 10 and below with WRITE_EXTERNAL_STORAGE permission
                    File(Environment.getExternalStorageDirectory(), 
                        "OpenARMaps/exports/gaussian_${timestamp}")
                } else {
                    // Fallback to app-specific directory
                    File(context.getExternalFilesDir(null), "exports/gaussian_${timestamp}")
                }
                
                // Create directory structure for COLMAP format
                val colmapDir = File(exportDir, "colmap")
                val imagesDir = File(colmapDir, "images")
                val sparseDir = File(colmapDir, "sparse/0")
                
                withContext(Dispatchers.IO) {
                    colmapDir.mkdirs()
                    imagesDir.mkdirs()
                    sparseDir.mkdirs()
                
                    // Get frames for this session
                    val frames = scanDatabase.frameDao().getFramesForScan(session.scanId).first()
                    
                    textExportStatus.post { textExportStatus.text = "Exporting ${frames.size} frames..." }
                    progressExport.post { progressExport.max = frames.size + 2 } // +2 for cameras.txt and points3D.txt
                    
                    // Write cameras.txt
                    val camerasFile = File(sparseDir, "cameras.txt")
                    FileWriter(camerasFile).use { writer ->
                        writer.write("# Camera list with one line of data per camera:\n")
                        writer.write("# CAMERA_ID, MODEL, WIDTH, HEIGHT, PARAMS[]\n")
                        
                        // Use session's camera intrinsics or default values
                        val width = session.cameraIntrinsics?.width ?: 1920
                        val height = session.cameraIntrinsics?.height ?: 1080
                        val fx = session.cameraIntrinsics?.fx ?: 1080.0f
                        val fy = session.cameraIntrinsics?.fy ?: 1080.0f
                        val cx = session.cameraIntrinsics?.cx ?: (width / 2.0f)
                        val cy = session.cameraIntrinsics?.cy ?: (height / 2.0f)
                        
                        writer.write("1 PINHOLE $width $height $fx $fy $cx $cy\n")
                    }
                    progressExport.post { progressExport.progress = 1 }
                    
                    // Write images.txt
                    val imagesFile = File(sparseDir, "images.txt")
                    FileWriter(imagesFile).use { writer ->
                        writer.write("# Image list with two lines of data per image:\n")
                        writer.write("# IMAGE_ID, QW, QX, QY, QZ, TX, TY, TZ, CAMERA_ID, NAME\n")
                        writer.write("# POINTS2D[] as (X, Y, POINT3D_ID)\n")
                        
                        frames.forEachIndexed { index, frame ->
                            // Copy image to the images directory
                            val sourceImage = File(context.getExternalFilesDir(null), "${frame.frameId}.jpg")
                            if (sourceImage.exists()) {
                                val destImage = File(imagesDir, "${index+1}.jpg")
                                sourceImage.inputStream().use { input ->
                                    FileOutputStream(destImage).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                
                                // Extract rotation quaternion and translation from pose
                                val rotation = FloatArray(4)
                                val translation = FloatArray(3)
                                frame.localPose.getRotationQuaternion(rotation, 0)
                                frame.localPose.getTranslation(translation, 0)
                                
                                // Convert from ARCore coordinate system to COLMAP coordinate system
                                // ARCore: Y-up, COLMAP: Y-down
                                // We need to flip Y and Z
                                val qw = rotation[0]
                                val qx = rotation[1]
                                val qy = -rotation[2]  // Flip Y
                                val qz = -rotation[3]  // Flip Z
                                
                                val tx = translation[0]
                                val ty = -translation[1]  // Flip Y
                                val tz = -translation[2]  // Flip Z
                                
                                // Write image entry
                                writer.write("${index + 1} $qw $qx $qy $qz $tx $ty $tz 1 ${index+1}.jpg\n")
                                
                                // Empty points2D line (we don't have feature matches)
                                writer.write("\n")
                            }
                            
                            progressExport.post { progressExport.progress = 2 + index }
                            textExportStatus.post { textExportStatus.text = "Exported frame ${index + 1}/${frames.size}" }
                        }
                    }
                    
                    // Create an empty points3D.txt file
                    val pointsFile = File(sparseDir, "points3D.txt")
                    FileWriter(pointsFile).use { writer ->
                        writer.write("# 3D point list with one line of data per point:\n")
                        writer.write("# POINT3D_ID, X, Y, Z, R, G, B, ERROR, TRACK[] as (IMAGE_ID, POINT2D_IDX)\n")
                        // We don't have 3D points yet, Gaussian Splatting will generate them
                    }
                    
                    // Create a README file with instructions
                    val readmeFile = File(exportDir, "README.txt")
                    FileWriter(readmeFile).use { writer ->
                        writer.write("3D Gaussian Splatting Export\n")
                        writer.write("===========================\n\n")
                        writer.write("This data is exported in COLMAP format for use with 3D Gaussian Splatting.\n\n")
                        writer.write("To use this data with Gaussian Splatting:\n")
                        writer.write("1. Copy the 'colmap' directory to your computer\n")
                        writer.write("2. Run the training script:\n")
                        writer.write("   python train.py -s path/to/colmap/directory\n\n")
                        writer.write("For more information, visit: https://github.com/graphdeco-inria/gaussian-splatting\n")
                    }
                }
                
                textExportStatus.text = "Export complete!\nSaved to: ${exportDir.absolutePath}"
                Toast.makeText(context, "Export complete!", Toast.LENGTH_SHORT).show()

                // Show additional info for non-public directories
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && !Environment.isExternalStorageManager()) {
                    textExportStatus.text = "${textExportStatus.text}\n\nNote: Due to storage permissions, files were saved to app-specific storage. Use a file manager app to access them."
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                textExportStatus.text = "Export failed: ${e.message}"
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnExport.isEnabled = true
            }
        }
    }
} 