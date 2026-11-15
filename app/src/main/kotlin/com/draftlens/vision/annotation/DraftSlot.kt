package com.draftlens.vision.annotation

import com.draftlens.vision.geometry.NormalizedRect
import kotlinx.serialization.Serializable

/** Current annotation JSON schema version. */
object SlotSchema {
    const val CURRENT: Int = 1
}

enum class SlotType { BAN, PICK }

enum class SlotTeam { ALLY, ENEMY, GLOBAL }

/**
 * Manual annotation for one draft slot. Calibration data, never a detected hero.
 */
@Serializable
data class DraftSlot(
    val schemaVersion: Int = SlotSchema.CURRENT,
    val id: String,
    val type: SlotType,
    val team: SlotTeam,
    val index: Int,
    val rect: NormalizedRect,
    val label: String? = null,
)

enum class SlotIssueCode {
    DUPLICATE_INDEX,
    DUPLICATE_ID,
    OVERLAP,
    INVALID_RECT,
    INVALID_INDEX,
}

enum class SlotIssueSeverity { ERROR, WARNING }

data class SlotIssue(
    val code: SlotIssueCode,
    val severity: SlotIssueSeverity,
    val message: String,
    val slotIds: List<String>,
)

/** Failure carried by [Result] from store operations; [issueCode] is machine readable. */
class SlotIssueException(
    val issueCode: SlotIssueCode,
    message: String,
) : IllegalArgumentException(message)
