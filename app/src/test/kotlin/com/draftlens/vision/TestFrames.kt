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

/** A real, minimal, decodable 1x1 red PNG (69 bytes). */
fun testRealPng(): ByteArray = byteArrayOf(
    0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
    0x08.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x90.toByte(), 0x77.toByte(), 0x53.toByte(),
    0xDE.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
    0x54.toByte(), 0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00.toByte(),
    0x00.toByte(), 0x03.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0xC9.toByte(), 0xFE.toByte(), 0x92.toByte(),
    0xEF.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(),
    0x44.toByte(), 0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte(),
)

/** A real, minimal, decodable 1x1 green PNG (69 bytes); a distinct asset for cache tests. */
fun testRealGreenPng(): ByteArray = byteArrayOf(
    0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
    0x08.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x90.toByte(), 0x77.toByte(), 0x53.toByte(),
    0xDE.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
    0x54.toByte(), 0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0x60.toByte(), 0xF8.toByte(), 0xCF.toByte(), 0x00.toByte(),
    0x00.toByte(), 0x02.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte(), 0x7B.toByte(), 0x09.toByte(), 0x81.toByte(),
    0x78.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(),
    0x44.toByte(), 0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte(),
)
