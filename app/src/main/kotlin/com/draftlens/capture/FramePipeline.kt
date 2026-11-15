package com.draftlens.capture

import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.draftlens.models.CapturedFrame
import com.draftlens.models.FrameSnapshots
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

    /**
     * Guards the frozen flag and the latest-frame publication as one unit, so a freeze
     * command can never be crossed by a half-published frame. While frozen the images
     * arriving at the ImageReader are acquired and closed (the reader stays healthy) but
     * no conversion is performed and nothing is published.
     */
    private val freezeGate = Any()

    @Volatile
    private var frozen = false

    /** Result of a freeze request against this pipeline. */
    sealed interface FreezeResult {
        /** A frame was pinned and is ready to store. */
        data class Frozen(val frame: CapturedFrame) : FreezeResult

        /** A frame is already pinned; the caller keeps the existing one. */
        data object AlreadyFrozen : FreezeResult

        /** No captured frame is available to pin yet. */
        data object NoFrame : FreezeResult
    }

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
     *
     * Concurrency guarantee: written by the display listener's thread, read by the
     * ImageReader thread. The property is [Volatile], and the ImageReader thread reads it
     * exactly once per frame (at the `OriginalFrame` construction site in `start()`), so the
     * value stamped on [OriginalFrame.displayRotation] always agrees with the value used to
     * derive the orientation correction — the two can never see different rotations for the
     * same frame. Documented rather than stress-tested: the failure mode of a racy read is a
     * mismatch between these two fields, and this is the only read site.
     */
    @Volatile
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
                    if (frozen) {
                        // Frozen: images are acquired and closed in finally; no conversion, no retention.
                        return@setOnImageAvailableListener
                    }
                    val sampledAtMs = sampler.currentTimeMs()
                    if (sampler.shouldEmit(sampledAtMs)) {
                        // Reference image only to avoid leaks; copy pixels via Image plane.
                        // Sprint 0 keeps this synchronous and in-memory; no disk, no upload.
                        val payload = imageToPayload(image, width, height)
                        if (payload == null) {
                            Log.w(TAG, "Dropped frame: RGBA_8888 payload conversion failed")
                            return@setOnImageAvailableListener
                        }
                        synchronized(freezeGate) {
                            if (frozen) {
                                // A freeze command landed during the conversion; the snapshot is authoritative.
                                Log.d(TAG, "Dropped frame: a freeze command landed during conversion")
                            } else {
                                val processingLatencyMs = System.currentTimeMillis() - wallStartedAtMs
                                // One read per frame: both fields below see the same rotation.
                                val frameDisplayRotation = displayRotation
                                val visionFrame = OriginalFrame(
                                    id = FrameId.random(),
                                    payload = payload,
                                    sourceWidth = width,
                                    sourceHeight = height,
                                    displayRotation = frameDisplayRotation,
                                    // MediaProjection buffers are display-oriented; never double-rotate pixels.
                                    rotation = FrameOrientation.correctionFor(frameDisplayRotation),
                                    capturedAtElapsedRealtimeMs = sampledAtMs,
                                    wallClockTimestampMs = System.currentTimeMillis(),
                                    captureLatencyMs = processingLatencyMs,
                                )
                                sampler.markEmitted(sampledAtMs)
                                _latestFrame.value = CapturedFrame(
                                    frame = visionFrame,
                                    processingLatencyMs = processingLatencyMs
                                )
                            }
                        }
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

    /**
     * Pins the most recently published valid frame and stops publication. Images keep being
     * acquired and closed (the reader and VirtualDisplay stay healthy); no conversion runs
     * until [resumeCapture]. The returned copy shares no payload buffer with the pipeline.
     * An already-frozen pipeline keeps its existing snapshot.
     */
    fun freezeAndSnapshot(): FreezeResult = synchronized(freezeGate) {
        if (frozen) return FreezeResult.AlreadyFrozen
        val frame = _latestFrame.value ?: return FreezeResult.NoFrame
        val copy = FrameSnapshots.copyOf(frame) ?: return FreezeResult.NoFrame
        frozen = true
        FreezeResult.Frozen(copy)
    }

    /** Resumes live publication. No projection or display is recreated. */
    fun resumeCapture() {
        synchronized(freezeGate) { frozen = false }
        sampler.reset()
    }

    /** True while the pipeline is frozen; images flow but nothing is published. */
    val isFrozen: Boolean get() = synchronized(freezeGate) { frozen }

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
