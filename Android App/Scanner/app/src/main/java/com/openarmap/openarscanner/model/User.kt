package com.openarmap.openarscanner.model

/**
 * User data model representing a user in the OpenARMap system.
 * 
 * This data class holds all user-related information including authentication details,
 * profile information, and user preferences. It's designed to work with Firebase Auth
 * and supports multiple authentication providers (email, Google, GitHub).
 * 
 * The class is immutable (data class) to ensure thread safety and follows the
 * repository pattern for data consistency across the application.
 * 
 * @property id Unique identifier for the user in the local database/Firestore
 * @property firebaseUid Firebase Authentication unique identifier
 * @property email User's email address (required for all auth providers)
 * @property displayName User's display name (can be null for email-only accounts)
 * @property photoUrl URL to the user's profile photo (nullable, typically from OAuth providers)
 * @property provider Authentication provider used ("email", "google", or "github")
 * @property createdAt Timestamp when the user account was created (Unix timestamp)
 * @property lastLoginAt Timestamp of the user's last login (Unix timestamp)
 * @property preferences User preferences stored as flexible key-value pairs
 */
data class User(
    /** Unique identifier for the user in the local database/Firestore */
    val id: String = "",
    
    /** Firebase Authentication unique identifier - primary key for Firebase operations */
    val firebaseUid: String = "",
    
    /** User's email address - required for all authentication providers */
    val email: String = "",
    
    /** User's display name - may be null for email-only accounts */
    val displayName: String? = null,
    
    /** URL to user's profile photo - typically provided by OAuth providers like Google */
    val photoUrl: String? = null,
    
    /** Authentication provider used: "email", "google", or "github" */
    val provider: String = "",
    
    /** Timestamp when the user account was created (Unix timestamp in milliseconds) */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Timestamp of the user's last login (Unix timestamp in milliseconds) */
    val lastLoginAt: Long = System.currentTimeMillis(),
    
    /** User preferences stored as flexible key-value pairs for app customization */
    val preferences: Map<String, Any> = emptyMap()
) 