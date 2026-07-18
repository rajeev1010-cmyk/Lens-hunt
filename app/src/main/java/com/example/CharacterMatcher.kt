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

    fun findBestMatch(measured: MeasuredAxes, characters: List<CharacterEntry>): MatchResult? =
        findTopMatches(measured, characters, limit = 1).firstOrNull()

    fun findTopMatches(measured: MeasuredAxes, characters: List<CharacterEntry>, limit: Int = 5): List<MatchResult> {
        if (characters.isEmpty()) return emptyList()

        return characters
            .map { character -> character to distance(measured, character.profile) }
            .sortedBy { it.second }
            .take(limit)
            .map { (character, dist) ->
                val similarity = ((1.0 - (dist / MAX_DISTANCE)) * 100.0).coerceIn(0.0, 100.0)
                MatchResult(character, similarity.toInt())
            }
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
