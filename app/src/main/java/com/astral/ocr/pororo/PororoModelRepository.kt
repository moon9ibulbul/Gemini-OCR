package com.astral.ocr.pororo

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

private const val DEFAULT_LANG = "ko"

class PororoModelRepository(context: Context) {

    data class ImportResult(
        val imported: List<String>,
        val ignored: List<String>,
        val errors: Map<String, String>
    )

    private val baseDir: File = File(context.filesDir, "pororo").apply { mkdirs() }

    fun getDetectorModel(lang: String): File = ensureAvailable(lang, "craft.pt")

    fun getRecognizerModel(lang: String): File = ensureAvailable(lang, "brainocr.pt")

    fun getOptionsFile(lang: String): File = ensureAvailable(lang, "ocr-opt.txt")

    fun hasAllRequiredModels(lang: String = DEFAULT_LANG): Boolean {
        return REQUIRED_FILES.keys.all { name ->
            val file = File(baseDir, "$lang/${REQUIRED_FILES.getValue(name)}")
            file.exists() && file.length() >= minExpectedSize(name)
        }
    }

    fun importModels(contentResolver: ContentResolver, uris: List<Uri>, lang: String = DEFAULT_LANG): ImportResult {
        if (uris.isEmpty()) {
            return ImportResult(emptyList(), emptyList(), emptyMap())
        }

        val imported = mutableListOf<String>()
        val ignored = mutableListOf<String>()
        val errors = mutableMapOf<String, String>()

        for (uri in uris) {
            val displayName = resolveDisplayName(contentResolver, uri)?.trim()
            if (displayName.isNullOrEmpty()) {
                ignored.add("(tanpa nama)")
                continue
            }

            val normalized = REQUIRED_FILES.keys.firstOrNull { it.equals(displayName, ignoreCase = true) }
            if (normalized == null) {
                ignored.add(displayName)
                continue
            }

            val relativePath = REQUIRED_FILES.getValue(normalized)
            val target = File(baseDir, "$lang/$relativePath")
            target.parentFile?.mkdirs()

            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    copyToFile(input, target)
                } ?: throw IOException("Tidak dapat membaca konten $displayName")

                if (target.length() < minExpectedSize(normalized)) {
                    target.delete()
                    throw IOException("Ukuran berkas $displayName tidak valid")
                }

                imported.add(normalized)
            } catch (ex: IOException) {
                target.delete()
                errors[normalized] = ex.message ?: "Gagal mengimpor $normalized"
            }
        }

        return ImportResult(imported.distinct(), ignored, errors)
    }

    private fun ensureAvailable(lang: String, name: String): File {
        val relativePath = REQUIRED_FILES.getValue(name)
        val target = File(baseDir, "$lang/$relativePath")
        if (!target.exists() || target.length() < minExpectedSize(name)) {
            throw IllegalStateException(
                "Model Pororo \"$name\" belum ditemukan. Impor craft.pt, brainocr.pt, dan ocr-opt.txt melalui menu Pengaturan."
            )
        }
        return target
    }

    private fun copyToFile(input: InputStream, target: File) {
        FileOutputStream(target).use { output ->
            input.copyTo(output)
        }
    }

    private fun resolveDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { File(it).name }
        }

        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) cursor.getString(index) else null
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    private fun minExpectedSize(name: String): Long {
        return when (name.lowercase()) {
            "ocr-opt.txt" -> 10L
            "craft.pt", "brainocr.pt" -> 1024L
            else -> 1L
        }
    }

    companion object {
        private val REQUIRED_FILES = mapOf(
            "craft.pt" to "misc/craft.pt",
            "brainocr.pt" to "misc/brainocr.pt",
            "ocr-opt.txt" to "misc/ocr-opt.txt"
        )
    }
}
