package com.openarmap.openarscanner.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openarmap.openarscanner.data.model.User
import com.openarmap.openarscanner.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AuthViewModel manages authentication state and operations for the OpenARMap application.
 * 
 * This ViewModel follows the MVVM architecture pattern and handles all authentication-related
 * business logic including sign-in, sign-up, password reset, and user session management.
 * It provides a reactive interface using StateFlow for UI components to observe authentication
 * state changes.
 * 
 * Key Responsibilities:
 * - Manage authentication state throughout the app lifecycle
 * - Handle multiple authentication providers (email, Google, GitHub)
 * - Provide user session management and persistence
 * - Coordinate with AuthRepository for authentication operations
 * - Emit appropriate states for UI components to react to
 * - Handle authentication errors with user-friendly messages
 * 
 * Architecture Features:
 * - Uses StateFlow for reactive state management
 * - Leverages Kotlin coroutines for asynchronous operations
 * - Implements proper error handling and loading states
 * - Maintains separation between UI and business logic
 * - Provides automatic user session restoration
 * 
 * State Management:
 * - authState: Current authentication state (loading, authenticated, error, etc.)
 * - currentUser: Currently authenticated user data (null if not authenticated)
 * 
 * @param authRepository Repository for authentication operations
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    /** Private mutable StateFlow for authentication state */
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    
    /** Public read-only StateFlow for authentication state - observed by UI */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Private mutable StateFlow for current user data */
    private val _currentUser = MutableStateFlow<User?>(null)
    
    /** Public read-only StateFlow for current user data - observed by UI */
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /**
     * Initializes the ViewModel and sets up user session monitoring.
     * 
     * This initialization block observes the authentication repository's
     * current user flow and automatically updates the authentication state
     * when the user session changes (login, logout, session restoration).
     */
    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _currentUser.value = user
                _authState.value = if (user != null) AuthState.Authenticated(user) else AuthState.Unauthenticated
            }
        }
    }

    /**
     * Signs in a user with email and password.
     * 
     * This method handles traditional email/password authentication through
     * Firebase Authentication. It updates the authentication state to show
     * loading during the operation and handles both success and error cases.
     * 
     * @param email User's email address
     * @param password User's password
     */
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.signInWithEmail(email, password)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Authentication failed")
                }
        }
    }

    /**
     * Creates a new user account with email, password, and display name.
     * 
     * This method handles user registration through Firebase Authentication.
     * After successful registration, the user is automatically signed in.
     * 
     * @param email User's email address (must be valid and unique)
     * @param password User's password (must meet Firebase security requirements)
     * @param displayName User's display name for their profile
     */
    fun signUpWithEmail(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.signUpWithEmail(email, password, displayName)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Registration failed")
                }
        }
    }

    /**
     * Signs in a user with Google OAuth2.
     * 
     * This method completes the Google Sign-In flow by exchanging the
     * Google ID token for a Firebase authentication session. The ID token
     * should be obtained from the Google Sign-In SDK.
     * 
     * @param idToken Google ID token from Google Sign-In flow
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Google sign-in failed")
                }
        }
    }

    /**
     * Signs out the current user.
     * 
     * This method clears the user session and authentication state.
     * After sign-out, the authState will automatically update to
     * Unauthenticated through the init block's observer.
     */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    /**
     * Sends a password reset email to the specified email address.
     * 
     * This method initiates the password reset flow through Firebase
     * Authentication. If successful, the user will receive an email
     * with instructions to reset their password.
     * 
     * @param email Email address to send the password reset link to
     */
    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.resetPassword(email)
                .onSuccess {
                    _authState.value = AuthState.PasswordResetSent
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Password reset failed")
                }
        }
    }
}

/**
 * AuthState represents the different states of the authentication system.
 * 
 * This sealed class is used throughout the authentication flow to track
 * and respond to different authentication states. It follows the sealed
 * class pattern to ensure exhaustive handling of all possible states.
 * 
 * The states represent the complete authentication lifecycle from initial
 * app launch through various authentication operations to final authenticated
 * or error states.
 * 
 * Usage:
 * - UI components observe this state to show appropriate screens/feedback
 * - AuthViewModel emits these states based on authentication operations
 * - Repository operations trigger state changes through the ViewModel
 */
sealed class AuthState {
    /**
     * Initial state when the app starts.
     * 
     * This is the default state before any authentication operations
     * have been attempted or before user session restoration completes.
     */
    object Initial : AuthState()
    
    /**
     * Loading state during authentication operations.
     * 
     * This state is active when:
     * - Sign-in operation is in progress
     * - Sign-up operation is in progress
     * - Password reset email is being sent
     * - Any other authentication operation is processing
     * 
     * UI should show loading indicators during this state.
     */
    object Loading : AuthState()
    
    /**
     * Unauthenticated state when no user is signed in.
     * 
     * This state indicates:
     * - No user session exists
     * - User has signed out
     * - Authentication session has expired
     * - App should show login/registration screens
     */
    object Unauthenticated : AuthState()
    
    /**
     * Authenticated state when a user is successfully signed in.
     * 
     * This state contains the authenticated user's data and indicates:
     * - User has successfully signed in
     * - User session is active and valid
     * - App should show main application screens
     * - User data is available for app operations
     * 
     * @property user The authenticated user's data
     */
    data class Authenticated(val user: User) : AuthState()
    
    /**
     * Error state when an authentication operation fails.
     * 
     * This state contains error information and is triggered when:
     * - Sign-in fails (wrong credentials, network issues, etc.)
     * - Sign-up fails (email already exists, weak password, etc.)
     * - Password reset fails (invalid email, network issues, etc.)
     * - Any other authentication error occurs
     * 
     * @property message Human-readable error message to display to the user
     */
    data class Error(val message: String) : AuthState()
    
    /**
     * Password reset email sent state.
     * 
     * This state indicates that a password reset email has been
     * successfully sent to the user's email address. The user should
     * check their email and follow the reset instructions.
     */
    object PasswordResetSent : AuthState()
} 