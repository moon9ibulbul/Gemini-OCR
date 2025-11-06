package com.astral.ocr.pororo

import android.content.Context
import org.opencv.android.OpenCVLoader
import java.io.File

class PororoOcrEngine(context: Context) {

    private val repository = PororoModelRepository(context.applicationContext)
    private val detector = BrainOcrDetector()
    private val recognizer = BrainOcrRecognizer()

    init {
        if (!OpenCVLoader.initDebug()) {
            throw IllegalStateException("Gagal memuat OpenCV")
        }
    }

    fun runOcr(imageFile: File, dilatationFactor: Float): String {
        val lang = "ko"
        val detectorPath = repository.getDetectorModel(lang)
        val recognizerPath = repository.getRecognizerModel(lang)
        val optionsFile = repository.getOptionsFile(lang)

        val config = buildConfig(lang, optionsFile, detectorPath, recognizerPath)
        val opt = config.options.toMutableMap()
        opt["device"] = "cpu"

        val reader = BrainOcrReader(detector, recognizer, config)
        val rawResult = reader.execute(imageFile, dilatationFactor)
        return PororoResultFormatter.format(rawResult)
    }
}
