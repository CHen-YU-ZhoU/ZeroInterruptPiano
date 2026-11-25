package com.zerointerrupt.piano.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

@Immutable
data class ExpressiveOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null
)

/**
 * Material 3 Expressive Connected Button Group — unified with ZeroInterruptClicker:
 * - No outer wrapper slab container.
 * - Adjacent unselected items share tight 8.dp inner corners with 24.dp outer end caps on secondaryContainer fill.
 * - Selected item morphs all 4 corners smoothly into a full pill with primary fill and onPrimary content.
 */
@Composable
fun <T> ExpressiveConnectedGroup(
    options: List<ExpressiveOption<T>>,
    selectedValue: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selectedValue
            val isFirst = index == 0
            val isLast = index == options.lastIndex

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val pressScale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
                label = "connPress_$index"
            )

            val innerTarget = when {
                isSelected -> 24.dp
                isPressed -> 14.dp
                else -> 8.dp
            }
            val innerCorner by animateDpAsState(
                targetValue = innerTarget,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 440f
                ),
                label = "connCorner_$index"
            )

            val shape = RoundedCornerShape(
                topStart = if (isFirst || isSelected) 24.dp else innerCorner,
                bottomStart = if (isFirst || isSelected) 24.dp else innerCorner,
                topEnd = if (isLast || isSelected) 24.dp else innerCorner,
                bottomEnd = if (isLast || isSelected) 24.dp else innerCorner
            )

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) cs.primary else cs.secondaryContainer,
                animationSpec = tween(durationMillis = 180),
                label = "connBg_$index"
            )

            val fgColor by animateColorAsState(
                targetValue = if (isSelected) cs.onPrimary else cs.onSecondaryContainer,
                animationSpec = tween(durationMillis = 180),
                label = "connFg_$index"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(shape)
                    .background(bgColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option.value)
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (option.icon != null) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = option.label,
                        color = fgColor,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Capsule Slider — unified with ZeroInterruptClicker:
 * High-contrast split track, trailing stop dot, tall capsule thumb, and floating tooltip Popup while dragging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    floatingLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = (isPressed || isDragged) && enabled

    val thumbWidth by animateDpAsState(
        targetValue = if (isInteracting) 6.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "sliderThumbW"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isInteracting) 32.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "sliderThumbH"
    )

    val activeColor = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.28f)
    val inactiveColor = if (enabled) cs.secondaryContainer else cs.onSurface.copy(alpha = 0.12f)
    val stopDotColor = if (enabled) cs.primary.copy(alpha = 0.75f) else cs.onSurface.copy(alpha = 0.22f)

    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        thumb = {
            Box(contentAlignment = Alignment.Center) {
                if (isInteracting && floatingLabel.isNotBlank()) {
                    Popup(
                        alignment = Alignment.TopCenter,
                        offset = IntOffset(0, -105)
                    ) {
                        AnimatedVisibility(
                            visible = isInteracting,
                            enter = fadeIn(tween(90)) + scaleIn(initialScale = 0.8f),
                            exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.8f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = cs.inverseSurface,
                                contentColor = cs.inverseOnSurface,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = floatingLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .clip(CircleShape)
                        .background(activeColor)
                )
            }
        },
        track = { sliderState ->
            val rangeSpan = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                .takeIf { it > 0f } ?: 1f
            val fraction = ((sliderState.value - sliderState.valueRange.start) / rangeSpan).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                val h = size.height
                val w = size.width
                val gapPx = 8.dp.toPx()
                val splitX = (w * fraction).coerceIn(0f, w)
                val radius = CornerRadius(h * 0.5f, h * 0.5f)

                val activeEnd = (splitX - gapPx).coerceAtLeast(0f)
                if (activeEnd >= h * 0.9f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(activeEnd, h),
                        cornerRadius = radius
                    )
                }

                val inactiveStart = (splitX + gapPx).coerceAtMost(w)
                val inactiveWidth = (w - inactiveStart).coerceAtLeast(0f)
                if (inactiveWidth >= h * 0.9f) {
                    drawRoundRect(
                        color = inactiveColor,
                        topLeft = Offset(inactiveStart, 0f),
                        size = Size(inactiveWidth, h),
                        cornerRadius = radius
                    )
                    if (inactiveWidth > h * 0.6f) {
                        drawCircle(
                            color = stopDotColor,
                            radius = 2.2.dp.toPx(),
                            center = Offset(w - h * 0.5f, h * 0.5f)
                        )
                    }
                }
            }
        }
    )
}

/**
 * Label + Pill Value Badge + ExpressiveSlider block — unified with ZeroInterruptClicker's TuningTab.
 */
@Composable
fun ExpressiveSliderBlock(
    label: String,
    valueBadge: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Surface(
                shape = CircleShape,
                color = if (enabled) cs.surfaceContainerHigh else cs.onSurface.copy(alpha = 0.08f)
            ) {
                Text(
                    text = valueBadge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        ExpressiveSlider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            floatingLabel = valueBadge,
            enabled = enabled,
            steps = steps
        )
    }
}

/**
 * Clean borderless Material 3 tonal surface card — unified with ZeroInterruptClicker.
 */
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    cornerRadius: Dp = 24.dp,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
fun CheckSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = null
    )
}
