package com.astral.ocr.network

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class GeminiOcrService(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun extractSpeech(contentResolver: ContentResolver, uri: Uri, apiKey: String, model: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || model.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API key dan model harus diisi pada pengaturan."))
            }

            val mimeType = contentResolver.getType(uri) ?: "image/*"
            val base64 = contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } ?: return@withContext Result.failure(IOException("Gagal membaca berkas gambar."))

            val prompt = buildPrompt()
            val requestBody = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(
                                inlineData = InlineData(
                                    mimeType = mimeType,
                                    data = base64
                                )
                            )
                        )
                    )
                )
            )

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.encodeToString(requestBody).toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        val message = parseErrorMessage(errorBody)
                        val friendly = if (response.code == 429) {
                            "Batas kuota Gemini tercapai. Coba lagi nanti atau gunakan model lain."
                        } else message
                        return@withContext Result.failure(IOException(friendly ?: "Permintaan gagal dengan kode ${response.code}"))
                    }
                    val responseBody = response.body?.string() ?: return@withContext Result.failure(IOException("Respon kosong dari Gemini"))
                    val parsed = json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
                    val text = parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
                    if (text.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalStateException("Gemini tidak mengembalikan teks."))
                    }
                    Result.success(postProcess(text))
                }
            } catch (ex: IOException) {
                Result.failure(ex)
            }
        }

    private fun parseErrorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val parsed = json.decodeFromString(GeminiErrorResponse.serializer(), raw)
            parsed.error?.message
        } catch (_: Exception) {
            raw
        }
    }

    private fun buildPrompt(): String =
        """
            Kamu adalah asisten OCR khusus untuk manhwa. Ekstrak seluruh teks dan klasifikasikan menjadi empat kategori berikut:\n\n""".trimIndent() +
            """
            1. Bubble speech bulat/oval -> beri format `() : <teks>`\n""" +
            """
            2. Bubble speech kotak -> beri format `[] : <teks>`\n""" +
            """
            3. SFX -> beri format `// : <teks>`\n""" +
            """
            4. Teks di luar bubble -> beri format `'' : <teks>`\n\n""" +
            """
            Aturan tambahan:\n""" +
            """
            - Gabungkan teks dalam bubble yang sama menjadi satu baris dengan spasi, jangan pakai newline di tengah.\n""" +
            """
            - Gunakan bahasa asli hasil OCR, jangan terjemahkan.\n""" +
            """
            - Sertakan seluruh teks yang terbaca.\n""" +
            """
            - Jangan tambahkan komentar lain atau format selain daftar baris sesuai kategori.\n""".trimIndent()

    private fun postProcess(raw: String): String {
        val lines = raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<String>()
        var currentPrefix: String? = null
        val builder = StringBuilder()

        fun flush() {
            if (currentPrefix != null && builder.isNotEmpty()) {
                result.add("$currentPrefix${builder.toString().trim()}")
            }
            currentPrefix = null
            builder.clear()
        }

        val prefixes = listOf("() :", "[] :", "// :", "'' :")
        for (line in lines) {
            val prefix = prefixes.firstOrNull { line.startsWith(it) }
            if (prefix != null) {
                flush()
                currentPrefix = "$prefix "
                builder.append(line.removePrefix(prefix).trim()).append(' ')
            } else {
                builder.append(line.trim()).append(' ')
            }
        }
        flush()
        return result.joinToString(separator = "\n")
    }

    companion object {
        private fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
        }
    }
}
