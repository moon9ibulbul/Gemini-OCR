package com.astral.ocr

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.astral.ocr.data.OcrResult
import com.astral.ocr.data.SettingsRepository
import com.astral.ocr.network.GeminiOcrService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    private val geminiOcrService: GeminiOcrService = GeminiOcrService()
) : ViewModel() {

    data class UiState(
        val apiKey: String = "",
        val model: String = "gemini-1.5-flash-latest",
        val isProcessing: Boolean = false,
        val results: List<OcrResult> = emptyList(),
        val bulkMode: Boolean = false,
        val lastSavedPath: String? = null,
        val combinedText: String = ""
    )

    private val mutableResults = MutableStateFlow<List<OcrResult>>(emptyList())
    private val mutableProcessing = MutableStateFlow(false)
    private val mutableBulkMode = MutableStateFlow(false)
    private val mutableLastSavedPath = MutableStateFlow<String?>(null)
    private val mutableCombinedText = MutableStateFlow("")

    val notifications = MutableSharedFlow<String?>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<UiState> = combine(
        settingsRepository.apiKey,
        settingsRepository.model,
        mutableProcessing,
        mutableResults,
        mutableBulkMode,
        mutableLastSavedPath,
        mutableCombinedText
    ) { values ->
        val apiKey = values[0] as String
        val model = values[1] as String
        val processing = values[2] as Boolean
        val results = values[3] as List<OcrResult>
        val bulk = values[4] as Boolean
        val saved = values[5] as String?
        val combined = values[6] as String

        UiState(
            apiKey = apiKey,
            model = if (model.isBlank()) "gemini-1.5-flash-latest" else model,
            isProcessing = processing,
            results = results,
            bulkMode = bulk,
            lastSavedPath = saved,
            combinedText = combined
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    var onImagePickedCallback: ((Uri?) -> Unit)? = null
    var onMultipleImagesPickedCallback: ((List<Uri>) -> Unit)? = null
    var onDocumentCreatedCallback: ((Uri?) -> Unit)? = null

    fun toggleBulkMode(enabled: Boolean) {
        mutableBulkMode.value = enabled
    }

    fun updateApiKey(value: String) {
        viewModelScope.launch {
            settingsRepository.updateApiKey(value)
        }
    }

    fun updateModel(value: String) {
        viewModelScope.launch {
            settingsRepository.updateModel(value)
        }
    }

    fun processSingle(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            mutableProcessing.value = true
            mutableCombinedText.value = ""
            val start = System.currentTimeMillis()
            val result = geminiOcrService.extractSpeech(contentResolver, uri, uiState.value.apiKey, uiState.value.model)
            result.fold(
                onSuccess = { text ->
                    val duration = System.currentTimeMillis() - start
                    mutableResults.value = listOf(
                        OcrResult(uri.toString(), text, duration)
                    )
                    mutableCombinedText.value = text
                },
                onFailure = { ex ->
                    notifyError(ex)
                }
            )
            mutableProcessing.value = false
        }
    }

    fun processBulk(contentResolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mutableProcessing.value = true
            mutableCombinedText.value = ""
            val newResults = mutableListOf<OcrResult>()
            val combinedBuilder = StringBuilder()
            val apiKey = uiState.value.apiKey
            val model = uiState.value.model
            for (uri in uris) {
                val start = System.currentTimeMillis()
                val result = geminiOcrService.extractSpeech(contentResolver, uri, apiKey, model)
                result.fold(
                    onSuccess = { text ->
                        val duration = System.currentTimeMillis() - start
                        newResults.add(OcrResult(uri.toString(), text, duration))
                        if (combinedBuilder.isNotEmpty()) {
                            combinedBuilder.append("\n\n")
                        }
                        combinedBuilder.append(text)
                        mutableResults.value = newResults.toList()
                        mutableCombinedText.value = combinedBuilder.toString()
                    },
                    onFailure = { ex ->
                        notifyError(ex)
                    }
                )
            }
            mutableResults.value = newResults
            mutableCombinedText.value = combinedBuilder.toString()
            mutableProcessing.value = false
        }
    }

    fun clearResults() {
        mutableResults.value = emptyList()
        mutableCombinedText.value = ""
    }

    fun setLastSavedPath(path: String?) {
        mutableLastSavedPath.value = path
    }

    private fun notifyError(ex: Throwable) {
        val message = ex.message ?: "Terjadi kesalahan tidak diketahui"
        viewModelScope.launch {
            notifications.emit(message)
        }
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
