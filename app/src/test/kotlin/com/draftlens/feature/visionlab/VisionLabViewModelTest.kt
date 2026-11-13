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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
            ProcessFrameResult.Success(buildProcessedFrame(input))
    }

    private class FailingPreprocessor : FramePreprocessor {
        override fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult =
            ProcessFrameResult.Failure(
                PreprocessFailure(PreprocessFailureCode.RENDER_FAILED, "render blew up"),
            )
    }

    /** Blocks the call for [BLOCKED_FRAME_ID] until released, so a stale result can arrive late. */
    private class BlockingFakePreprocessor : FramePreprocessor {
        private val gate = CountDownLatch(1)
        private val entered = CountDownLatch(1)
        private val firstReturned = CountDownLatch(1)
        private val blocked = AtomicBoolean(false)

        override fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult {
            if (input.id.value == BLOCKED_FRAME_ID && blocked.compareAndSet(false, true)) {
                entered.countDown()
                gate.await()
                firstReturned.countDown()
            }
            return ProcessFrameResult.Success(buildProcessedFrame(input))
        }

        /** True once the blocked call is actually inside the preprocessor. */
        fun awaitEntered(): Boolean = entered.await(5, TimeUnit.SECONDS)

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

    @Test fun `clear cancels in flight processing and drops the late result`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame(BLOCKED_FRAME_ID))
        val preprocessor = BlockingFakePreprocessor()
        val vm = VisionLabViewModel(source, preprocessor, FakeCalibrationRepository())

        vm.processFrame()
        assertTrue("the request never reached the preprocessor", preprocessor.awaitEntered())

        vm.clear()
        preprocessor.releaseFirst()
        assertTrue("the blocked call never returned", preprocessor.awaitFirstReturned())
        Thread.sleep(200)

        assertNull("the late result must not come back", vm.uiState.value.processed)
        assertNull(vm.uiState.value.frozenFrame)
        assertEquals(ProcessStatus.IDLE, vm.uiState.value.processStatus)
    }

    @Test fun `save profile requires a frozen frame`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val repository = FakeCalibrationRepository()
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.saveProfile("Ranked")

        assertEquals(0, repository.upsertCount)
        assertTrue("message was: ${vm.uiState.value.message}", vm.uiState.value.message!!.contains("Freeze"))
    }

    @Test fun `update profile requires a frozen frame`() = runBlocking {
        val profile = CalibrationProfile(
            profileId = "p1",
            label = "Landscape",
            targetAspectRatio = 16.0 / 9.0,
            expectedOrientation = OrientationCategory.LANDSCAPE,
            createdAtMs = 1L,
            updatedAtMs = 2L,
        )
        val source = FakeVisionFrameSource(originalFrame("live"))
        val repository = FakeCalibrationRepository(CalibrationLoadResult.Success(listOf(profile)))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.updateProfile("p1", "Ranked")

        assertEquals(0, repository.upsertCount)
        assertTrue("message was: ${vm.uiState.value.message}", vm.uiState.value.message!!.contains("Freeze"))
    }

    @Test fun `apply profile requires a frozen frame`() = runBlocking {
        val profile = CalibrationProfile(
            profileId = "p1",
            label = "Landscape",
            targetAspectRatio = 16.0 / 9.0,
            expectedOrientation = OrientationCategory.LANDSCAPE,
            createdAtMs = 1L,
            updatedAtMs = 2L,
        )
        val source = FakeVisionFrameSource(originalFrame("live"))
        val repository = FakeCalibrationRepository(CalibrationLoadResult.Success(listOf(profile)))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.applyProfile("p1")

        assertTrue("no slot may be applied", vm.uiState.value.slots.isEmpty())
        assertTrue("message was: ${vm.uiState.value.message}", vm.uiState.value.message!!.contains("Freeze"))
    }

    @Test fun `a frozen frame satisfies the profile frame requirement`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live", width = 16, height = 9))
        val repository = FakeCalibrationRepository()
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.freeze()
        vm.saveProfile("Ranked")

        assertEquals(1, repository.upsertCount)
    }

    @Test fun `live frame is exposed in the ui state`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        assertEquals("live", vm.uiState.value.liveFrame?.id?.value)
        assertEquals(VisionSourceState.CAPTURING, vm.uiState.value.sourceState)
    }

    // region slot operations

    @Test fun `selecting a slot by id selects exactly that slot`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.4f, 0.4f, 0.5f, 0.5f))
        val second = vm.uiState.value.slots[1]

        vm.selectSlot(second.id)

        assertEquals(second.id, vm.uiState.value.selectedSlotId)
    }

    @Test fun `deleting an arbitrary slot deletes only that slot`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.4f, 0.4f, 0.5f, 0.5f))
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.6f, 0.6f, 0.7f, 0.7f))
        val ids = vm.uiState.value.slots.map { it.id }
        val target = ids[1]

        vm.deleteSlot(target)

        val remaining = vm.uiState.value.slots.map { it.id }
        assertEquals(2, remaining.size)
        assertTrue(remaining.contains(ids[0]))
        assertTrue(remaining.contains(ids[2]))
        assertFalse(remaining.contains(target))
        assertFalse(
            "the deleted slot must not stay selected",
            vm.uiState.value.selectedSlotId == target,
        )
    }

    @Test fun `undo restores a deleted slot with its own id and metadata`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f), label = "first")
        val slot = vm.uiState.value.slots.single()

        vm.deleteSlot(slot.id)
        assertTrue(vm.uiState.value.slots.isEmpty())

        vm.undo()

        val restored = vm.uiState.value.slots.single()
        assertEquals(slot.id, restored.id)
        assertEquals("first", restored.label)
        assertEquals(slot.rect, restored.rect)
    }

    @Test fun `undo removes a created slot`() = runBlocking {
        val vm = slotVm()
        vm.createSlot(NormalizedRect(0.2f, 0.2f, 0.4f, 0.4f))
        assertEquals(1, vm.uiState.value.slots.size)

        vm.undo()

        assertTrue(vm.uiState.value.slots.isEmpty())
    }

    @Test fun `a created slot becomes the selected slot`() = runBlocking {
        val vm = slotVm()

        vm.createSlot(NormalizedRect(0.2f, 0.2f, 0.4f, 0.4f))

        assertEquals(vm.uiState.value.slots.single().id, vm.uiState.value.selectedSlotId)
    }

    @Test fun `copy and paste creates a new slot and keeps the original`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f), label = "enemy")
        val original = vm.uiState.value.slots.single()

        vm.copySlot(original.id)
        val pasted = vm.pasteSlot(original.id)

        assertTrue(pasted.isSuccess)
        val slots = vm.uiState.value.slots
        assertEquals(2, slots.size)
        assertTrue("the pasted slot needs a new id", slots.any { it.id != original.id })
        assertTrue("the original must survive", slots.any { it.id == original.id })
    }

    @Test fun `a pasted slot gets the next free index in its scope`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
        val original = vm.uiState.value.slots.single()

        vm.copySlot(original.id)
        vm.pasteSlot(original.id)

        val pasted = vm.uiState.value.slots.first { it.id != original.id }
        assertEquals(2, pasted.index)
    }

    @Test fun `a duplicated slot keeps the source and the metadata`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f), label = "enemy")
        val original = vm.uiState.value.slots.single()

        val duplicate = vm.duplicateSlot(original.id)

        assertTrue(duplicate.isSuccess)
        val slots = vm.uiState.value.slots
        assertEquals(2, slots.size)
        val copy = slots.first { it.id != original.id }
        assertEquals(original.type, copy.type)
        assertEquals(original.team, copy.team)
        assertEquals(original.label, copy.label)
        assertEquals(original.index + 1, copy.index)
    }

    @Test fun `a pasted slot is offset but stays inside the image`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.8f, 0.8f, 0.95f, 0.95f))
        val original = vm.uiState.value.slots.single()

        vm.copySlot(original.id)
        vm.pasteSlot(original.id)

        val pasted = vm.uiState.value.slots.first { it.id != original.id }
        assertTrue(pasted.rect.right <= 1f)
        assertTrue(pasted.rect.bottom <= 1f)
    }

    @Test fun `the menu opens for a slot that exists`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
        val slot = vm.uiState.value.slots.single()

        vm.openSlotMenu(slot.id)
        assertEquals(slot.id, vm.uiState.value.menuSlotId)

        vm.closeSlotMenu()
        assertNull(vm.uiState.value.menuSlotId)
    }

    @Test fun `a move is undoable`() = runBlocking {
        val vm = slotVm()
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.2f, 0.2f, 0.4f, 0.4f))
        val slot = vm.uiState.value.slots.single()

        vm.beginSlotGesture(slot.id)
        vm.moveSlot(slot.id, 0.1f, 0f)
        vm.endSlotGesture()
        assertNotEquals(slot.rect, vm.uiState.value.slots.single().rect)

        vm.undo()

        assertEquals(slot.rect, vm.uiState.value.slots.single().rect)
    }

    private fun slotVm(): VisionLabViewModel = VisionLabViewModel(
        FakeVisionFrameSource(originalFrame("live")),
        EchoPreprocessor(),
        FakeCalibrationRepository(),
    )

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
        val source = FakeVisionFrameSource(originalFrame(BLOCKED_FRAME_ID))
        val preprocessor = BlockingFakePreprocessor()
        val vm = VisionLabViewModel(source, preprocessor, FakeCalibrationRepository())

        vm.processFrame()
        // The first request must be inside the preprocessor before the second one supersedes it;
        // a request cancelled before it starts never reaches the preprocessor at all.
        assertTrue("the first request never reached the preprocessor", preprocessor.awaitEntered())
        source.emit(originalFrame("second"))
        vm.processFrame()

        val published = withTimeout(10_000) { vm.uiState.first { it.processed != null } }
        assertEquals("second", published.processed?.sourceId?.value)

        preprocessor.releaseFirst()
        assertTrue("the blocked call never returned", preprocessor.awaitFirstReturned())
        Thread.sleep(200)

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
        vm.freeze()
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

        vm.freeze()
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
        vm.freeze()
        vm.applyProfile("p1")

        val slots = vm.uiState.value.slots
        assertEquals(1, slots.size)
        assertEquals(SlotType.PICK, slots.single().type)
        assertEquals(SlotTeam.ENEMY, slots.single().team)
        assertEquals(NormalizedRect(0.2f, 0.3f, 0.4f, 0.5f), slots.single().rect)
    }

    @Test fun `apply profile the store would reject does not report success`() = runBlocking {
        val tooSmall = com.draftlens.vision.annotation.DraftSlot(
            id = "s1",
            type = SlotType.PICK,
            team = SlotTeam.ENEMY,
            index = 2,
            rect = NormalizedRect(0.1f, 0.1f, 0.104f, 0.104f),
        )
        val profile = CalibrationProfile(
            profileId = "p1",
            label = "Too small",
            targetAspectRatio = 16.0 / 9.0,
            expectedOrientation = OrientationCategory.LANDSCAPE,
            createdAtMs = 1L,
            updatedAtMs = 2L,
            slots = listOf(tooSmall),
        )
        val source = FakeVisionFrameSource(originalFrame("live", width = 16, height = 9))
        val repository = FakeCalibrationRepository(CalibrationLoadResult.Success(listOf(profile)))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), repository)

        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.6f, 0.6f, 0.7f, 0.7f))
        vm.freeze()
        vm.applyProfile("p1")

        val message = vm.uiState.value.message
        assertNotNull("a rejected profile must say so", message)
        assertTrue("message was: $message", message!!.contains("too small"))
        assertEquals("the existing slot must survive", 1, vm.uiState.value.slots.size)
    }

    @Test fun `slot index that duplicates another slot in the same scope is rejected`() = runBlocking {
        val source = FakeVisionFrameSource(originalFrame("live"))
        val vm = VisionLabViewModel(source, EchoPreprocessor(), FakeCalibrationRepository())

        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
        vm.addSlot(SlotType.BAN, SlotTeam.ALLY, NormalizedRect(0.4f, 0.4f, 0.5f, 0.5f))
        val second = vm.uiState.value.slots[1]

        val result = vm.updateSlotIndex(second.id, 1)

        assertTrue("the duplicate index must be reported", result.isFailure)
        assertEquals(2, vm.uiState.value.slots[1].index)
    }

    private companion object {
        /** The frame id whose processing call the fake blocks. */
        const val BLOCKED_FRAME_ID = "first"
    }
}

/** Top level so the nested fakes can build a processed frame too. */
private fun buildProcessedFrame(input: OriginalFrame) = ProcessedFrame(
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
