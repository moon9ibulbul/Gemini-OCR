package com.astral.ocr.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "astral_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.apiKey].orEmpty() }
    val model: Flow<String> = context.dataStore.data.map { it[Keys.model].orEmpty() }

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
}
