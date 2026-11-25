package com.zerointerrupt.piano.ui

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerointerrupt.piano.model.AppThemeMode
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.CalibrationMethod
import com.zerointerrupt.piano.model.CalibrationProfile
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.player.OverlayMode
import com.zerointerrupt.piano.player.PlaybackSnapshot
import com.zerointerrupt.piano.player.PlaybackStatus
import com.zerointerrupt.piano.rpc.RpcConnectionProbe
import com.zerointerrupt.piano.ui.components.CheckSwitch
import com.zerointerrupt.piano.ui.components.ExpressiveCard
import com.zerointerrupt.piano.ui.components.ExpressiveConnectedGroup
import com.zerointerrupt.piano.ui.components.ExpressiveOption
import com.zerointerrupt.piano.ui.components.ExpressiveSlider
import com.zerointerrupt.piano.ui.components.ExpressiveSliderBlock
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
    val onOpenManualCalibrationOverlay: () -> Unit,
    val onSelectCalibrationProfile: (String) -> Unit,
    val onCreateCalibrationProfile: (name: String, copyCurrent: Boolean) -> Unit,
    val onRenameCalibrationProfile: (profileId: String, newName: String) -> Unit,
    val onDeleteCalibrationProfile: (profileId: String) -> Unit,
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
    calibrationProfiles: List<CalibrationProfile>,
    activeCalibrationProfileId: String,
    playback: PlaybackSnapshot,
    probe: RpcConnectionProbe?,
    overlayMode: OverlayMode,
    overlayPermGranted: Boolean,
    snackbarHostState: SnackbarHostState,
    callbacks: PianoUiCallbacks
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val activeProfile = remember(calibrationProfiles, activeCalibrationProfileId) {
        calibrationProfiles.firstOrNull { it.id == activeCalibrationProfileId }
            ?: calibrationProfiles.firstOrNull()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Aspect ratio <= 1 (width <= height, e.g. portrait or half-screen split) ALWAYS uses vertical single-column layout
        val isWideLandscape = maxWidth > maxHeight && maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isWideLandscape) {
                    PlayStyleBottomNavBar(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it }
                    )
                }
            }
        ) { innerPadding ->
            if (isWideLandscape) {
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
                                    calibrationProfiles = calibrationProfiles,
                                    activeProfile = activeProfile,
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
                                    calibrationProfiles = calibrationProfiles,
                                    activeCalibrationProfileId = activeCalibrationProfileId,
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
                            calibrationProfiles = calibrationProfiles,
                            activeProfile = activeProfile,
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
                            calibrationProfiles = calibrationProfiles,
                            activeCalibrationProfileId = activeCalibrationProfileId,
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
    keyboardMode: KeyboardMode,
    activeProfileName: String,
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
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "零断奏钢琴",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${keyboardMode.label} · $activeProfileName · RPC 硬件并发",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    activeProfile: CalibrationProfile?,
    profileCount: Int,
    onKeyboardModeChange: (KeyboardMode) -> Unit,
    onTogglePlayerOverlay: () -> Unit,
    onOpenCalibrationOverlay: () -> Unit,
    onOpenManualCalibrationOverlay: () -> Unit,
    onOpenProfilePicker: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ExpressiveCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 21-key / 36-key keyboard mode switcher — unified M3 Expressive Connected Button Group
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "键盘模式",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurfaceVariant
                )
                ExpressiveConnectedGroup(
                    options = listOf(
                        ExpressiveOption(KeyboardMode.KEYS_21, "21键"),
                        ExpressiveOption(KeyboardMode.KEYS_36, "36键")
                    ),
                    selectedValue = keyboardMode,
                    onSelect = onKeyboardModeChange,
                    height = 40.dp
                )
            }

            // Active calibration profile quick switcher pill
            if (activeProfile != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenProfilePicker)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "当前校准方案：${activeProfile.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeProfile.summaryText(),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = cs.secondaryContainer
                        ) {
                            Text(
                                text = if (profileCount > 1) "切换 ($profileCount)" else "方案",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Primary floating bar button
            Button(
                onClick = onTogglePlayerOverlay,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text(
                    text = when {
                        !overlayPermGranted -> "授权悬浮窗"
                        overlayMode == OverlayMode.OFF -> "开启悬浮播放条"
                        else -> "关闭悬浮播放条"
                    },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Dual calibration launchers: Grid vs Manual Picking
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenCalibrationOverlay,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text(
                        text = if (keyboardMode == KeyboardMode.KEYS_36) "36键 网格校准" else "3×7 网格校准",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onOpenManualCalibrationOverlay,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text(
                        text = "手动逐音拾取",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
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
    calibrationProfiles: List<CalibrationProfile>,
    activeProfile: CalibrationProfile?,
    playback: PlaybackSnapshot,
    probe: RpcConnectionProbe?,
    overlayMode: OverlayMode,
    overlayPermGranted: Boolean,
    isLandscape: Boolean,
    callbacks: PianoUiCallbacks,
    modifier: Modifier = Modifier
) {
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showProfilePickerDialog by rememberSaveable { mutableStateOf(false) }

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

    if (showProfilePickerDialog) {
        CalibrationProfilePickerDialog(
            profiles = calibrationProfiles,
            activeProfileId = activeProfile?.id.orEmpty(),
            onSelect = { id ->
                showProfilePickerDialog = false
                callbacks.onSelectCalibrationProfile(id)
            },
            onDismiss = { showProfilePickerDialog = false }
        )
    }

    val activeProfileName = activeProfile?.name ?: CalibrationProfile.DEFAULT_PROFILE_NAME

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
                    keyboardMode = settings.keyboardMode,
                    activeProfileName = activeProfileName,
                    probe = probe,
                    hasToken = settings.rpcToken.isNotBlank(),
                    onProbeRpc = callbacks.onProbeRpc
                )
                OverlayLaunchCard(
                    hasToken = settings.rpcToken.isNotBlank(),
                    overlayMode = overlayMode,
                    overlayPermGranted = overlayPermGranted,
                    keyboardMode = settings.keyboardMode,
                    activeProfile = activeProfile,
                    profileCount = calibrationProfiles.size,
                    onKeyboardModeChange = callbacks.onKeyboardModeChange,
                    onTogglePlayerOverlay = callbacks.onTogglePlayerOverlay,
                    onOpenCalibrationOverlay = callbacks.onOpenCalibrationOverlay,
                    onOpenManualCalibrationOverlay = callbacks.onOpenManualCalibrationOverlay,
                    onOpenProfilePicker = { showProfilePickerDialog = true }
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
                    keyboardMode = settings.keyboardMode,
                    activeProfileName = activeProfileName,
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
                    activeProfile = activeProfile,
                    profileCount = calibrationProfiles.size,
                    onKeyboardModeChange = callbacks.onKeyboardModeChange,
                    onTogglePlayerOverlay = callbacks.onTogglePlayerOverlay,
                    onOpenCalibrationOverlay = callbacks.onOpenCalibrationOverlay,
                    onOpenManualCalibrationOverlay = callbacks.onOpenManualCalibrationOverlay,
                    onOpenProfilePicker = { showProfilePickerDialog = true }
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

            // Progress Slider — unified M3 Expressive Capsule Slider
            val curSec = ((playback.positionMs / speed) / 1000f).roundToInt().coerceAtLeast(0)
            val totSec = ((playback.durationMs / speed) / 1000f).roundToInt().coerceAtLeast(0)
            val curTimeLabel = "%d:%02d".format(curSec / 60, curSec % 60)
            Column {
                ExpressiveSlider(
                    value = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f,
                    valueRange = 0f..1f,
                    onValueChange = { frac ->
                        callbacks.onSeek((frac * playback.durationMs).toLong())
                    },
                    floatingLabel = curTimeLabel,
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

            // Speed Control Row — unified M3 Expressive Slider + Connected Preset Group
            val haptic = LocalHapticFeedback.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveSliderBlock(
                    label = "演奏倍速",
                    valueBadge = "${"%.2f".format(speed)}x",
                    value = speed,
                    valueRange = PianoSettings.MIN_SPEED..PianoSettings.MAX_SPEED,
                    onValueChange = { raw ->
                        val snapped = ((raw * 20f).roundToInt() / 20f)
                            .coerceIn(PianoSettings.MIN_SPEED, PianoSettings.MAX_SPEED)
                        if (kotlin.math.abs(snapped - speed) >= 0.01f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            callbacks.onSpeedChange(snapped)
                        }
                    }
                )
                val speedPresets = remember { listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
                val matchedPreset = speedPresets.firstOrNull { kotlin.math.abs(speed - it) < 0.03f }
                ExpressiveConnectedGroup(
                    options = speedPresets.map { p -> ExpressiveOption(p, "${p}x") },
                    selectedValue = matchedPreset,
                    onSelect = callbacks.onSpeedChange,
                    height = 36.dp
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
    calibrationProfiles: List<CalibrationProfile>,
    activeCalibrationProfileId: String,
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
                    profiles = calibrationProfiles,
                    activeProfileId = activeCalibrationProfileId,
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
                profiles = calibrationProfiles,
                activeProfileId = activeCalibrationProfileId,
                callbacks = callbacks
            )
            Spacer(Modifier.height(6.dp))
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

    ExpressiveCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "外观与控制",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "应用外观",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                ExpressiveConnectedGroup(
                    options = listOf(
                        ExpressiveOption(AppThemeMode.SYSTEM, "跟随系统"),
                        ExpressiveOption(AppThemeMode.DARK, "深色"),
                        ExpressiveOption(AppThemeMode.LIGHT, "浅色")
                    ),
                    selectedValue = settings.themeMode,
                    onSelect = onThemeModeChange,
                    height = 42.dp
                )
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))

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
                CheckSwitch(
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

    ExpressiveCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "连点器 RPC 接口桥 (${settings.rpcHost}:${settings.rpcPort})",
                style = MaterialTheme.typography.titleMedium,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = callbacks.onPasteToken,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.22f)
                        .height(40.dp)
                ) {
                    Text(
                        text = "粘贴 Token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = callbacks.onProbeRpc,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.03f)
                        .height(40.dp)
                ) {
                    Text(
                        text = "测试连接",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = callbacks.onOpenClickerApp,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(0.75f)
                        .height(40.dp)
                ) {
                    Text(
                        text = "连点器",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
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
                shape = RoundedCornerShape(16.dp),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "HyperOS 实测提醒：切换游戏后，若连点器未开启悬浮窗且电池策略未设为「无限制」，系统约 5 秒后会冻结连点器导致 RPC 超时。建议保持连点器悬浮条（可收起）开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
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
    val haptic = LocalHapticFeedback.current
    ExpressiveCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "M0 真机安全参数",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = callbacks.onResetDefaults,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("恢复默认", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }

            ExpressiveSliderBlock(
                label = "最短按压时长 (M0 底线 ≥ 28ms)",
                valueBadge = "${settings.minPressMs} ms",
                value = settings.minPressMs.toFloat(),
                valueRange = PianoSettings.FLOOR_PRESS_MS.toFloat()..100f,
                onValueChange = { raw ->
                    val next = raw.roundToInt().toLong().coerceIn(PianoSettings.FLOOR_PRESS_MS, 100L)
                    if (next != settings.minPressMs) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        callbacks.onMinPressChange(next)
                    }
                }
            )

            ExpressiveSliderBlock(
                label = "同键最小抬指间隔 (M0 防粘连 ≥ 35ms)",
                valueBadge = "${settings.minRestrikeGapMs} ms",
                value = settings.minRestrikeGapMs.toFloat(),
                valueRange = PianoSettings.FLOOR_RESTRIKE_GAP_MS.toFloat()..140f,
                onValueChange = { raw ->
                    val next = raw.roundToInt().toLong().coerceIn(PianoSettings.FLOOR_RESTRIKE_GAP_MS, 140L)
                    if (next != settings.minRestrikeGapMs) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        callbacks.onMinGapChange(next)
                    }
                }
            )

            ExpressiveSliderBlock(
                label = "最大并发键数 (TouchMux 16 槽位保护)",
                valueBadge = "${settings.maxPolyphony} 键",
                value = settings.maxPolyphony.toFloat(),
                valueRange = 4f..14f,
                onValueChange = { raw ->
                    val next = raw.roundToInt().coerceIn(4, 14)
                    if (next != settings.maxPolyphony) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        callbacks.onMaxPolyChange(next)
                    }
                }
            )

            ExpressiveSliderBlock(
                label = "单音封顶持续时长",
                valueBadge = "${settings.maxTapHoldMs} ms",
                value = settings.maxTapHoldMs.toFloat(),
                valueRange = 150f..1200f,
                onValueChange = { raw ->
                    val next = ((raw / 25f).roundToInt() * 25L).coerceIn(150L, 1200L)
                    if (next != settings.maxTapHoldMs) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        callbacks.onMaxHoldChange(next)
                    }
                }
            )
        }
    }
}

@Composable
private fun CalibrationInfoCard(
    calibration: CalibrationGrid,
    profiles: List<CalibrationProfile>,
    activeProfileId: String,
    callbacks: PianoUiCallbacks
) {
    val cs = MaterialTheme.colorScheme
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<CalibrationProfile?>(null) }

    if (showCreateDialog) {
        CalibrationProfileNameDialog(
            title = "新建校准方案",
            initialName = "",
            confirmLabel = "创建",
            showCopySwitch = true,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, copyCurrent ->
                showCreateDialog = false
                callbacks.onCreateCalibrationProfile(name, copyCurrent)
            }
        )
    }

    renamingProfile?.let { target ->
        CalibrationProfileNameDialog(
            title = "重命名校准方案",
            initialName = target.name,
            confirmLabel = "保存",
            showCopySwitch = false,
            onDismiss = { renamingProfile = null },
            onConfirm = { newName, _ ->
                renamingProfile = null
                callbacks.onRenameCalibrationProfile(target.id, newName)
            }
        )
    }

    ExpressiveCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "校准方案管理 (${profiles.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "可按游戏命名保存多套网格或手动拾取布局",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建方案", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }

            // Saved Calibration Profiles List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    val isSelected = profile.id == activeProfileId
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) cs.primaryContainer.copy(alpha = 0.45f) else cs.surfaceContainer,
                        border = if (isSelected) BorderStroke(1.5.dp, cs.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { callbacks.onSelectCalibrationProfile(profile.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = cs.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = if (profile.grid.calibrationMethod == CalibrationMethod.MANUAL_PICK) {
                                            cs.tertiaryContainer
                                        } else {
                                            cs.secondaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = "${profile.keyboardMode.label}·${profile.grid.calibrationMethod.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (profile.grid.calibrationMethod == CalibrationMethod.MANUAL_PICK) {
                                                cs.onTertiaryContainer
                                            } else {
                                                cs.onSecondaryContainer
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = profile.summaryText(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { renamingProfile = profile },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = "重命名",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                if (profiles.size > 1) {
                                    IconButton(
                                        onClick = { callbacks.onDeleteCalibrationProfile(profile.id) },
                                        modifier = Modifier.size(32.dp)
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

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = callbacks.onOpenCalibrationOverlay,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text(
                        text = "网格悬浮校准",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = callbacks.onOpenManualCalibrationOverlay,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text(
                        text = "手动逐音拾取",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = callbacks.onResetCalibration,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Text(
                    text = "重置当前方案为默认 3×7 网格",
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CalibrationProfileNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    showCopySwitch: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, copyCurrent: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var copyCurrent by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 24) name = it },
                    singleLine = true,
                    label = { Text("方案名称") },
                    placeholder = { Text("例如：原神、光遇、心动小镇") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showCopySwitch) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { copyCurrent = !copyCurrent }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("复制当前校准布局作为起点", style = MaterialTheme.typography.bodyMedium)
                        CheckSwitch(checked = copyCurrent, onCheckedChange = { copyCurrent = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifEmpty { initialName }, copyCurrent) },
                enabled = name.isNotBlank(),
                shape = CircleShape
            ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CalibrationProfilePickerDialog(
    profiles: List<CalibrationProfile>,
    activeProfileId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = cs.surfaceContainerHigh,
        title = {
            Text(
                text = "切换校准方案",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { profile ->
                    val selected = profile.id == activeProfileId
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
                        border = if (selected) BorderStroke(1.5.dp, cs.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(profile.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${profile.keyboardMode.label} · ${profile.summaryText()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
