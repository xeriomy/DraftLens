package com.draftlens.annotation

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotAnnotationCodec
import com.draftlens.vision.annotation.SlotCodecException
import com.draftlens.vision.annotation.SlotSchema
import com.draftlens.vision.annotation.SlotSchemaException
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotAnnotationCodecTest {

    private fun slot(
        schemaVersion: Int = SlotSchema.CURRENT,
        id: String = "a",
        type: SlotType = SlotType.BAN,
        team: SlotTeam = SlotTeam.ALLY,
        index: Int = 1,
        left: Float = 0.1f,
        top: Float = 0.1f,
        right: Float = 0.3f,
        bottom: Float = 0.3f,
        label: String? = null,
    ) = DraftSlot(schemaVersion, id, type, team, index, NormalizedRect(left, top, right, bottom), label)

    @Test fun `codec round trip preserves ids and rect`() {
        val slots = listOf(
            slot(id = "a", index = 1, label = "A-B1"),
            slot(id = "b", type = SlotType.PICK, team = SlotTeam.ENEMY, index = 2, left = 0.5f, right = 0.7f),
        )
        val decoded = SlotAnnotationCodec.decode(SlotAnnotationCodec.encode(slots))
        assertEquals(slots, decoded)
    }

    @Test fun `encode writes schema version one`() {
        val json = SlotAnnotationCodec.encode(listOf(slot()))
        assertTrue(json.contains("\"schemaVersion\":1"))
    }

    @Test fun `codec rejects unsupported schema`() {
        val json = """{"schemaVersion":2,"slots":[{"schemaVersion":2,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.1,"top":0.1,"right":0.3,"bottom":0.3},"label":null}]}"""
        assertThrows(SlotSchemaException::class.java) { SlotAnnotationCodec.decode(json) }
    }

    @Test fun `codec rejects unsupported slot schema inside supported document`() {
        val json = """{"schemaVersion":1,"slots":[{"schemaVersion":9,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.1,"top":0.1,"right":0.3,"bottom":0.3},"label":null}]}"""
        assertThrows(SlotSchemaException::class.java) { SlotAnnotationCodec.decode(json) }
    }

    @Test fun `codec rejects malformed json with readable message`() {
        val exception = assertThrows(Exception::class.java) { SlotAnnotationCodec.decode("{not json") }
        assertTrue(exception.message.isNullOrBlank().not())
    }

    @Test fun `codec rejects unknown keys`() {
        val json = """{"schemaVersion":1,"slots":[],"surprise":true}"""
        assertThrows(Exception::class.java) { SlotAnnotationCodec.decode(json) }
    }

    @Test fun `codec rejects invalid rectangle after decode`() {
        val json = """{"schemaVersion":1,"slots":[{"schemaVersion":1,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.5,"top":0.1,"right":0.2,"bottom":0.3},"label":null}]}"""
        assertThrows(Exception::class.java) { SlotAnnotationCodec.decode(json) }
    }

    @Test fun `codec rejects rectangle below minimum size after decode`() {
        val json = """{"schemaVersion":1,"slots":[{"schemaVersion":1,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.1,"top":0.1,"right":0.105,"bottom":0.3},"label":null}]}"""
        assertThrows(Exception::class.java) { SlotAnnotationCodec.decode(json) }
    }

    @Test fun `codec rejects duplicate indexes after decode`() {
        val json = """{"schemaVersion":1,"slots":[
            {"schemaVersion":1,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.0,"top":0.0,"right":0.2,"bottom":0.2},"label":null},
            {"schemaVersion":1,"id":"b","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.4,"top":0.4,"right":0.6,"bottom":0.6},"label":null}
        ]}"""
        assertThrows(Exception::class.java) { SlotAnnotationCodec.decode(json) }
    }

    @Test fun `codec rejects duplicate slot ids on decode`() {
        val json = """{"schemaVersion":1,"slots":[
            {"schemaVersion":1,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.0,"top":0.0,"right":0.2,"bottom":0.2},"label":null},
            {"schemaVersion":1,"id":"a","type":"PICK","team":"ENEMY","index":2,"rect":{"left":0.4,"top":0.4,"right":0.6,"bottom":0.6},"label":null}
        ]}"""
        val exception = assertThrows(SlotCodecException::class.java) { SlotAnnotationCodec.decode(json) }
        assertTrue(exception.message.orEmpty().contains("'a'"))
    }

    @Test fun `codec rejects duplicate slot ids on encode`() {
        val slots = listOf(
            slot(id = "a", index = 1),
            slot(id = "a", type = SlotType.PICK, team = SlotTeam.ENEMY, index = 2, left = 0.4f, top = 0.4f, right = 0.6f, bottom = 0.6f),
        )
        assertThrows(SlotCodecException::class.java) { SlotAnnotationCodec.encode(slots) }
    }

    @Test fun `codec accepts same index in different scopes`() {
        val json = """{"schemaVersion":1,"slots":[
            {"schemaVersion":1,"id":"a","type":"BAN","team":"ALLY","index":1,"rect":{"left":0.0,"top":0.0,"right":0.2,"bottom":0.2},"label":null},
            {"schemaVersion":1,"id":"b","type":"BAN","team":"ENEMY","index":1,"rect":{"left":0.4,"top":0.4,"right":0.6,"bottom":0.6},"label":null}
        ]}"""
        assertEquals(2, SlotAnnotationCodec.decode(json).size)
    }

    @Test fun `round trip preserves empty list`() {
        assertEquals(emptyList<DraftSlot>(), SlotAnnotationCodec.decode(SlotAnnotationCodec.encode(emptyList())))
    }

    @Test fun `enums serialize by name`() {
        val json = SlotAnnotationCodec.encode(listOf(slot()))
        assertTrue(json.contains("\"BAN\""))
        assertTrue(json.contains("\"ALLY\""))
    }
}
