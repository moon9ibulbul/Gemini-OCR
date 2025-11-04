package com.astral.ocr.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astral.ocr.data.OcrProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "astral_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val provider = stringPreferencesKey("provider")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.apiKey].orEmpty() }
    val model: Flow<String> = context.dataStore.data.map { it[Keys.model].orEmpty() }
    val provider: Flow<OcrProvider> = context.dataStore.data.map {
        OcrProvider.fromValue(it[Keys.provider])
    }

    suspend fun updateApiKey(value: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.apiKey] = value
        }
    }

    suspend fun updateModel(value: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.model] = value
        }
    }

    suspend fun updateProvider(value: OcrProvider) {
        context.dataStore.edit { prefs ->
            prefs[Keys.provider] = value.name
        }
    }
}
