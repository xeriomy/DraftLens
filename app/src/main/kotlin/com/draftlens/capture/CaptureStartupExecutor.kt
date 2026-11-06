package com.draftlens.capture

/**
 * Executes the mandatory capture-start sequence in the only safe order:
 * 1. Promote the service to a typed foreground service.
 * 2. Obtain MediaProjection only after promotion succeeds.
 * 3. Initialize the projection only after it is obtained.
 *
 * Android does not tolerate the steps running out of order, so the executor keeps the
 * sequence in one place for tests and for [CaptureSessionService].
 */
internal class CaptureStartupExecutor<P> {
    fun execute(
        promoteToForeground: () -> Unit,
        obtainProjection: () -> P,
        initializeProjection: (P) -> Unit
    ): P {
        promoteToForeground()
        val projection = obtainProjection()
        initializeProjection(projection)
        return projection
    }
}
