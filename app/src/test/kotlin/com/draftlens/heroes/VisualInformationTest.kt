package com.draftlens.heroes

import com.draftlens.vision.heroes.HeroCatalogEntry
import com.draftlens.vision.heroes.HeroCatalogSource
import com.draftlens.vision.heroes.HeroIdentifyOutcome
import com.draftlens.vision.heroes.HeroId
import com.draftlens.vision.heroes.HeroReference
import com.draftlens.vision.heroes.HeroRecognition
import com.draftlens.vision.heroes.VisualInformationAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun uniform(width: Int, height: Int, value: Int) = IntArray(width * height) { value }

private fun uniformColor(width: Int, height: Int, r: Int, g: Int, b: Int) =
    IntArray(width * height) { 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b }

private fun nearUniformNoise(width: Int, height: Int, base: Int, amplitude: Int) =
    IntArray(width * height) { i ->
        val wobble = ((i * 37 + 11) % (2 * amplitude + 1)) - amplitude
        0xFF000000.toInt() or ((base + wobble).coerceIn(0, 255))
    }

/** Deterministic 0..255 horizontal gradient; the same fixture the matcher tests use. */
private fun gradient(size: Int, invert: Boolean = false): IntArray =
    IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        val span = (x * 255) / (size - 1)
        val v = if (invert) 255 - span else span
        0xFF000000.toInt() or v or (v shl 8) or (v shl 16)
    }

/** High-contrast 4x4-block checkerboard: dense edges, two tones. */
private fun blockCheckerboard(size: Int, low: Int = 40, high: Int = 210): IntArray =
    IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        val v = if (((x / 4) + (y / 4)) % 2 == 0) low else high
        0xFF000000.toInt() or v or (v shl 8) or (v shl 16)
    }

/** Deterministic pseudo-random detail: many edges, high variance, no pattern reuse. */
private fun pseudoRandomDetail(size: Int): IntArray =
    IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        val v = (x * 31 + y * 17 + (x * y) % 7 * 29 + y * 13) % 200 + 30
        0xFF000000.toInt() or v or (v shl 8) or (v shl 16)
    }

private fun reference(id: String, pixels: IntArray, size: Int): HeroReference {
    val entry = HeroCatalogEntry(
        id = HeroId(id),
        canonicalName = id.replaceFirstChar { it.uppercaseChar() },
        aliases = emptyList(),
        roles = emptyList(),
        lanes = emptyList(),
        specialties = emptyList(),
        assets = emptyList(),
        externalIds = emptyMap(),
        source = HeroCatalogSource.RONE_ARENA,
    )
    return HeroReference(entry, pixels, size, size)
}

class VisualInformationTest {

    // ---- Pure analyzer ----

    @Test fun `uniform gray is rejected`() {
        val report = VisualInformationAnalyzer.analyze(uniform(16, 16, 128), 16, 16)
        assertNotNull(report.insufficientReason)
        assertEquals(0f, report.luminanceVariance, 0f)
        assertEquals(0f, report.edgeDensity, 0f)
        assertEquals(0, report.meaningfulDifferences)
    }

    @Test fun `uniform black and white are rejected`() {
        assertNotNull(VisualInformationAnalyzer.analyze(uniform(16, 16, 0), 16, 16).insufficientReason)
        assertNotNull(VisualInformationAnalyzer.analyze(uniform(16, 16, 255), 16, 16).insufficientReason)
    }

    @Test fun `uniform saturated colors are rejected`() {
        assertNotNull(VisualInformationAnalyzer.analyze(uniformColor(16, 16, 200, 30, 30), 16, 16).insufficientReason)
        assertNotNull(VisualInformationAnalyzer.analyze(uniformColor(16, 16, 30, 200, 30), 16, 16).insufficientReason)
        assertNotNull(VisualInformationAnalyzer.analyze(uniformColor(16, 16, 30, 30, 200), 16, 16).insufficientReason)
    }

    @Test fun `near-uniform images with tiny noise are rejected`() {
        assertNotNull(VisualInformationAnalyzer.analyze(nearUniformNoise(16, 16, 128, 1), 16, 16).insufficientReason)
        assertNotNull(VisualInformationAnalyzer.analyze(nearUniformNoise(16, 16, 128, 3), 16, 16).insufficientReason)
    }

