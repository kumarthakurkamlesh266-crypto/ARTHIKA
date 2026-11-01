package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.ai.GeminiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ApiKeyStatus {
    object Idle : ApiKeyStatus()
    object Checking : ApiKeyStatus()

    data class Valid(
        val message: String = "Connected successfully to Gemini 3.8 Flash!",
        val model: String = GeminiConfig.DEFAULT_MODEL,
        val latencyMs: Long = 0
    ) : ApiKeyStatus()

    sealed class Invalid(
        open val error: String,
        open val actionableTip: String = ""
    ) : ApiKeyStatus() {
        data class BadKey(
            override val error: String = "Invalid API Key: Rejected by Google Gemini.",
            override val actionableTip: String = "Verify that you copied the complete key starting with 'AIzaSy...' from Google AI Studio."
        ) : Invalid(error, actionableTip)

        data class QuotaExceeded(
            override val error: String = "Quota Limit Reached: Rate limit or daily token quota exceeded.",
            override val actionableTip: String = "Wait 60 seconds before retrying or check your quota at https://aistudio.google.com."
        ) : Invalid(error, actionableTip)

        data class ModelUnsupported(
            override val error: String = "Model Unsupported: The selected model is unavailable for this key/region.",
            override val actionableTip: String = "Switch to 'gemini-3.8-flash' or another supported model in the settings."
        ) : Invalid(error, actionableTip)

        data class NetworkError(
            override val error: String = "Network Connection Error: Unable to reach Google servers.",
            override val actionableTip: String = "Check your device's Wi-Fi or mobile data connection and try again."
        ) : Invalid(error, actionableTip)

        data class GenericError(
            override val error: String,
            override val actionableTip: String = "Please verify your API key and network settings."
        ) : Invalid(error, actionableTip)
    }
}

class ApiKeyManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("arthika_secure_api_settings", Context.MODE_PRIVATE)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun getApiKey(): String {
        val userKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        if (userKey.isNotBlank()) {
            return userKey.trim()
        }
        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNullOrBlank() || buildKey == "MY_GEMINI_API_KEY") "" else buildKey.trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun getUserSavedApiKey(): String {
        return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun hasKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun getSelectedModel(): String {
        return GeminiConfig.getModel(context)
    }

    fun setSelectedModel(model: String) {
        GeminiConfig.setModel(context, model)
    }

    suspend fun testApiKey(apiKey: String, modelOverride: String? = null): ApiKeyStatus = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            return@withContext ApiKeyStatus.Invalid.BadKey(
                error = "API Key cannot be blank.",
                actionableTip = "Enter your Google Gemini API key to test the connection."
            )
        }

        val model = modelOverride?.trim()?.ifBlank { null } ?: GeminiConfig.getModel(context)
        val startTime = System.currentTimeMillis()

        try {
            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Ping. Respond with 'OK'.")
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
            }

            val requestUrl = GeminiConfig.getGenerateContentUrl(key, model)
            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime
            val responseCode = response.code
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = try { JSONObject(responseBody) } catch (e: Exception) { null }
                val candidates = json?.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    ApiKeyStatus.Valid(
                        message = "Connected successfully to $model",
                        model = model,
                        latencyMs = latencyMs
                    )
                } else {
                    ApiKeyStatus.Invalid.GenericError(
                        error = "Received empty response from Gemini API.",
                        actionableTip = "Google servers returned HTTP 200 without candidate content. Check API permissions."
                    )
                }
            } else {
                val errorObj = try {
                    val root = JSONObject(responseBody)
                    root.optJSONObject("error")
                } catch (e: Exception) {
                    null
                }
                val rawMessage = errorObj?.optString("message", "") ?: ""
                val status = errorObj?.optString("status", "") ?: ""

                when {
                    responseCode == 400 || status == "INVALID_ARGUMENT" ||
                    rawMessage.contains("API key not valid", ignoreCase = true) ||
                    rawMessage.contains("API_KEY_INVALID", ignoreCase = true) -> {
                        ApiKeyStatus.Invalid.BadKey(
                            error = "Invalid API Key: Rejected by Google Gemini (HTTP 400).",
                            actionableTip = "Ensure you copied the entire key starting with 'AIzaSy...' without extra spaces."
                        )
                    }
                    responseCode == 403 || status == "PERMISSION_DENIED" -> {
                        ApiKeyStatus.Invalid.BadKey(
                            error = "Permission Denied: API key blocked or missing access (HTTP 403).",
                            actionableTip = "Verify that Generative Language API is enabled in your Google Cloud / AI Studio project."
                        )
                    }
                    responseCode == 404 || status == "NOT_FOUND" || rawMessage.contains("not found", ignoreCase = true) -> {
                        ApiKeyStatus.Invalid.ModelUnsupported(
                            error = "Model '$model' Not Found: Model unavailable for this key/region (HTTP 404).",
                            actionableTip = "Switch back to 'gemini-3.8-flash' or another supported model in settings."
                        )
                    }
                    responseCode == 429 || status == "RESOURCE_EXHAUSTED" ||
                    rawMessage.contains("quota", ignoreCase = true) ||
                    rawMessage.contains("rate limit", ignoreCase = true) -> {
                        ApiKeyStatus.Invalid.QuotaExceeded(
                            error = "Quota Exceeded: Rate limit or daily token quota reached (HTTP 429).",
                            actionableTip = "Wait 60 seconds before making another request, or check your quota at https://aistudio.google.com."
                        )
                    }
                    responseCode in 500..599 -> {
                        ApiKeyStatus.Invalid.GenericError(
                            error = "Google Gemini Service Unavailable (HTTP $responseCode).",
                            actionableTip = "Google's AI servers are temporarily experiencing downtime. Please retry in a few moments."
                        )
                    }
                    else -> {
                        val displayError = rawMessage.ifBlank { "HTTP $responseCode unexpected response" }
                        ApiKeyStatus.Invalid.GenericError(
                            error = "Connection Failed: $displayError",
                            actionableTip = "Please verify your API key and network settings."
                        )
                    }
                }
            }
        } catch (e: java.net.UnknownHostException) {
            ApiKeyStatus.Invalid.NetworkError(
                error = "Network Error: Could not resolve Google Gemini servers.",
                actionableTip = "Check your device's Wi-Fi or mobile data connection and try again."
            )
        } catch (e: java.net.SocketTimeoutException) {
            ApiKeyStatus.Invalid.NetworkError(
                error = "Network Timeout: Google Gemini servers did not respond in time.",
                actionableTip = "Your connection may be slow or unstable. Try again in a moment."
            )
        } catch (e: java.io.IOException) {
            ApiKeyStatus.Invalid.NetworkError(
                error = "Network Error: ${e.message ?: "Failed to connect to Google servers"}.",
                actionableTip = "Check your internet connection and firewall settings."
            )
        } catch (e: Exception) {
            ApiKeyStatus.Invalid.GenericError(
                error = "Unexpected error: ${e.message ?: "Unknown issue"}",
                actionableTip = "Please check your settings and retry."
            )
        }
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
