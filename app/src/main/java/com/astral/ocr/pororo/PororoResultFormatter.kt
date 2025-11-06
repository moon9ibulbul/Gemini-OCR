package com.astral.ocr.pororo

import kotlin.math.max
import kotlin.math.min

internal object PororoResultFormatter {

    fun format(output: BrainOcrOutput): String {
        val entries = output.entries
        if (entries.isEmpty()) {
            val description = output.descriptions
            if (description.isEmpty()) return ""
            val text = description.joinToString(" ") { it.trim() }.trim()
            return if (text.isEmpty()) "" else "() : $text"
        }

        val stats = collectStats(entries)
        val lines = entries.mapNotNull { entry ->
            formatEntry(entry, stats)
        }
        return lines.joinToString("\n")
    }

    private fun collectStats(entries: List<OcrEntry>): Stats {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxWidth = 0f
        var maxHeight = 0f
        for (entry in entries) {
            if (entry.vertices.isEmpty()) continue
            val xs = entry.vertices.map { it.x }
            val ys = entry.vertices.map { it.y }
            val width = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
            val height = (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
            minX = min(minX, xs.minOrNull() ?: minX)
            maxX = max(maxX, xs.maxOrNull() ?: maxX)
            minY = min(minY, ys.minOrNull() ?: minY)
            maxY = max(maxY, ys.maxOrNull() ?: maxY)
            maxWidth = max(maxWidth, width)
            maxHeight = max(maxHeight, height)
        }
        val widthSpan = max(maxX - minX, 1f)
        val heightSpan = max(maxY - minY, 1f)
        maxWidth = max(maxWidth, 1f)
        maxHeight = max(maxHeight, 1f)
        return Stats(minX, maxX, minY, maxY, widthSpan, heightSpan, maxWidth, maxHeight)
    }

    private fun formatEntry(entry: OcrEntry, stats: Stats): String? {
        val text = entry.text.trim()
        if (text.isEmpty()) return null
        val xs = entry.vertices.map { it.x }
        val ys = entry.vertices.map { it.y }
        if (xs.isEmpty() || ys.isEmpty()) {
            return "() : $text"
        }
        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 0f
        val minY = ys.minOrNull() ?: 0f
        val maxY = ys.maxOrNull() ?: 0f
        val width = maxX - minX
        val height = maxY - minY
        val aspect = if (height > 0f) width / height else 0f
        val areaRatio = (width * height) / (stats.widthSpan * stats.heightSpan)
        val relWidth = width / stats.maxWidth
        val relHeight = height / stats.maxHeight
        val centerX = (minX + maxX) / 2f
        val edgeMargin = 0.12f * stats.widthSpan
        val wordCount = text.replace("\n", " ").split(" ").count { it.isNotBlank() }
        val charCount = text.length
        val uppercaseRatio = if (charCount == 0) 0f else {
            text.count { it.isLetter() && it.uppercaseChar() == it }.toFloat() / charCount
        }

        var prefix = "() :"
        if (width <= 0f || height <= 0f) {
            prefix = "() :"
        } else if (aspect < 0.7f || charCount <= 4 || uppercaseRatio > 0.6f) {
            prefix = "// :"
        } else if (areaRatio > 0.4f && (centerX - stats.minX < edgeMargin || stats.maxX - centerX < edgeMargin)) {
            prefix = "'' :"
        } else if (wordCount >= 10 || charCount > 40 || relWidth > 0.75f) {
            prefix = "[] :"
        } else if ((minY - stats.minY) < 0.15f * stats.heightSpan && relWidth > 0.4f) {
            prefix = "[] :"
        } else if (relHeight > 0.6f && relWidth < 0.45f) {
            prefix = "'' :"
        }

        return "$prefix $text"
    }

    private data class Stats(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
        val widthSpan: Float,
        val heightSpan: Float,
        val maxWidth: Float,
        val maxHeight: Float
    )
}
