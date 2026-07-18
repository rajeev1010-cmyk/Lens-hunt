package com.example

import android.graphics.PointF
import android.graphics.RectF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The five VisualAxes this prototype trusts enough to match on. The other
 * seven axes in the character database (jawSharpness, browWeight,
 * hairDarkness, hairVolume, contrast, glasses, warmth) need pixel/hair
 * segmentation or a dedicated classifier ML Kit doesn't provide, and are
 * deliberately left out until that extraction exists.
 */
data class MeasuredAxes(
    val faceLength: Double,
    val eyeNarrowness: Double,
    val symmetry: Double,
    val expressionNeutrality: Double,
    val angularity: Double
)

object FaceAxesExtractor {

    val RELIABLE_AXES = listOf("faceLength", "eyeNarrowness", "symmetry", "expressionNeutrality", "angularity")

    fun extract(face: Face): MeasuredAxes? {
        val faceOval = face.getContour(FaceContour.FACE)?.points ?: return null
        val leftEye = face.getContour(FaceContour.LEFT_EYE)?.points ?: return null
        val rightEye = face.getContour(FaceContour.RIGHT_EYE)?.points ?: return null
        if (faceOval.isEmpty() || leftEye.isEmpty() || rightEye.isEmpty()) return null

        val ovalBounds = bounds(faceOval)
        val ovalWidth = ovalBounds.width().toDouble()
        val ovalHeight = ovalBounds.height().toDouble()
        if (ovalWidth <= 0.0 || ovalHeight <= 0.0) return null

        // Face-oval contours from ML Kit are typically ~1.0-1.8x taller than wide.
        val faceLength = normalize(ovalHeight / ovalWidth, 1.0, 1.8)

        val leftEyeBounds = bounds(leftEye)
        val rightEyeBounds = bounds(rightEye)
        val leftEyeAspect = safeAspect(leftEyeBounds)
        val rightEyeAspect = safeAspect(rightEyeBounds)
        if (leftEyeAspect == null || rightEyeAspect == null) return null
        // Narrower eyes have a smaller height/width ratio; typical range ~0.25-0.55.
        val eyeNarrowness = normalize(1.0 - ((leftEyeAspect + rightEyeAspect) / 2.0), 0.45, 0.75)

        val midlineX = ovalBounds.centerX().toDouble()
        val leftDist = abs(leftEyeBounds.centerX() - midlineX)
        val rightDist = abs(rightEyeBounds.centerX() - midlineX)
        val maxDist = maxOf(leftDist, rightDist).coerceAtLeast(1.0)
        val symmetry = 1.0 - (abs(leftDist - rightDist) / maxDist)

        val smiling = face.smilingProbability?.toDouble() ?: 0.5
        val expressionNeutrality = 1.0 - smiling

        val angularity = ovalAngularity(faceOval, ovalBounds)

        return MeasuredAxes(
            faceLength = faceLength.coerceIn(0.0, 1.0),
            eyeNarrowness = eyeNarrowness.coerceIn(0.0, 1.0),
            symmetry = symmetry.coerceIn(0.0, 1.0),
            expressionNeutrality = expressionNeutrality.coerceIn(0.0, 1.0),
            angularity = angularity.coerceIn(0.0, 1.0)
        )
    }

    private fun safeAspect(rect: RectF): Double? {
        val w = rect.width().toDouble()
        val h = rect.height().toDouble()
        if (w <= 0.0 || h <= 0.0) return null
        return h / w
    }

    /**
     * Coefficient of variation of each contour point's distance from the oval
     * centroid: a round face traces a near-constant radius (low variation);
     * a sharp jawline/angular face swings the radius more (high variation).
     * Empirically ML Kit face ovals sit roughly in the 0.05-0.20 band.
     */
    private fun ovalAngularity(points: List<PointF>, ovalBounds: RectF): Double {
        if (points.size < 8) return 0.5
        val cx = ovalBounds.centerX().toDouble()
        val cy = ovalBounds.centerY().toDouble()
        val radii = points.map { p -> hypot(p.x - cx, p.y - cy) }
        val mean = radii.average()
        if (mean <= 0.0) return 0.5
        val variance = radii.sumOf { (it - mean) * (it - mean) } / radii.size
        val coeffVariation = sqrt(variance) / mean
        return normalize(coeffVariation, 0.05, 0.20)
    }

    private fun bounds(points: List<PointF>): RectF {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return RectF(minX, minY, maxX, maxY)
    }

    private fun normalize(value: Double, min: Double, max: Double): Double {
        if (max <= min) return 0.5
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }
}
