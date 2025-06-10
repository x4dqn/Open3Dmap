package com.openarmap.openarscanner.auth

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuthRepository handles all authentication operations with Firebase Authentication.
 * 
 * This repository provides a clean abstraction layer for authentication operations,
 * supporting multiple authentication providers including email/password, Google,
 * and GitHub. It follows the repository pattern to centralize authentication logic
 * and provide consistent error handling across the application.
 * 
 * Supported Authentication Providers:
 * - Email/Password: Traditional email-based authentication
 * - Google Sign-In: OAuth2 integration with Google accounts
 * - GitHub: OAuth2 integration with GitHub accounts
 * 
 * Key Features:
 * - Asynchronous operations using Kotlin coroutines
 * - Consistent error handling with Result<T> pattern
 * - Thread-safe operations with proper dispatcher usage
 * - Integration with Firebase Authentication service
 * - Support for both sign-in and sign-up flows
 * 
 * Security Considerations:
 * - All authentication tokens are handled by Firebase SDK
 * - No sensitive credentials are stored locally
 * - OAuth flows follow industry best practices
 * - Firebase handles session management and token refresh
 */
class AuthRepository {
    
    /** Firebase Authentication instance for all auth operations */
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Signs in a user with email and password.
     * 
     * This method authenticates users using their email address and password
     * through Firebase Authentication. It's the primary sign-in method for
     * users who registered with email/password.
     * 
     * @param email User's email address
     * @param password User's password
     * @return Result<FirebaseUser> containing authenticated user or error
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a new user account with email and password.
     * 
     * This method registers a new user in Firebase Authentication using their
     * email address and password. After successful registration, the user is
     * automatically signed in.
     * 
     * @param email User's email address (must be valid and unique)
     * @param password User's password (must meet Firebase security requirements)
     * @return Result<FirebaseUser> containing newly created user or error
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in a user with Google Sign-In.
     * 
     * This method completes the Google OAuth2 authentication flow by exchanging
     * the Google Sign-In account for a Firebase authentication credential.
     * The GoogleSignInAccount should be obtained from Google Sign-In SDK.
     * 
     * @param account GoogleSignInAccount from Google Sign-In flow
     * @return Result<FirebaseUser> containing authenticated user or error
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            // Convert Google ID token to Firebase credential
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in a user with GitHub authentication.
     * 
     * This method authenticates users using GitHub OAuth2 flow. The GitHub
     * access token should be obtained through the GitHub OAuth2 process
     * (typically through a web view or external browser).
     * 
     * @param token GitHub OAuth2 access token
     * @return Result<FirebaseUser> containing authenticated user or error
     */
    suspend fun signInWithGithub(token: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            // Convert GitHub token to Firebase credential
            val credential = com.google.firebase.auth.GithubAuthProvider.getCredential(token)
            val result = firebaseAuth.signInWithCredential(credential).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets the currently authenticated user.
     * 
     * This method returns the currently signed-in Firebase user, or null if
     * no user is authenticated. It's synchronous and safe to call from any thread.
     * 
     * Use this method to:
     * - Check if a user is currently signed in
     * - Get user information for authenticated operations
     * - Determine authentication state in UI components
     * 
     * @return FirebaseUser if authenticated, null otherwise
     */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Signs out the current user.
     * 
     * This method signs out the currently authenticated user from Firebase
     * Authentication. After sign-out, getCurrentUser() will return null.
     * 
     * The operation clears all authentication state and tokens, requiring
     * the user to sign in again to access protected resources.
     * 
     * @return Result<Unit> indicating success or failure of sign-out operation
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 