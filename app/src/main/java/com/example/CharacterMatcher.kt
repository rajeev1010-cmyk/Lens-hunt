package com.example

import kotlin.math.sqrt

data class MatchResult(
    val character: CharacterEntry,
    val similarityPercent: Int
)

/**
 * Nearest-neighbor lookup over only the 5 axes FaceAxesExtractor can
 * currently measure reliably (see FaceAxesExtractor.RELIABLE_AXES). The
 * other 7 authored axes per character are ignored until a matching
 * extraction exists for them.
 */
object CharacterMatcher {

    private val MAX_DISTANCE = sqrt(FaceAxesExtractor.RELIABLE_AXES.size.toDouble())

    fun findBestMatch(measured: MeasuredAxes, characters: List<CharacterEntry>): MatchResult? {
        if (characters.isEmpty()) return null

        var best: CharacterEntry? = null
        var bestDistance = Double.MAX_VALUE

        for (character in characters) {
            val d = distance(measured, character.profile)
            if (d < bestDistance) {
                bestDistance = d
                best = character
            }
        }

        val match = best ?: return null
        val similarity = ((1.0 - (bestDistance / MAX_DISTANCE)) * 100.0).coerceIn(0.0, 100.0)
        return MatchResult(match, similarity.toInt())
    }

    private fun distance(measured: MeasuredAxes, profile: VisualAxes): Double {
        val dFaceLength = measured.faceLength - profile.faceLength
        val dEyeNarrowness = measured.eyeNarrowness - profile.eyeNarrowness
        val dSymmetry = measured.symmetry - profile.symmetry
        val dExpression = measured.expressionNeutrality - profile.expressionNeutrality
        val dAngularity = measured.angularity - profile.angularity

        val sumSquares = dFaceLength * dFaceLength +
            dEyeNarrowness * dEyeNarrowness +
            dSymmetry * dSymmetry +
            dExpression * dExpression +
            dAngularity * dAngularity

        return sqrt(sumSquares)
    }
}
