package com.openarmap.openarscanner.data.model

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