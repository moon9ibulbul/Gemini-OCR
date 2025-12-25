package com.astral.ocr.data

import android.graphics.Bitmap

const val DEFAULT_SEGMENT_HEIGHT = 1400
const val DEFAULT_SEGMENT_OVERLAP = 80
const val MIN_SEGMENT_HEIGHT = 400

/**
 * Memotong bitmap secara vertikal menjadi beberapa segmen dengan overlap agar teks di batas potongan tidak hilang.
 * Overlap menjaga bubble yang melintasi batas tetap terbaca di segmen berikutnya.
 */
fun sliceVerticalWithOverlap(
    bitmap: Bitmap,
    targetHeight: Int = DEFAULT_SEGMENT_HEIGHT,
    overlap: Int = DEFAULT_SEGMENT_OVERLAP,
): List<Bitmap> {
    if (bitmap.height <= targetHeight) return listOf(bitmap)

    val slices = mutableListOf<Bitmap>()
    var top = 0
    val step = (targetHeight - overlap).coerceAtLeast(1)

    while (top < bitmap.height) {
        val sliceHeight = if (top + targetHeight > bitmap.height) bitmap.height - top else targetHeight
        val slice = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, sliceHeight)
        slices.add(slice)
        top += step
    }
    return slices
}
