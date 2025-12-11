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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

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
        val progressMessage: String? = null
    )

    private val mutableResults = MutableStateFlow<List<OcrResult>>(emptyList())
    private val mutableProcessing = MutableStateFlow(false)
    private val mutableBulkMode = MutableStateFlow(false)
    private val mutableLastSavedPath = MutableStateFlow<String?>(null)
    private val mutableProgress = MutableStateFlow<String?>(null)

    private var processingJob: Job? = null

    val notifications = MutableSharedFlow<String?>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<UiState> = combine(
        settingsRepository.apiKey,
        settingsRepository.model,
        mutableProcessing,
        mutableResults,
        mutableBulkMode,
        mutableLastSavedPath,
        mutableProgress
    ) { values ->
        val apiKey = values[0] as String
        val model = values[1] as String
        val processing = values[2] as Boolean
        val results = values[3] as List<OcrResult>
        val bulk = values[4] as Boolean
        val saved = values[5] as String?
        val progress = values[6] as String?

        UiState(
            apiKey = apiKey,
            model = if (model.isBlank()) "gemini-1.5-flash-latest" else model,
            isProcessing = processing,
            results = results,
            bulkMode = bulk,
            lastSavedPath = saved,
            progressMessage = progress
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
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            performProcessing(contentResolver, listOf(uri))
        }
    }

    fun processBulk(contentResolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty()) return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            performProcessing(contentResolver, uris)
        }
    }

    fun clearResults() {
        mutableResults.value = emptyList()
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        mutableProcessing.value = false
        mutableProgress.value = null
        processingJob = null
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

    private suspend fun performProcessing(contentResolver: ContentResolver, uris: List<Uri>) {
        mutableProcessing.value = true
        mutableProgress.value = "Menyiapkan gambar..."
        val newResults = mutableListOf<OcrResult>()
        val total = uris.size

        try {
            for ((index, uri) in uris.withIndex()) {
                coroutineContext.ensureActive()
                val start = System.currentTimeMillis()
                val result = geminiOcrService.extractSpeech(
                    contentResolver,
                    uri,
                    uiState.value.apiKey,
                    uiState.value.model,
                    pageIndex = index,
                    totalPages = total,
                    onProgress = { message -> mutableProgress.value = message }
                )
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
        } finally {
            mutableProgress.value = null
            mutableProcessing.value = false
            processingJob = null
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
