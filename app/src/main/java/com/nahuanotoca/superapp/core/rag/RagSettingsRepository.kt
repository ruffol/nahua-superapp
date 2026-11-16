package com.nahuanotoca.superapp.core.rag

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nahuanotoca.superapp.core.data.appPreferencesStore
import com.nahuanotoca.superapp.core.model.RagSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RagSettingsRepository(private val context: Context) {

    private val store = context.appPreferencesStore

    val settings: Flow<RagSettings> = store.data.map { preferences ->
        RagSettings(
            baseUrl = preferences[BASE_URL] ?: RagSettings.DEFAULT_BASE_URL,
            model = preferences[MODEL] ?: RagSettings.DEFAULT_MODEL,
            apiKey = preferences[API_KEY].orEmpty(),
            topK = preferences[TOP_K]?.toIntOrNull() ?: 4,
        )
    }

    suspend fun save(settings: RagSettings) {
        store.edit { preferences ->
            preferences[BASE_URL] = settings.baseUrl.trim()
            preferences[MODEL] = settings.model.trim()
            preferences[API_KEY] = settings.apiKey.trim()
            preferences[TOP_K] = settings.topK.toString()
        }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("rag_base_url")
        val MODEL = stringPreferencesKey("rag_model")
        val API_KEY = stringPreferencesKey("rag_api_key")
        val TOP_K = stringPreferencesKey("rag_top_k")
    }
}
