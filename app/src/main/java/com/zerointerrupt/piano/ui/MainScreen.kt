package com.zerointerrupt.piano.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerointerrupt.piano.R
import com.zerointerrupt.piano.model.AppThemeMode
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.CalibrationProfile
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.player.OverlayMode
import com.zerointerrupt.piano.player.PlaybackSnapshot
import com.zerointerrupt.piano.rpc.RpcConnectionProbe
import com.zerointerrupt.piano.ui.tabs.CalibrationTab
import com.zerointerrupt.piano.ui.tabs.HomeTab
import com.zerointerrupt.piano.ui.tabs.ScoresTab
import com.zerointerrupt.piano.ui.tabs.TuningTab
import kotlinx.coroutines.flow.map

enum class PianoMainTab(val label: String) {
    HOME("启动"),
    SCORES("曲目"),
    CALIBRATION("校准"),
    TUNING("连接调优")
}

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
    val onKeyboardModeChange: (KeyboardMode) -> Unit,
    val onCheckUpdate: () -> Unit = {}
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
    var selectedTab by rememberSaveable { mutableStateOf(PianoMainTab.HOME) }
    val haptic = LocalHapticFeedback.current

    val onSelectTab: (PianoMainTab) -> Unit = { tab ->
        if (tab != selectedTab) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selectedTab = tab
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLandscape = maxWidth > maxHeight && maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isWideLandscape) {
                    PlayStyleBottomNavBar(
                        selectedTab = selectedTab,
                        onSelectTab = onSelectTab
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
                        onSelectTab = onSelectTab
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        MainTabHost(
                            selectedTab = selectedTab,
                            scores = scores,
                            selectedScore = selectedScore,
                            settings = settings,
                            calibration = calibration,
                            calibrationProfiles = calibrationProfiles,
                            activeCalibrationProfileId = activeCalibrationProfileId,
                            playback = playback,
                            probe = probe,
                            overlayMode = overlayMode,
                            overlayPermGranted = overlayPermGranted,
                            isLandscape = true,
                            onNavigateToTuning = { onSelectTab(PianoMainTab.TUNING) },
                            callbacks = callbacks
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    MainTabHost(
                        selectedTab = selectedTab,
                        scores = scores,
                        selectedScore = selectedScore,
                        settings = settings,
                        calibration = calibration,
                        calibrationProfiles = calibrationProfiles,
                        activeCalibrationProfileId = activeCalibrationProfileId,
                        playback = playback,
                        probe = probe,
                        overlayMode = overlayMode,
                        overlayPermGranted = overlayPermGranted,
                        isLandscape = false,
                        onNavigateToTuning = { onSelectTab(PianoMainTab.TUNING) },
                        callbacks = callbacks
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabHost(
    selectedTab: PianoMainTab,
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
    isLandscape: Boolean,
    onNavigateToTuning: () -> Unit,
    callbacks: PianoUiCallbacks
) {
    Crossfade(
        targetState = selectedTab,
        animationSpec = tween(durationMillis = 180),
        label = "mainTabCrossfade",
        modifier = Modifier.fillMaxSize()
    ) { tab ->
        when (tab) {
            PianoMainTab.HOME -> HomeTab(
                hasToken = settings.rpcToken.isNotBlank(),
                overlayMode = overlayMode,
                overlayPermGranted = overlayPermGranted,
                playback = playback,
                probe = probe,
                onNavigateToTuning = onNavigateToTuning,
                callbacks = callbacks,
                modifier = Modifier.fillMaxSize()
            )
            PianoMainTab.SCORES -> ScoresTab(
                scores = scores,
                selectedScore = selectedScore,
                settings = settings,
                playback = playback,
                isLandscape = isLandscape,
                callbacks = callbacks,
                modifier = Modifier.fillMaxSize()
            )
            PianoMainTab.CALIBRATION -> CalibrationTab(
                keyboardMode = settings.keyboardMode,
                calibration = calibration,
                profiles = calibrationProfiles,
                activeProfileId = activeCalibrationProfileId,
                isLandscape = isLandscape,
                callbacks = callbacks,
                modifier = Modifier.fillMaxSize()
            )
            PianoMainTab.TUNING -> TuningTab(
                settings = settings,
                probe = probe,
                isLandscape = isLandscape,
                callbacks = callbacks,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PianoTabIcon(
    tab: PianoMainTab,
    selected: Boolean,
    size: Dp
) {
    when (tab) {
        PianoMainTab.HOME -> Icon(
            imageVector = if (selected) Icons.Rounded.Home else Icons.Outlined.Home,
            contentDescription = tab.label,
            modifier = Modifier.size(size)
        )
        PianoMainTab.SCORES -> Icon(
            painter = painterResource(
                if (selected) R.drawable.ic_scores else R.drawable.ic_scores_outlined
            ),
            contentDescription = tab.label,
            modifier = Modifier.size(size)
        )
        PianoMainTab.CALIBRATION -> Icon(
            painter = painterResource(
                if (selected) R.drawable.ic_calibrate else R.drawable.ic_calibrate_outlined
            ),
            contentDescription = tab.label,
            modifier = Modifier.size(size)
        )
        PianoMainTab.TUNING -> Icon(
            painter = painterResource(
                if (selected) R.drawable.ic_tune else R.drawable.ic_tune_outlined
            ),
            contentDescription = tab.label,
            modifier = Modifier.size(size)
        )
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
    selectedTab: PianoMainTab,
    onSelectTab: (PianoMainTab) -> Unit
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
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PianoMainTab.entries.forEach { tab ->
                    PlayStyleNavItem(
                        tab = tab,
                        selected = selectedTab == tab,
                        indicatorWidth = 56.dp,
                        indicatorHeight = 30.dp,
                        iconSize = 21.dp,
                        iconLabelGap = 3.dp,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayStyleNavRail(
    selectedTab: PianoMainTab,
    onSelectTab: (PianoMainTab) -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            PianoMainTab.entries.forEach { tab ->
                PlayStyleNavItem(
                    tab = tab,
                    selected = selectedTab == tab,
                    indicatorWidth = 56.dp,
                    indicatorHeight = 30.dp,
                    iconSize = 21.dp,
                    iconLabelGap = 4.dp,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayStyleNavItem(
    tab: PianoMainTab,
    selected: Boolean,
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
        label = "navIndicatorProgress_${tab.name}"
    )
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "navPressScale_${tab.name}"
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
        label = "navIconTint_${tab.name}"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) cs.onSurface else cs.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "navLabelColor_${tab.name}"
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
                Box(
                    modifier = Modifier.graphicsLayer {
                        val s = pressScale * selectionBounce.value
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconTint) {
                        PianoTabIcon(tab = tab, selected = selected, size = iconSize)
                    }
                }
            }
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = labelColor,
                maxLines = 1
            )
        }
    }
}
