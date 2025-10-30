package com.astral.ocr.data

data class OcrResult(
    val imageUri: String,
    val processedText: String,
    val durationMillis: Long
)
