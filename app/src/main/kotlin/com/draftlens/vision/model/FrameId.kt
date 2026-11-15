package com.draftlens.vision.model

import java.util.UUID

@JvmInline value class FrameId(val value: String) {
    companion object {
        fun random(): FrameId = FrameId(UUID.randomUUID().toString())
    }
}
