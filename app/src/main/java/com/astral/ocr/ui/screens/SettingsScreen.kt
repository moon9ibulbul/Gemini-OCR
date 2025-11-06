package com.astral.ocr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astral.ocr.MainViewModel
import com.astral.ocr.data.OcrProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: MainViewModel.UiState,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onProviderChanged: (OcrProvider) -> Unit,
    onImportPororoModels: () -> Unit
) {
    val apiKeyState = remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    val modelState = remember(uiState.model) { mutableStateOf(uiState.model) }
    val providerState = remember(uiState.provider) { mutableStateOf(uiState.provider) }
    val providerOptions = remember { listOf(OcrProvider.GEMINI, OcrProvider.PORORO) }
    val saveEnabled = providerState.value != OcrProvider.GEMINI || apiKeyState.value.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopAppBar(
            title = { Text("Pengaturan OCR") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
            }
        )

        Text(
            text = "Pilih mesin OCR yang ingin digunakan dan atur opsinya.",
            style = MaterialTheme.typography.bodyLarge
        )

        SingleChoiceSegmentedButtonRow {
            providerOptions.forEach { provider ->
                SegmentedButton(
                    selected = providerState.value == provider,
                    onClick = { providerState.value = provider },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(provider.displayName)
                }
            }
        }

        if (providerState.value.requiresCredentials) {
            Text(
                text = "Masukkan API Key Gemini dan model vision yang ingin digunakan.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = apiKeyState.value,
                onValueChange = { apiKeyState.value = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = modelState.value,
                onValueChange = { modelState.value = it },
                label = { Text("Model Gemini") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "PororoOCR berjalan lokal dengan Chaquopy sehingga tidak memerlukan API Key.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Impor craft.pt, brainocr.pt, dan ocr-opt.txt secara manual sebelum menjalankan Pororo.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = if (uiState.pororoReady) {
                    "Semua berkas model Pororo sudah siap."
                } else {
                    "Berkas model Pororo belum lengkap."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(onClick = onImportPororoModels) {
                Text("Impor Model Pororo")
            }
        }

        Button(
            onClick = {
                onProviderChanged(providerState.value)
                onApiKeyChanged(apiKeyState.value)
                onModelChanged(modelState.value)
                onBack()
            },
            enabled = saveEnabled
        ) {
            Text("Simpan")
        }
    }
}
