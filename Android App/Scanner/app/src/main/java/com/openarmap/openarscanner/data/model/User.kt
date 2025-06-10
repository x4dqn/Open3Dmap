package com.openarmap.openarscanner.data.model

//Data class for User
//Fields: id, firebaseUid, email, displayName, photoUrl, provider, createdAt, lastLoginAt, preferences
//id: Unique identifier for the user
//firebaseUid: Firebase UID for the user
//email: Email address of the user
//displayName: Display name of the user
//photoUrl: URL of the user's profile picture
//provider: Provider of the user (e.g. email, google)
//createdAt: Timestamp of when the user was created
data class User(
    val id: String = "",
    val firebaseUid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val provider: String = AuthProvider.EMAIL.toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val preferences: Map<String, Any> = mapOf()
)