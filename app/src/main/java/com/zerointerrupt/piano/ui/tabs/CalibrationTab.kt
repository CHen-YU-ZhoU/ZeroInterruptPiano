package com.zerointerrupt.piano.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.CalibrationMethod
import com.zerointerrupt.piano.model.CalibrationProfile
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.ui.PianoUiCallbacks
import com.zerointerrupt.piano.ui.components.CheckSwitch
import com.zerointerrupt.piano.ui.components.ExpressiveCard
import com.zerointerrupt.piano.ui.components.OverflowMenuAction
import com.zerointerrupt.piano.ui.components.PageTopHeader
import com.zerointerrupt.piano.ui.components.SmoothRoundedCornerShape

@Composable
fun CalibrationTab(
    keyboardMode: KeyboardMode,
    calibration: CalibrationGrid,
    profiles: List<CalibrationProfile>,
    activeProfileId: String,
    isLandscape: Boolean,
    callbacks: PianoUiCallbacks,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<CalibrationProfile?>(null) }

    val activeProfile = remember(profiles, activeProfileId, calibration, keyboardMode) {
        profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.firstOrNull()
            ?: CalibrationProfile(
                id = CalibrationProfile.DEFAULT_PROFILE_ID,
                name = CalibrationProfile.DEFAULT_PROFILE_NAME,
                keyboardMode = keyboardMode,
                grid = calibration
            )
    }

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

    val headerMenuActions = listOf(
        OverflowMenuAction(
            label = "重置当前方案为默认网格",
            isDestructive = true,
            onClick = callbacks.onResetCalibration
        )
    )

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PageTopHeader(
                    title = "校准",
                    menuActions = headerMenuActions
                )
                ActiveCalibrationHeroCard(
                    profile = activeProfile,
                    keyboardMode = keyboardMode,
                    onOpenGridCalibration = callbacks.onOpenCalibrationOverlay,
                    onOpenManualCalibration = callbacks.onOpenManualCalibrationOverlay,
                    onRenameActive = { renamingProfile = activeProfile }
                )
                Spacer(Modifier.height(4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalibrationProfilesSectionHeader(
                    count = profiles.size,
                    onCreateNew = { showCreateDialog = true }
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        CalibrationProfileRowCard(
                            profile = profile,
                            isSelected = profile.id == activeProfileId,
                            canDelete = profiles.size > 1,
                            onSelect = { callbacks.onSelectCalibrationProfile(profile.id) },
                            onRename = { renamingProfile = profile },
                            onDelete = { callbacks.onDeleteCalibrationProfile(profile.id) }
                        )
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageTopHeader(
                    title = "校准",
                    menuActions = headerMenuActions,
                    trailingAction = {
                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("新建方案", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            item {
                ActiveCalibrationHeroCard(
                    profile = activeProfile,
                    keyboardMode = keyboardMode,
                    onOpenGridCalibration = callbacks.onOpenCalibrationOverlay,
                    onOpenManualCalibration = callbacks.onOpenManualCalibrationOverlay,
                    onRenameActive = { renamingProfile = activeProfile }
                )
            }

            item {
                CalibrationProfilesSectionHeader(
                    count = profiles.size,
                    onCreateNew = null
                )
            }

            items(profiles, key = { it.id }) { profile ->
                CalibrationProfileRowCard(
                    profile = profile,
                    isSelected = profile.id == activeProfileId,
                    canDelete = profiles.size > 1,
                    onSelect = { callbacks.onSelectCalibrationProfile(profile.id) },
                    onRename = { renamingProfile = profile },
                    onDelete = { callbacks.onDeleteCalibrationProfile(profile.id) }
                )
            }

            item {
                OutlinedButton(
                    onClick = callbacks.onResetCalibration,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = "重置当前方案为默认网格",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            item { Spacer(Modifier.height(6.dp)) }
        }
    }
}

@Composable
private fun ActiveCalibrationHeroCard(
    profile: CalibrationProfile,
    keyboardMode: KeyboardMode,
    onOpenGridCalibration: () -> Unit,
    onOpenManualCalibration: () -> Unit,
    onRenameActive: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isManual = profile.grid.calibrationMethod == CalibrationMethod.MANUAL_PICK

    ExpressiveCard(cornerRadius = 24.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = CircleShape,
                            color = cs.secondaryContainer
                        ) {
                            Text(
                                text = profile.keyboardMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (isManual) cs.tertiaryContainer else cs.primaryContainer
                        ) {
                            Text(
                                text = profile.grid.calibrationMethod.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isManual) cs.onTertiaryContainer else cs.onPrimaryContainer,
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

                IconButton(
                    onClick = onRenameActive,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "重命名当前方案",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Visual landscape keyboard layout map
            CalibrationPreviewCanvas(
                grid = profile.grid,
                keyboardMode = profile.keyboardMode,
                isMini = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.15f)
            )

            // Quick calibration launchers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenGridCalibration,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Text(
                        text = if (keyboardMode == KeyboardMode.KEYS_36) "36键 网格校准" else "3×7 网格校准",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onOpenManualCalibration,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
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
        }
    }
}

/**
 * Landscape screen preview rendering the normalized piano calibration grid and key positions.
 */
@Composable
private fun CalibrationPreviewCanvas(
    grid: CalibrationGrid,
    keyboardMode: KeyboardMode,
    isMini: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = SmoothRoundedCornerShape(if (isMini) 10.dp else 16.dp)
    val keys = remember(keyboardMode) { CalibrationGrid.pickOrderFor(keyboardMode) }
    val diamondPath = remember { Path() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(cs.surfaceContainerHighest.copy(alpha = 0.72f))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.55f), shape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle landscape screen center guides
            drawLine(
                color = cs.outlineVariant.copy(alpha = 0.28f),
                start = Offset(w * 0.5f, 0f),
                end = Offset(w * 0.5f, h),
                strokeWidth = 1f
            )
            drawLine(
                color = cs.outlineVariant.copy(alpha = 0.28f),
                start = Offset(0f, h * 0.5f),
                end = Offset(w, h * 0.5f),
                strokeWidth = 1f
            )

            // Calibration bounding box
            val boxLeft = grid.leftNorm * w
            val boxTop = grid.topNorm * h
            val boxW = ((grid.rightNorm - grid.leftNorm) * w).coerceAtLeast(4f)
            val boxH = ((grid.bottomNorm - grid.topNorm) * h).coerceAtLeast(4f)

            val isManual = grid.calibrationMethod == CalibrationMethod.MANUAL_PICK
            if (!isManual) {
                drawRoundRect(
                    color = cs.primary.copy(alpha = 0.10f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(if (isMini) 4f else 10f, if (isMini) 4f else 10f)
                )
                drawRoundRect(
                    color = cs.primary.copy(alpha = 0.65f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(if (isMini) 4f else 10f, if (isMini) 4f else 10f),
                    style = Stroke(width = if (isMini) 1.dp.toPx() else 1.5.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color = cs.tertiary.copy(alpha = 0.35f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(if (isMini) 4f else 10f, if (isMini) 4f else 10f),
                    style = Stroke(
                        width = if (isMini) 0.8.dp.toPx() else 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                        cap = StrokeCap.Round
                    )
                )
            }

            // Key dots
            val whiteRadius = if (isMini) 1.7.dp.toPx() else 4.2.dp.toPx()
            val sharpRadius = if (isMini) 1.4.dp.toPx() else 3.4.dp.toPx()

            for (keyId in keys) {
                val (nx, ny) = grid.keyNorm(keyId)
                val cx = nx * w
                val cy = ny * h
                val sharp = CalibrationGrid.isSharpKey(keyId)
                val isPicked = !isManual || grid.manualPoints.containsKey(keyId)

                if (sharp) {
                    val r = sharpRadius
                    diamondPath.reset()
                    diamondPath.moveTo(cx, cy - r)
                    diamondPath.lineTo(cx + r, cy)
                    diamondPath.lineTo(cx, cy + r)
                    diamondPath.lineTo(cx - r, cy)
                    diamondPath.close()
                    if (isPicked) {
                        drawPath(path = diamondPath, color = cs.tertiary)
                    } else {
                        drawPath(
                            path = diamondPath,
                            color = cs.outline.copy(alpha = 0.45f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                } else {
                    if (isPicked) {
                        if (!isMini) {
                            drawCircle(
                                color = cs.primary.copy(alpha = 0.18f),
                                radius = whiteRadius * 1.55f,
                                center = Offset(cx, cy)
                            )
                        }
                        drawCircle(
                            color = if (isManual) cs.tertiary else cs.primary,
                            radius = whiteRadius,
                            center = Offset(cx, cy)
                        )
                    } else {
                        drawCircle(
                            color = cs.outline.copy(alpha = 0.45f),
                            radius = whiteRadius * 0.9f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationProfilesSectionHeader(
    count: Int,
    onCreateNew: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "校准方案 ($count)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "可按游戏保存并快速切换多套键位布局",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        if (onCreateNew != null) {
            FilledTonalButton(
                onClick = onCreateNew,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("新建方案", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CalibrationProfileRowCard(
    profile: CalibrationProfile,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isManual = profile.grid.calibrationMethod == CalibrationMethod.MANUAL_PICK
    val shape = SmoothRoundedCornerShape(18.dp)

    Surface(
        shape = shape,
        color = if (isSelected) cs.primaryContainer.copy(alpha = 0.45f) else cs.surfaceContainerLow,
        border = if (isSelected) BorderStroke(1.5.dp, cs.primary) else BorderStroke(0.75.dp, cs.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isManual) cs.tertiaryContainer else cs.secondaryContainer
                    ) {
                        Text(
                            text = "${profile.keyboardMode.label}·${profile.grid.calibrationMethod.label}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isManual) cs.onTertiaryContainer else cs.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                    onClick = onRename,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "重命名",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
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
