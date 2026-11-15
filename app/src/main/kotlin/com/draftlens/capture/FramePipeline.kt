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

    /**
     * Bumped every time a new reader/surface generation is created (start/resize).
     * Images captured with an older generation are dropped, so a late callback from a
     * released reader can never publish into the replacement reader's frame stream.
     */
    private val generation = GenerationCounter()

    /** Current capture generation; exposed for structured logs only. */
    internal fun peekGeneration(): Long = generation.current()

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
        val generationAtStart = generation.bump()
        Log.i(TAG, "pipeline start: width=$width height=$height density=$densityDpi rotation=$initialDisplayRotation generation=$generationAtStart")

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
                    if (!generation.isCurrent(generationAtStart)) {
                        // A newer reader/surface generation owns the pipeline; drop late images.
                        return@setOnImageAvailableListener
                    }
                    if (frozen) {
                        // Frozen: images are acquired and closed in finally; no conversion, no retention.
                        return@setOnImageAvailableListener
                    }
                    val sampledAtMs = sampler.currentTimeMs()
                    if (sampler.shouldEmit(sampledAtMs)) {
                        // Reference image only to avoid leaks; pixels are copied via the plane.
                        // Sprint 0 keeps this synchronous and in-memory; no disk, no upload.
                        val payload = imageToPayload(image, generationAtStart)
                        if (payload == null) {
                            Log.w(TAG, "generation=$generationAtStart dropped frame: payload conversion failed")
                            return@setOnImageAvailableListener
                        }
                        synchronized(freezeGate) {
                            if (frozen) {
                                // A freeze command landed during the conversion; the snapshot is authoritative.
                                Log.d(TAG, "generation=$generationAtStart dropped frame: a freeze command landed during conversion")
                            } else if (!generation.isCurrent(generationAtStart)) {
                                // An even newer generation took over while converting.
                                Log.d(TAG, "generation=$generationAtStart dropped frame: superseded by generation=${generation.current()} during conversion")
                            } else {
                                val processingLatencyMs = System.currentTimeMillis() - wallStartedAtMs
                                // One read per frame: both fields below see the same rotation.
                                val frameDisplayRotation = displayRotation
                                val visionFrame = OriginalFrame(
                                    id = FrameId.random(),
                                    payload = payload,
                                    sourceWidth = payload.width,
                                    sourceHeight = payload.height,
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
                                Log.d(TAG, "generation=$generationAtStart frame stored: source=${payload.width}x${payload.height} rotation=$frameDisplayRotation processingMs=$processingLatencyMs")
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
        Log.i(TAG, "generation=${generation.current()} frame pinned: ${frame.sourceWidth}x${frame.sourceHeight} id=${frame.id.value}")
        FreezeResult.Frozen(copy)
    }

    /** Resumes live publication. No projection or display is recreated. */
    fun resumeCapture() {
        synchronized(freezeGate) { frozen = false }
        sampler.reset()
        Log.i(TAG, "generation=${generation.current()} resumed live capture")
    }

    /** True while the pipeline is frozen; images flow but nothing is published. */
    val isFrozen: Boolean get() = synchronized(freezeGate) { frozen }

    /**
     * Copies one Image's pixels into a new ARGB_8888 bitmap, honoring the plane layout,
     * padded rows and the image's cropRect (full-frame is the common case). Never logs or
     * stores image contents. Returns null when the plane or crop is malformed so the
     * frame is dropped instead of producing a shifted or partial image.
     */
    private fun imageToPayload(image: android.media.Image, generation: Long): BitmapFramePayload? {
        return try {
            val planes = image.planes
            if (planes.size != 1) {
                Log.w(TAG, "generation=$generation dropped frame: ${planes.size} planes (expected 1 for RGBA_8888)")
                return null
            }
            val plane = planes[0]
            val crop = image.cropRect
            val layout = RgbaPlaneCopy.layout(
                imageWidth = image.width,
                imageHeight = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                cropLeft = crop.left,
                cropTop = crop.top,
                cropRight = crop.right,
                cropBottom = crop.bottom,
            )
            if (layout == null) {
                Log.w(
                    TAG,
                    "generation=$generation dropped frame: malformed layout " +
                        "image=${image.width}x${image.height} " +
                        "crop=${crop.left},${crop.top}..${crop.right},${crop.bottom} " +
                        "rowStride=${plane.rowStride} pixelStride=${plane.pixelStride}",
                )
                return null
            }
            val argb = RgbaPlaneCopy.copyArgb(plane.buffer, layout)
            if (argb == null) {
                Log.w(TAG, "generation=$generation dropped frame: plane copy failed for layout=$layout")
                return null
            }
            Log.d(
                TAG,
                "generation=$generation plane: image=${layout.imageWidth}x${layout.imageHeight} " +
                    "crop=${layout.cropLeft},${layout.cropTop} ${layout.cropWidth}x${layout.cropHeight} " +
                    "rowStride=${layout.rowStride} pixelStride=${layout.pixelStride} " +
                    "paddingBytes=${layout.rowPaddingBytes} fullCrop=${layout.isFullCrop} bufferBytes=${plane.buffer.remaining()}",
            )
            val bitmap = Bitmap.createBitmap(argb, layout.cropWidth, layout.cropHeight, Bitmap.Config.ARGB_8888)
            Log.d(TAG, "generation=$generation bitmap: ${bitmap.width}x${bitmap.height}")
            BitmapFramePayload(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "generation=$generation dropped frame: conversion threw", e)
            null
        }
    }

    fun stop() {
        Log.i(TAG, "pipeline stop: generation=${generation.current()} releasing surface/reader/thread")
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

        Log.i(TAG, "pipeline resize: detaching previous generation=${generation.current()} (${previousWidth}x${previousHeight}) for replacement ${newWidth}x${newHeight}")

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
            Log.i(TAG, "pipeline resize: releasing previous surface/reader/thread (generation ${generation.current() - 1} superseded)")
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
