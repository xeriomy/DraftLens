package com.draftlens.vision.heroes

import android.graphics.BitmapFactory

/**
 * Decode-based validator for cached reference images on a device: it parses the header,
 * then fully decodes (downsampled when huge) to prove the bytes are a readable image.
 * The decode is released immediately; nothing is retained.
 */
object AndroidReferenceImageValidator : ReferenceImageValidator {

    override fun isDecodable(bytes: ByteArray): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            false
        } else {
            val decode = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight)
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
            if (decoded == null) {
                false
            } else {
                try {
                    true
                } finally {
                    decoded.recycle()
                }
            }
        }
    } catch (e: Exception) {
        false
    }

    private fun sampleFor(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_DECODE_DIMENSION) sample *= 2
        return sample
    }

    private const val MAX_DECODE_DIMENSION = 1024
}
