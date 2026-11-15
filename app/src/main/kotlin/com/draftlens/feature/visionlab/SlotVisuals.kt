package com.draftlens.feature.visionlab

import androidx.compose.ui.graphics.Color
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType

/**
 * The colours and labels of the slot overlay.
 *
 * Colour is never the only way to tell teams apart: every rectangle carries a readable
 * label with its team, type and index.
 */
object SlotVisuals {

    /** Ally slots are blue. */
    val ALLY = Color(0xFF2196F3)

    /** Enemy slots are red. */
    val ENEMY = Color(0xFFE53935)

    /** Global slots are neutral grey. */
    val GLOBAL = Color(0xFF9E9E9E)

    /** The selected slot is highlighted in amber. */
    val SELECTED = Color(0xFFFFC107)

    fun teamColor(team: SlotTeam): Color = when (team) {
        SlotTeam.ALLY -> ALLY
        SlotTeam.ENEMY -> ENEMY
        SlotTeam.GLOBAL -> GLOBAL
    }

    fun teamInitial(team: SlotTeam): String = when (team) {
        SlotTeam.ALLY -> "A"
        SlotTeam.ENEMY -> "E"
        SlotTeam.GLOBAL -> "G"
    }

    fun typeInitial(type: SlotType): String = when (type) {
        SlotType.BAN -> "B"
        SlotType.PICK -> "P"
    }

    /** The overlay label: team, type, index and the user's own label when there is one. */
    fun label(slot: DraftSlot): String {
        val base = "${teamInitial(slot.team)}-${typeInitial(slot.type)}${slot.index}"
        val extra = slot.label?.takeIf { it.isNotBlank() } ?: return base
        return "$base $extra"
    }
}
