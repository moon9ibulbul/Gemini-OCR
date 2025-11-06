package com.astral.ocr.pororo

import java.io.File

internal data class Vertex(val x: Float, val y: Float)
internal data class OcrEntry(val text: String, val vertices: List<Vertex>)
internal data class BrainOcrOutput(val entries: List<OcrEntry>) {
    val descriptions: List<String> get() = entries.map { it.text }
}

internal class BrainOcrReader(
    private val detector: BrainOcrDetector,
    private val recognizer: BrainOcrRecognizer,
    private val config: BrainOcrConfig
) {

    fun execute(imageFile: File, dilatationFactor: Float): BrainOcrOutput {
        val (color, gray) = ImagePreprocessor.reformatInput(imageFile)
        return try {
            val polygons = detector.detect(color, config, dilatationFactor)
            val recognized = recognizer.recognize(gray, polygons, config)
            val entries = recognized.map { result ->
                val vertices = mutableListOf<Vertex>()
                val poly = result.polygon
                for (i in 0 until minOf(poly.size / 2, 4)) {
                    vertices.add(Vertex(poly[i * 2], poly[i * 2 + 1]))
                }
                OcrEntry(result.text, vertices)
            }
            BrainOcrOutput(entries)
        } finally {
            color.release()
            gray.release()
        }
    }
}
