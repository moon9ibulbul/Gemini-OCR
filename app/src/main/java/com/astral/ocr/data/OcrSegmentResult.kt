package com.astral.ocr.data

/**
 * Representasi hasil OCR untuk satu segmen vertikal dari halaman komik.
 */
data class OcrSegmentResult(
    val pageIndex: Int,
    val segmentIndex: Int,
    val totalSegments: Int,
    val rawText: String
)
