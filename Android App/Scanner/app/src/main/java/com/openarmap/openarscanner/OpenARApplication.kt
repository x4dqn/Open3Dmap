package com.openarmap.openarscanner

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.storage.FirebaseStorage
import com.openarmap.openarscanner.repository.ARScanRepository
import com.openarmap.openarscanner.repository.UserRepository

class Open3DApplication : Application() {
    private lateinit var userRepository: UserRepository
    private lateinit var arScanRepository: ARScanRepository

    companion object {
        private const val TAG = "Open3DApplication"
        private var instance: Open3DApplication? = null

        fun getInstance(): Open3DApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            
            // Initialize Firebase App Check
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            
            Log.d(TAG, "Firebase App Check initialized successfully")
            
            // Verify Firebase Storage configuration
            try {
                val storage = FirebaseStorage.getInstance("gs://openarmap.firebasestorage.app")
                val storageRef = storage.reference
                Log.d(TAG, "Firebase Storage initialized successfully")
                Log.d(TAG, "Storage bucket: ${storage.app.options.storageBucket}")
                Log.d(TAG, "Storage reference: ${storageRef.bucket}")
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Storage initialization failed", e)
                // Try with default storage as fallback
                try {
                    val defaultStorage = FirebaseStorage.getInstance()
                    Log.w(TAG, "Using default Firebase Storage instance")
                    Log.w(TAG, "Default storage bucket: ${defaultStorage.app.options.storageBucket}")
                } catch (e2: Exception) {
                    Log.e(TAG, "Default Firebase Storage also failed", e2)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
        
        // Initialize repositories
        userRepository = UserRepository()
        arScanRepository = ARScanRepository()
    }
} 