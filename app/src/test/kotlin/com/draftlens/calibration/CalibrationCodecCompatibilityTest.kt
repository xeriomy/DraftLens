package com.draftlens.calibration

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.calibration.CalibrationCompatibility
import com.draftlens.vision.calibration.CalibrationDocument
import com.draftlens.vision.calibration.CalibrationDocumentException
import com.draftlens.vision.calibration.CalibrationProfile
import com.draftlens.vision.calibration.CalibrationProfileCodec
import com.draftlens.vision.calibration.CalibrationSchema
import com.draftlens.vision.calibration.UnsupportedCalibrationSchemaException
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.OrientationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCodecCompatibilityTest {

    private fun slot(
        schemaVersion: Int = com.draftlens.vision.annotation.SlotSchema.CURRENT,
        id: String = "slot-1",
    ) = DraftSlot(
        schemaVersion = schemaVersion,
        id = id,
        type = SlotType.BAN,
        team = SlotTeam.ALLY,
        index = 1,
        rect = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
    )

    private fun profile(
        targetAspectRatio: Double = 16.0 / 9.0,
        expectedOrientation: OrientationCategory = OrientationCategory.LANDSCAPE,
        schemaVersion: Int = CalibrationSchema.CURRENT,
        slots: List<DraftSlot> = listOf(slot()),
    ) = CalibrationProfile(
        schemaVersion = schemaVersion,
        profileId = "profile-1",
        label = "Ranked",
        targetAspectRatio = targetAspectRatio,
        expectedOrientation = expectedOrientation,
        createdAtMs = 1_000L,
        updatedAtMs = 2_000L,
        slots = slots,
    )

    @Test fun `equal ratio and orientation is compatible`() {
        val profile = profile(targetAspectRatio = 16.0 / 9.0, expectedOrientation = OrientationCategory.LANDSCAPE)
        assertEquals(
            CalibrationCompatibility.Compatible,
            CalibrationCompatibility.check(profile, orientedWidth = 2400, orientedHeight = 1350),
        )
    }

    @Test fun `ratio mismatch returns expected and actual values`() {
        val result = CalibrationCompatibility.check(profile(16.0 / 9.0, OrientationCategory.LANDSCAPE), 1080, 1920)
        assertTrue(result is CalibrationCompatibility.Incompatible)
        val incompatible = result as CalibrationCompatibility.Incompatible
        assertEquals(16.0 / 9.0, incompatible.expectedAspectRatio, 0.0)
        assertEquals(1080.0 / 1920.0, incompatible.actualAspectRatio, 0.0)
        assertEquals(OrientationCategory.LANDSCAPE, incompatible.expectedOrientation)
        assertEquals(OrientationCategory.PORTRAIT, incompatible.actualOrientation)
        assertTrue(incompatible.ratioMismatch)
        assertTrue(incompatible.orientationMismatch)
    }

    @Test fun `orientation must match exactly even when ratio matches`() {
        val profile = profile(targetAspectRatio = 1.0, expectedOrientation = OrientationCategory.LANDSCAPE)
        val result = CalibrationCompatibility.check(profile, 1000, 1000)
        assertTrue(result is CalibrationCompatibility.Incompatible)
        val incompatible = result as CalibrationCompatibility.Incompatible
        assertFalse(incompatible.ratioMismatch)
        assertTrue(incompatible.orientationMismatch)
        assertEquals(OrientationCategory.SQUARE, incompatible.actualOrientation)
    }

    @Test fun `ratio within one percent tolerance is compatible`() {
        val profile = profile(targetAspectRatio = 16.0 / 9.0, expectedOrientation = OrientationCategory.LANDSCAPE)
        assertEquals(
            CalibrationCompatibility.Compatible,
            CalibrationCompatibility.check(profile, orientedWidth = 1790, orientedHeight = 1000),
        )
    }

    @Test fun `ratio beyond one percent tolerance is incompatible`() {
        val profile = profile(targetAspectRatio = 16.0 / 9.0, expectedOrientation = OrientationCategory.LANDSCAPE)
        val result = CalibrationCompatibility.check(profile, orientedWidth = 1820, orientedHeight = 1000)
        assertTrue(result is CalibrationCompatibility.Incompatible)
        assertTrue((result as CalibrationCompatibility.Incompatible).ratioMismatch)
    }

    @Test fun `compatibility rejects non positive dimensions`() {
        val profile = profile()
        assertThrows(IllegalArgumentException::class.java) {
            CalibrationCompatibility.check(profile, orientedWidth = 0, orientedHeight = 1080)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalibrationCompatibility.check(profile, orientedWidth = 2400, orientedHeight = -1)
        }
    }

    @Test fun `future schema is rejected and delete remains possible`() {
        val document = """{"schemaVersion":2,"profiles":[]}"""
        assertThrows(UnsupportedCalibrationSchemaException::class.java) {
            CalibrationProfileCodec.decodeDocument(document)
        }
    }

    @Test fun `future profile schema is rejected`() {
        val document = """{"schemaVersion":1,"profiles":[{"schemaVersion":9,"profileId":"p","label":null,"targetAspectRatio":1.7778,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":[]}]}"""
        assertThrows(UnsupportedCalibrationSchemaException::class.java) {
            CalibrationProfileCodec.decodeDocument(document)
        }
    }

    @Test fun `future slot schema is rejected`() {
        val document = """{"schemaVersion":1,"profiles":[{"schemaVersion":1,"profileId":"p","label":null,"targetAspectRatio":1.7778,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":[{"schemaVersion":2,"id":"s","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.1,"top":0.1,"right":0.3,"bottom":0.3},"label":null}]}]}"""
        assertThrows(UnsupportedCalibrationSchemaException::class.java) {
            CalibrationProfileCodec.decodeDocument(document)
        }
    }

    @Test fun `malformed json throws CalibrationDocumentException`() {
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument("{not json")
        }
    }

    @Test fun `unknown envelope key is rejected`() {
        val document = """{"schemaVersion":1,"profiles":[],"surprise":true}"""
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(document)
        }
    }

    @Test fun `non finite or non positive target ratio is rejected`() {
        val zeroRatio = """{"schemaVersion":1,"profiles":[{"schemaVersion":1,"profileId":"p","label":null,"targetAspectRatio":0.0,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":[]}]}"""
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(zeroRatio)
        }
    }

    @Test fun `blank profile id is rejected`() {
        val document = """{"schemaVersion":1,"profiles":[{"schemaVersion":1,"profileId":"  ","label":null,"targetAspectRatio":1.7778,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":[]}]}"""
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(document)
        }
    }

    @Test fun `codec round trip preserves profiles`() {
        val document = CalibrationDocument(profiles = listOf(profile(), profile().copy(profileId = "profile-2", label = null)))
        val decoded = CalibrationProfileCodec.decodeDocument(CalibrationProfileCodec.encodeDocument(document))
        assertEquals(document, decoded)
    }

    @Test fun `encode writes schema version one`() {
        val json = CalibrationProfileCodec.encodeDocument(CalibrationDocument(profiles = listOf(profile())))
        assertTrue(json.contains("\"schemaVersion\":1"))
    }

    @Test fun `invalid slot rect in document throws CalibrationDocumentException`() {
        val document = """{"schemaVersion":1,"profiles":[{"schemaVersion":1,"profileId":"p","label":null,"targetAspectRatio":1.7778,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":[{"schemaVersion":1,"id":"s","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.5,"top":0.1,"right":0.2,"bottom":0.3},"label":null}]}]}"""
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(document)
        }
    }

    @Test fun `a slot below the minimum size is rejected`() {
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(documentWithSlots(slots = """[{"schemaVersion":1,"id":"s","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.1,"top":0.1,"right":0.104,"bottom":0.104},"label":null}]"""))
        }
    }

    @Test fun `a slot index below one is rejected`() {
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(documentWithSlots(slots = """[{"schemaVersion":1,"id":"s","type":"BAN","team":"ALLY","index":0,"rect":{"left":0.1,"top":0.1,"right":0.3,"bottom":0.3},"label":null}]"""))
        }
    }

    @Test fun `duplicate slot indexes in the same scope are rejected`() {
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(documentWithSlots(slots = """[{"schemaVersion":1,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.1,"top":0.1,"right":0.3,"bottom":0.3},"label":null},{"schemaVersion":1,"id":"b","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.4,"top":0.4,"right":0.6,"bottom":0.6},"label":null}]"""))
        }
    }

    @Test fun `duplicate slot ids are rejected`() {
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(documentWithSlots(slots = """[{"schemaVersion":1,"id":"same","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.1,"top":0.1,"right":0.3,"bottom":0.3},"label":null},{"schemaVersion":1,"id":"same","type":"PICK","team":"ENEMY","index":2,"rect":{"left":0.4,"top":0.4,"right":0.6,"bottom":0.6},"label":null}]"""))
        }
    }

    @Test fun `duplicate profile ids are rejected`() {
        val document = """{"schemaVersion":1,"profiles":[{"schemaVersion":1,"profileId":"same","label":null,"targetAspectRatio":1.7778,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":[]},{"schemaVersion":1,"profileId":"same","label":null,"targetAspectRatio":1.7778,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":[]}]}"""
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.decodeDocument(document)
        }
    }

    @Test fun `encoding a profile with an unusable slot is rejected`() {
        val badSlot = DraftSlot(
            id = "s1",
            type = SlotType.BAN,
            team = SlotTeam.ALLY,
            index = 1,
            rect = NormalizedRect(0.1f, 0.1f, 0.104f, 0.104f),
        )
        assertThrows(CalibrationDocumentException::class.java) {
            CalibrationProfileCodec.encodeDocument(CalibrationDocument(profiles = listOf(profile().copy(slots = listOf(badSlot)))))
        }
    }

    private fun documentWithSlots(slots: String): String =
        """{"schemaVersion":1,"profiles":[{"schemaVersion":1,"profileId":"p","label":null,"targetAspectRatio":1.7778,"expectedOrientation":"LANDSCAPE","createdAtMs":1,"updatedAtMs":2,"slots":$slots}]}"""
}
