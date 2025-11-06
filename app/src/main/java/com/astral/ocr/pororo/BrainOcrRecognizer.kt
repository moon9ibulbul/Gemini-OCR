package com.astral.ocr.pororo

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal data class RecognizedText(
    val polygon: FloatArray,
    val text: String,
    val confidence: Float
)

internal class BrainOcrRecognizer {

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

    fun recognize(gray: Mat, polygons: List<FloatArray>, config: BrainOcrConfig): List<RecognizedText> {
        if (polygons.isEmpty()) return emptyList()

        val imgH = (config.options["imgH"] as? Number)?.toInt() ?: 64
        val imgW = (config.options["imgW"] as? Number)?.toInt() ?: 256
        val converter = VocabularyConverter(config.vocab)
        val crops = polygons.mapNotNull { cropRegion(gray, it) }
        if (crops.isEmpty()) return emptyList()

        val inputTensor = buildInputTensor(crops, imgH, imgW)
        val module = ensureModule(config.recognizerPath)
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val shape = outputTensor.shape()
        val batchSize = shape[0].toInt()
        val sequenceLength = shape[1].toInt()
        val numClasses = shape[2].toInt()
        val logits = outputTensor.dataAsFloatArray

        val results = mutableListOf<RecognizedText>()
        for (index in 0 until batchSize) {
            val probs = FloatArray(sequenceLength * numClasses)
            for (step in 0 until sequenceLength) {
                var sum = 0.0
                for (cls in 0 until numClasses) {
                    val logit = logits[index * sequenceLength * numClasses + step * numClasses + cls].toDouble()
                    val exp = kotlin.math.exp(logit)
                    probs[step * numClasses + cls] = exp.toFloat()
                    sum += exp
                }
                if (sum > 0) {
                    val inv = 1.0f / sum.toFloat()
                    for (cls in 0 until numClasses) {
                        probs[step * numClasses + cls] *= inv
                    }
                }
            }
            val (decoded, confidence) = converter.decodeGreedy(probs, sequenceLength, numClasses)
            results.add(RecognizedText(polygons[index], decoded, confidence))
        }

        crops.forEach { it.release() }
        return results
    }

    private fun buildInputTensor(crops: List<Mat>, imgH: Int, imgW: Int): Tensor {
        val batchSize = crops.size
        val imageSize = imgH * imgW
        val inputData = FloatArray(batchSize * imageSize)
        for ((index, crop) in crops.withIndex()) {
            val prepared = prepareCrop(crop, imgH, imgW)
            System.arraycopy(prepared, 0, inputData, index * imageSize, prepared.size)
        }
        return Tensor.fromBlob(inputData, longArrayOf(batchSize.toLong(), 1L, imgH.toLong(), imgW.toLong()))
    }

    private fun prepareCrop(crop: Mat, imgH: Int, imgW: Int): FloatArray {
        val floatMat = Mat()
        crop.convertTo(floatMat, CvType.CV_32F)
        val height = floatMat.rows()
        val width = floatMat.cols()
        val ratio = width.toDouble() / height.toDouble()
        val targetW = min(imgW, max(1, ceil(imgH * ratio).toInt()))

        val resized = Mat()
        Imgproc.resize(floatMat, resized, Size(targetW.toDouble(), imgH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        val padded = Mat.zeros(imgH, imgW, CvType.CV_32F)
        val roi = padded.submat(0, imgH, 0, targetW)
        resized.copyTo(roi)
        roi.release()
        if (targetW < imgW) {
            val lastCol = resized.col(max(targetW - 1, 0))
            for (col in targetW until imgW) {
                val dest = padded.submat(0, imgH, col, col + 1)
                lastCol.copyTo(dest)
                dest.release()
            }
            lastCol.release()
        }

        Core.multiply(padded, Scalar.all(1.0 / 255.0), padded)
        val dataDouble = DoubleArray(imgH * imgW)
        padded.get(0, 0, dataDouble)
        val data = FloatArray(imgH * imgW)
        for (i in data.indices) {
            data[i] = dataDouble[i].toFloat()
        }

        floatMat.release()
        resized.release()
        padded.release()
        return data
    }

    private fun cropRegion(gray: Mat, polygon: FloatArray): Mat? {
        if (polygon.size < 8) return null
        val src = MatOfPoint2f(
            Point(polygon[0].toDouble(), polygon[1].toDouble()),
            Point(polygon[2].toDouble(), polygon[3].toDouble()),
            Point(polygon[4].toDouble(), polygon[5].toDouble()),
            Point(polygon[6].toDouble(), polygon[7].toDouble())
        )

        val widthA = distance(polygon[4], polygon[5], polygon[6], polygon[7])
        val widthB = distance(polygon[2], polygon[3], polygon[0], polygon[1])
        val maxWidth = max(1, max(widthA, widthB).toInt())
        val heightA = distance(polygon[2], polygon[3], polygon[4], polygon[5])
        val heightB = distance(polygon[0], polygon[1], polygon[6], polygon[7])
        val maxHeight = max(1, max(heightA, heightB).toInt())

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )

        val m = Imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        Imgproc.warpPerspective(gray, warped, m, Size(maxWidth.toDouble(), maxHeight.toDouble()), Imgproc.INTER_CUBIC)
        src.release()
        dst.release()
        m.release()
        return warped
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        return sqrt((x1 - x2).toDouble().pow(2.0) + (y1 - y2).toDouble().pow(2.0))
    }
}
