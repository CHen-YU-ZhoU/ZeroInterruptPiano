package com.zerointerrupt.piano.ui.tabs

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerointerrupt.piano.BuildConfig
import com.zerointerrupt.piano.model.AppThemeMode
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.rpc.RpcConnectionProbe
import com.zerointerrupt.piano.ui.PianoUiCallbacks
import com.zerointerrupt.piano.ui.components.CheckSwitch
import com.zerointerrupt.piano.ui.components.ExpressiveCard
import com.zerointerrupt.piano.ui.components.ExpressiveConnectedGroup
import com.zerointerrupt.piano.ui.components.ExpressiveOption
import com.zerointerrupt.piano.ui.components.ExpressiveSliderBlock
import com.zerointerrupt.piano.ui.components.PageTopHeader
import kotlin.math.roundToInt

@Composable
fun TuningTab(
    settings: PianoSettings,
    probe: RpcConnectionProbe?,
    isLandscape: Boolean,
    callbacks: PianoUiCallbacks,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PageTopHeader(
                    title = "连接与外观",
                    trailingAction = {
                        RpcStatusBadge(
                            probe = probe,
                            hasToken = settings.rpcToken.isNotBlank(),
                            onClick = callbacks.onProbeRpc
                        )
                    }
                )
                RpcConfigCard(
                    settings = settings,
                    probe = probe,
                    callbacks = callbacks
                )
                AppearanceSettingsCard(
                    settings = settings,
                    onThemeModeChange = callbacks.onThemeModeChange,
                    onDynamicColorChange = callbacks.onDynamicColorChange,
                    onCheckUpdate = callbacks.onCheckUpdate
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
                PageTopHeader(title = "硬件调优")
                HardwareTuningCard(
                    settings = settings,
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageTopHeader(
                title = "连接与调优",
                trailingAction = {
                    RpcStatusBadge(
                        probe = probe,
                        hasToken = settings.rpcToken.isNotBlank(),
                        onClick = callbacks.onProbeRpc
                    )
                }
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
            AppearanceSettingsCard(
                settings = settings,
                onThemeModeChange = callbacks.onThemeModeChange,
                onDynamicColorChange = callbacks.onDynamicColorChange,
                onCheckUpdate = callbacks.onCheckUpdate
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun RpcStatusBadge(
    probe: RpcConnectionProbe?,
    hasToken: Boolean,
    onClick: () -> Unit
) {
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
private fun AppearanceSettingsCard(
    settings: PianoSettings,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit
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
                        text = "应用版本",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "当前 v${BuildConfig.VERSION_NAME} · GitHub Releases",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onCheckUpdate,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "检查更新",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
