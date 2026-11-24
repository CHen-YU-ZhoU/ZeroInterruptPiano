package com.zerointerrupt.piano.ui

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerointerrupt.piano.model.AppThemeMode
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.player.OverlayMode
import com.zerointerrupt.piano.player.PlaybackSnapshot
import com.zerointerrupt.piano.player.PlaybackStatus
import com.zerointerrupt.piano.rpc.RpcConnectionProbe
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

data class PianoUiCallbacks(
    val onSelectScore: (String) -> Unit,
    val onDeleteScore: (String) -> Unit,
    val onImportFile: (KeyboardMode) -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onStopPlayback: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onSpeedChange: (Float) -> Unit,
    val onOpenCalibrationOverlay: () -> Unit,
    val onTogglePlayerOverlay: () -> Unit,
    val onExportBundleJson: () -> Unit,
    val onProbeRpc: () -> Unit,
    val onUpdateToken: (String) -> Unit,
    val onPasteToken: () -> Unit,
    val onOpenClickerApp: () -> Unit,
    val onMinPressChange: (Long) -> Unit,
    val onMinGapChange: (Long) -> Unit,
    val onMaxPolyChange: (Int) -> Unit,
    val onMaxHoldChange: (Long) -> Unit,
    val onResetDefaults: () -> Unit,
    val onResetCalibration: () -> Unit,
    val onThemeModeChange: (AppThemeMode) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onKeyboardModeChange: (KeyboardMode) -> Unit
)

