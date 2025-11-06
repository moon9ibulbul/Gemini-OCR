package com.astral.ocr.pororo

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import android.graphics.Bitmap
import org.opencv.android.Utils

internal object ImagePreprocessor {

    fun loadColor(file: File): Mat {
        val mat = Imgcodecs.imread(file.absolutePath, Imgcodecs.IMREAD_COLOR)
        if (mat.empty()) {
            throw IllegalArgumentException("Tidak dapat membuka gambar: ${file.absolutePath}")
        }
        return mat
    }

    fun toGray(mat: Mat): Mat {
        val gray = Mat()
        if (mat.channels() == 1) {
            mat.copyTo(gray)
        } else {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        }
        return gray
    }

    fun normalizeMeanVariance(mat: Mat): Mat {
        val mean = doubleArrayOf(0.485, 0.456, 0.406)
        val std = doubleArrayOf(0.229, 0.224, 0.225)
        val result = Mat(mat.size(), CvType.CV_32FC3)
        val floatMat = Mat()
        mat.convertTo(floatMat, CvType.CV_32FC3)
        val meanScalar = Mat(1, 1, CvType.CV_32FC3)
        val meanValues = mean.map { (it * 255.0).toFloat() }.toFloatArray()
        meanScalar.put(0, 0, meanValues)
        val stdScalar = Mat(1, 1, CvType.CV_32FC3)
        val stdValues = std.map { (it * 255.0).toFloat() }.toFloatArray()
        stdScalar.put(0, 0, stdValues)
        Core.subtract(floatMat, meanScalar, result)
        Core.divide(result, stdScalar, result)
        meanScalar.release()
        stdScalar.release()
        floatMat.release()
        return result
    }

    fun resizeAspectRatio(mat: Mat, squareSize: Int, magRatio: Double): Triple<Mat, Double, Size> {
        val height = mat.rows()
        val width = mat.cols()
        var targetSize = magRatio * maxOf(height, width)
        if (targetSize > squareSize) {
            targetSize = squareSize.toDouble()
        }
        val ratio = targetSize / maxOf(height, width)
        val targetH = (height * ratio).toInt()
        val targetW = (width * ratio).toInt()
        val resized = Mat()
        Imgproc.resize(mat, resized, Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        val targetH32 = if (targetH % 32 == 0) targetH else targetH + (32 - targetH % 32)
        val targetW32 = if (targetW % 32 == 0) targetW else targetW + (32 - targetW % 32)
        val canvas = Mat.zeros(targetH32, targetW32, resized.type())
        val roi = canvas.submat(0, resized.rows(), 0, resized.cols())
        resized.copyTo(roi)
        val heatmapSize = Size(targetW32 / 2.0, targetH32 / 2.0)
        resized.release()
        return Triple(canvas, ratio, heatmapSize)
    }

    fun reformatInput(file: File): Pair<Mat, Mat> {
        val color = loadColor(file)
        val gray = toGray(color)
        return color to gray
    }

    fun matToBitmap(mat: Mat): Bitmap {
        val converted = Mat()
        if (mat.channels() == 3) {
            Imgproc.cvtColor(mat, converted, Imgproc.COLOR_BGR2RGB)
        } else {
            mat.copyTo(converted)
        }
        val bitmap = Bitmap.createBitmap(converted.cols(), converted.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(converted, bitmap)
        converted.release()
        return bitmap
    }
}
