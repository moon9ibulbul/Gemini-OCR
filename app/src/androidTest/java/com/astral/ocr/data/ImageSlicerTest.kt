package com.astral.ocr.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageSlicerTest {

    @Test
    fun sliceVerticalWithOverlap_respectsStepAndCount() {
        val bitmap = Bitmap.createBitmap(720, 3200, Bitmap.Config.ARGB_8888)

        val slices = sliceVerticalWithOverlap(bitmap, targetHeight = 1000, overlap = 100)

        // 3200 height with 1000 target and 900 step => 4 segmen (1000, 1000, 1000, 200)
        assertEquals(4, slices.size)
        assertEquals(1000, slices[0].height)
        assertEquals(1000, slices[1].height)
        assertEquals(1000, slices[2].height)
        assertEquals(200, slices[3].height)
    }
}
