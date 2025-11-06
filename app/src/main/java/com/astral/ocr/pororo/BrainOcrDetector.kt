package com.astral.ocr.pororo

import android.graphics.Bitmap
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File

internal class BrainOcrDetector {

    @Volatile
    private var module: Module? = null

    private fun ensureModule(modelPath: File): Module {
        val current = module
        if (current != null) {
            return current
        }
        val loaded = Module.load(modelPath.absolutePath)
        module = loaded
        return loaded
    }

    fun detect(image: Mat, config: BrainOcrConfig, dilatationFactor: Float): List<FloatArray> {
        val canvasSize = (config.options["canvas_size"] as? Number)?.toInt() ?: 2560
        val magRatio = (config.options["mag_ratio"] as? Number)?.toDouble() ?: 1.0
        val textThreshold = (config.options["text_threshold"] as? Number)?.toFloat() ?: 0.7f
        val linkThreshold = (config.options["link_threshold"] as? Number)?.toFloat() ?: 0.4f
        val lowText = (config.options["low_text"] as? Number)?.toFloat() ?: 0.4f

        val (resized, ratio, _) = ImagePreprocessor.resizeAspectRatio(image, canvasSize, magRatio)
        val ratioH = 1f / ratio.toFloat()
        val ratioW = ratioH

        val bitmap: Bitmap = ImagePreprocessor.matToBitmap(resized)
        val meanRGB = floatArrayOf(0.485f, 0.456f, 0.406f)
        val stdRGB = floatArrayOf(0.229f, 0.224f, 0.225f)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap, meanRGB, stdRGB)
        val batchTensor = inputTensor.unsqueeze(0)

        val module = ensureModule(config.detectorPath)
        val output = module.forward(IValue.from(batchTensor)).toTuple()
        val yTensor = output[0].toTensor()
        val shape = yTensor.shape()
        val h = shape[1].toInt()
        val w = shape[2].toInt()
        val data = yTensor.dataAsFloatArray()
        val scoreText = Mat.zeros(h, w, CvType.CV32F)
        val scoreLink = Mat.zeros(h, w, CvType.CV32F)
        var offset = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                scoreText.put(row, col, data[offset].toDouble())
                scoreLink.put(row, col, data[offset + 1].toDouble())
                offset += 2
            }
        }

        val polys = CraftPostProcessor.getDetPolygons(
            scoreText,
            scoreLink,
            textThreshold,
            linkThreshold,
            lowText,
            dilatationFactor
        )

        val adjusted = polys.map { poly ->
            val adjustedPoly = FloatArray(8)
            for (i in 0 until 4) {
                val x = poly[i * 2] * ratioW
                val y = poly[i * 2 + 1] * ratioH
                adjustedPoly[i * 2] = x
                adjustedPoly[i * 2 + 1] = y
            }
            adjustedPoly
        }

        resized.release()
        scoreText.release()
        scoreLink.release()
        return adjusted
    }
}