    @Test fun `a large blank image is still rejected`() {
        assertNotNull(VisualInformationAnalyzer.analyze(uniform(64, 64, 128), 64, 64).insufficientReason)
    }

    @Test fun `low-resolution crops are rejected`() {
        assertTrue(blockCheckerboard(8).size != 0)
        assertNotNull(
            "a detailed 8x8 crop is still too small to be useful",
            VisualInformationAnalyzer.analyze(blockCheckerboard(8), 8, 8).insufficientReason,
        )
        assertNull(
            "the same image at 16x16 has enough pixels",
            VisualInformationAnalyzer.analyze(blockCheckerboard(16), 16, 16).insufficientReason,
        )
    }

    @Test fun `a smooth wide gradient is accepted`() {
        assertNull(VisualInformationAnalyzer.analyze(gradient(32), 32, 32).insufficientReason)
    }

    @Test fun `a low-contrast gradient is not automatically rejected`() {
        // Luma range 110..124 across the image: low contrast but far from uniform.
        val lowContrast = IntArray(32 * 32) { i ->
            val x = i % 32
            val y = i / 32
            val v = 110 + (x * 14) / 31
            0xFF000000.toInt() or v or (v shl 8) or (v shl 16)
        }
        assertNull(VisualInformationAnalyzer.analyze(lowContrast, 32, 32).insufficientReason)
    }

    @Test fun `a low-contrast detailed image is accepted`() {
        val detailed = IntArray(32 * 32) { i ->
            val x = i % 32
            val y = i / 32
            val v = if (((x / 2) + (y / 2)) % 2 == 0) 110 else 120
            0xFF000000.toInt() or v or (v shl 8) or (v shl 16)
        }
        assertNull(VisualInformationAnalyzer.analyze(detailed, 32, 32).insufficientReason)
    }

    @Test fun `textured synthetic images are accepted`() {
        assertNull(VisualInformationAnalyzer.analyze(blockCheckerboard(32), 32, 32).insufficientReason)
    }

    @Test fun `realistic synthetic high-detail patterns are accepted`() {
        assertNull(VisualInformationAnalyzer.analyze(pseudoRandomDetail(32), 32, 32).insufficientReason)
    }

    @Test fun `a dimension mismatch is reported as insufficient, never thrown`() {
        val report = VisualInformationAnalyzer.analyze(IntArray(10), 16, 16)
        assertNotNull(report.insufficientReason)
    }

    // ---- End-to-end through Identify ----

    @Test fun `a uniform gray crop produces InsufficientVisualInformation, never candidates`() {
        val outcome = HeroRecognition.identify(
            uniformColor(16, 16, 128, 128, 128),
            16,
            16,
            listOf(reference("miya", gradient(16), 16)),
        )
        assertTrue(
            "expected InsufficientVisualInformation, got $outcome",
            outcome is HeroIdentifyOutcome.InsufficientVisualInformation,
        )
    }

    @Test fun `a saturated solid-color crop produces InsufficientVisualInformation`() {
        val outcome = HeroRecognition.identify(
            uniformColor(16, 16, 180, 40, 200),
            16,
            16,
            listOf(reference("miya", gradient(16), 16)),
        )
        assertTrue(outcome is HeroIdentifyOutcome.InsufficientVisualInformation)
    }

    @Test fun `blank crops are insufficient even with an empty reference set`() {
        val outcome = HeroRecognition.identify(uniform(16, 16, 0), 16, 16, emptyList())
        assertTrue(outcome is HeroIdentifyOutcome.InsufficientVisualInformation)
    }

    @Test fun `NoMatch is kept distinct from InsufficientVisualInformation`() {
        val inverse = gradient(16, invert = true)
        val outcome = HeroRecognition.identify(
            gradient(16),
            16,
            16,
            listOf(reference("anti", inverse, 16)),
        )
        assertTrue(
            "a textured crop with an alien reference must be NoMatch, not Insufficient: $outcome",
            outcome is HeroIdentifyOutcome.NoMatch,
        )
    }
}
