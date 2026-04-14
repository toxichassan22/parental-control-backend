package com.parentalcontrol.app.network

import com.parentalcontrol.app.utils.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pairing Client for communicating with Firebase Cloud Functions
 * 
 * Handles device pairing flow:
 * 1. Admin creates pairing token (from web dashboard)
 * 2. Android app exchanges token for device credentials
 * 3. App receives deviceId + Firebase custom token
 */
class PairingClient {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(NetworkConstants.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(NetworkConstants.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(NetworkConstants.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    
    /**
     * Exchange pairing token for device credentials
     * 
     * @param tokenId The pairing token ID (from admin dashboard)
     * @param pairingSecret The secret code (from admin dashboard)
     * @param backupCode Optional backup code (if pairing secret is lost)
     * @param deviceName Name for this device
     * @return PairingResult with deviceId and customToken
     */
    suspend fun exchangePairingToken(
        tokenId: String,
        pairingSecret: String? = null,
        backupCode: String? = null,
        deviceName: String = "Managed Device",
        appVersion: String = "1.0.0",
        model: String = android.os.Build.MODEL,
        manufacturer: String = android.os.Build.MANUFACTURER
    ): Result<PairingResult> = withContext(Dispatchers.IO) {
        try {
            // Build request body
            val jsonBody = JSONObject().apply {
                put("tokenId", tokenId)
                put("deviceName", deviceName)
                put("appVersion", appVersion)
                put("model", model)
                put("manufacturer", manufacturer)
                
                // Either pairingSecret OR backupCode is required
                pairingSecret?.let { put("pairingSecret", it) }
                backupCode?.let { put("backupCode", it) }
            }
            
            val requestBody = jsonBody.toString()
                .toMediaType("application/json; charset=utf-8".toMediaType())
            
            // Create HTTP request
            val request = Request.Builder()
                .url(NetworkConstants.PAIRING_ENDPOINT)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            // Execute request
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                val error = parseErrorResponse(responseBody)
                return@withContext Result.failure(PairingException(error))
            }
            
            // Parse success response
            val resultJson = JSONObject(responseBody ?: "")
            val deviceId = resultJson.optString("deviceId", "")
            val customToken = resultJson.optString("customToken", "")
            
            if (deviceId.isEmpty() || customToken.isEmpty()) {
                return@withContext Result.failure(
                    PairingException("Invalid response from server")
                )
            }
            
            Result.success(PairingResult(deviceId, customToken))
            
        } catch (e: Exception) {
            Result.failure(PairingException("Network error: ${e.message}", e))
        }
    }
    
    /**
     * Parse error response from server
     */
    private fun parseErrorResponse(responseBody: String?): String {
        return try {
            val json = JSONObject(responseBody ?: "")
            json.optString("error", "Unknown error occurred")
        } catch (e: Exception) {
            responseBody ?: "Network request failed"
        }
    }
}

/**
 * Result of successful pairing
 */
data class PairingResult(
    val deviceId: String,
    val customToken: String
)

/**
 * Custom exception for pairing errors
 */
class PairingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
