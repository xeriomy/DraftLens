package com.draftlens.vision.heroes

import android.graphics.BitmapFactory
import java.io.File

/**
 * Android-side decoding for the matcher. Everything stays in memory and nothing is
 * written to disk by these functions.
 */
object HeroReferenceImages {

    /** Decode a cached reference asset, downsampled so matching stays cheap. Null when missing/corrupt. */
    fun loadReference(file: File, entry: HeroCatalogEntry): HeroReference? = runCatching {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val sample = (maxOf(options.outWidth, options.outHeight) / MAX_REFERENCE_DIMENSION).coerceAtLeast(1)
        val decoded = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decoded) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        HeroReference(entry, pixels, bitmap.width, bitmap.height)
    }.getOrNull()

    private const val MAX_REFERENCE_DIMENSION = 256
}
