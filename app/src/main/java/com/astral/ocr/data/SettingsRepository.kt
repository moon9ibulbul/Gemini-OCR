package com.astral.ocr.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astral.ocr.data.DEFAULT_SEGMENT_HEIGHT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "astral_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val sliceEnabled = booleanPreferencesKey("slice_enabled")
        val sliceHeight = intPreferencesKey("slice_height")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.apiKey].orEmpty() }
    val model: Flow<String> = context.dataStore.data.map { it[Keys.model].orEmpty() }
    val sliceEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.sliceEnabled] ?: true }
    val sliceHeight: Flow<Int> = context.dataStore.data.map { it[Keys.sliceHeight] ?: DEFAULT_SEGMENT_HEIGHT }

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

    suspend fun updateSliceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.sliceEnabled] = enabled
        }
    }

    suspend fun updateSliceHeight(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.sliceHeight] = value
        }
    }
}
