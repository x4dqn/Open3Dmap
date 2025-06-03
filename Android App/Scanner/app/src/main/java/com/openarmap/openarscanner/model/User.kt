package com.openarmap.openarscanner.model

data class User(
    val id: String = "",
    val firebaseUid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val provider: String = "", // "email", "google", or "github"
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val preferences: Map<String, Any> = emptyMap()
) 