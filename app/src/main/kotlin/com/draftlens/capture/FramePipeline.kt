package com.draftlens.capture

import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.draftlens.models.CapturedFrame
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameOrientation
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.android.BitmapFramePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns ImageReader → Bitmap payload → StateFlow.
 * - maxImages capped at 3; oldest frame dropped on backpressure (no blocking).
 * - Only the latest [CapturedFrame] is published; stale references are cleared on stop/reset.
 * - Bitmaps are not recycled while Compose may still be rendering them; GC reclaims them.
 * - All work off the main thread (dedicated HandlerThread + drop-when-late).
 */
class FramePipeline(
    private val sampler: FrameSampler,
    private val isActive: () -> Boolean = { true }
) {
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var surface: Surface? = null

    private val _latestFrame = MutableStateFlow<CapturedFrame?>(null)
    val latestFrame: StateFlow<CapturedFrame?> = _latestFrame.asStateFlow()

    var displayWidth: Int = 0
        private set
    var displayHeight: Int = 0
        private set

    /**
     * The device display rotation recorded on every frame. Updated independently of the
     * ImageReader size, so a rotation that leaves the capture size unchanged is still
     * reflected on the next frame.
     */
    var displayRotation: FrameRotation = FrameRotation.ROTATION_0
        private set

    /** Records a new display rotation without touching the ImageReader. */
    fun updateDisplayRotation(rotation: FrameRotation) {
        displayRotation = rotation
    }

    fun start(
        width: Int,
        height: Int,
        densityDpi: Int,
        initialDisplayRotation: FrameRotation = FrameRotation.ROTATION_0
    ) {
        stopInternal(release = true)
        displayWidth = width
        displayHeight = height
        displayRotation = initialDisplayRotation
        handlerThread = HandlerThread("draftlens-capture").also { it.start() }
        val handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 3).also { reader ->
            reader.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                if (image == null) return@setOnImageAvailableListener
                val wallStartedAtMs = System.currentTimeMillis()
                try {
                    if (!isActive()) return@setOnImageAvailableListener
                    val sampledAtMs = sampler.currentTimeMs()
                    if (sampler.shouldEmit(sampledAtMs)) {
                        // Reference image only to avoid leaks; copy pixels via Image plane.
                        // Sprint 0 keeps this synchronous and in-memory; no disk, no upload.
                        val payload = imageToPayload(image, width, height)
                        if (payload == null) {
                            Log.w(TAG, "Dropped frame: RGBA_8888 payload conversion failed")
                            return@setOnImageAvailableListener
                        }
                        sampler.markEmitted(sampledAtMs)
                        val processingLatencyMs = System.currentTimeMillis() - wallStartedAtMs
                        val visionFrame = OriginalFrame(
                            id = FrameId.random(),
                            payload = payload,
                            sourceWidth = width,
                            sourceHeight = height,
                            displayRotation = displayRotation,
                            // MediaProjection buffers are display-oriented; never double-rotate pixels.
                            rotation = FrameOrientation.correctionFor(displayRotation),
                            capturedAtElapsedRealtimeMs = sampledAtMs,
                            wallClockTimestampMs = System.currentTimeMillis(),
                            captureLatencyMs = processingLatencyMs
                        )
                        _latestFrame.value = CapturedFrame(
                            frame = visionFrame,
                            processingLatencyMs = processingLatencyMs
                        )
                    }
                } catch (e: Exception) {
                    // Never let a bad frame kill the pipeline; next frame recovers.
                    e.printStackTrace()
                } finally {
                    try { image.close() } catch (_: Exception) {}
                }
            }, handler)
        }
        surface = imageReader!!.surface
        sampler.reset()
    }

    fun surface(): Surface? = surface

    private fun imageToPayload(
        image: android.media.Image,
        width: Int,
        height: Int
    ): BitmapFramePayload? {
        // Direct plane copy for RGBA_8888; null drops the frame.
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return null
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            val bmp = Bitmap.createBitmap(width + (rowPadding / pixelStride), height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            val cropped = if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height).also {
                if (it !== bmp) bmp.recycle()
            }
            BitmapFramePayload(cropped)
        } catch (e: Exception) {
            null
        }
    }

    fun stop() {
        stopInternal(release = true)
        _latestFrame.value = null
    }

    private fun stopInternal(release: Boolean) {
        try { surface?.release() } catch (_: Exception) {}
        surface = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { handlerThread?.quitSafely() } catch (_: Exception) {}
        handlerThread = null
        if (release) {
            // Stale frame references are cleared by stop()/CaptureStateHolder.
        }
    }

    /** Creates a replacement surface without closing the old resources yet. */
    fun prepareResize(
        newWidth: Int,
        newHeight: Int,
        densityDpi: Int,
        initialDisplayRotation: FrameRotation = FrameRotation.ROTATION_0
    ): SurfaceSwitch? {
        if (newWidth == displayWidth && newHeight == displayHeight) return null
        val previousSurface = surface
        val previousReader = imageReader
        val previousThread = handlerThread
        val previousWidth = displayWidth
        val previousHeight = displayHeight

        // Detach the old resources from the pipeline so start() can build the replacement.
        surface = null
        imageReader = null
        handlerThread = null
        displayWidth = 0
        displayHeight = 0

        try {
            start(newWidth, newHeight, densityDpi, initialDisplayRotation)
        } catch (e: Exception) {
            // Discard partially created replacement resources and restore the previous session.
            runCatching { surface?.release() }
            runCatching { imageReader?.close() }
            runCatching { handlerThread?.quitSafely() }
            surface = previousSurface
            imageReader = previousReader
            handlerThread = previousThread
            displayWidth = previousWidth
            displayHeight = previousHeight
            throw e
        }
        val replacement = surface ?: error("Replacement surface was not created")
        var closed = false
        return SurfaceSwitch(replacement) {
            if (closed) return@SurfaceSwitch
            closed = true
            runCatching { previousSurface?.release() }
            runCatching { previousReader?.close() }
            runCatching { previousThread?.quitSafely() }
        }
    }

    data class SurfaceSwitch(
        val surface: Surface,
        val closePrevious: () -> Unit
    )

    private companion object {
        const val TAG = "FramePipeline"
    }
}
