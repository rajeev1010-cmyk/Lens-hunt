package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

fun Bitmap.crop(rect: Rect): Bitmap {
    val x = rect.left.coerceAtLeast(0)
    val y = rect.top.coerceAtLeast(0)
    val width = rect.width().coerceAtMost(this.width - x)
    val height = rect.height().coerceAtMost(this.height - y)
    if (width <= 0 || height <= 0) return this
    return Bitmap.createBitmap(this, x, y, width, height)
}

fun Bitmap.mirroredHorizontally(): Bitmap {
    val matrix = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    }
} catch (e: Exception) {
    null
}

/** Saves [bitmap] to the app's cache and launches the system share sheet for it. */
fun shareBitmap(context: Context, bitmap: Bitmap, filename: String) {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(imagesDir, filename)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share your anime twin match"))
}
