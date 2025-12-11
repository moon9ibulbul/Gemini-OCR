package com.astral.ocr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.astral.ocr.MainViewModel
import com.astral.ocr.data.MIN_SEGMENT_HEIGHT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: MainViewModel.UiState,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onSliceEnabledChanged: (Boolean) -> Unit,
    onSliceHeightChanged: (Int) -> Unit
) {
    val apiKeyState = remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    val modelState = remember(uiState.model) { mutableStateOf(uiState.model) }
    val sliceEnabledState = remember(uiState.sliceEnabled) { mutableStateOf(uiState.sliceEnabled) }
    val sliceHeightState = remember(uiState.sliceHeight) { mutableStateOf(uiState.sliceHeight.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopAppBar(
            title = { Text("Pengaturan Gemini") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
            }
        )

        Text(
            text = "Masukkan API Key Gemini dan model vision yang ingin digunakan.",
            style = MaterialTheme.typography.bodyLarge
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Potong halaman panjang menjadi segmen")
                Text(
                    text = "Aktifkan agar halaman tinggi di-slice sebelum dikirim ke Gemini.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = sliceEnabledState.value,
                onCheckedChange = { sliceEnabledState.value = it }
            )
        }

        OutlinedTextField(
            value = sliceHeightState.value,
            onValueChange = { input ->
                sliceHeightState.value = input.filter { it.isDigit() }
            },
            label = { Text("Tinggi segmen (px)") },
            supportingText = { Text("Digunakan saat memotong halaman panjang.") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = sliceEnabledState.value
        )

        Button(onClick = {
            onApiKeyChanged(apiKeyState.value)
            onModelChanged(modelState.value)
            val parsedHeight = sliceHeightState.value.toIntOrNull()?.coerceAtLeast(MIN_SEGMENT_HEIGHT)
                ?: uiState.sliceHeight
            onSliceEnabledChanged(sliceEnabledState.value)
            onSliceHeightChanged(parsedHeight)
            onBack()
        }) {
            Text("Simpan")
        }
    }
}
