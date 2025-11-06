package com.astral.ocr.pororo

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val BASE_MODEL_URL = "https://twg.kakaocdn.net/pororo/%s/models/%s"
private const val BASE_DICT_URL = "https://twg.kakaocdn.net/pororo/%s/dicts/%s"

private val MODEL_FALLBACK_URLS = mapOf(
    "craft.pt" to listOf(
        "https://huggingface.co/Snowad/Pororo-ocr/resolve/main/craft.pt?download=true"
    ),
    "brainocr.pt" to listOf(
        "https://huggingface.co/Snowad/Pororo-ocr/resolve/main/brainocr.pt?download=true"
    )
)

class PororoModelRepository(context: Context) {

    private val client = OkHttpClient()
    private val baseDir: File = File(context.filesDir, "pororo").apply { mkdirs() }

    fun getDetectorModel(lang: String): File = ensureDownloaded(lang, "misc/craft.pt", isDict = false)

    fun getRecognizerModel(lang: String): File = ensureDownloaded(lang, "misc/brainocr.pt", isDict = false)

    fun getOptionsFile(lang: String): File = ensureDownloaded(lang, "misc/ocr-opt.txt", isDict = false)

    private fun ensureDownloaded(lang: String, relativePath: String, isDict: Boolean): File {
        val target = File(baseDir, "$lang/$relativePath")
        if (target.exists()) {
            return target
        }
        target.parentFile?.mkdirs()
        val remoteName = relativePath.substringAfterLast('/')
        val urls = if (isDict) {
            listOf(String.format(BASE_DICT_URL, lang, remoteName))
        } else {
            buildModelUrls(lang, remoteName)
        }

        downloadToFile(urls, target)
        return target
    }

    private fun buildModelUrls(lang: String, remoteName: String): List<String> {
        val defaultUrl = String.format(BASE_MODEL_URL, lang, remoteName)
        val fallbacks = MODEL_FALLBACK_URLS[remoteName].orEmpty()
        return listOf(defaultUrl) + fallbacks
    }

    private fun downloadToFile(urls: List<String>, target: File) {
        var lastException: IOException? = null
        for (url in urls) {
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastException = IOException("Gagal mengunduh $url: ${response.code}")
                        return@use
                    }
                    val body = response.body ?: run {
                        lastException = IOException("Tidak ada konten dari $url")
                        return@use
                    }
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (target.exists()) {
                    return
                }
            } catch (ex: IOException) {
                target.delete()
                lastException = ex
            }
        }

        target.delete()
        throw lastException ?: IOException("Gagal mengunduh ${urls.firstOrNull() ?: "model"}")
    }
}
