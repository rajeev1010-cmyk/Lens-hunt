package com.example

import android.graphics.Bitmap
import android.graphics.Rect

fun Bitmap.crop(rect: Rect): Bitmap {
    val x = rect.left.coerceAtLeast(0)
    val y = rect.top.coerceAtLeast(0)
    val width = rect.width().coerceAtMost(this.width - x)
    val height = rect.height().coerceAtMost(this.height - y)
    if (width <= 0 || height <= 0) return this
    return Bitmap.createBitmap(this, x, y, width, height)
}
