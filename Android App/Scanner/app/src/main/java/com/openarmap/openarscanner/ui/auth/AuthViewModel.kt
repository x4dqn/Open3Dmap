package com.openarmap.openarscanner.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openarmap.openarscanner.data.model.User
import com.openarmap.openarscanner.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _currentUser.value = user
                _authState.value = if (user != null) AuthState.Authenticated(user) else AuthState.Unauthenticated
            }
        }
    }

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

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

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

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
    object PasswordResetSent : AuthState()
} 