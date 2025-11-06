package com.astral.ocr.network

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.astral.ocr.pororo.PororoOcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PororoOcrService(private val context: Context) {

    private val engine: PororoOcrEngine by lazy {
        PororoOcrEngine(context)
    }

    suspend fun extractSpeech(
        contentResolver: ContentResolver,
        uri: Uri,
        dilatationFactor: Float = 2.0f
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("astral_pororo", null, context.cacheDir)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(IOException("Gagal membaca berkas gambar."))

                val formattedText = engine.runOcr(tempFile, dilatationFactor).trim()
                if (formattedText.isEmpty()) {
                    Result.failure(IOException("Pororo tidak mengembalikan teks."))
                } else {
                    Result.success(formattedText)
                }
            } catch (ex: Throwable) {
                if (ex is CancellationException) throw ex
                Result.failure(IOException(ex.message ?: "Pororo gagal memproses gambar.", ex))
            } finally {
                tempFile.delete()
            }
        } catch (ex: Throwable) {
            if (ex is CancellationException) throw ex
            Result.failure(IOException(ex.message ?: "Pororo gagal memproses gambar.", ex))
        }
    }
}
