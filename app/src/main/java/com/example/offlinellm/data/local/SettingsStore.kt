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
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
    }

    val settings: Flow<InferenceSettings> = context.dataStore.data.map { p ->
        val defaults = InferenceSettings()
        InferenceSettings(
            systemPrompt = p[Keys.SYSTEM_PROMPT] ?: defaults.systemPrompt,
            temperature = p[Keys.TEMPERATURE] ?: defaults.temperature,
            topK = p[Keys.TOP_K] ?: defaults.topK,
            topP = p[Keys.TOP_P] ?: defaults.topP,
            maxTokens = p[Keys.MAX_TOKENS] ?: defaults.maxTokens,
            contextSize = p[Keys.CONTEXT_SIZE] ?: defaults.contextSize,
            threads = p[Keys.THREADS] ?: defaults.threads,
            defaultModelId = p[Keys.DEFAULT_MODEL],
            uiLanguage = p[Keys.UI_LANGUAGE] ?: defaults.uiLanguage
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
            p[Keys.UI_LANGUAGE] = s.uiLanguage
            s.defaultModelId?.let { p[Keys.DEFAULT_MODEL] = it }
        }
    }
}
