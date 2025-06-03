package com.openarmap.openarscanner.ui

sealed class ScanState {
    object Idle : ScanState()
    object Saving : ScanState()
    object Saved : ScanState()
    data class Error(val message: String) : ScanState()
} 