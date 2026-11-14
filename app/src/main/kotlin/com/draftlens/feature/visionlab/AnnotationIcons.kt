package com.draftlens.feature.visionlab

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * An expand glyph: four corner brackets pointing outwards, like the usual
 * fullscreen icon. It is drawn here so the app needs no icon-font dependency.
 */
val ExpandIconVector: ImageVector = ImageVector.Builder(
    name = "Expand",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(20f, 9f); lineTo(20f, 4f); lineTo(15f, 4f)
        moveTo(4f, 15f); lineTo(4f, 20f); lineTo(9f, 20f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
    }
}.build()

/**
 * A collapse glyph: four corner brackets pointing inwards, the counterpart of
 * [ExpandIconVector].
 */
val CollapseIconVector: ImageVector = ImageVector.Builder(
    name = "Collapse",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9f, 4f); lineTo(9f, 9f); lineTo(4f, 9f)
        moveTo(15f, 4f); lineTo(15f, 9f); lineTo(20f, 9f)
        moveTo(9f, 20f); lineTo(9f, 15f); lineTo(4f, 15f)
        moveTo(15f, 20f); lineTo(15f, 15f); lineTo(20f, 15f)
    }
}.build()
