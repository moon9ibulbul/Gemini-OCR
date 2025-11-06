package com.astral.ocr.pororo

internal class VocabularyConverter(private val vocab: List<String>) {

    private val blankIndex = 0

    fun decodeGreedy(probabilities: FloatArray, length: Int, numClasses: Int): Pair<String, Float> {
        val builder = StringBuilder()
        var lastIndex = -1
        var confidence = 1.0f
        for (step in 0 until length) {
            var bestIndex = 0
            var bestProb = 0f
            val base = step * numClasses
            for (cls in 0 until numClasses) {
                val prob = probabilities[base + cls]
                if (prob > bestProb) {
                    bestProb = prob
                    bestIndex = cls
                }
            }
            if (bestIndex != blankIndex && bestIndex != lastIndex) {
                val symbol = vocab.getOrNull(bestIndex) ?: ""
                builder.append(symbol)
                confidence *= bestProb
            }
            lastIndex = bestIndex
        }
        return builder.toString() to confidence
    }
}
