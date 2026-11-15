package com.draftlens.vision.model

import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.geometry.PixelRect

/** Common marker for image-bearing frames in the Vision contract. */
interface VisionFrame

/**
 * Immutable acquisition metadata with a non-null payload.
 *
 * [displayRotation] records the device display rotation at acquisition for inspection.
 * [rotation] is only the pixel-orientation correction needed to make pixels upright;
 * MediaProjection buffers are usually already display-oriented, so production sets it to
 * [FrameRotation.ROTATION_0] rather than implicitly double-rotating upright pixels.
 */
data class OriginalFrame(
    val id: FrameId,
    val payload: FramePayload,
    val sourceWidth: Int,
    val sourceHeight: Int,
    /** Display rotation at acquisition, recorded by capture for inspection. */
    val displayRotation: FrameRotation,
    /** Correction needed to make the pixels upright. MediaProjection buffers are usually already display-oriented. */
    val rotation: FrameRotation,
    val capturedAtElapsedRealtimeMs: Long,
    val wallClockTimestampMs: Long,
    val captureLatencyMs: Long?,
) : VisionFrame

data class ProcessedFrame(
    val id: FrameId,
    val sourceId: FrameId,
    val payload: FramePayload,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val displayRotation: FrameRotation,
    val rotation: FrameRotation,
    val capturedAtElapsedRealtimeMs: Long,
    val wallClockTimestampMs: Long,
    val transform: FrameTransform,
    val processingDurationMs: Long,
) : VisionFrame

data class SlotImage(
    val id: FrameId,
    val sourceId: FrameId,
    val slotId: String,
    val payload: FramePayload,
    val sourceRect: PixelRect,
    val createdAtElapsedRealtimeMs: Long,
) : VisionFrame
