package com.draftlens.vision.calibration

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Schema version is not supported; the document was written by a newer or older build. */
class UnsupportedCalibrationSchemaException(message: String) : IllegalArgumentException(message)

/** Document could not be decoded; message is safe to show to the user. */
class CalibrationDocumentException(message: String, cause: Throwable? = null) : IllegalArgumentException(message)

/**
 * JSON codec for calibration profiles. Strict: unknown keys are rejected and
 * only envelope/profile/slot schema version 1 is accepted. Neither unsupported
 * nor malformed input is ever converted to an empty list.
 */
object CalibrationProfileCodec {

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encodeDocument(document: CalibrationDocument): String {
        validate(document)
        return json.encodeToString(CalibrationDocument.serializer(), document)
    }

    fun decodeDocument(text: String): CalibrationDocument {
        val document = try {
            json.decodeFromString(CalibrationDocument.serializer(), text)
        } catch (e: SerializationException) {
            throw CalibrationDocumentException("The calibration data is not valid or uses an unknown field. ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw CalibrationDocumentException("The calibration data is invalid: ${e.message}", e)
        }
        validate(document)
        return document
    }

    private fun validate(document: CalibrationDocument) {
        if (document.schemaVersion != CalibrationSchema.CURRENT) {
            throw UnsupportedCalibrationSchemaException(
                "Unsupported calibration schema version ${document.schemaVersion}. Expected ${CalibrationSchema.CURRENT}.",
            )
        }
        document.profiles.forEach { profile ->
            if (profile.schemaVersion != CalibrationSchema.CURRENT) {
                throw UnsupportedCalibrationSchemaException(
                    "Unsupported calibration profile schema version ${profile.schemaVersion} for profile '${profile.profileId}'. Expected ${CalibrationSchema.CURRENT}.",
                )
            }
            profile.slots.forEach { slot ->
                if (slot.schemaVersion != CalibrationSchema.CURRENT) {
                    throw UnsupportedCalibrationSchemaException(
                        "Unsupported slot schema version ${slot.schemaVersion} for slot '${slot.id}'. Expected ${CalibrationSchema.CURRENT}.",
                    )
                }
            }
            if (profile.profileId.isBlank()) {
                throw CalibrationDocumentException("Calibration profile id must not be blank.")
            }
            if (!profile.targetAspectRatio.isFinite() || profile.targetAspectRatio <= 0.0) {
                throw CalibrationDocumentException(
                    "Calibration profile '${profile.profileId}' target aspect ratio must be finite and positive, was ${profile.targetAspectRatio}.",
                )
            }
        }
    }
}
