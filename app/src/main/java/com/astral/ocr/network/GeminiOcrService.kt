package com.astral.ocr.network

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.astral.ocr.data.OcrSegmentResult
import com.astral.ocr.data.sliceVerticalWithOverlap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class GeminiOcrService(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun extractSpeech(
        contentResolver: ContentResolver,
        uri: Uri,
        apiKey: String,
        model: String,
        pageIndex: Int = 0,
        totalPages: Int = 1,
        onProgress: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || model.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key dan model harus diisi pada pengaturan."))
        }

        val payloadMimeType = "image/jpeg"
        val originalBytes = contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return@withContext Result.failure(IOException("Gagal membaca berkas gambar."))

        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
            ?: return@withContext Result.failure(IOException("Gagal memuat bitmap."))

        val segments = if (bitmap.height > LONG_PAGE_HEIGHT_THRESHOLD) {
            // Memotong halaman panjang menjadi beberapa segmen vertikal dengan overlap agar bubble tidak terpotong di batas.
            sliceVerticalWithOverlap(bitmap, targetHeight = TARGET_SLICE_HEIGHT, overlap = SLICE_OVERLAP)
        } else {
            listOf(bitmap)
        }

        val segmentResults = mutableListOf<OcrSegmentResult>()
        val totalSegments = segments.size

        segments.forEachIndexed { index, segment ->
            onProgress("Gambar ${pageIndex + 1}/$totalPages, segmen ${index + 1}/$totalSegments")

            val prompt = buildPrompt(index + 1, totalSegments)
            val base64 = encodeBitmap(segment)

            val response = requestWithRetry(apiKey, model, payloadMimeType, base64, prompt)
            response.fold(
                onSuccess = { raw ->
                    segmentResults.add(
                        OcrSegmentResult(
                            pageIndex = pageIndex,
                            segmentIndex = index,
                            totalSegments = totalSegments,
                            rawText = normalizeOutput(raw)
                        )
                    )
                },
                onFailure = { ex ->
                    return@withContext Result.failure(ex)
                }
            )
        }

        val merged = mergeSegments(segmentResults)
        Result.success(merged)
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

    private fun buildPrompt(segmentIndex: Int, totalSegments: Int): String =
        """
            Kamu adalah asisten OCR khusus untuk manhwa. Gambar ini adalah SEGMENT ${segmentIndex}/$totalSegments dari halaman komik panjang yang dipotong secara vertikal.
            Hanya baca teks yang benar-benar terlihat pada segmen ini, jangan menebak kelanjutan di luar gambar.\n\n
            Tugas:
            - Temukan semua teks pada bubble bulat/oval, bubble kotak, efek suara (SFX), dan teks luar bubble.
            - Urutkan berdasarkan posisi visual: dari atas ke bawah, dan jika sejajar secara vertikal, dari kiri ke kanan.
            - Beri nomor setiap blok teks agar urutan mudah diikuti. Gunakan format `[BLOCK n] <tipe> <teks>`.
            - Tipe teks:
              * Bubble bulat/oval -> `()`
              * Bubble kotak -> `[]`
              * SFX -> `//`
              * Teks luar bubble -> `''`
            - Contoh keluaran:
              [BLOCK 1] () Halo apa kabar?
              [BLOCK 2] [] Ini contoh narasi.
              [BLOCK 3] // *tap tap*
              [BLOCK 4] '' Catatan editor\n\n
            Aturan tambahan:
            - Urutkan teks sesuai instruksi posisi, jangan mengubah urutan dialog seenaknya.
            - Jangan menggabungkan bubble berbeda menjadi satu kalimat jika posisinya terpisah.
            - Gunakan bahasa asli hasil OCR, jangan terjemahkan.
            - Output hanya daftar teks dengan format di atas tanpa penjelasan tambahan.
        """.trimIndent()

    private fun normalizeOutput(raw: String): String {
        val lines = raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        data class Block(val order: Int?, val prefix: String?, val builder: StringBuilder, val originalIndex: Int)

        val blockRegex = Regex("^\\[BLOCK\\s*(\\d+)]\\s*(.*)$", RegexOption.IGNORE_CASE)
        val prefixes = listOf("()", "[]", "//", "''")
        val blocks = mutableListOf<Block>()
        var lastBlock: Block? = null

        fun extractPrefix(text: String): Pair<String?, String> {
            val prefix = prefixes.firstOrNull { candidate ->
                text.startsWith(candidate) || text.startsWith("${candidate} :")
            }
            return if (prefix != null) {
                val cleaned = text.removePrefix(prefix).trim().removePrefix(":").trim()
                prefix to cleaned
            } else prefix to text
        }

        for ((index, line) in lines.withIndex()) {
            val match = blockRegex.find(line)
            val order = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            val rawContent = match?.groupValues?.getOrNull(2)?.trim().orEmpty().ifBlank { line }
            val (prefix, content) = extractPrefix(rawContent)

            when {
                order != null -> {
                    val block = Block(order, prefix ?: lastBlock?.prefix, StringBuilder(content), index)
                    blocks.add(block)
                    lastBlock = block
                }

                prefix != null -> {
                    val block = Block(null, prefix, StringBuilder(content), index)
                    blocks.add(block)
                    lastBlock = block
                }

                lastBlock != null -> {
                    lastBlock.builder.append(' ').append(rawContent)
                }

                else -> {
                    val block = Block(null, null, StringBuilder(rawContent), index)
                    blocks.add(block)
                    lastBlock = block
                }
            }
        }

        val sorted = blocks.sortedWith { a, b ->
            when {
                a.order != null && b.order != null -> a.order.compareTo(b.order)
                a.order != null -> -1
                b.order != null -> 1
                else -> a.originalIndex.compareTo(b.originalIndex)
            }
        }

        return sorted.mapNotNull { block ->
            val prefix = block.prefix
            val text = block.builder.toString().trim()
            if (text.isBlank()) return@mapNotNull null
            if (prefix.isNullOrBlank()) text else "$prefix : $text"
        }.joinToString(separator = "\n")
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        // Menggunakan JPEG agar ukuran per segmen lebih kecil sehingga risiko timeout berkurang.
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private suspend fun requestWithRetry(
        apiKey: String,
        model: String,
        mimeType: String,
        base64: String,
        prompt: String,
        retries: Int = MAX_RETRIES,
    ): Result<String> {
        var attempt = 0
        var delayMs = INITIAL_BACKOFF_MS

        while (attempt <= retries) {
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
                executeRequest(request).use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        val message = parseErrorMessage(errorBody)
                        val friendly = if (response.code == 429) {
                            "Batas kuota Gemini tercapai. Coba lagi nanti atau gunakan model lain."
                        } else message
                        throw IOException(friendly ?: "Permintaan gagal dengan kode ${response.code}")
                    }

                    val responseBody = response.body?.string() ?: return Result.failure(IOException("Respon kosong dari Gemini"))
                    val parsed = json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
                    val text = parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
                    if (text.isNullOrBlank()) {
                        return Result.failure(IllegalStateException("Gemini tidak mengembalikan teks."))
                    }
                    return Result.success(text)
                }
            } catch (ex: IOException) {
                if (attempt == retries) {
                    return Result.failure(ex)
                }
                delay(delayMs)
                delayMs = (delayMs * BACKOFF_MULTIPLIER).toLong()
                attempt++
            }
        }
        return Result.failure(IOException("Gagal memproses permintaan."))
    }

    private suspend fun executeRequest(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        try {
            val response = call.execute()
            if (continuation.isActive) {
                continuation.resume(response)
            } else {
                response.close()
            }
        } catch (ex: IOException) {
            if (continuation.isActive) {
                continuation.resumeWithException(ex)
            }
        }
    }

    private fun mergeSegments(segments: List<OcrSegmentResult>): String {
        if (segments.isEmpty()) return ""
        val sorted = segments.sortedBy { it.segmentIndex }

        val mergedLines = mutableListOf<String>()
        for (segment in sorted) {
            val lines = segment.rawText.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (line in lines) {
                // Deduplicate sederhana: jika baris sudah ada di 3 baris terakhir, anggap overlap dan lewati.
                val window = mergedLines.takeLast(3)
                if (window.contains(line)) continue
                mergedLines.add(line)
            }
        }
        return mergedLines.joinToString(separator = "\n")
    }

    companion object {
        // Halaman dengan tinggi melebihi ambang ini dianggap komik panjang dan akan di-slice.
        const val LONG_PAGE_HEIGHT_THRESHOLD = 3400
        // Tinggi target per segmen agar request tidak terlalu berat dan terhindar dari timeout.
        const val TARGET_SLICE_HEIGHT = 1400
        // Overlap untuk menjaga bubble yang melintasi batas potongan tetap terbaca.
        const val SLICE_OVERLAP = 80
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 2000L
        private const val BACKOFF_MULTIPLIER = 2

        private fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
}
