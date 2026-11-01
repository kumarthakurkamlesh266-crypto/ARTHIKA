package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
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
    data class Valid(val message: String = "API Key is valid and active") : ApiKeyStatus()
    data class Invalid(val error: String) : ApiKeyStatus()
}

class ApiKeyManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("arthika_api_settings", Context.MODE_PRIVATE)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
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

    suspend fun testApiKey(apiKey: String): ApiKeyStatus = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            return@withContext ApiKeyStatus.Invalid("API Key cannot be blank")
        }

        try {
            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Ping. Reply with 'OK'.")
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                ApiKeyStatus.Valid("Connected successfully to Gemini 2.5 Flash!")
            } else {
                val errBody = response.body?.string() ?: ""
                val msg = try {
                    val root = JSONObject(errBody)
                    val errorObj = root.optJSONObject("error")
                    errorObj?.optString("message", "HTTP ${response.code}") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: Check your key permissions"
                }
                ApiKeyStatus.Invalid(msg)
            }
        } catch (e: Exception) {
            ApiKeyStatus.Invalid(e.localizedMessage ?: "Network connection error")
        }
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
