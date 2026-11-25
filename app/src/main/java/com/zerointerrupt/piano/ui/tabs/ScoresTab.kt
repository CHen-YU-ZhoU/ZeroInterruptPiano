package com.zerointerrupt.piano.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as dpLerp
import com.zerointerrupt.piano.R
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.player.PlaybackSnapshot
import com.zerointerrupt.piano.player.PlaybackStatus
import com.zerointerrupt.piano.ui.PianoUiCallbacks
import com.zerointerrupt.piano.ui.components.ExpressiveConnectedGroup
import com.zerointerrupt.piano.ui.components.ExpressiveOption
import com.zerointerrupt.piano.ui.components.ExpressiveSlider
import com.zerointerrupt.piano.ui.components.PageTopHeader
import com.zerointerrupt.piano.ui.components.SmoothRoundedCornerShape
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ScoresTab(
    scores: List<PianoScore>,
    selectedScore: PianoScore?,
    settings: PianoSettings,
    playback: PlaybackSnapshot,
    isLandscape: Boolean,
    callbacks: PianoUiCallbacks,
    modifier: Modifier = Modifier
) {
    var showImportDialog by rememberSaveable { mutableStateOf(false) }

    if (showImportDialog) {
        ImportModeSelectionDialog(
            currentMode = settings.keyboardMode,
            onDismiss = { showImportDialog = false },
            onConfirmMode = { chosenMode ->
                showImportDialog = false
                callbacks.onImportFile(chosenMode)
            }
        )
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ScoresTopBar(
                    canExport = selectedScore != null,
                    onExportJson = callbacks.onExportBundleJson
                )
                KeyboardModeSwitcher(
                    keyboardMode = settings.keyboardMode,
                    onKeyboardModeChange = callbacks.onKeyboardModeChange,
                    compact = false
                )
                if (selectedScore != null) {
                    CompactActivePlayerCard(
                        score = selectedScore,
                        playback = playback,
                        speed = settings.speedMultiplier,
                        compact = false,
                        callbacks = callbacks
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreLibraryHeader(
                    count = scores.size,
                    keyboardMode = settings.keyboardMode,
                    onOpenImportDialog = { showImportDialog = true },
                    onToggleMode = callbacks.onKeyboardModeChange
                )
                MorphingScoreLibraryList(
                    scores = scores,
                    selectedScoreId = selectedScore?.id,
                    speed = settings.speedMultiplier,
                    onSelectScore = callbacks.onSelectScore,
                    onDeleteScore = callbacks.onDeleteScore,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScoresTopBar(
                canExport = selectedScore != null,
                onExportJson = callbacks.onExportBundleJson
            )
            KeyboardModeSwitcher(
                keyboardMode = settings.keyboardMode,
                onKeyboardModeChange = callbacks.onKeyboardModeChange
            )
            if (selectedScore != null) {
                CompactActivePlayerCard(
                    score = selectedScore,
                    playback = playback,
                    speed = settings.speedMultiplier,
                    callbacks = callbacks
                )
            }
            ScoreLibraryHeader(
                count = scores.size,
                keyboardMode = settings.keyboardMode,
                onOpenImportDialog = { showImportDialog = true },
                onToggleMode = callbacks.onKeyboardModeChange
            )
            MorphingScoreLibraryList(
                scores = scores,
                selectedScoreId = selectedScore?.id,
                speed = settings.speedMultiplier,
                onSelectScore = callbacks.onSelectScore,
                onDeleteScore = callbacks.onDeleteScore,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScoresTopBar(
    canExport: Boolean,
    onExportJson: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    PageTopHeader(
        title = "曲目",
        trailingAction = {
            Surface(
                shape = CircleShape,
                color = if (canExport) cs.secondaryContainer else cs.surfaceContainerHigh,
                contentColor = if (canExport) cs.onSecondaryContainer else cs.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canExport, onClick = onExportJson)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_export),
                        contentDescription = "导出 JSON",
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "导出 JSON",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    )
}

@Composable
private fun KeyboardModeSwitcher(
    keyboardMode: KeyboardMode,
    onKeyboardModeChange: (KeyboardMode) -> Unit,
    compact: Boolean = true
) {
    ExpressiveConnectedGroup(
        options = listOf(
            ExpressiveOption(KeyboardMode.KEYS_21, "21键"),
            ExpressiveOption(KeyboardMode.KEYS_36, "36键")
        ),
        selectedValue = keyboardMode,
        onSelect = onKeyboardModeChange,
        height = if (compact) 36.dp else 42.dp
    )
}

@Composable
private fun CompactActivePlayerCard(
    score: PianoScore,
    playback: PlaybackSnapshot,
    speed: Float,
    callbacks: PianoUiCallbacks,
    compact: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Card(
        shape = SmoothRoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 11.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 12.dp)
        ) {
            // Single-line header: title + fit badge (subtitle removed for compactness)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = score.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                FitBadge(score.diatonicPct)
            }

            // Live 3x7 key visualizer strip
            MiniKeyboardMonitor(activeKeys = playback.activeKeys, compact = compact)

            // Progress Slider + timing caption
            val curSec = ((playback.positionMs / speed) / 1000f).roundToInt().coerceAtLeast(0)
            val totSec = ((playback.durationMs / speed) / 1000f).roundToInt().coerceAtLeast(0)
            val curTimeLabel = "%d:%02d".format(curSec / 60, curSec % 60)
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)) {
                ExpressiveSlider(
                    value = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f,
                    valueRange = 0f..1f,
                    onValueChange = { frac ->
                        callbacks.onSeek((frac * playback.durationMs).toLong())
                    },
                    floatingLabel = curTimeLabel,
                    compact = compact,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = curTimeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = "${playback.eventIndex}/${playback.totalEvents} 拍",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = "%d:%02d".format(totSec / 60, totSec % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            // Speed control + Preset pills
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp)) {
                val speedLabel = "${"%.2f".format(speed)}x"
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "倍速",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        ExpressiveSlider(
                            value = speed,
                            valueRange = PianoSettings.MIN_SPEED..PianoSettings.MAX_SPEED,
                            onValueChange = { raw ->
                                val snapped = ((raw * 20f).roundToInt() / 20f)
                                    .coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED)
                                if (abs(snapped - speed) >= 0.01f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    callbacks.onSpeedChange(snapped)
                                }
                            },
                            floatingLabel = speedLabel,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = cs.surfaceContainerHigh
                        ) {
                            Text(
                                text = speedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "演奏倍速",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = CircleShape,
                                color = cs.surfaceContainerHigh
                            ) {
                                Text(
                                    text = speedLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        ExpressiveSlider(
                            value = speed,
                            valueRange = PianoSettings.MIN_SPEED..PianoSettings.MAX_SPEED,
                            onValueChange = { raw ->
                                val snapped = ((raw * 20f).roundToInt() / 20f)
                                    .coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED)
                                if (abs(snapped - speed) >= 0.01f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    callbacks.onSpeedChange(snapped)
                                }
                            },
                            floatingLabel = speedLabel,
                            compact = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                val speedPresets = remember { listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
                val matchedPreset = speedPresets.firstOrNull { abs(speed - it) < 0.03f }
                ExpressiveConnectedGroup(
                    options = speedPresets.map { p -> ExpressiveOption(p, "${p}x") },
                    selectedValue = matchedPreset,
                    onSelect = callbacks.onSpeedChange,
                    height = if (compact) 30.dp else 36.dp
                )
            }

            if (playback.status == PlaybackStatus.ERROR && playback.message.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = playback.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniKeyboardMonitor(
    activeKeys: Set<String>,
    compact: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val rows = remember { listOf("H", "M", "L") }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
        rows.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 2.5.dp else 4.dp)
            ) {
                (1..7).forEach { c ->
                    val k = "$r$c"
                    val on = k in activeKeys
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (compact) 16.dp else 22.dp)
                            .clip(RoundedCornerShape(if (compact) 4.dp else 6.dp))
                            .background(if (on) cs.primary else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = k,
                            fontSize = if (compact) 9.5.sp else 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) cs.onPrimary else cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreLibraryHeader(
    count: Int,
    keyboardMode: KeyboardMode,
    onOpenImportDialog: () -> Unit,
    onToggleMode: (KeyboardMode) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "曲库 ($count)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = CircleShape,
                color = cs.secondaryContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val next = if (keyboardMode == KeyboardMode.KEYS_36) KeyboardMode.KEYS_21 else KeyboardMode.KEYS_36
                        onToggleMode(next)
                    }
            ) {
                Text(
                    text = if (keyboardMode == KeyboardMode.KEYS_36) "36_keys 目录 ⇄" else "21_keys 目录 ⇄",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        FilledTonalButton(
            onClick = onOpenImportDialog,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(3.dp))
            Text("导入 MIDI / MXL", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Immutable
private data class EdgeMorphState(
    val expandT: Float,
    val nearTopEdge: Boolean
)

private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Vertically scrollable score library with Material 3 Carousel-inspired boundary morphing:
 * - Constant outer slot height (72.dp) guarantees rock-solid scroll physics with zero jitter.
 * - As items scroll toward the top or bottom boundary, they smoothly compress from wide -> narrow
 *   (both horizontal inset and vertical capsule height) and collapse text from detailed 2 lines -> 1 line.
 * - As items scroll inward from the boundary into the main zone, they smoothly expand from narrow -> wide
 *   and reveal the detailed 2nd subtitle line.
 */
@Composable
private fun MorphingScoreLibraryList(
    scores: List<PianoScore>,
    selectedScoreId: String?,
    speed: Float,
    onSelectScore: (String) -> Unit,
    onDeleteScore: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = scores,
            key = { _, item -> item.id }
        ) { index, score ->
            val isSelected = score.id == selectedScoreId
            MorphingScoreCardItem(
                index = index,
                listState = listState,
                score = score,
                selected = isSelected,
                speed = speed,
                onSelect = { onSelectScore(score.id) },
                onDelete = if (!score.isBuiltIn) ({ onDeleteScore(score.id) }) else null
            )
        }
    }
}

@Composable
private fun MorphingScoreCardItem(
    index: Int,
    listState: LazyListState,
    score: PianoScore,
    selected: Boolean,
    speed: Float,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current

    val activateDistPx = remember(density) { with(density) { 44.dp.toPx() } }
    val maxZoneEndPx = remember(density) { with(density) { 108.dp.toPx() } }
    val maxZoneStartPx = remember(density) { with(density) { 20.dp.toPx() } }

    val morphState by remember(index, listState, density) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val vpStart = layoutInfo.viewportStartOffset.toFloat()
            val vpEnd = layoutInfo.viewportEndOffset.toFloat()
            val vpHeight = (vpEnd - vpStart).coerceAtLeast(1f)
            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                ?: return@derivedStateOf EdgeMorphState(1f, false)

            val zoneEnd = minOf(maxZoneEndPx, vpHeight * 0.36f)
            val zoneStart = minOf(maxZoneStartPx, zoneEnd * 0.24f)
            val zoneSpan = (zoneEnd - zoneStart).coerceAtLeast(1f)

            val scrolledFromTop = if (listState.firstVisibleItemIndex > 0) {
                activateDistPx
            } else {
                listState.firstVisibleItemScrollOffset.toFloat()
            }
            val topEdgeActive = (scrolledFromTop / activateDistPx).coerceIn(0f, 1f)

            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            val remainingBottom = if (lastVisible == null || lastVisible.index < layoutInfo.totalItemsCount - 1) {
                activateDistPx
            } else {
                ((lastVisible.offset + lastVisible.size) - vpEnd).coerceAtLeast(0f)
            }
            val bottomEdgeActive = (remainingBottom / activateDistPx).coerceIn(0f, 1f)

            val itemCenterY = itemInfo.offset + itemInfo.size * 0.5f
            val distFromTop = itemCenterY - vpStart
            val distFromBottom = vpEnd - itemCenterY

            val rawTop = ((distFromTop - zoneStart) / zoneSpan).coerceIn(0f, 1f)
            val rawBottom = ((distFromBottom - zoneStart) / zoneSpan).coerceIn(0f, 1f)

            val topT = 1f - topEdgeActive * (1f - smoothStep(rawTop))
            val bottomT = 1f - bottomEdgeActive * (1f - smoothStep(rawBottom))

            EdgeMorphState(
                expandT = minOf(topT, bottomT),
                nearTopEdge = topT < bottomT
            )
        }
    }

    val expandT = morphState.expandT
    val detailT = ((expandT - 0.28f) / 0.72f).coerceIn(0f, 1f)

    val horizontalInset = dpLerp(22.dp, 0.dp, expandT)
    val cardHeight = dpLerp(44.dp, 72.dp, expandT)
    val cornerRadius = dpLerp(22.dp, 18.dp, expandT)
    val shape = SmoothRoundedCornerShape(cornerRadius)

    val containerColor = if (selected) {
        lerp(cs.primaryContainer.copy(alpha = 0.62f), cs.primaryContainer.copy(alpha = 0.45f), expandT)
    } else {
        lerp(cs.surfaceContainerHigh, cs.surfaceContainerLow, expandT)
    }
    val borderStroke = if (selected) {
        BorderStroke(1.5.dp, cs.primary)
    } else {
        BorderStroke(0.75.dp, cs.outlineVariant.copy(alpha = 0.25f + 0.15f * (1f - expandT)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = if (morphState.nearTopEdge) Alignment.BottomCenter else Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalInset)
                .height(cardHeight)
                .graphicsLayer {
                    alpha = 0.78f + 0.22f * expandT
                }
                .clip(shape)
                .border(borderStroke, shape)
                .background(containerColor)
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dpLerp(14.dp, 14.dp, expandT),
                        vertical = dpLerp(6.dp, 10.dp, expandT)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = score.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Detailed 2nd line smoothly collapses to 0dp (1-line mode) near top/bottom boundaries
                    if (detailT > 0.01f) {
                        Spacer(Modifier.height(dpLerp(0.dp, 3.dp, detailT)))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dpLerp(0.dp, 18.dp, detailT))
                                .clipToBounds(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${score.composer} · ${score.sourceFormat} · ${score.formattedDuration(speed)} · 最大 ${score.maxChordOnset} 键和弦",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.graphicsLayer {
                                    alpha = detailT
                                    translationY = (1f - detailT) * 3.dp.toPx()
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FitBadge(score.diatonicPct)
                    if (onDelete != null && detailT > 0.35f) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer { alpha = ((detailT - 0.35f) / 0.65f).coerceIn(0f, 1f) }
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除",
                                tint = cs.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FitBadge(pct: Float) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        pct >= 93f -> cs.tertiaryContainer to cs.onTertiaryContainer
        pct >= 88f -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.errorContainer to cs.onErrorContainer
    }
    Surface(shape = CircleShape, color = bg) {
        Text(
            text = "${pct}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ImportModeSelectionDialog(
    currentMode: KeyboardMode,
    onDismiss: () -> Unit,
    onConfirmMode: (KeyboardMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = cs.surfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "选择导入琴谱的键盘模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "系统将针对所选键位进行最佳音域变调或半音阶映射，并自动存入对应专属目录：",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )

                ModeSelectionOptionCard(
                    title = "21 键模式（3×7 自然音阶）",
                    subtitle = "无黑键 · 智能移调至白键自然音阶，适合常规伴奏与轻音乐",
                    dirLabel = "保存至 21_keys 目录",
                    isSelected = selectedMode == KeyboardMode.KEYS_21,
                    onClick = { selectedMode = KeyboardMode.KEYS_21 }
                )

                ModeSelectionOptionCard(
                    title = "36 键模式（3×12 完整半音阶）",
                    subtitle = "含 15 个升降半音菱形黑键 · 高还原度原调钢琴独奏原谱",
                    dirLabel = "保存至 36_keys 目录",
                    isSelected = selectedMode == KeyboardMode.KEYS_36,
                    onClick = { selectedMode = KeyboardMode.KEYS_36 }
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "自动切换提示",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "在曲目页切换「21键/36键」模式时，曲库将自动同步加载对应目录的曲目与 MIDI。",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmMode(selectedMode) },
                shape = CircleShape
            ) {
                Text("选取文件并导入 (${selectedMode.label})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ModeSelectionOptionCard(
    title: String,
    subtitle: String,
    dirLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) cs.primaryContainer else cs.surfaceContainerLow,
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) cs.primary else cs.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = cs.primary)
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) cs.onPrimaryContainer else cs.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) cs.onPrimaryContainer.copy(alpha = 0.85f) else cs.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) cs.primary.copy(alpha = 0.15f) else cs.surfaceContainerHigh
                ) {
                    Text(
                        text = dirLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
