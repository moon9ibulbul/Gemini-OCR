package com.astral.ocr

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.astral.ocr.data.OcrProvider
import com.astral.ocr.data.OcrResult
import com.astral.ocr.data.SettingsRepository
import com.astral.ocr.network.GeminiOcrService
import com.astral.ocr.network.PororoOcrService
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
    private val geminiOcrService: GeminiOcrService = GeminiOcrService(),
    private val pororoOcrService: PororoOcrService = PororoOcrService(context)
) : ViewModel() {

    data class UiState(
        val apiKey: String = "",
        val model: String = "gemini-1.5-flash-latest",
        val provider: OcrProvider = OcrProvider.GEMINI,
        val isProcessing: Boolean = false,
        val results: List<OcrResult> = emptyList(),
        val bulkMode: Boolean = false,
        val lastSavedPath: String? = null
    )

    private val mutableResults = MutableStateFlow<List<OcrResult>>(emptyList())
    private val mutableProcessing = MutableStateFlow(false)
    private val mutableBulkMode = MutableStateFlow(false)
    private val mutableLastSavedPath = MutableStateFlow<String?>(null)

    val notifications = MutableSharedFlow<String?>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<UiState> = combine(
        settingsRepository.apiKey,
        settingsRepository.model,
        settingsRepository.provider,
        mutableProcessing,
        mutableResults,
        mutableBulkMode,
        mutableLastSavedPath
    ) { values ->
        val apiKey = values[0] as String
        val model = values[1] as String
        val provider = values[2] as OcrProvider
        val processing = values[3] as Boolean
        val results = values[4] as List<OcrResult>
        val bulk = values[5] as Boolean
        val saved = values[6] as String?

        UiState(
            apiKey = apiKey,
            model = if (provider == OcrProvider.GEMINI && model.isBlank()) {
                "gemini-1.5-flash-latest"
            } else {
                model
            },
            provider = provider,
            isProcessing = processing,
            results = results,
            bulkMode = bulk,
            lastSavedPath = saved
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

    fun updateProvider(value: OcrProvider) {
        viewModelScope.launch {
            settingsRepository.updateProvider(value)
        }
    }

    fun processSingle(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            mutableProcessing.value = true
            val provider = uiState.value.provider
            val start = System.currentTimeMillis()
            val result = runOcr(contentResolver, uri, provider)
            result.fold(
                onSuccess = { text ->
                    val duration = System.currentTimeMillis() - start
                    mutableResults.value = listOf(
                        OcrResult(uri.toString(), text, duration)
                    )
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
            val newResults = mutableListOf<OcrResult>()
            val provider = uiState.value.provider
            for (uri in uris) {
                val start = System.currentTimeMillis()
                val result = runOcr(contentResolver, uri, provider)
                result.fold(
                    onSuccess = { text ->
                        val duration = System.currentTimeMillis() - start
                        newResults.add(OcrResult(uri.toString(), text, duration))
                    },
                    onFailure = { ex ->
                        notifyError(ex)
                    }
                )
            }
            mutableResults.value = newResults
            mutableProcessing.value = false
        }
    }

    fun clearResults() {
        mutableResults.value = emptyList()
    }

    fun setLastSavedPath(path: String?) {
        mutableLastSavedPath.value = path
    }

    private suspend fun runOcr(contentResolver: ContentResolver, uri: Uri, provider: OcrProvider): Result<String> {
        return when (provider) {
            OcrProvider.GEMINI -> geminiOcrService.extractSpeech(contentResolver, uri, uiState.value.apiKey, uiState.value.model)
            OcrProvider.PORORO -> pororoOcrService.extractSpeech(contentResolver, uri)
        }
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
