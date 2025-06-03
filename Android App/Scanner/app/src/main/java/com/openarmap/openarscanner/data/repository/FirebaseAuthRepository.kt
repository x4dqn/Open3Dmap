package com.openarmap.openarscanner.data.repository

import com.openarmap.openarscanner.data.model.AuthProvider
import com.openarmap.openarscanner.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository : AuthRepository {
    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<User> = try {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val user = result.user?.toUser() ?: throw IllegalStateException("User is null")
        saveUserToFirestore(user)
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user?.toUser()?.copy(displayName = displayName) ?: throw IllegalStateException("User is null")
        saveUserToFirestore(user)
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user?.toUser() ?: throw IllegalStateException("User is null")
        saveUserToFirestore(user)
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signInWithGithub(token: String): Result<User> = try {
        val credential = com.google.firebase.auth.GithubAuthProvider.getCredential(token)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user?.toUser() ?: throw IllegalStateException("User is null")
        saveUserToFirestore(user)
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun resetPassword(email: String): Result<Unit> = try {
        auth.sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateUserProfile(displayName: String?, photoUrl: String?): Result<User> = try {
        val updates = UserProfileChangeRequest.Builder().apply {
            displayName?.let { setDisplayName(it) }
            photoUrl?.let { setPhotoUri(android.net.Uri.parse(it)) }
        }.build()
        
        auth.currentUser?.updateProfile(updates)?.await()
        val user = auth.currentUser?.toUser() ?: throw IllegalStateException("User is null")
        saveUserToFirestore(user)
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun saveUserToFirestore(user: User) {
        usersCollection.document(user.id).set(user).await()
    }

    private fun com.google.firebase.auth.FirebaseUser.toUser(): User {
        return User(
            id = uid,
            firebaseUid = uid,
            email = email ?: "",
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            provider = when (providerData.firstOrNull()?.providerId) {
                GoogleAuthProvider.PROVIDER_ID -> AuthProvider.GOOGLE.toString()
                else -> AuthProvider.EMAIL.toString()
            },
            createdAt = metadata?.creationTimestamp ?: System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
    }
} 