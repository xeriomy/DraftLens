package com.draftlens.vision

import com.draftlens.models.CapturedFrame
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload

fun testCapturedFrame(): CapturedFrame = CapturedFrame(
    frame = OriginalFrame(
        id = FrameId("test-frame"),
        payload = PixelArrayFramePayload(2, 2, intArrayOf(1, 2, 3, 4)),
        sourceWidth = 2,
        sourceHeight = 2,
        displayRotation = FrameRotation.ROTATION_90,
        rotation = FrameRotation.ROTATION_0,
        capturedAtElapsedRealtimeMs = 10_000,
        wallClockTimestampMs = 1_700_000_000_000,
        captureLatencyMs = 4,
    ),
    processingLatencyMs = 12,
)
