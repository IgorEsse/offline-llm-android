package com.example.offlinellm.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.offlinellm.domain.InferenceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val THREADS = intPreferencesKey("threads")
        val DEFAULT_MODEL = longPreferencesKey("default_model")
    }

    val settings: Flow<InferenceSettings> = context.dataStore.data.map { p ->
        InferenceSettings(
            systemPrompt = p[Keys.SYSTEM_PROMPT] ?: InferenceSettings().systemPrompt,
            temperature = p[Keys.TEMPERATURE] ?: 0.7f,
            topK = p[Keys.TOP_K] ?: 40,
            topP = p[Keys.TOP_P] ?: 0.95f,
            maxTokens = p[Keys.MAX_TOKENS] ?: 256,
            contextSize = p[Keys.CONTEXT_SIZE] ?: 2048,
            threads = p[Keys.THREADS] ?: 4,
            defaultModelId = p[Keys.DEFAULT_MODEL]
        )
    }

    suspend fun update(s: InferenceSettings) {
        context.dataStore.edit { p ->
            p[Keys.SYSTEM_PROMPT] = s.systemPrompt
            p[Keys.TEMPERATURE] = s.temperature
            p[Keys.TOP_K] = s.topK
            p[Keys.TOP_P] = s.topP
            p[Keys.MAX_TOKENS] = s.maxTokens
            p[Keys.CONTEXT_SIZE] = s.contextSize
            p[Keys.THREADS] = s.threads
            s.defaultModelId?.let { p[Keys.DEFAULT_MODEL] = it }
        }
    }
}
