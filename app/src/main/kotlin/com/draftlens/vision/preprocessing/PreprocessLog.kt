package com.draftlens.vision.preprocessing

/**
 * Diagnostics seam for the preprocessing path. Production wires [sink] to `android.util.Log`
 * in `DraftLensApp`; unit tests keep the default no-op, so the pure preprocessors stay
 * platform-free and testable. Never logs pixels, only dimensions and geometry.
 */
internal object PreprocessLog {
    var sink: (String) -> Unit = {}

    fun write(message: String) = sink(message)
}
