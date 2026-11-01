package com.example.ai

import android.content.Context

/**
 * Centralized Gemini AI model configuration.
 * Configures the active model ID across all AI features (AI Assistant, OCR Bill Scanning,
 * and Financial Analytics) and provides endpoint URL generation.
 */
object GeminiConfig {
    /**
     * Primary active model ID throughout ARTHIKA.
     */
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    /**
     * Supported Gemini models available for selection.
     */
    val AVAILABLE_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    )

    private const val PREFS_NAME = "arthika_gemini_config"
    private const val KEY_SELECTED_MODEL = "selected_gemini_model"

    @Volatile
    private var cachedModel: String? = null

    /**
     * Retrieves the configured Gemini model ID.
     */
    fun getModel(context: Context? = null): String {
        cachedModel?.let { return it }
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
            cachedModel = saved
            return saved
        }
        return DEFAULT_MODEL
    }

    /**
     * Updates the configured Gemini model ID.
     */
    fun setModel(context: Context, model: String) {
        val cleanModel = model.trim().ifBlank { DEFAULT_MODEL }
        cachedModel = cleanModel
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_MODEL, cleanModel).apply()
    }

    /**
     * Resets the model ID cache (useful for testing or hot updates).
     */
    fun clearCache() {
        cachedModel = null
    }

    /**
     * Constructs the official Google Generative Language v1beta REST endpoint URL
     * for content generation.
     */
    fun getGenerateContentUrl(apiKey: String, model: String = cachedModel ?: DEFAULT_MODEL): String {
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
    }
}
