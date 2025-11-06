package com.astral.ocr.pororo

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val BASE_MODEL_URL = "https://twg.kakaocdn.net/pororo/%s/models/%s"
private const val BASE_DICT_URL = "https://twg.kakaocdn.net/pororo/%s/dicts/%s"

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
        val url = if (isDict) {
            String.format(BASE_DICT_URL, lang, remoteName)
        } else {
            String.format(BASE_MODEL_URL, lang, remoteName)
        }

        downloadToFile(url, target)
        return target
    }

    private fun downloadToFile(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Gagal mengunduh $url: ${response.code}")
                }
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Tidak ada konten dari $url")
            }
        } catch (ex: IOException) {
            target.delete()
            throw ex
        }
    }
}
