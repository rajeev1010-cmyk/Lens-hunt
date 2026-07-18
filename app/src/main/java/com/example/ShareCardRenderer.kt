package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Renders the "Anime Twin Match" share card by drawing dynamic content on top
 * of the animetwin_sharecard.png template. The template has no transparent
 * cutouts (it's a flat reference mockup, not an overlay asset), so every zone
 * below is pixel-scanned from the 941x1672 source and painted over directly.
 * There is no character portrait to place in the "twin" slot (the character
 * database deliberately bundles no copyrighted images), so that slot gets the
 * same gold gradient + initials avatar used elsewhere in the app.
 */
object ShareCardRenderer {

    private const val GOLD = 0xFFFFA800.toInt()
    private const val GOLD_DIM = 0xFFC9821A.toInt()

    private val selfieBox = RectF(57f, 341f, 340f, 757f)
    private val twinBox = RectF(592f, 341f, 880f, 757f)
    private val creatorBox = RectF(57f, 797f, 468f, 892f)
    private val designLanguageBox = RectF(468f, 797f, 881f, 892f)
    private val visualTraitsBox = RectF(57f, 914f, 925f, 1093f)
    private val overviewBox = RectF(57f, 1110f, 468f, 1613f)
    private val principlesBox = RectF(468f, 1110f, 881f, 1613f)

    fun render(context: Context, character: CharacterEntry, selfie: Bitmap?): Bitmap {
        val template = BitmapFactory.decodeResource(context.resources, R.drawable.animetwin_sharecard)
        val output = template.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        if (selfie != null) drawCoverImage(canvas, selfie, selfieBox)
        drawTwinAvatar(canvas, character)

        drawProseBlock(
            canvas, creatorBox,
            listOf(character.designer, character.studio).filter { it.isNotBlank() }
        )
        drawListBlock(canvas, designLanguageBox, character.designLanguage)
        drawListBlock(canvas, visualTraitsBox, character.visualTraits)
        drawProseBlock(
            canvas, overviewBox,
            listOfNotNull(
                "${character.name} — ${character.series}".takeIf { character.name.isNotBlank() },
                character.description.takeIf { it.isNotBlank() }
            )
        )
        drawListBlock(canvas, principlesBox, character.through.ifEmpty { character.communicates })

        return output
    }

    private fun drawTwinAvatar(canvas: Canvas, character: CharacterEntry) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                twinBox.left, twinBox.top, twinBox.right, twinBox.bottom,
                GOLD, GOLD_DIM, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(twinBox, 24f, 24f, paint)

        val initials = character.name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2).joinToString("").uppercase()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 90f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val cy = twinBox.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(initials, twinBox.centerX(), cy, textPaint)
    }

    private fun drawCoverImage(canvas: Canvas, bitmap: Bitmap, box: RectF) {
        val boxAspect = box.width() / box.height()
        val bmpAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val srcRect = if (bmpAspect > boxAspect) {
            val cropWidth = (bitmap.height * boxAspect).toInt().coerceIn(1, bitmap.width)
            val left = (bitmap.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, bitmap.height)
        } else {
            val cropHeight = (bitmap.width / boxAspect).toInt().coerceIn(1, bitmap.height)
            val top = (bitmap.height - cropHeight) / 2
            Rect(0, top, bitmap.width, top + cropHeight)
        }
        canvas.save()
        val path = Path().apply { addRoundRect(box, 24f, 24f, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, srcRect, box, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** Wrapped paragraphs with a blank line between them -- for prose fields. */
    private fun drawProseBlock(canvas: Canvas, box: RectF, paragraphs: List<String>) {
        if (paragraphs.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            textSize = 26f
        }
        val padding = 24f
        val labelReserve = 60f // clears the box's baked-in icon + label row
        val maxWidth = box.width() - padding * 2
        val lineHeight = paint.textSize + 10f
        val bottomLimit = box.bottom - padding

        var y = box.top + labelReserve + paint.textSize
        for (paragraph in paragraphs) {
            for (line in wrapText(paragraph, paint, maxWidth)) {
                if (y > bottomLimit) return
                canvas.drawText(line, box.left + padding, y, paint)
                y += lineHeight
            }
            y += lineHeight * 0.4f
        }
    }

    /** Bulleted short items -- for array-style fields like traits/keywords. */
    private fun drawListBlock(canvas: Canvas, box: RectF, items: List<String>) {
        if (items.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            textSize = 24f
        }
        val padding = 24f
        val labelReserve = 60f
        val maxWidth = box.width() - padding * 2
        val lineHeight = paint.textSize + 10f
        val bottomLimit = box.bottom - padding

        var y = box.top + labelReserve + paint.textSize
        for (item in items) {
            for (line in wrapText("• $item", paint, maxWidth)) {
                if (y > bottomLimit) return
                canvas.drawText(line, box.left + padding, y, paint)
                y += lineHeight
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