@Composable
fun MainScreen(
    scores: List<PianoScore>,
    selectedScore: PianoScore?,
    settings: PianoSettings,
    calibration: CalibrationGrid,
    playback: PlaybackSnapshot,
    probe: RpcConnectionProbe?,
    overlayMode: OverlayMode,
    overlayPermGranted: Boolean,
    snackbarHostState: SnackbarHostState,
    callbacks: PianoUiCallbacks
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isLandscape) {
                PlayStyleBottomNavBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            ) {
                PlayStyleNavRail(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Crossfade(
                        targetState = selectedTab,
                        animationSpec = tween(durationMillis = 180),
                        label = "landscapeTabCrossfade"
                    ) { tab ->
                        if (tab == 0) {
                            ScoresAndPlayerTab(
                                scores = scores,
                                selectedScore = selectedScore,
                                settings = settings,
                                playback = playback,
                                probe = probe,
                                overlayMode = overlayMode,
                                overlayPermGranted = overlayPermGranted,
                                isLandscape = true,
                                callbacks = callbacks,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            SettingsTab(
                                settings = settings,
                                calibration = calibration,
                                probe = probe,
                                isLandscape = true,
                                callbacks = callbacks,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        } else {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 180),
                modifier = Modifier.padding(innerPadding),
                label = "portraitTabCrossfade"
            ) { tab ->
                if (tab == 0) {
                    ScoresAndPlayerTab(
                        scores = scores,
                        selectedScore = selectedScore,
                        settings = settings,
                        playback = playback,
                        probe = probe,
                        overlayMode = overlayMode,
                        overlayPermGranted = overlayPermGranted,
                        isLandscape = false,
                        callbacks = callbacks,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SettingsTab(
                        settings = settings,
                        calibration = calibration,
                        probe = probe,
                        isLandscape = false,
                        callbacks = callbacks,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private class NavBoundsState(
    var outerPos: Offset = Offset.Zero,
    var pillPos: Offset = Offset.Zero,
    var pillSize: IntSize = IntSize.Zero
)

/**
 * Maps press coordinates from the full navigation tab touch target into the icon indicator pill
 * bounds, matching Material 3 / Google Play Store's strictly pill-bounded ripple behavior.
 */
private class MappedInteractionSource(
    underlyingInteractionSource: InteractionSource,
    private val deltaProvider: () -> Offset,
    private val targetSizeProvider: () -> IntSize
) : InteractionSource {

    private val mappedPresses = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()

    override val interactions = underlyingInteractionSource.interactions.map { interaction ->
        when (interaction) {
            is PressInteraction.Press -> {
                val mapped = mapPress(interaction)
                mappedPresses[interaction] = mapped
                mapped
            }
            is PressInteraction.Cancel -> {
                val mapped = mappedPresses.remove(interaction.press)
                if (mapped != null) PressInteraction.Cancel(mapped) else interaction
            }
            is PressInteraction.Release -> {
                val mapped = mappedPresses.remove(interaction.press)
                if (mapped != null) PressInteraction.Release(mapped) else interaction
            }
            else -> interaction
        }
    }

    private fun mapPress(press: PressInteraction.Press): PressInteraction.Press {
        val delta = deltaProvider()
        val size = targetSizeProvider()
        val rawX = press.pressPosition.x - delta.x
        val rawY = press.pressPosition.y - delta.y
        val clampedX = if (size.width > 0) rawX.coerceIn(0f, size.width.toFloat()) else rawX
        val clampedY = if (size.height > 0) rawY.coerceIn(0f, size.height.toFloat()) else rawY
        return PressInteraction.Press(Offset(clampedX, clampedY))
    }
}

@Composable
private fun PlayStyleBottomNavBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = cs.outlineVariant.copy(alpha = 0.4f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(62.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayStyleNavItem(
                    selected = selectedTab == 0,
                    selectedIcon = Icons.Rounded.PlayArrow,
                    unselectedIcon = Icons.Outlined.PlayArrow,
                    label = "曲库与演奏",
                    indicatorWidth = 64.dp,
                    indicatorHeight = 30.dp,
                    iconSize = 22.dp,
                    iconLabelGap = 3.dp,
                    onClick = { onSelectTab(0) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                PlayStyleNavItem(
                    selected = selectedTab == 1,
                    selectedIcon = Icons.Rounded.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    label = "连接与调优",
                    indicatorWidth = 64.dp,
                    indicatorHeight = 30.dp,
                    iconSize = 22.dp,
                    iconLabelGap = 3.dp,
                    onClick = { onSelectTab(1) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun PlayStyleNavRail(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainerLow,
        modifier = Modifier
            .width(78.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)
        ) {
            PlayStyleNavItem(
                selected = selectedTab == 0,
                selectedIcon = Icons.Rounded.PlayArrow,
                unselectedIcon = Icons.Outlined.PlayArrow,
                label = "演奏",
                indicatorWidth = 56.dp,
                indicatorHeight = 32.dp,
                iconSize = 22.dp,
                iconLabelGap = 4.dp,
                onClick = { onSelectTab(0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
            PlayStyleNavItem(
                selected = selectedTab == 1,
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                label = "设置",
                indicatorWidth = 56.dp,
                indicatorHeight = 32.dp,
                iconSize = 22.dp,
                iconLabelGap = 4.dp,
                onClick = { onSelectTab(1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PlayStyleNavItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    indicatorWidth: Dp,
    indicatorHeight: Dp,
    iconSize: Dp,
    iconLabelGap: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val bounds = remember { NavBoundsState() }
    val mappedInteractionSource = remember(interactionSource) {
        MappedInteractionSource(
            underlyingInteractionSource = interactionSource,
            deltaProvider = { bounds.pillPos - bounds.outerPos },
            targetSizeProvider = { bounds.pillSize }
        )
    }

    val indicatorProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "navIndicatorProgress"
    )
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "navPressScale"
    )

    val selectionBounce = remember { Animatable(1f) }
    var prevSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !prevSelected) {
            selectionBounce.snapTo(0.86f)
            selectionBounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.52f,
                    stiffness = 520f
                )
            )
        }
        prevSelected = selected
    }

    val iconTint by animateColorAsState(
        targetValue = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "navIconTint"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) cs.onSurface else cs.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "navLabelColor"
    )

    Box(
        modifier = modifier
            .onGloballyPositioned { bounds.outerPos = it.positionInRoot() }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(iconLabelGap)
        ) {
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(indicatorHeight)
                    .onGloballyPositioned {
                        bounds.pillPos = it.positionInRoot()
                        bounds.pillSize = it.size
                    }
                    .clip(CircleShape)
                    .indication(mappedInteractionSource, ripple(bounded = true)),
                contentAlignment = Alignment.Center
            ) {
                if (indicatorProgress > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = (0.28f + 0.72f * indicatorProgress).coerceIn(0f, 1f)
                                alpha = (indicatorProgress * 1.8f).coerceIn(0f, 1f)
                            }
                            .clip(CircleShape)
                            .background(cs.secondaryContainer)
                    )
                }
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            val s = pressScale * selectionBounce.value
                            scaleX = s
                            scaleY = s
                        }
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = labelColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppHeaderBar(
    probe: RpcConnectionProbe?,
    hasToken: Boolean,
    onProbeRpc: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "零断奏钢琴",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "3×7 自然音阶 · RPC 硬件并发",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        RpcStatusBadge(probe = probe, hasToken = hasToken, onClick = onProbeRpc)
    }
}

@Composable
private fun OverlayLaunchCard(
    hasToken: Boolean,
    overlayMode: OverlayMode,
    overlayPermGranted: Boolean,
    keyboardMode: KeyboardMode,
    onKeyboardModeChange: (KeyboardMode) -> Unit,
    onTogglePlayerOverlay: () -> Unit,
    onOpenCalibrationOverlay: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 21-key / 36-key keyboard mode switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "键盘模式",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurfaceVariant
                )
                Surface(
                    shape = CircleShape,
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        KeyboardMode.entries.forEach { mode ->
                            val selected = keyboardMode == mode
                            Surface(
                                shape = CircleShape,
                                color = if (selected) cs.primary else cs.surfaceContainerHigh,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onKeyboardModeChange(mode) }
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTogglePlayerOverlay,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when {
                            !overlayPermGranted -> "授权悬浮窗"
                            overlayMode == OverlayMode.OFF -> "开启悬浮播放条"
                            else -> "关闭悬浮播放条"
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                FilledTonalButton(
                    onClick = onOpenCalibrationOverlay,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (keyboardMode == KeyboardMode.KEYS_36) "36键 悬浮校准" else "3×7 悬浮校准",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            if (!hasToken) {
                Text(
                    text = "提示：请先切换到「连接与调优」填写连点器 RPC Access Token。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error
                )
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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("导入 MIDI / MXL", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
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
                            text = "💡 自动切换提示：",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "在主界面切换「21键/36键」模式时，曲库将自动同步加载对应目录的曲目与 MIDI。",
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
                colors = RadioButtonDefaults.colors(
                    selectedColor = cs.primary
                )
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

@Composable
private fun ScoresAndPlayerTab(
    scores: List<PianoScore>,
    selectedScore: PianoScore?,
    settings: PianoSettings,
    playback: PlaybackSnapshot,
    probe: RpcConnectionProbe?,
    overlayMode: OverlayMode,
    overlayPermGranted: Boolean,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left pane: Header + Overlay launcher + Active Player
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppHeaderBar(
                    probe = probe,
                    hasToken = settings.rpcToken.isNotBlank(),
                    onProbeRpc = callbacks.onProbeRpc
                )
                OverlayLaunchCard(
                    hasToken = settings.rpcToken.isNotBlank(),
                    overlayMode = overlayMode,
                    overlayPermGranted = overlayPermGranted,
                    keyboardMode = settings.keyboardMode,
                    onKeyboardModeChange = callbacks.onKeyboardModeChange,
                    onTogglePlayerOverlay = callbacks.onTogglePlayerOverlay,
                    onOpenCalibrationOverlay = callbacks.onOpenCalibrationOverlay
                )
                if (selectedScore != null) {
                    ActivePlayerCard(
                        score = selectedScore,
                        playback = playback,
                        speed = settings.speedMultiplier,
                        callbacks = callbacks
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // Right pane: Dedicated Score Library column
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScoreLibraryHeader(
                    count = scores.size,
                    keyboardMode = settings.keyboardMode,
                    onOpenImportDialog = { showImportDialog = true },
                    onToggleMode = callbacks.onKeyboardModeChange
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(scores, key = { it.id }) { score ->
                        val isSelected = score.id == selectedScore?.id
                        ScoreRowCard(
                            score = score,
                            selected = isSelected,
                            speed = settings.speedMultiplier,
                            onSelect = { callbacks.onSelectScore(score.id) },
                            onDelete = if (!score.isBuiltIn) ({ callbacks.onDeleteScore(score.id) }) else null
                        )
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppHeaderBar(
                    probe = probe,
                    hasToken = settings.rpcToken.isNotBlank(),
                    onProbeRpc = callbacks.onProbeRpc
                )
            }

            item {
                OverlayLaunchCard(
                    hasToken = settings.rpcToken.isNotBlank(),
                    overlayMode = overlayMode,
                    overlayPermGranted = overlayPermGranted,
                    keyboardMode = settings.keyboardMode,
                    onKeyboardModeChange = callbacks.onKeyboardModeChange,
                    onTogglePlayerOverlay = callbacks.onTogglePlayerOverlay,
                    onOpenCalibrationOverlay = callbacks.onOpenCalibrationOverlay
                )
            }

            if (selectedScore != null) {
                item {
                    ActivePlayerCard(
                        score = selectedScore,
                        playback = playback,
                        speed = settings.speedMultiplier,
                        callbacks = callbacks
                    )
                }
            }

            item {
                ScoreLibraryHeader(
                    count = scores.size,
                    keyboardMode = settings.keyboardMode,
                    onOpenImportDialog = { showImportDialog = true },
                    onToggleMode = callbacks.onKeyboardModeChange
                )
            }

            items(scores, key = { it.id }) { score ->
                val isSelected = score.id == selectedScore?.id
                ScoreRowCard(
                    score = score,
                    selected = isSelected,
                    speed = settings.speedMultiplier,
                    onSelect = { callbacks.onSelectScore(score.id) },
                    onDelete = if (!score.isBuiltIn) ({ callbacks.onDeleteScore(score.id) }) else null
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ActivePlayerCard(
    score: PianoScore,
    playback: PlaybackSnapshot,
    speed: Float,
    callbacks: PianoUiCallbacks
) {
    val cs = MaterialTheme.colorScheme
    val isPlaying = playback.status == PlaybackStatus.PLAYING

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = score.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${score.composer} · 移调 ${if (score.bestShift >= 0) "+" else ""}${score.bestShift} · 契合度 ${score.diatonicPct}% (${score.fitTierLabel})",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                FitBadge(score.diatonicPct)
            }

            // Live 3x7 key visualizer strip
            MiniKeyboardMonitor(activeKeys = playback.activeKeys)

            // Progress Slider
            val curSec = ((playback.positionMs / speed) / 1000f).roundToInt().coerceAtLeast(0)
            val totSec = ((playback.durationMs / speed) / 1000f).roundToInt().coerceAtLeast(0)
            Column {
                Slider(
                    value = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f,
                    onValueChange = { frac ->
                        callbacks.onSeek((frac * playback.durationMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "%d:%02d".format(curSec / 60, curSec % 60),
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

            // Speed Control Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "演奏倍速",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${"%.2f".format(speed)}x",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                }
                Slider(
                    value = speed,
                    onValueChange = callbacks.onSpeedChange,
                    valueRange = PianoSettings.MIN_SPEED..PianoSettings.MAX_SPEED,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                        val active = kotlin.math.abs(speed - preset) < 0.03f
                        Surface(
                            shape = CircleShape,
                            color = if (active) cs.primary else cs.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable { callbacks.onSpeedChange(preset) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${preset}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) cs.onPrimary else cs.onSurface
                                )
                            }
                        }
                    }
                }
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
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = callbacks.onTogglePlayPause,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(42.dp)
                ) {
                    Text(
                        text = when {
                            isPlaying -> "暂停演奏"
                            playback.status == PlaybackStatus.PAUSED -> "继续演奏"
                            else -> "立即演奏"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = callbacks.onStopPlayback,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .height(42.dp)
                ) {
                    Text(
                        text = "停止",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                FilledTonalButton(
                    onClick = callbacks.onExportBundleJson,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.05f)
                        .height(42.dp)
                ) {
                    Text(
                        text = "导出 JSON",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniKeyboardMonitor(activeKeys: Set<String>) {
    val cs = MaterialTheme.colorScheme
    val rows = listOf("H", "M", "L")
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                (1..7).forEach { c ->
                    val k = "$r$c"
                    val on = k in activeKeys
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (on) cs.primary else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = k,
                            fontSize = 10.sp,
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
private fun ScoreRowCard(
    score: PianoScore,
    selected: Boolean,
    speed: Float,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) cs.primaryContainer.copy(alpha = 0.45f) else cs.surfaceContainerLow
        ),
        border = if (selected) BorderStroke(1.5.dp, cs.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(16.dp)
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
                Text(
                    text = "${score.composer} · ${score.sourceFormat} · ${score.formattedDuration(speed)} · 最大 ${score.maxChordOnset} 键和弦",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FitBadge(score.diatonicPct)
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除",
                            tint = cs.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FitBadge(pct: Float) {
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
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RpcStatusBadge(probe: RpcConnectionProbe?, hasToken: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (label, bg, fg) = when {
        !hasToken -> Triple("未填 Token", cs.errorContainer, cs.onErrorContainer)
        probe == null -> Triple("点此测连", cs.surfaceContainerHigh, cs.onSurfaceVariant)
        probe.ok && probe.shizukuConnected ->
            Triple("RPC ${probe.latencyMs}ms", cs.tertiaryContainer, cs.onTertiaryContainer)
        probe.ok ->
            Triple("Shizuku 未连", cs.errorContainer, cs.onErrorContainer)
        else -> Triple("RPC 断开", cs.errorContainer, cs.onErrorContainer)
    }
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

@Composable
private fun SettingsTab(
    settings: PianoSettings,
    calibration: CalibrationGrid,
    probe: RpcConnectionProbe?,
    isLandscape: Boolean,
    callbacks: PianoUiCallbacks,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "外观与连接",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                AppearanceSettingsCard(
                    settings = settings,
                    onThemeModeChange = callbacks.onThemeModeChange,
                    onDynamicColorChange = callbacks.onDynamicColorChange
                )
                RpcConfigCard(
                    settings = settings,
                    probe = probe,
                    callbacks = callbacks
                )
                Spacer(Modifier.height(4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "硬件参数与校准",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                HardwareTuningCard(
                    settings = settings,
                    callbacks = callbacks
                )
                CalibrationInfoCard(
                    calibration = calibration,
                    callbacks = callbacks
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "连接、外观与硬件参数",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            AppearanceSettingsCard(
                settings = settings,
                onThemeModeChange = callbacks.onThemeModeChange,
                onDynamicColorChange = callbacks.onDynamicColorChange
            )
            RpcConfigCard(
                settings = settings,
                probe = probe,
                callbacks = callbacks
            )
            HardwareTuningCard(
                settings = settings,
                callbacks = callbacks
            )
            CalibrationInfoCard(
                calibration = calibration,
                callbacks = callbacks
            )
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    settings: PianoSettings,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "应用外观",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Segmented theme mode selector (Follow System / Light / Dark)
            Surface(
                shape = CircleShape,
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val selected = settings.themeMode == mode
                        Surface(
                            shape = CircleShape,
                            color = if (selected) cs.primary else cs.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable { onThemeModeChange(mode) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "动态取色（Material You）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (supportsDynamicColor) {
                            "根据系统壁纸自动生成主界面与悬浮窗配色"
                        } else {
                            "需要 Android 12 及以上系统支持"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Switch(
                    checked = supportsDynamicColor && settings.dynamicColor,
                    onCheckedChange = { onDynamicColorChange(it) },
                    enabled = supportsDynamicColor
                )
            }
        }
    }
}

@Composable
private fun RpcConfigCard(
    settings: PianoSettings,
    probe: RpcConnectionProbe?,
    callbacks: PianoUiCallbacks
) {
    val cs = MaterialTheme.colorScheme
    var tokenInput by remember(settings.rpcToken) { mutableStateOf(settings.rpcToken) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "连点器 RPC 接口桥 (${settings.rpcHost}:${settings.rpcPort})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = tokenInput,
                onValueChange = {
                    tokenInput = it
                    callbacks.onUpdateToken(it)
                },
                label = { Text("RPC Access Token") },
                placeholder = { Text("粘贴连点器「实验室 → RPC 接口桥」中的 Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = callbacks.onPasteToken,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("粘贴 Token")
                }
                Button(
                    onClick = callbacks.onProbeRpc,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("测试连接")
                }
                OutlinedButton(
                    onClick = callbacks.onOpenClickerApp,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("连点器")
                }
            }
            if (probe != null) {
                val statusText = if (probe.ok) {
                    "连接成功 · 延迟 ${probe.latencyMs}ms · Shizuku=${probe.shizukuConnected} · 引擎=${probe.engineMode} · 悬浮窗=${probe.overlayVisible}"
                } else {
                    probe.error
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (probe.ok) cs.primary else cs.error
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "HyperOS 实测提醒：切换游戏后，若连点器未开启悬浮窗且电池策略未设为「无限制」，系统约 5 秒后会冻结连点器导致 RPC 超时。建议保持连点器悬浮条（可收起）开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun HardwareTuningCard(
    settings: PianoSettings,
    callbacks: PianoUiCallbacks
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "M0 真机安全参数",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = callbacks.onResetDefaults,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("恢复默认", fontSize = 12.sp)
                }
            }

            TuningSlider(
                title = "最短按压时长 (M0 底线 ≥ 28ms)",
                valueLabel = "${settings.minPressMs} ms",
                value = settings.minPressMs.toFloat(),
                range = PianoSettings.FLOOR_PRESS_MS.toFloat()..100f,
                onValueChange = { callbacks.onMinPressChange(it.roundToInt().toLong()) }
            )

            TuningSlider(
                title = "同键最小抬指间隔 (M0 防粘连 ≥ 35ms)",
                valueLabel = "${settings.minRestrikeGapMs} ms",
                value = settings.minRestrikeGapMs.toFloat(),
                range = PianoSettings.FLOOR_RESTRIKE_GAP_MS.toFloat()..140f,
                onValueChange = { callbacks.onMinGapChange(it.roundToInt().toLong()) }
            )

            TuningSlider(
                title = "最大并发键数 (TouchMux 16 槽位保护)",
                valueLabel = "${settings.maxPolyphony} 键",
                value = settings.maxPolyphony.toFloat(),
                range = 4f..14f,
                onValueChange = { callbacks.onMaxPolyChange(it.roundToInt()) }
            )

            TuningSlider(
                title = "单音封顶持续时长",
                valueLabel = "${settings.maxTapHoldMs} ms",
                value = settings.maxTapHoldMs.toFloat(),
                range = 150f..1200f,
                onValueChange = { callbacks.onMaxHoldChange((it / 25f).roundToInt() * 25L) }
            )
        }
    }
}

@Composable
private fun CalibrationInfoCard(
    calibration: CalibrationGrid,
    callbacks: PianoUiCallbacks
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "3×7 键盘归一化校准框",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "L=%.4f  T=%.4f  R=%.4f  B=%.4f".format(
                    calibration.leftNorm, calibration.topNorm,
                    calibration.rightNorm, calibration.bottomNorm
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant
            )
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = callbacks.onOpenCalibrationOverlay,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("打开悬浮校准")
                }
                OutlinedButton(
                    onClick = callbacks.onResetCalibration,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重置默认布局")
                }
            }
        }
    }
}

@Composable
private fun TuningSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
