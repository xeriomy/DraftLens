package com.draftlens.feature.visionlab

import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.calibration.CalibrationLoadResult
import com.draftlens.vision.calibration.CalibrationProfile
import com.draftlens.vision.calibration.CalibrationRepository
import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.OrientationCategory
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.model.ProcessedFrame
import com.draftlens.vision.model.VisionFrameSource
import com.draftlens.vision.model.VisionSourceState
import com.draftlens.vision.preprocessing.FramePreprocessor
import com.draftlens.vision.preprocessing.PreprocessFailure
import com.draftlens.vision.preprocessing.PreprocessFailureCode
import com.draftlens.vision.preprocessing.ProcessFrameRequest
import com.draftlens.vision.preprocessing.ProcessFrameResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VisionLabViewModelTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    // region fakes

    private class FakeVisionFrameSource(initial: OriginalFrame? = null) : VisionFrameSource {
        private val _frames = MutableStateFlow(initial)
        override val frames: StateFlow<OriginalFrame?> = _frames.asStateFlow()
        private val _state = MutableStateFlow(VisionSourceState.CAPTURING)
        override val state: StateFlow<VisionSourceState> = _state.asStateFlow()

        fun emit(frame: OriginalFrame?) {
            _frames.value = frame
        }
    }

    private class FakeCalibrationRepository(
        initial: CalibrationLoadResult = CalibrationLoadResult.Success(emptyList()),
    ) : CalibrationRepository {
        private val _profiles = MutableStateFlow(initial)
        override val profiles: Flow<CalibrationLoadResult> = _profiles.asStateFlow()
        var upsertCount = 0
        var clearCount = 0
        var lastUpserted: CalibrationProfile? = null

        override suspend fun upsert(profile: CalibrationProfile): Result<Unit> {
            upsertCount++
            lastUpserted = profile
            val current = _profiles.value
            if (current !is CalibrationLoadResult.Success) {
                return Result.failure(IllegalStateException("unsupported schema"))
            }
            _profiles.value = CalibrationLoadResult.Success(
                current.profiles.filterNot { it.profileId == profile.profileId } + profile,
            )
            return Result.success(Unit)
        }

        override suspend fun delete(profileId: String): Result<Unit> {
            val current = _profiles.value
            if (current !is CalibrationLoadResult.Success) {
                return Result.failure(IllegalStateException("unsupported schema"))
            }
            _profiles.value = CalibrationLoadResult.Success(current.profiles.filterNot { it.profileId == profileId })
            return Result.success(Unit)
        }

        override suspend fun clearDocument(): Result<Unit> {
            clearCount++
            _profiles.value = CalibrationLoadResult.Success(emptyList())
            return Result.success(Unit)
        }
    }

    private class EchoPreprocessor : FramePreprocessor {
        override fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult =
            ProcessFrameResult.Success(processedFrame(input))
    }

    private class FailingPreprocessor : FramePreprocessor {
        override fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult =
            ProcessFrameResult.Failure(
                PreprocessFailure(PreprocessFailureCode.RENDER_FAILED, "render blew up"),
            )
    }

    /** Blocks the first call until released, so a stale result can arrive late. */
    private class BlockingFakePreprocessor : FramePreprocessor {
        private val gate = CountDownLatch(1)
        private val firstReturned = CountDownLatch(1)
        private var calls = 0

        override fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult {
            calls++
            if (calls == 1) {
                gate.await()
                firstReturned.countDown()
            }
            return ProcessFrameResult.Success(processedFrame(input))
        }

        fun releaseFirst() {
            gate.countDown()
        }

        fun awaitFirstReturned(): Boolean = firstReturned.await(5, TimeUnit.SECONDS)
    }

    // endregion

    private fun originalFrame(id: String, width: Int = 4, height: Int = 2) = OriginalFrame(
        id = FrameId(id),
        payload = PixelArrayFramePayload(width, height, IntArray(width * height) { it }),
        sourceWidth = width,
        sourceHeight = height,
        displayRotation = FrameRotation.ROTATION_0,
        rotation = FrameRotation.ROTATION_0,
        capturedAtElapsedRealtimeMs = 10_000,
        wallClockTimestampMs = 1_700_000_000_000,
        captureLatencyMs = 4,
    )

    private fun processedFrame(input: OriginalFrame) = ProcessedFrame(
        id = FrameId("${input.id.value}-processed"),
        sourceId = input.id,
        payload = input.payload,
        sourceWidth = input.sourceWidth,
        sourceHeight = input.sourceHeight,
        displayRotation = input.displayRotation,
        rotation = input.rotation,
        capturedAtElapsedRealtimeMs = input.capturedAtElapsedRealtimeMs,
        wallClockTimestampMs = input.wallClockTimestampMs,
        transform = FrameTransform.create(
            sourceWidth = input.sourceWidth,
            sourceHeight = input.sourceHeight,
            rotation = input.rotation,
            crop = null,
            processedWidth = input.sourceWidth,
            processedHeight = input.sourceHeight,
        ),
        processingDurationMs = 5,
    )

    @Test fun `live frame is exposed in the ui state`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        assertEquals("live", vm.uiState.value.liveFrame?.id?.value)
        assertEquals(VisionSourceState.CAPTURING, vm.uiState.value.sourceState)
    }

    @Test fun `freeze pins the current frame while live frames continue`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("first"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        vm.freeze()
        source.emit(originalFrame("second"))

        assertEquals("first", vm.uiState.value.frozenFrame?.id?.value)
        assertEquals("first", vm.uiState.value.displayFrame?.id?.value)
        assertTrue(vm.uiState.value.isFrozen)
    }

    @Test fun `resume releases the pinned frame`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("first"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        vm.freeze()
        vm.resume()

        assertNull(vm.uiState.value.frozenFrame)
        assertEquals("first", vm.uiState.value.displayFrame?.id?.value)
    }

    @Test fun `clear releases the pinned frame and the processed frame`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("first"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        vm.freeze()
        vm.processFrame()
        withTimeout(10_000) { vm.uiState.first { it.processed != null } }

        vm.clear()

        assertNull(vm.uiState.value.frozenFrame)
        assertNull(vm.uiState.value.processed)
    }

    @Test fun `process frame publishes the processed frame`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        vm.processFrame()
        val state = withTimeout(10_000) { vm.uiState.first { it.processed != null } }

        assertEquals("live", state.processed?.sourceId?.value)
        assertEquals(ProcessStatus.SUCCESS, state.processStatus)
    }

    @Test fun `stale processing result is discarded after a newer request`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("first"))
        val preprocessor = BlockingFakePreprocessor()
        val vm = VisionLabViewModel(source, preprocessor, FakeCalibrationRepository())

        vm.processFrame()
        source.emit(originalFrame("second"))
        vm.processFrame()

        val published = withTimeout(10_000) { vm.uiState.first { it.processed != null } }
        assertEquals("second", published.processed?.sourceId?.value)

        preprocessor.releaseFirst()
        assertTrue("the first call never returned", preprocessor.awaitFirstReturned())
        Thread.sleep(100)

        assertEquals("second", vm.uiState.value.processed?.sourceId?.value)
    }

    @Test fun `processing failure is reported honestly`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val vm = VisionLabViewModel(source, FailingPreprocessor(), FakeCalibrationRepository())

        vm.processFrame()
        val state = withTimeout(10_000) { vm.uiState.first { it.processStatus == ProcessStatus.FAILED } }

        assertEquals("render blew up", state.processError)
        assertNull(state.processed)
    }

    @Test fun `process frame without a frame reports an error`() = runBlocking {
        val source = FakeVisionFrameSource(null)
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        vm.processFrame()

        assertEquals(ProcessStatus.FAILED, vm.uiState.value.processStatus)
        assertNotNull(vm.uiState.value.processError)
    }

    @Test fun `save profile is refused when the document schema is unsupported`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val repository = FakeCalibrationRepository(CalibrationLoadResult.UnsupportedSchema("too new"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.saveProfile("Ranked")

        assertEquals(0, repository.upsertCount)
        assertTrue(vm.uiState.value.message!!.contains("unsupported schema"))
    }

    @Test fun `delete all calibration data stays available when the schema is unsupported`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val repository = FakeCalibrationRepository(CalibrationLoadResult.UnsupportedSchema("too new"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.deleteAllProfiles()

        assertEquals(1, repository.clearCount)
    }

    @Test fun `save profile stores the current slots and the oriented aspect ratio`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live", width = 16, height = 9))
        val repository = FakeCalibrationRepository()
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
        vm.saveProfile("Ranked")

        val saved = repository.lastUpserted
        assertNotNull(saved)
        assertEquals(16.0 / 9.0, saved!!.targetAspectRatio, 0.0001)
        assertEquals(OrientationCategory.LANDSCAPE, saved.expectedOrientation)
        assertEquals(1, saved.slots.size)
    }

    @Test fun `apply profile refuses a frame that does not match the profile`() = runBlocking {
        val profile = CalibrationProfile(
            profileId = "p1",
            label = "Landscape",
            targetAspectRatio = 16.0 / 9.0,
            expectedOrientation = OrientationCategory.LANDSCAPE,
            createdAtMs = 1L,
            updatedAtMs = 2L,
        )
        val source = FakeVisionFrameSource(originalFrame("live", width = 10, height = 10))
        val repository = FakeCalibrationRepository(CalibrationLoadResult.Success(listOf(profile)))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.applyProfile("p1")

        assertTrue(vm.uiState.value.message!!.contains("does not match"))
        assertTrue(vm.uiState.value.slots.isEmpty())
    }

    @Test fun `apply profile replaces the manual slots`() = runBlocking {
        val profile = CalibrationProfile(
            profileId = "p1",
            label = "Landscape",
            targetAspectRatio = 16.0 / 9.0,
            expectedOrientation = OrientationCategory.LANDSCAPE,
            createdAtMs = 1L,
            updatedAtMs = 2L,
            slots = listOf(
                com.draftlens.vision.annotation.DraftSlot(
                    id = "s1",
                    type = SlotType.PICK,
                    team = SlotTeam.ENEMY,
                    index = 2,
                    rect = NormalizedRect(0.2f, 0.3f, 0.4f, 0.5f),
                ),
            ),
        )
        val source = FakeVisionFrameSource(originalFrame("live", width = 16, height = 9))
        val repository = FakeCalibrationRepository(CalibrationLoadResult.Success(listOf(profile)))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.6f, 0.6f, 0.7f, 0.7f))
        vm.applyProfile("p1")

        val slots = vm.uiState.value.slots
        assertEquals(1, slots.size)
        assertEquals(SlotType.PICK, slots.single().type)
        assertEquals(SlotTeam.ENEMY, slots.single().team)
        assertEquals(NormalizedRect(0.2f, 0.3f, 0.4f, 0.5f), slots.single().rect)
    }
}
