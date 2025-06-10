package com.openarmap.openarscanner.repository

import com.openarmap.openarscanner.data.model.User
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * UserRepository handles all user-related data operations with Firebase Firestore.
 * 
 * This repository class provides a clean abstraction layer between the application's
 * business logic and Firebase Firestore for user data management. It follows the
 * repository pattern to centralize data access and provide consistent error handling.
 * 
 * Key Responsibilities:
 * - Create and update user profiles in Firestore
 * - Retrieve user data by Firebase UID
 * - Manage user preferences and settings
 * - Handle authentication state synchronization
 * - Provide offline-first data access patterns
 * 
 * Architecture:
 * - Uses Kotlin coroutines for asynchronous operations
 * - All operations run on Dispatchers.IO for optimal performance
 * - Returns Result<T> for consistent error handling
 * - Integrates with Firebase Authentication for user identity
 * 
 * Thread Safety:
 * - All suspend functions are thread-safe
 * - Uses withContext(Dispatchers.IO) for background operations
 * - Safe to call from any coroutine context
 */
class UserRepository {
    
    /** Firebase Firestore database instance */
    private val db = Firebase.firestore
    
    /** Reference to the users collection in Firestore */
    private val usersCollection = db.collection("users")

    /**
     * Creates a new user or updates an existing user in Firestore.
     * 
     * This method handles both user creation (first-time login) and user updates
     * (subsequent logins with potentially changed profile information).
     * 
     * The Firebase UID is used as the document ID to ensure uniqueness and
     * efficient lookups. The lastLoginAt timestamp is automatically updated
     * to track user activity.
     * 
     * @param firebaseUid Firebase Authentication unique identifier
     * @param email User's email address from authentication provider
     * @param displayName User's display name (nullable for email-only accounts)
     * @param photoUrl URL to user's profile photo (nullable)
     * @param provider Authentication provider ("email", "google", "github")
     * @return Result<User> containing the created/updated user or error
     */
    suspend fun createOrUpdateUser(
        firebaseUid: String,
        email: String,
        displayName: String?,
        photoUrl: String?,
        provider: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            // Create user object with current timestamp
            val user = User(
                id = firebaseUid,
                firebaseUid = firebaseUid,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                provider = provider,
                lastLoginAt = System.currentTimeMillis()
            )
            
            // Save to Firestore using Firebase UID as document ID
            usersCollection.document(firebaseUid).set(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieves a user by their Firebase UID.
     * 
     * This method fetches user data from Firestore using the Firebase UID as
     * the document identifier. It's the primary method for loading user profiles
     * throughout the application.
     * 
     * @param firebaseUid Firebase Authentication unique identifier
     * @return Result<User?> containing the user data (null if not found) or error
     */
    suspend fun getUserByFirebaseUid(firebaseUid: String): Result<User?> = withContext(Dispatchers.IO) {
        try {
            // Fetch document from Firestore
            val doc = usersCollection.document(firebaseUid).get().await()
            
            // Convert Firestore document to User object
            val user = doc.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates user preferences in Firestore.
     * 
     * This method allows updating user preferences while preserving all other
     * user data. It first fetches the current user data, updates only the
     * preferences field, and saves the updated user back to Firestore.
     * 
     * User preferences are stored as a flexible Map<String, Any> to accommodate
     * various types of settings and future extensibility.
     * 
     * @param firebaseUid Firebase Authentication unique identifier
     * @param preferences Map of preference keys and values to update
     * @return Result<User> containing the updated user data or error
     */
    suspend fun updateUserPreferences(firebaseUid: String, preferences: Map<String, Any>): Result<User> = withContext(Dispatchers.IO) {
        try {
            val docRef = usersCollection.document(firebaseUid)
            
            // Fetch current user data
            val doc = docRef.get().await()
            val user = doc.toObject(User::class.java) 
                ?: return@withContext Result.failure(Exception("User not found"))
            
            // Update only the preferences field
            val updatedUser = user.copy(preferences = preferences)
            
            // Save updated user to Firestore
            docRef.set(updatedUser).await()
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 