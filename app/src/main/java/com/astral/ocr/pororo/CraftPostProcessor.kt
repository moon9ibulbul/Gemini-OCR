package com.astral.ocr.pororo

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal object CraftPostProcessor {

    fun getDetPolygons(
        scoreText: Mat,
        scoreLink: Mat,
        textThreshold: Float,
        linkThreshold: Float,
        lowText: Float,
        dilatationFactor: Float
    ): List<FloatArray> {
        val textScore = threshold(scoreText, lowText)
        val linkScore = threshold(scoreLink, linkThreshold)
        val textScoreComb = Mat()
        Core.add(textScore, linkScore, textScoreComb)
        Imgproc.threshold(textScoreComb, textScoreComb, 0.0, 1.0, Imgproc.THRESH_TRUNC)

        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val nLabels = Imgproc.connectedComponentsWithStats(
            textScoreComb.convertToByte(),
            labels,
            stats,
            centroids,
            4,
            CvType.CV_32S
        )

        val polys = mutableListOf<FloatArray>()
        val imgH = scoreText.rows()
        val imgW = scoreText.cols()

        for (label in 1 until nLabels) {
            val size = stats.get(label, Imgproc.CC_STAT_AREA)?.getOrNull(0)?.toInt() ?: 0
            if (size < 10) continue

            val mask = Mat.zeros(imgH, imgW, CvType.CV_8U)
            for (y in 0 until imgH) {
                for (x in 0 until imgW) {
                    if (labels.get(y, x)[0].toInt() == label) {
                        mask.put(y, x, 255.0)
                    }
                }
            }

            if (maxPixel(scoreText, labels, label) < textThreshold) {
                mask.release()
                continue
            }

            val segMap = mask.clone()
            val linkMask = Mat()
            Imgproc.threshold(linkScore, linkMask, 0.999, 1.0, Imgproc.THRESH_BINARY)
            val textMask = Mat()
            Imgproc.threshold(textScore, textMask, 0.999, 1.0, Imgproc.THRESH_BINARY_INV)
            Core.bitwise_and(linkMask, textMask, linkMask)
            Core.subtract(segMap, linkMask, segMap)

            val statsRow = stats.row(label)
            val x = statsRow.get(0, Imgproc.CC_STAT_LEFT)[0].toInt()
            val y = statsRow.get(0, Imgproc.CC_STAT_TOP)[0].toInt()
            val w = statsRow.get(0, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val h = statsRow.get(0, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            val niter = sqrt(size * min(w, h).toDouble() / (w * h).toDouble()).times(2.0 * dilatationFactor).toInt()
            val sx = max(0, x - niter)
            val sy = max(0, y - niter)
            val ex = min(imgW, x + w + niter + 1)
            val ey = min(imgH, y + h + niter + 1)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size((1 + niter).toDouble(), (1 + niter).toDouble()))
            val roi = segMap.submat(sy, ey, sx, ex)
            Imgproc.dilate(roi, roi, kernel)
            roi.release()
            kernel.release()

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(segMap, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            if (contours.isEmpty()) {
                mask.release()
                segMap.release()
                linkMask.release()
                textMask.release()
                continue
            }

            val contour = contours.maxByOrNull { Imgproc.contourArea(it) }
            if (contour == null) {
                mask.release()
                segMap.release()
                linkMask.release()
                textMask.release()
                continue
            }
            val contour2f = MatOfPoint2f(*contour.toArray())
            val rect: RotatedRect = Imgproc.minAreaRect(contour2f)
            val box = arrayOfNulls<Point>(4)
            rect.points(box)
            val pts = box.map { Point(it!!.x, it.y) }.toTypedArray()

            val wRect = distance(pts[0], pts[1])
            val hRect = distance(pts[1], pts[2])
            val boxRatio = max(wRect, hRect) / (min(wRect, hRect) + 1e-5)
            if (abs(1 - boxRatio) <= 0.1) {
                val xs = contour.toArray().map { it.x }
                val ys = contour.toArray().map { it.y }
                val l = xs.minOrNull() ?: 0.0
                val r = xs.maxOrNull() ?: 0.0
                val t = ys.minOrNull() ?: 0.0
                val b = ys.maxOrNull() ?: 0.0
                polys.add(
                    floatArrayOf(
                        l.toFloat(), t.toFloat(),
                        r.toFloat(), t.toFloat(),
                        r.toFloat(), b.toFloat(),
                        l.toFloat(), b.toFloat()
                    )
                )
            } else {
                pts.sortBy { it.x + it.y }
                val ordered = arrayOf(pts[0], pts[1], pts[3], pts[2])
                val poly = FloatArray(8)
                ordered.forEachIndexed { index, point ->
                    poly[index * 2] = point.x.toFloat()
                    poly[index * 2 + 1] = point.y.toFloat()
                }
                polys.add(poly)
            }

            mask.release()
            segMap.release()
            linkMask.release()
            textMask.release()
            contour.release()
            contour2f.release()
        }

        textScore.release()
        linkScore.release()
        textScoreComb.release()
        labels.release()
        stats.release()
        centroids.release()

        return polys
    }

    private fun maxPixel(score: Mat, labels: Mat, label: Int): Float {
        var maxVal = 0f
        for (y in 0 until score.rows()) {
            for (x in 0 until score.cols()) {
                if (labels.get(y, x)[0].toInt() == label) {
                    maxVal = max(maxVal, score.get(y, x)[0].toFloat())
                }
            }
        }
        return maxVal
    }

    private fun threshold(src: Mat, thresh: Float): Mat {
        val result = Mat()
        Imgproc.threshold(src, result, thresh.toDouble(), 1.0, Imgproc.THRESH_BINARY)
        return result
    }

    private fun distance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    private fun Mat.convertToByte(): Mat {
        val out = Mat(rows(), cols(), CvType.CV_8UC1)
        Imgproc.threshold(this, out, 0.0, 255.0, Imgproc.THRESH_BINARY)
        return out
    }
}
