package com.openarmap.openarscanner.ui

/**
 * ScanState represents the different states of an AR scanning operation.
 * 
 * This sealed class is used throughout the UI layer to track and respond to
 * the current state of AR scan operations. It follows the sealed class pattern
 * to ensure exhaustive handling of all possible states in when expressions.
 * 
 * The states represent the complete lifecycle of a scan operation from idle
 * through saving to completion or error handling.
 * 
 * Usage:
 * - UI components observe this state to show appropriate feedback
 * - ViewModels emit these states to update the UI
 * - Repository operations trigger state changes
 */
sealed class ScanState {
    /**
     * Idle state - no scanning operation is currently active.
     * 
     * This is the default state when:
     * - The app is first opened
     * - A scan operation completes successfully
     * - An error is dismissed and the user returns to normal operation
     */
    object Idle : ScanState()
    
    /**
     * Saving state - a scan is currently being processed and saved.
     * 
     * This state is active when:
     * - AR data is being processed after capture
     * - Files are being uploaded to Firebase Storage
     * - Metadata is being saved to Firestore
     * - The operation is in progress but not yet complete
     * 
     * UI should show loading indicators and disable user input during this state.
     */
    object Saving : ScanState()
    
    /**
     * Saved state - a scan operation completed successfully.
     * 
     * This state indicates:
     * - AR data was successfully processed
     * - Files were uploaded to Firebase Storage
     * - Metadata was saved to Firestore
     * - The operation completed without errors
     * 
     * UI can show success feedback and allow the user to start a new scan.
     */
    object Saved : ScanState()
    
    /**
     * Error state - a scan operation failed with an error.
     * 
     * This state contains the error message and is triggered when:
     * - File upload to Firebase Storage fails
     * - Firestore save operation fails
     * - Network connectivity issues occur
     * - Authentication problems prevent saving
     * - Any other unexpected error during the scan process
     * 
     * @property message Human-readable error message to display to the user
     */
    data class Error(val message: String) : ScanState()
} 