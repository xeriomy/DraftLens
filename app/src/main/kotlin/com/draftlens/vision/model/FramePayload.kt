package com.draftlens.vision.model

// Plain interface, not sealed: the spec places BitmapFramePayload in vision.model.android and
// Kotlin only allows sealed hierarchies within one package.
interface FramePayload {
    val width: Int
    val height: Int
    val format: PixelFormat
    val byteCountEstimate: Long
}

class PixelArrayFramePayload(
    override val width: Int,
    override val height: Int,
    pixels: IntArray,
    override val format: PixelFormat = PixelFormat.ARGB_8888,
) : FramePayload {
    private val storedPixels: IntArray

    init {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(format == PixelFormat.ARGB_8888) { "unsupported pixel format: $format" }
        require(pixels.size == width * height) {
            "pixel count ${pixels.size} does not match ${width}x$height"
        }
        // Defensive copy: callers may mutate their array after construction.
        storedPixels = pixels.copyOf()
    }

    /** Returns a copy so external mutation cannot corrupt the stored pixels. */
    fun pixels(): IntArray = storedPixels.copyOf()

    override val byteCountEstimate: Long
        get() = width.toLong() * height.toLong() * BYTES_PER_PIXEL

    companion object {
        /** ARGB_8888 stores 4 bytes per pixel. */
        const val BYTES_PER_PIXEL = 4L
    }
}
