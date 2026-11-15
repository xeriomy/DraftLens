package com.draftlens.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure conversion between an RGBA_8888 image plane and tightly packed ARGB pixels.
 *
 * Handles every layout MediaProjection can deliver in practice without stretching,
 * inventing or discarding pixels:
 * - padded rows (`rowStride > imageWidth * pixelStride`),
 * - `cropRect` offsets and partial content (letterboxed single-app capture),
 * - non-square landscape/portrait sizes,
 * - any `pixelStride >= 1` (RGBA_8888 is always 4; the math is generic),
 * - little-endian and big-endian plane buffers.
 *
 * Byte order per pixel follows the buffer's own order: little-endian reads R,G,B,A;
 * big-endian reads A,R,G,B. Never mutates or repositions the source buffer. Returns null
 * on malformed input so the frame is dropped instead of producing a partial image.
 */
internal object RgbaPlaneCopy {

    /** RGBA_8888 stores 4 bytes per pixel. */
    const val RGBA_PIXEL_BYTES = 4

    /** A validated plane layout, used for the conversion and for structured logs. */
    data class PlaneLayout(
        val imageWidth: Int,
        val imageHeight: Int,
        val rowStride: Int,
        val pixelStride: Int,
        val rowPaddingBytes: Int,
        val cropLeft: Int,
        val cropTop: Int,
        val cropWidth: Int,
        val cropHeight: Int,
    ) {
        val rowPaddingPixels: Int get() = rowPaddingBytes / pixelStride

        /** True when the crop covers the whole image. */
        val isFullCrop: Boolean
            get() = cropLeft == 0 && cropTop == 0 &&
                cropWidth == imageWidth && cropHeight == imageHeight
    }

    /**
     * Validates [imageWidth] x [imageHeight] against [rowStride]/[pixelStride] and a
     * crop rectangle given as `[left, top, right, bottom)`. Returns null on any mismatch.
     */
    fun layout(
        imageWidth: Int,
        imageHeight: Int,
        rowStride: Int,
        pixelStride: Int,
        cropLeft: Int,
        cropTop: Int,
        cropRight: Int,
        cropBottom: Int,
    ): PlaneLayout? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (pixelStride <= 0) return null
        if (rowStride < imageWidth.toLong() * pixelStride) return null
        if (rowStride.toLong() * imageHeight > Int.MAX_VALUE) return null
        if (cropRight <= cropLeft || cropBottom <= cropTop) return null
        if (cropLeft < 0 || cropTop < 0 || cropRight > imageWidth || cropBottom > imageHeight) return null
        return PlaneLayout(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rowStride = rowStride,
            pixelStride = pixelStride,
            rowPaddingBytes = rowStride - imageWidth * pixelStride,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropWidth = cropRight - cropLeft,
            cropHeight = cropBottom - cropTop,
        )
    }

    /**
     * Copies the crop region out of [buffer] into a tightly packed ARGB_8888 pixel array,
     * row by row. Padding bytes are skipped; pixels outside the crop are never read.
     * The result is laid out row-major with [PlaneLayout.cropWidth] columns.
     */
    fun copyArgb(buffer: ByteBuffer, layout: PlaneLayout): IntArray? {
        if (layout.cropWidth <= 0 || layout.cropHeight <= 0) return null
        val needsRead = (layout.cropTop.toLong() + layout.cropHeight) * layout.rowStride
        // Absolute reads do not depend on the buffer's position; the limit is the real end.
        if (buffer.limit().toLong() < needsRead) return null
        val bigEndian = buffer.order() == ByteOrder.BIG_ENDIAN
        val out = IntArray(layout.cropWidth * layout.cropHeight)
        val stride = layout.rowStride
        val pixelStride = layout.pixelStride
        val cropLeftBytes = layout.cropLeft * pixelStride
        for (y in 0 until layout.cropHeight) {
            val rowStart = (layout.cropTop + y) * stride + cropLeftBytes
            val outRow = y * layout.cropWidth
            for (x in 0 until layout.cropWidth) {
                val address = rowStart + x * pixelStride
                val r: Int
                val g: Int
                val b: Int
                val a: Int
                if (bigEndian) {
                    a = buffer.get(address).toInt() and 0xFF
                    r = buffer.get(address + 1).toInt() and 0xFF
                    g = buffer.get(address + 2).toInt() and 0xFF
                    b = buffer.get(address + 3).toInt() and 0xFF
                } else {
                    r = buffer.get(address).toInt() and 0xFF
                    g = buffer.get(address + 1).toInt() and 0xFF
                    b = buffer.get(address + 2).toInt() and 0xFF
                    a = buffer.get(address + 3).toInt() and 0xFF
                }
                // Merge into the ARGB int this codebase uses (0xAARRGGBB).
                out[outRow + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }
}
