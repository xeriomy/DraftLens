package com.draftlens.vision.annotation

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Schema version is not supported; the document was written by a newer or older build. */
class SlotSchemaException(message: String) : IllegalArgumentException(message)

/** Document could not be decoded; message is safe to show to the user. */
class SlotCodecException(message: String, cause: Throwable? = null) : IllegalArgumentException(message)

@Serializable
data class SlotAnnotationDocument(
    val schemaVersion: Int = SlotSchema.CURRENT,
    val slots: List<DraftSlot> = emptyList(),
)

/**
 * JSON codec for [DraftSlot] lists. Strict: unknown keys are rejected and only
 * schema version 1 is accepted. Decode validates rectangles and duplicate indexes.
 */
object SlotAnnotationCodec {

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encode(slots: List<DraftSlot>): String {
        validateSlots(slots)
        return json.encodeToString(SlotAnnotationDocument.serializer(), SlotAnnotationDocument(SlotSchema.CURRENT, slots))
    }

    fun decode(text: String): List<DraftSlot> {
        val document = try {
            json.decodeFromString(SlotAnnotationDocument.serializer(), text)
        } catch (e: SerializationException) {
            throw SlotCodecException("The annotation data is not valid or uses an unknown field. ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw SlotCodecException("The annotation data is invalid: ${e.message}", e)
        }
        if (document.schemaVersion != SlotSchema.CURRENT) {
            throw SlotSchemaException("Unsupported annotation schema version ${document.schemaVersion}. Expected ${SlotSchema.CURRENT}.")
        }
        document.slots.firstOrNull { it.schemaVersion != SlotSchema.CURRENT }?.let { slot ->
            throw SlotSchemaException("Unsupported slot schema version ${slot.schemaVersion} for slot '${slot.id}'. Expected ${SlotSchema.CURRENT}.")
        }
        validateSlots(document.slots)
        return document.slots
    }

    private fun validateSlots(slots: List<DraftSlot>) {
        for (slot in slots) {
            if (slot.rect.width < MIN_SIZE || slot.rect.height < MIN_SIZE) {
                throw SlotCodecException("Slot '${slot.id}' rectangle is below the minimum size of $MIN_SIZE in one axis.")
            }
            if (slot.index < 1) {
                throw SlotCodecException("Slot '${slot.id}' index must be at least 1, was ${slot.index}.")
            }
        }
        slots
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
            ?.let { id ->
                throw SlotCodecException("Duplicate slot id '$id' in annotation data.")
            }
        slots
            .groupBy { Triple(it.type, it.team, it.index) }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
            ?.let { (type, team, index) ->
                throw SlotCodecException("Duplicate index $index for $type/$team in annotation data.")
            }
    }

    private const val MIN_SIZE = 0.01f
}
