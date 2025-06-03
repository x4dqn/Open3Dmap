package com.openarmap.openarscanner.repository

import com.openarmap.openarscanner.data.model.User
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")

    suspend fun createOrUpdateUser(
        firebaseUid: String,
        email: String,
        displayName: String?,
        photoUrl: String?,
        provider: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val user = User(
                id = firebaseUid,
                firebaseUid = firebaseUid,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                provider = provider,
                lastLoginAt = System.currentTimeMillis()
            )
            
            usersCollection.document(firebaseUid).set(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserByFirebaseUid(firebaseUid: String): Result<User?> = withContext(Dispatchers.IO) {
        try {
            val doc = usersCollection.document(firebaseUid).get().await()
            val user = doc.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(firebaseUid: String, preferences: Map<String, Any>): Result<User> = withContext(Dispatchers.IO) {
        try {
            val docRef = usersCollection.document(firebaseUid)
            val doc = docRef.get().await()
            val user = doc.toObject(User::class.java) ?: return@withContext Result.failure(Exception("User not found"))
            
            val updatedUser = user.copy(preferences = preferences)
            docRef.set(updatedUser).await()
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 