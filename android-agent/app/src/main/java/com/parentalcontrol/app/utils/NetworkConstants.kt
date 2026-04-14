package com.parentalcontrol.app.utils

import com.parentalcontrol.app.BuildConfig

/**
 * Network Configuration Constants
 * 
 * Firebase Cloud Functions endpoints for device pairing and management
 */
object NetworkConstants {
    
    // Firebase Project ID: network-managment-92c59
    const val FIREBASE_PROJECT_ID = "network-managment-92c59"
    const val FIREBASE_REGION = "us-central1"
    
    // Pairing Endpoint (from build.gradle.kts)
    val PAIRING_ENDPOINT = BuildConfig.PAIRING_ENDPOINT
    
    // Request timeouts
    const val CONNECT_TIMEOUT_MS = 15000L
    const val READ_TIMEOUT_MS = 30000L
    const val WRITE_TIMEOUT_MS = 30000L
    
    // Retry configuration
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_BACKOFF_MS = 2000L
}
