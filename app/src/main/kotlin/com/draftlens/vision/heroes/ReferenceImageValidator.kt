package com.draftlens.vision.heroes

import java.util.zip.CRC32

/**
 * Verifies that bytes are a decodable image, without pulling Android APIs into the
 * catalog cache. Implementations must be deterministic.
 */
fun interface ReferenceImageValidator {
    fun isDecodable(bytes: ByteArray): Boolean
}

/**
 * Pure-Kotlin container structure check: signature, chunk framing, per-chunk CRC for PNG,
 * trailer markers for JPEG and GIF, RIFF/WEBP chunk framing for WebP. It does not run an
 * image decoder; it rejects truncation, corruption, HTML error pages and non-images that
 * happen to carry a magic prefix. This is what unit tests exercise on the JVM.
 */
object StructuralImageValidator : ReferenceImageValidator {

    override fun isDecodable(bytes: ByteArray): Boolean = when {
        isPng(bytes) -> pngStructureValid(bytes)
        isJpeg(bytes) -> jpegStructureValid(bytes)
        isGif(bytes) -> gifStructureValid(bytes)
        isWebp(bytes) -> webpStructureValid(bytes)
        else -> false
    }

    private fun isPng(b: ByteArray): Boolean =
        b.size >= 8 &&
            b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() &&
            b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() &&
            b[4] == 0x0D.toByte() && b[5] == 0x0A.toByte() &&
            b[6] == 0x1A.toByte() && b[7] == 0x0A.toByte()

    private val pngSignature = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
    )

    private fun pngStructureValid(b: ByteArray): Boolean {
        var offset = pngSignature.size
        val crc = CRC32()
        var seenIhdr = false
        while (true) {
            if (offset + 12 > b.size) return false
            val length = readInt32Be(b, offset)
            if (length < 0) return false
            val typeStart = offset + 4
            val dataStart = typeStart + 4
            val dataEnd = dataStart + length
            if (length > b.size - dataStart) return false
            if (dataEnd + 4 > b.size) return false
            crc.reset()
            crc.update(b, typeStart, 4 + length)
            val storedCrc = readInt32Be(b, dataEnd)
            if (crc.value.toInt() != storedCrc) return false
            val type = String(b, typeStart, 4, Charsets.US_ASCII)
            when (type) {
                "IHDR" -> {
                    // The first chunk must be IHDR and carry exactly 13 bytes.
                    if (seenIhdr || offset != pngSignature.size || length != 13) return false
                    seenIhdr = true
                }

                "IEND" -> return seenIhdr
            }
            offset = dataEnd + 4
        }
    }

    private fun isJpeg(b: ByteArray): Boolean =
        b.size >= 4 &&
            b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()

    private fun jpegStructureValid(b: ByteArray): Boolean {
        // Minimal structural sanity: SOI at the front, EOI at the back, no empty body.
        if (!isJpeg(b)) return false
        val last = b.size - 1
        return b[last - 1] == 0xFF.toByte() && b[last] == 0xD9.toByte()
    }

    private fun isGif(b: ByteArray): Boolean {
        if (b.size < 6) return false
        val header = String(b, 0, 6, Charsets.US_ASCII)
        return header == "GIF87a" || header == "GIF89a"
    }

    private fun gifStructureValid(b: ByteArray): Boolean {
        // Header plus logical screen descriptor; trailer byte 0x3B at the very end.
        if (!isGif(b) || b.size < 20) return false
        return b[b.size - 1] == 0x3B.toByte()
    }

    private fun isWebp(b: ByteArray): Boolean =
        b.size >= 16 &&
            String(b, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(b, 8, 4, Charsets.US_ASCII) == "WEBP"

    private fun webpStructureValid(b: ByteArray): Boolean {
        if (!isWebp(b)) return false
        val riffSize = readInt32Le(b, 4)
        if (riffSize < 4 || riffSize.toLong() > b.size.toLong() - 8) return false
        // At least the first chunk must be a 4-character type with a well-formed size.
        val chunkTypeStart = 12
        if (chunkTypeStart + 8 > b.size) return false
        val chunkSize = readInt32Le(b, chunkTypeStart + 4)
        if (chunkSize < 0 || chunkTypeStart.toLong() + 8 + chunkSize > b.size) return false
        return true
    }

    private fun readInt32Be(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun readInt32Le(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
}
