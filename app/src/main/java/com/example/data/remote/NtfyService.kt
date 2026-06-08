package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object NtfyService {
    private const val TAG = "NtfyService"
    private const val BASE_URL = "https://ntfy.sh"

    // Configuration for real-time streaming: no read/write timeout to keep stream open indefinitely
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read timeout for long-polling
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Data class representing ntfy stream objects
    data class NtfyEvent(
        val id: String?,
        val time: Long?,
        val event: String, // e.g. "open", "keepalive", "message", "poll"
        val topic: String?,
        val message: String? // Containing our custom payload
    )

    private val eventAdapter = moshi.adapter(NtfyEvent::class.java)

    /**
     * Publishes a string payload to a specific ntfy topic.
     */
    suspend fun publish(topic: String, payload: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/$topic"
            val requestBody = payload.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to publish to $topic: HTTP ${response.code}")
                    return@withContext false
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing to $topic: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Subscribes to an ntfy topic in real-time, emitting message strings.
     * Retries automatically if the connection is interrupted.
     */
    fun subscribe(topic: String): Flow<String> = flow {
        val url = "$BASE_URL/$topic/json"
        
        while (currentCoroutineContext().isActive) {
            Log.d(TAG, "Attempting subscription connection to topic: $topic")
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            var response: okhttp3.Response? = null
            var reader: BufferedReader? = null

            try {
                response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Subscription connection failed for $topic: HTTP ${response.code}")
                    delay(5000) // Wait before retrying
                    continue
                }

                val source = response.body?.source()
                if (source == null) {
                    Log.e(TAG, "Subscription body is nul for topic: $topic")
                    delay(5000)
                    continue
                }

                reader = BufferedReader(InputStreamReader(source.inputStream()))
                Log.i(TAG, "Successfully subscribed in real-time to topic: $topic")

                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine()
                    if (line == null) {
                        Log.w(TAG, "Disconnected from streaming endpoint for topic: $topic. Reconnecting...")
                        break // Break inner loop to reconnect
                    }

                    if (line.isNotBlank()) {
                        try {
                            val event = eventAdapter.fromJson(line)
                            if (event != null && event.event == "message" && !event.message.isNullOrEmpty()) {
                                emit(event.message)
                            }
                        } catch (je: Exception) {
                            Log.e(TAG, "Error parsing event line: $line", je)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Active subscription error for topic $topic: ${e.message}. Retrying in 5s...", e)
            } finally {
                try {
                    reader?.close()
                } catch (ignored: Exception) {}
                try {
                    response?.close()
                } catch (ignored: Exception) {}
            }

            // Apply incremental or fixed delay before reconnecting
            delay(5000)
        }
    }.flowOn(Dispatchers.IO)
}
