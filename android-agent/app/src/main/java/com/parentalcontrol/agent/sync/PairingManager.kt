package com.parentalcontrol.agent.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.parentalcontrol.agent.data.store.LocalStateStore

/**
 * Pairing Manager - Handles the complete pairing flow
 * 
 * Usage Example:
 * ```
 * val pairingManager = PairingManager(context)
 * 
 * // When user enters pairing codes from admin dashboard:
 * lifecycleScope.launch {
 *     val result = pairingManager.pairDevice(
 *         tokenId = "token-id-from-dashboard",
 *         pairingSecret = "secret-code-from-dashboard"
 *     )
 *     
 *     result.onSuccess {
 *         // Pairing successful!
 *         // Device is now authenticated with Firebase
 *     }.onFailure { error ->
 *         // Handle error (show message to user)
 *     }
 * }
 * ```
 */
class PairingManager(private val context: Context) {
    
    private val pairingClient = PairingApiClient()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val stateStore = LocalStateStore(context)
    
    companion object {
        private const val TAG = "PairingManager"
    }
    
    /**
     * Pair device with admin dashboard
     * 
     * @param tokenId Token ID from admin dashboard
     * @param pairingSecret Secret code from admin dashboard
     * @param backupCode Optional backup code (use if secret is lost)
     * @param deviceName Custom name for this device
     */
    suspend fun pairDevice(
        tokenId: String,
        pairingSecret: String? = null,
        backupCode: String? = null,
        deviceName: String = android.os.Build.MODEL
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Starting pairing process...")
            
            // Step 1: Exchange pairing token with Firebase Cloud Function
            val pairingPayload = com.parentalcontrol.agent.data.model.PairingPayload(
                tokenId = tokenId,
                pairingSecret = pairingSecret ?: "",
                backupCode = backupCode,
                deviceName = deviceName
            )
            
            val pairingResult = pairingClient.exchangePairingCode(
                endpoint = "https://YOUR_REGION-YOUR_PROJECT.cloudfunctions.net/exchangePairingToken",
                payload = pairingPayload
            )
            
            Log.d(TAG, "Token exchanged successfully. Device ID: ${pairingResult.deviceId}")
            
            // Step 2: Sign in with Firebase custom token
            signInWithCustomToken(pairingResult.customToken)
            
            // Step 3: Save device ID locally
            stateStore.setPairing(pairingResult.deviceId)
            
            Log.d(TAG, "Pairing completed successfully!")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sign in to Firebase with custom token
     */
    private suspend fun signInWithCustomToken(customToken: String) {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            firebaseAuth.signInWithCustomToken(customToken)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Firebase authentication successful")
                        continuation.resumeWith(Result.success(Unit))
                    } else {
                        val error = task.exception ?: Exception("Authentication failed")
                        Log.e(TAG, "Firebase authentication failed: ${error.message}")
                        continuation.resumeWith(Result.failure(error))
                    }
                }
        }
    }
    
    /**
     * Check if device is already paired
     */
    suspend fun isDevicePaired(): Boolean {
        return firebaseAuth.currentUser != null && 
               stateStore.currentSnapshot().paired
    }
    
    /**
     * Get current device ID
     */
    suspend fun getDeviceId(): String {
        return stateStore.currentSnapshot().deviceId ?: ""
    }
    
    /**
     * Unpair device (sign out from Firebase)
     */
    suspend fun unpairDevice(): Result<Unit> {
        return try {
            Log.d(TAG, "Unpairing device...")
            
            // Sign out from Firebase
            firebaseAuth.signOut()
            
            // Clear local device ID
            stateStore.clearPairing()
            
            Log.d(TAG, "Device unpaired successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Unpair failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
