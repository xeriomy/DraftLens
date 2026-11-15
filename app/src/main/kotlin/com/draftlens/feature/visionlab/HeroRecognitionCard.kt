package com.draftlens.feature.visionlab

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.draftlens.vision.heroes.HeroCatalogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hero recognition for the selected slot: explicit sync, explicit Identify, manual confirm,
 * manual correction. Nothing runs continuously and nothing selects a hero automatically.
 */
@Composable
fun HeroRecognitionCard(state: VisionLabUiState, vm: VisionLabViewModel) {
    val selectedSlot = state.slots.firstOrNull { it.id == state.selectedSlotId }
    val recognition = selectedSlot?.let { state.heroRecognition[it.id] }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Hero recognition", style = MaterialTheme.typography.titleMedium)
            Text(
                "Unofficial community data. Mobile Legends: Bang Bang is a trademark of " +
                    "Shanghai Moonton Technology Co., Ltd.; DraftLens is not affiliated with or endorsed by Moonton.",
                style = MaterialTheme.typography.bodySmall,
            )

            CatalogSyncRow(state.heroCatalog, onSync = vm::syncHeroCatalog)

            if (selectedSlot == null) {
                Text("Select a slot to identify the hero in it.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "Selected slot: ${selectedSlot.id.take(8)} · ${selectedSlot.type} · ${selectedSlot.team} · #${selectedSlot.index}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                HeroPreview(state)
                HeroResultArea(slotId = selectedSlot.id, state = recognition, vm = vm)
            }

            HorizontalDivider()
            Text(
                "Hero data: Rone Arena API (arena.rone.dev, unofficial community project, BSD-3-Clause). " +
                    "Roles and lanes: MLBBDex (mlbbdex.com, community wiki data, CC BY-SA). " +
                    "Images: courtesy of their respective rights holders. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CatalogSyncRow(catalog: HeroCatalogState, onSync: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (catalog) {
            HeroCatalogState.NotSynced -> Text("Catalog not synced yet.", modifier = Modifier.weight(1f))
            is HeroCatalogState.Syncing -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Text(catalog.stage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }

            is HeroCatalogState.Ready -> Text(
                "Catalog ready: ${catalog.entryCount} heroes.",
                modifier = Modifier.weight(1f),
            )

            is HeroCatalogState.Error -> Text(
                "Catalog error: ${catalog.message}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val syncing = catalog is HeroCatalogState.Syncing
        OutlinedButton(onClick = onSync, enabled = !syncing) { Text("Sync catalog") }
    }
}

@Composable
private fun HeroPreview(state: VisionLabUiState) {
    val bitmap = state.heroPreview ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Extracted slot image",
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun HeroResultArea(
    slotId: String,
    state: HeroSlotUiState?,
    vm: VisionLabViewModel,
) {
    when (state?.phase) {
        null, HeroSlotPhase.IDLE -> {
            OutlinedButton(
                onClick = vm::identifySelectedSlot,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Identify hero") }
        }

        HeroSlotPhase.PROCESSING -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
            Text("Extracting and matching…", style = MaterialTheme.typography.bodySmall)
        }

        HeroSlotPhase.CANDIDATES -> {
            val candidates = state?.candidates.orEmpty()
            Text("Candidates (similarity, not a probability):", style = MaterialTheme.typography.labelMedium)
            candidates.forEach { candidate ->
                CandidateRow(
                    candidate = candidate,
                    onConfirm = { vm.confirmHero(slotId, candidate.hero.id.value) },
                    vm = vm,
                )
            }
            TextButton(onClick = vm::identifySelectedSlot) { Text("Try again") }
        }

        HeroSlotPhase.NO_MATCH -> {
            val bestSimilarity = state?.bestSimilarity
            Text(
                "No confident match (best similarity ${bestSimilarity?.let { "%.2f".format(it) } ?: "—"}). " +
                    "Confirm the hero manually below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            ManualHeroSearch(slotId = slotId, vm = vm)
            TextButton(onClick = vm::identifySelectedSlot) { Text("Try again") }
        }

        HeroSlotPhase.CONFIRMED -> {
            Text(
                "Confirmed hero: ${state?.message}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.clearHeroConfirmation(slotId) }) { Text("Clear") }
                TextButton(onClick = vm::identifySelectedSlot) { Text("Change…") }
            }
        }

        HeroSlotPhase.FAILED -> {
            Text(
                state?.message ?: "Recognition failed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = vm::identifySelectedSlot) { Text("Try again") }
            ManualHeroSearch(slotId = slotId, vm = vm)
        }
    }
}

@Composable
private fun CandidateRow(candidate: com.draftlens.vision.heroes.HeroCandidate, onConfirm: () -> Unit, vm: VisionLabViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroFileAvatar(resolveFile = { vm.heroAssetFile(candidate.hero) }, name = candidate.hero.canonicalName, size = 56.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.hero.canonicalName, style = MaterialTheme.typography.titleSmall)
                val ambiguousNote = if (candidate.ambiguous) " · ambiguous" else ""
                Text(
                    "Similarity ${"%.2f".format(candidate.similarity)}$ambiguousNote",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onConfirm) { Text("Confirm") }
        }
    }
}

/** Loads the reference image from the local catalog cache; never from the network. */
@Composable
private fun HeroFileAvatar(
    resolveFile: () -> java.io.File?,
    name: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val bitmap = produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            resolveFile()?.let { file ->
                runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                    .getOrNull()
            }
        }
    }
    val loaded = bitmap.value
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = name,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) { Text(name.take(2).uppercase(), style = MaterialTheme.typography.titleSmall) }
    }
}

/** Manual correction: find a hero by name and confirm it, no matching involved. */
@Composable
private fun ManualHeroSearch(slotId: String, vm: VisionLabViewModel) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { vm.searchCatalog(query) }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search hero by name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    results.forEach { hero ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.confirmHero(slotId, hero.id.value) }
                .padding(vertical = 6.dp),
        ) {
            Text(hero.canonicalName, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
