package com.parentalcontrol.agent.sync

import com.parentalcontrol.agent.data.model.PairingPayload
import com.parentalcontrol.agent.data.model.PairingResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class PairingApiClient {
    fun exchangePairingCode(endpoint: String, payload: PairingPayload): PairingResult {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doInput = true
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val requestBody = Json.encodeToString(payload)

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(requestBody.toString())
        }

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val responseBody = BufferedReader(stream.reader()).use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Pairing failed: $responseBody")
        }

        val jsonElement = Json.parseToJsonElement(responseBody)
        val json = jsonElement as JsonObject
        return PairingResult(
            deviceId = (json["deviceId"] as? JsonPrimitive)?.content ?: "",
            customToken = (json["customToken"] as? JsonPrimitive)?.content ?: "",
        )
    }
}

