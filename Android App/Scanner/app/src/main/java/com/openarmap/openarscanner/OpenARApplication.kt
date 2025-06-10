package com.openarmap.openarscanner

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.storage.FirebaseStorage
import com.openarmap.openarscanner.repository.ARScanRepository
import com.openarmap.openarscanner.repository.UserRepository

/**
 * OpenARApplication is the main application class for the OpenARScanner app.
 * 
 * This class extends Android's Application class and serves as the entry point for the entire
 * application lifecycle. It's responsible for initializing critical components that need to
 * be available throughout the app's lifetime, including Firebase services and data repositories.
 * 
 * The class follows the singleton pattern to ensure only one instance exists throughout the
 * application lifecycle, which is enforced by the Android system.
 */

class Open3DApplication : Application() {
    
    /**
     * Repository for managing user-related data operations.
     * Initialized lazily in onCreate() to ensure proper application context.
     */
    private lateinit var userRepository: UserRepository
    
    /**
     * Repository for managing AR scan data operations.
     * Handles storage and retrieval of 3D scan data to/from Firebase.
     */
    private lateinit var arScanRepository: ARScanRepository

    companion object {
        /**
         * Logging tag for this class - used for debugging and error tracking.
         */
        private const val TAG = "Open3DApplication"
        
        /**
         * Singleton instance of the application.
         * Nullable to handle the case where the application hasn't been initialized yet.
         */
        private var instance: Open3DApplication? = null

        /**
         * Provides global access to the application instance.
         * 
         * @return The singleton instance of Open3DApplication
         * @throws IllegalStateException if called before the application is initialized
         */
        fun getInstance(): Open3DApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    /**
     * Called when the application is starting, before any activity, service, or receiver objects
     * (excluding content providers) have been created.
     * 
     * This method is responsible for:
     * - Setting up the singleton instance
     * - Initializing Firebase services (App, App Check, Storage)
     * - Initializing data repositories
     * - Setting up error handling and logging
     * 
     * Note: This method should complete quickly as it blocks the main UI thread.
     * Heavy initialization should be moved to background threads if needed.
     */
    override fun onCreate() {
        super.onCreate()
        
        // Set up singleton instance for global access
        instance = this
        
        // Initialize Firebase services with comprehensive error handling
        initializeFirebaseServices()
        
        // Initialize data repositories after Firebase is ready
        initializeRepositories()
    }
    
    /**
     * Initializes all Firebase services required by the application.
     * 
     * This includes:
     * - Firebase App: Core Firebase functionality
     * - Firebase App Check: Security verification to prevent abuse
     * - Firebase Storage: Cloud storage for AR scan data
     * 
     * The method includes fallback mechanisms and detailed logging for troubleshooting.
     */
    private fun initializeFirebaseServices() {
        try {
            // Initialize the core Firebase application
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase App initialized successfully")
            
            // Initialize Firebase App Check for security
            // This helps protect against abuse by verifying that requests come from your authentic app
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "Firebase App Check initialized successfully")
            
            // Initialize Firebase Storage with specific bucket configuration
            initializeFirebaseStorage()
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error initializing Firebase services", e)
            // Note: Application continues to run even if Firebase fails
            // This allows for offline functionality or manual configuration
        }
    }
    
    /**
     * Initializes Firebase Storage with fallback mechanisms.
     * 
     * Attempts to use the specific OpenARMap storage bucket first,
     * then falls back to the default bucket if the primary fails.
     * This ensures the app can function even with configuration issues.
     */
    private fun initializeFirebaseStorage() {
        try {
            // Primary storage bucket for OpenARMap project
            val storage = FirebaseStorage.getInstance("gs://openarmap.firebasestorage.app")
            val storageRef = storage.reference
            
            Log.d(TAG, "Firebase Storage initialized successfully")
            Log.d(TAG, "Storage bucket: ${storage.app.options.storageBucket}")
            Log.d(TAG, "Storage reference: ${storageRef.bucket}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Primary Firebase Storage initialization failed, trying fallback", e)
            
            // Fallback to default storage instance
            try {
                val defaultStorage = FirebaseStorage.getInstance()
                Log.w(TAG, "Using default Firebase Storage instance as fallback")
                Log.w(TAG, "Default storage bucket: ${defaultStorage.app.options.storageBucket}")
                
            } catch (e2: Exception) {
                Log.e(TAG, "All Firebase Storage initialization attempts failed", e2)
                // Application continues without storage - users may see offline mode
            }
        }
    }
    
    /**
     * Initializes the data repositories used throughout the application.
     * 
     * These repositories handle:
     * - UserRepository: User authentication, profiles, and preferences
     * - ARScanRepository: 3D scan data storage, retrieval, and management
     * 
     * Repositories are initialized after Firebase to ensure proper backend connectivity.
     */
    private fun initializeRepositories() {
        try {
            userRepository = UserRepository()
            arScanRepository = ARScanRepository()
            Log.d(TAG, "Data repositories initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing data repositories", e)
            // This is a critical error - app may not function properly without repositories
        }
    }
} 