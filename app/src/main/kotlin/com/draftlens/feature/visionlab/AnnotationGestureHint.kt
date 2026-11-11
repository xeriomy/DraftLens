package com.draftlens.feature.visionlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * How the annotation workspace is sized.
 */
enum class AnnotationWorkspaceMode {
    /**
     * The workspace is exactly the aspect of the frame, so the image fills the whole
     * width of the workspace and no letterbox band appears beside it.
     */
    Aspect,

    /**
     * The workspace fills the space it is given and the image is fitted inside it.
     * Used by the fullscreen editor, where the image should be as large as possible and
     * the user can zoom and pan.
     */
    Fill,
}

/**
 * A short reminder of the annotation gestures.
 *
 * It is shown when the annotation editor is entered and can be dismissed; it never covers
 * the image permanently.
 */
@Composable
fun AnnotationGestureHint(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xE6202530)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Annotating",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text("Got it") }
                }
            }
            HintLine("Tap a rectangle to select it.")
            HintLine("Drag a selected rectangle to move it.")
            HintLine("Long-press a rectangle to edit its properties.")
            HintLine("Long-press and drag on empty image space to create one.")
            HintLine("Use the corner handles to resize, the X control to delete.")
        }
    }
}

@Composable
private fun HintLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD7DCE5))
}
