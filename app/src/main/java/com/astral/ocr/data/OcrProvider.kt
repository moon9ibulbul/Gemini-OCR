package com.astral.ocr.data

enum class OcrProvider(val displayName: String, val requiresCredentials: Boolean) {
    GEMINI(displayName = "Gemini", requiresCredentials = true),
    PORORO(displayName = "PororoOCR", requiresCredentials = false);

    companion object {
        fun fromValue(value: String?): OcrProvider =
            entries.firstOrNull { it.name == value } ?: GEMINI
    }
}
