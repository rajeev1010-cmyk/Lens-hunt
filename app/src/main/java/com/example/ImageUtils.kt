package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri

fun Bitmap.crop(rect: Rect): Bitmap {
    val x = rect.left.coerceAtLeast(0)
    val y = rect.top.coerceAtLeast(0)
    val width = rect.width().coerceAtMost(this.width - x)
    val height = rect.height().coerceAtMost(this.height - y)
    if (width <= 0 || height <= 0) return this
    return Bitmap.createBitmap(this, x, y, width, height)
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    }
} catch (e: Exception) {
    null
}
