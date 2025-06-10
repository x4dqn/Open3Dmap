package com.openarmap.openarscanner.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.openarmap.openarscanner.data.repository.AuthRepository

/**
 * AuthViewModelFactory creates AuthViewModel instances with proper dependency injection.
 * 
 * This factory class implements the ViewModelProvider.Factory interface to provide
 * custom ViewModel creation with dependency injection. It's necessary because the
 * AuthViewModel requires an AuthRepository parameter in its constructor, which
 * cannot be provided through the default ViewModel creation mechanism.
 * 
 * Key Responsibilities:
 * - Create AuthViewModel instances with proper dependencies
 * - Implement the Factory pattern for ViewModel creation
 * - Ensure proper dependency injection for authentication operations
 * - Provide type safety for ViewModel creation
 * 
 * Usage:
 * ```kotlin
 * val factory = AuthViewModelFactory(authRepository)
 * val viewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]
 * ```
 * 
 * Architecture Benefits:
 * - Enables dependency injection for ViewModels
 * - Maintains separation of concerns
 * - Allows for easy testing with mock repositories
 * - Follows Android's recommended ViewModel creation patterns
 * 
 * @param repository AuthRepository instance to inject into the AuthViewModel
 */
class AuthViewModelFactory(
    private val repository: AuthRepository
) : ViewModelProvider.Factory {
    
    /**
     * Creates a ViewModel instance of the specified class.
     * 
     * This method is called by the ViewModelProvider when a ViewModel
     * is requested. It checks if the requested class is AuthViewModel
     * and creates an instance with the injected AuthRepository.
     * 
     * @param modelClass The class of the ViewModel to create
     * @return A new instance of the requested ViewModel
     * @throws IllegalArgumentException if the requested ViewModel class is not supported
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 