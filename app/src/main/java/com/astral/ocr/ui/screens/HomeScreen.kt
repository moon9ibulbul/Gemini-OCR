package com.astral.ocr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImagesearchRoller
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astral.ocr.MainViewModel
import com.astral.ocr.data.OcrResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainViewModel.UiState,
    paddingValues: PaddingValues,
    onPickSingle: () -> Unit,
    onPickMultiple: () -> Unit,
    onToggleBulk: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSaveResult: (String, String) -> Unit,
    onCancelProcessing: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val segmentedOptions = listOf("Single", "Bulk")
    var selectedIndex by remember(uiState.bulkMode) {
        mutableIntStateOf(if (uiState.bulkMode) 1 else 0)
    }

    LaunchedEffect(selectedIndex) {
        onToggleBulk(selectedIndex == 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Deteksi OCR Manhwa modern",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Ekstrak bubble speech, SFX, dan teks luar dengan sekali sentuh.",
            style = MaterialTheme.typography.bodyLarge
        )

        SingleChoiceSegmentedButtonRow {
            segmentedOptions.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(text = label)
                }
            }
        }

        Button(
            onClick = if (uiState.bulkMode) onPickMultiple else onPickSingle,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isProcessing
        ) {
            Icon(
                imageVector = if (uiState.bulkMode) Icons.Default.ImagesearchRoller else Icons.Default.Image,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (uiState.bulkMode) "Pilih Banyak Gambar" else "Pilih Gambar")
        }

        if (uiState.isProcessing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(uiState.progressMessage ?: "Memproses dengan Gemini...")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onCancelProcessing) {
                    Text("Batal")
                }
            }
        }

        if (uiState.results.isNotEmpty()) {
            ActionRow(
                onClear = onClear,
                onSaveResult = onSaveResult,
                isBulk = uiState.bulkMode,
                results = uiState.results
            )
            ResultList(results = uiState.results)
        }
    }
}

@Composable
private fun ActionRow(
    onClear: () -> Unit,
    onSaveResult: (String, String) -> Unit,
    isBulk: Boolean,
    results: List<OcrResult>
) {
    val clipboard = LocalClipboardManager.current

    val combinedText = remember(results, isBulk) {
        if (results.isEmpty()) {
            ""
        } else if (isBulk) {
            results.mapIndexed { index, result ->
                buildString {
                    append("Panel : ")
                    append(index + 1)
                    append('\n')
                    append(result.processedText.trimEnd())
                }
            }.joinToString(separator = "\n\n")
        } else {
            results.joinToString(separator = "\n\n") { result ->
                result.processedText
            }
        }
    }

    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = {
                clipboard.setText(AnnotatedString(combinedText))
            },
            label = { Text("Salin Semua") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
            }
        )
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Bersihkan")
            }
            IconButton(
                onClick = {
                    val filename = if (isBulk) "astral_bulk_result.txt" else "astral_result.txt"
                    onSaveResult(filename, combinedText)
                }
            ) {
                Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Simpan")
            }
        }
    }
}

@Composable
private fun ResultList(results: List<OcrResult>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(results) { result ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ListAlt, contentDescription = null)
                        Text(text = "Gambar", fontWeight = FontWeight.SemiBold)
                        Text(text = result.imageUri, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = result.processedText, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Durasi: ${result.durationMillis} ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
