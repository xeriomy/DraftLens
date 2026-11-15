package com.draftlens.vision.model

/**
 * The pixel-orientation correction applied to captured frames.
 *
 * MediaProjection delivers buffers that are already display-oriented, so the correction
 * is always [FrameRotation.ROTATION_0]; the device rotation is recorded separately as
 * [OriginalFrame.displayRotation] for inspection. The rule lives here, in one tested
 * place, so the capture pipeline cannot start double-rotating upright pixels.
 */
object FrameOrientation {

    fun correctionFor(displayRotation: FrameRotation): FrameRotation = FrameRotation.ROTATION_0
}
