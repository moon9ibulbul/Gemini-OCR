package com.astral.ocr.pororo

import java.io.File

data class BrainOcrConfig(
    val vocab: List<String>,
    val vocabSize: Int,
    val lang: String,
    val detectorPath: File,
    val recognizerPath: File,
    val options: Map<String, Any>
)

internal fun parseOptions(optFile: File): Map<String, Any> {
    val options = mutableMapOf<String, Any>()
    optFile.forEachLine { line ->
        val content = line.trim()
        if (content.isEmpty() || !content.contains(":")) return@forEachLine
        val (key, valueRaw) = content.split(":", limit = 2)
        val value = valueRaw.trim().let { raw ->
            raw.toIntOrNull()
                ?: raw.toDoubleOrNull()
                ?: when {
                    raw.equals("True", ignoreCase = true) -> true
                    raw.equals("False", ignoreCase = true) -> false
                    raw.startsWith("[") && raw.endsWith("]") -> {
                        raw.substring(1, raw.length - 1)
                            .split(",")
                            .map { it.trim().trim('"', '\'') }
                            .filter { it.isNotEmpty() }
                    }
                    else -> raw.trim('"', '\'')
                }
        }
        options[key.trim()] = value
    }
    return options
}

internal fun buildConfig(
    lang: String,
    optFile: File,
    detectorPath: File,
    recognizerPath: File
): BrainOcrConfig {
    val options = parseOptions(optFile).toMutableMap()
    val characters = (options["character"] as? String)?.toList()?.map { it.toString() }
        ?: emptyList()
    val vocab = listOf("[blank]") + characters
    options["vocab"] = vocab
    options["vocab_size"] = vocab.size
    options["lang"] = lang
    options["det_model_ckpt_fp"] = detectorPath.absolutePath
    options["rec_model_ckpt_fp"] = recognizerPath.absolutePath
    return BrainOcrConfig(
        vocab = vocab,
        vocabSize = vocab.size,
        lang = lang,
        detectorPath = detectorPath,
        recognizerPath = recognizerPath,
        options = options
    )
}
