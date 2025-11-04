package com.astral.ocr.network

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PororoOcrService(private val context: Context) {

    suspend fun extractSpeech(contentResolver: ContentResolver, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            ensurePython()

            val tempFile = File.createTempFile("astral_pororo", null, context.cacheDir)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(IOException("Gagal membaca berkas gambar."))

                val python = Python.getInstance()
                val module = python.getModule("astral_pororo")
                val result = module.callAttr("run_ocr", tempFile.absolutePath)
                val text = result.toString().trim()
                if (text.isEmpty()) {
                    Result.failure(IllegalStateException("Pororo tidak mengembalikan teks."))
                } else {
                    Result.success(text)
                }
            } catch (ex: PyException) {
                Result.failure(IOException(ex.message ?: "Pororo gagal memproses gambar."))
            } finally {
                tempFile.delete()
            }
        }

    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }
}
