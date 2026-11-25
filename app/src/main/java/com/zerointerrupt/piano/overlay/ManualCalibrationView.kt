package com.zerointerrupt.piano.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.CalibrationMethod
import com.zerointerrupt.piano.model.KeyboardMode
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen interactive overlay for sequential per-key manual point picking.
 *
 * Interaction highlights:
 * - Draggable MD3 floating HUD card prompting each note in order ("请点击 低音 1", "接着点击 低音 #1",
 *   "接着点击 低音 2", "接着点击 低音 #2", "接着点击 低音 3", ...) with octave chips & live progress bar.
 * - Hold-and-aim precision crosshair reticle: drag while holding down to aim cleanly without thumb occlusion,
 *   release to lock the note with ripple pulse + haptic tick and auto-advance to the next note.
 * - Drag any already-placed marker to fine-tune its position; tap any placed marker to audition via RPC.
 * - Supports Undo ("撤销"), Skip ("跳过"), Restart ("重拾"), 21/36-key toggle, and Save ("保存").
 */
@SuppressLint("ViewConstructor")
class ManualCalibrationView(
    context: Context,
    initialGrid: CalibrationGrid,
    initialPalette: OverlayMd3Palette,
    initialKeyboardMode: KeyboardMode,
    private val initialProfileName: String,
    private val onAuditionKey: (keyId: String, liveGrid: CalibrationGrid) -> Unit,
    private val onRequestSave: (grid: CalibrationGrid, mode: KeyboardMode) -> Unit,
    private val onSwitchToGridMode: () -> Unit,
    private val onCancel: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private var palette: OverlayMd3Palette = initialPalette
    private var baseGrid: CalibrationGrid = initialGrid.clamped()
    private var keyboardMode: KeyboardMode = initialKeyboardMode
    private var orderedKeys: List<String> = CalibrationGrid.pickOrderFor(keyboardMode)
    private val pickedPoints = LinkedHashMap<String, Pair<Float, Float>>()
    private val pickHistory = ArrayDeque<String>()
    private var currentIndex: Int = 0
    private var hasAdvancedOnce: Boolean = false

    // HUD draggable position (normalized center X, top Y in px, initialized on first layout)
    private var hudInitialized = false
    private var hudLeft = 0f
    private var hudTop = 0f
    private val hudRect = RectF()
    private val modeChipRect = RectF()
    private val switchGridRect = RectF()
    private val btnUndoRect = RectF()
    private val btnSkipRect = RectF()
    private val btnResetRect = RectF()
    private val btnSaveRect = RectF()
    private val btnCancelRect = RectF()

    // Touch / aiming state
    private enum class TouchMode {
        NONE,
        DRAG_HUD,
        AIM_NEW_POINT,
        DRAG_MARKER,
        BTN_MODE,
        BTN_SWITCH_GRID,
        BTN_UNDO,
        BTN_SKIP,
        BTN_RESET,
        BTN_SAVE,
        BTN_CANCEL
    }

    private var touchMode = TouchMode.NONE
    private var downX = 0f
    private var downY = 0f
    private var hudStartLeft = 0f
    private var hudStartTop = 0f
    private var movedBeyondSlop = false
    private var aimingActive = false
    private var aimX = 0f
    private var aimY = 0f
    private var activeMarkerKey: String? = null
    private var markerOrigNorm: Pair<Float, Float>? = null

    // Visual animations (ripple + prompt pulse + audition flash)
    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleStartMs = 0L
    private var promptPulseStartMs = 0L
    private var auditionFlashKey: String? = null
    private val clearFlashRunnable = Runnable {
        auditionFlashKey = null
        invalidate()
    }

    // Paints
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 0, 0, 0)
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f)
    }
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
    }
    private val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(10.5f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.0f)
    }
    private val crosshairFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
    }
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hudStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.1f)
    }
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val promptBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val promptBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val promptTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = dp(15.5f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val promptSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = dp(11.5f)
    }
    private val headerMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = dp(11f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val progressTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val connectorPath = Path()

    init {
        if (baseGrid.calibrationMethod == CalibrationMethod.MANUAL_PICK && baseGrid.manualPoints.isNotEmpty()) {
            for (k in orderedKeys) {
                baseGrid.manualPoints[k]?.let {
                    pickedPoints[k] = it
                    pickHistory.addLast(k)
                }
            }
        }
        currentIndex = findNextUnpickedIndex(0)
        hasAdvancedOnce = pickedPoints.isNotEmpty()
        applyPalette(initialPalette)
    }

    fun applyPalette(newPalette: OverlayMd3Palette) {
        palette = newPalette
        hudBgPaint.color = OverlayMd3Palette.withAlpha(palette.surfaceContainerHigh, 246)
        hudStrokePaint.color = OverlayMd3Palette.withAlpha(palette.outlineVariant, 195)
        gripPaint.color = OverlayMd3Palette.withAlpha(palette.onSurfaceVariant, 150)
        promptTitlePaint.color = palette.onSurface
        promptSubPaint.color = palette.onSurfaceVariant
        headerMetaPaint.color = palette.onSurfaceVariant
        progressTrackPaint.color = palette.surfaceContainerHighest
        progressFillPaint.color = palette.primary
        pathPaint.color = OverlayMd3Palette.withAlpha(palette.primary, 110)
        invalidate()
    }

    fun setKeyboardMode(mode: KeyboardMode) {
        if (keyboardMode == mode) return
        keyboardMode = mode
        orderedKeys = CalibrationGrid.pickOrderFor(mode)
        currentIndex = findNextUnpickedIndex(0)
        invalidate()
    }

    fun flashKey(keyId: String) {
        removeCallbacks(clearFlashRunnable)
        auditionFlashKey = keyId
        invalidate()
        postDelayed(clearFlashRunnable, 220L)
    }

    private fun findNextUnpickedIndex(startFrom: Int): Int {
        for (i in startFrom until orderedKeys.size) {
            if (orderedKeys[i] !in pickedPoints) return i
        }
        for (i in 0 until startFrom.coerceAtMost(orderedKeys.size)) {
            if (orderedKeys[i] !in pickedPoints) return i
        }
        return orderedKeys.size
    }

    private fun currentTargetKey(): String? =
        orderedKeys.getOrNull(currentIndex)

    private fun pickedCountForCurrentMode(): Int =
        orderedKeys.count { it in pickedPoints }

    private fun buildLiveGrid(): CalibrationGrid {
        return baseGrid.copy(
            calibrationMethod = CalibrationMethod.MANUAL_PICK,
            manualPoints = LinkedHashMap(pickedPoints)
        ).clamped()
    }

    private fun updateHudGeometry() {
        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        val cardW = min(dp(384f), w - dp(24f))
        val cardH = dp(148f)

        if (!hudInitialized) {
            hudLeft = (w - cardW) * 0.5f
            hudTop = dp(24f)
            hudInitialized = true
        }
        hudLeft = hudLeft.coerceIn(dp(10f), max(dp(10f), w - cardW - dp(10f)))
        hudTop = hudTop.coerceIn(dp(10f), max(dp(10f), h - cardH - dp(10f)))
        hudRect.set(hudLeft, hudTop, hudLeft + cardW, hudTop + cardH)

        // Top right chips inside HUD: [21键/36键] [网格]
        val chipH = dp(24f)
        val chipTop = hudRect.top + dp(10f)
        val chipGap = dp(6f)
        val gridW = dp(52f)
        val modeW = dp(54f)
        switchGridRect.set(hudRect.right - dp(12f) - gridW, chipTop, hudRect.right - dp(12f), chipTop + chipH)
        modeChipRect.set(switchGridRect.left - chipGap - modeW, chipTop, switchGridRect.left - chipGap, chipTop + chipH)

        // Bottom button row: [撤销] [跳过] [重拾] [保存] [关闭]
        val padX = dp(12f)
        val gap = dp(6f)
        val btnH = dp(32f)
        val btnBtm = hudRect.bottom - dp(11f)
        val btnTop = btnBtm - btnH
        val avail = cardW - padX * 2f - gap * 4f
        val weights = floatArrayOf(0.95f, 0.9f, 0.9f, 1.35f, 0.9f)
        val sumW = weights.sum()
        var curX = hudRect.left + padX
        val rects = arrayOf(btnUndoRect, btnSkipRect, btnResetRect, btnSaveRect, btnCancelRect)
        for (i in rects.indices) {
            val bw = avail * (weights[i] / sumW)
            rects[i].set(curX, btnTop, curX + bw, btnBtm)
            curX += bw + gap
        }
    }

    private fun markerRadius(keyId: String): Float =
        if (CalibrationGrid.isSharpKey(keyId)) dp(14f) else dp(15.5f)

    private fun octaveColors(keyId: String, isLit: Boolean): Pair<Int, Int> {
        val isSharp = CalibrationGrid.isSharpKey(keyId)
        if (isLit) {
            return palette.tertiary to palette.onTertiary
        }
        return when (CalibrationGrid.normalizeKeyId(keyId).firstOrNull()) {
            'L' -> if (isSharp) palette.tertiary to palette.onTertiary else palette.tertiaryContainer to palette.onTertiaryContainer
            'H' -> if (isSharp) palette.secondary to palette.onSecondary else palette.secondaryContainer to palette.onSecondaryContainer
            else -> if (isSharp) palette.primary to palette.onPrimary else palette.primaryContainer to palette.onPrimaryContainer
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        updateHudGeometry()

        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()

        // Subtle scrim keeping game keys clearly visible
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        // Subtle dashed sequence path connecting picked keys within each octave
        for (oct in listOf('L', 'M', 'H')) {
            connectorPath.reset()
            var started = false
            for (k in orderedKeys) {
                if (k.firstOrNull() != oct) continue
                val pt = pickedPoints[k] ?: continue
                val px = pt.first * w
                val py = pt.second * h
                if (!started) {
                    connectorPath.moveTo(px, py)
                    started = true
                } else {
                    connectorPath.lineTo(px, py)
                }
            }
            if (started) {
                canvas.drawPath(connectorPath, pathPaint)
            }
        }

        // Draw already picked markers (white keys first, sharp diamonds on top)
        for (pass in 0..1) {
            val drawSharps = pass == 1
            for (k in orderedKeys) {
                val isSharp = CalibrationGrid.isSharpKey(k)
                if (isSharp != drawSharps) continue
                val pt = pickedPoints[k] ?: continue
                val cx = pt.first * w
                val cy = pt.second * h
                val isLit = (k == auditionFlashKey || k == activeMarkerKey)
                val (bg, fg) = octaveColors(k, isLit)
                val r = markerRadius(k)

                markerFillPaint.color = OverlayMd3Palette.withAlpha(bg, 232)
                markerStrokePaint.color = if (isLit) palette.onSurface else OverlayMd3Palette.withAlpha(fg, 190)

                if (isSharp) {
                    canvas.save()
                    canvas.rotate(45f, cx, cy)
                    canvas.drawRoundRect(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f, dp(4f), dp(4f), markerFillPaint)
                    canvas.drawRoundRect(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f, dp(4f), dp(4f), markerStrokePaint)
                    canvas.restore()
                } else {
                    canvas.drawCircle(cx, cy, r, markerFillPaint)
                    canvas.drawCircle(cx, cy, r, markerStrokePaint)
                }

                markerTextPaint.color = fg
                val ty = cy - (markerTextPaint.descent() + markerTextPaint.ascent()) * 0.5f
                canvas.drawText(CalibrationGrid.formatKeyCompactBadge(k), cx, ty, markerTextPaint)
            }
        }

        // Confirmation ripple animation
        val rippleAge = now - rippleStartMs
        if (rippleStartMs > 0L && rippleAge in 0L..320L) {
            val frac = rippleAge / 320f
            val rr = dp(14f) + dp(28f) * frac
            ripplePaint.color = OverlayMd3Palette.withAlpha(palette.primary, ((1f - frac) * 210).toInt())
            canvas.drawCircle(rippleX, rippleY, rr, ripplePaint)
            postInvalidateOnAnimation()
        }

        // Live aiming crosshair while finger is down
        if (aimingActive) {
            val labelKey = activeMarkerKey ?: currentTargetKey()
            val accent = if (labelKey != null && CalibrationGrid.isSharpKey(labelKey)) palette.tertiary else palette.primary
            crosshairPaint.color = accent
            crosshairFillPaint.color = OverlayMd3Palette.withAlpha(accent, 42)
            val r = dp(24f)
            canvas.drawCircle(aimX, aimY, r, crosshairFillPaint)
            canvas.drawCircle(aimX, aimY, r, crosshairPaint)
            val arm = dp(34f)
            val inner = dp(8f)
            canvas.drawLine(aimX - arm, aimY, aimX - inner, aimY, crosshairPaint)
            canvas.drawLine(aimX + inner, aimY, aimX + arm, aimY, crosshairPaint)
            canvas.drawLine(aimX, aimY - arm, aimX, aimY - inner, crosshairPaint)
            canvas.drawLine(aimX, aimY + inner, aimX, aimY + arm, crosshairPaint)
            crosshairFillPaint.color = accent
            canvas.drawCircle(aimX, aimY, dp(3f), crosshairFillPaint)

            if (labelKey != null) {
                val badgeText = CalibrationGrid.formatKeyWithOctave(labelKey)
                val pillW = max(dp(76f), btnTextPaint.measureText(badgeText) + dp(22f))
                val pillH = dp(26f)
                val px = aimX.coerceIn(pillW * 0.5f + dp(8f), w - pillW * 0.5f - dp(8f))
                val py = (aimY - dp(54f)).coerceAtLeast(dp(14f))
                val pillRect = RectF(px - pillW * 0.5f, py - pillH * 0.5f, px + pillW * 0.5f, py + pillH * 0.5f)
                btnBgPaint.color = accent
                btnTextPaint.color = if (accent == palette.tertiary) palette.onTertiary else palette.onPrimary
                canvas.drawRoundRect(pillRect, dp(13f), dp(13f), btnBgPaint)
                val by = pillRect.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) * 0.5f
                canvas.drawText(badgeText, pillRect.centerX(), by, btnTextPaint)
            }
        }

        // Draw Draggable HUD Card
        drawHudCard(canvas, now)
    }

    private fun drawHudCard(canvas: Canvas, now: Long) {
        // Card container
        canvas.drawRoundRect(hudRect, dp(22f), dp(22f), hudBgPaint)
        canvas.drawRoundRect(hudRect, dp(22f), dp(22f), hudStrokePaint)

        // Subtle drag handle bar at top-center
        val gripW = dp(34f)
        val gripH = dp(3.5f)
        val gripLeft = hudRect.centerX() - gripW * 0.5f
        val gripTop = hudRect.top + dp(5f)
        canvas.drawRoundRect(gripLeft, gripTop, gripLeft + gripW, gripTop + gripH, dp(2f), dp(2f), gripPaint)

        // Header line: Profile name & picked counter
        val pickedCount = pickedCountForCurrentMode()
        val totalCount = orderedKeys.size
        val headerY = hudRect.top + dp(26f)
        headerMetaPaint.color = palette.onSurfaceVariant
        canvas.drawText(
            "手动拾取 · $initialProfileName · $pickedCount/$totalCount",
            hudRect.left + dp(14f),
            headerY,
            headerMetaPaint
        )

        // Top-right mode chip (21键 / 36键) & switch-to-grid chip
        val chipCy = modeChipRect.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) * 0.5f
        btnBgPaint.color = palette.secondaryContainer
        btnTextPaint.color = palette.onSecondaryContainer
        canvas.drawRoundRect(modeChipRect, dp(12f), dp(12f), btnBgPaint)
        canvas.drawText(keyboardMode.label, modeChipRect.centerX(), chipCy, btnTextPaint)

        btnBgPaint.color = palette.surfaceContainerHighest
        btnTextPaint.color = palette.onSurfaceVariant
        canvas.drawRoundRect(switchGridRect, dp(12f), dp(12f), btnBgPaint)
        canvas.drawText("网格", switchGridRect.centerX(), chipCy, btnTextPaint)

        // Main guided prompt row with bounce pulse animation
        val targetKey = currentTargetKey()
        val pulseAge = now - promptPulseStartMs
        val pulseScale = if (promptPulseStartMs > 0L && pulseAge in 0L..220L) {
            val t = pulseAge / 220f
            postInvalidateOnAnimation()
            1f + 0.10f * kotlin.math.sin(t * Math.PI).toFloat()
        } else {
            1f
        }

        val badgeSize = dp(42f)
        val badgeLeft = hudRect.left + dp(14f)
        val badgeTop = hudRect.top + dp(40f)
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)

        canvas.save()
        canvas.scale(pulseScale, pulseScale, badgeRect.centerX(), badgeRect.centerY())
        if (targetKey != null) {
            val isSharp = CalibrationGrid.isSharpKey(targetKey)
            promptBadgePaint.color = if (isSharp) palette.tertiary else palette.primary
            promptBadgeTextPaint.color = if (isSharp) palette.onTertiary else palette.onPrimary
            canvas.drawRoundRect(badgeRect, dp(14f), dp(14f), promptBadgePaint)
            val shortTxt = CalibrationGrid.formatKeyShort(targetKey)
            val by = badgeRect.centerY() - (promptBadgeTextPaint.descent() + promptBadgeTextPaint.ascent()) * 0.5f
            canvas.drawText(shortTxt, badgeRect.centerX(), by, promptBadgeTextPaint)
        } else {
            promptBadgePaint.color = palette.tertiaryContainer
            promptBadgeTextPaint.color = palette.onTertiaryContainer
            canvas.drawRoundRect(badgeRect, dp(14f), dp(14f), promptBadgePaint)
            val by = badgeRect.centerY() - (promptBadgeTextPaint.descent() + promptBadgeTextPaint.ascent()) * 0.5f
            canvas.drawText("✓", badgeRect.centerX(), by, promptBadgeTextPaint)
        }
        canvas.restore()

        val textX = badgeRect.right + dp(12f)
        if (targetKey != null) {
            val verb = if (hasAdvancedOnce || pickedCount > 0) "接着点击" else "请点击"
            val promptMain = "$verb ${CalibrationGrid.formatKeyWithOctave(targetKey)}"
            val pitchName = CalibrationGrid.formatKeyPitchName(targetKey)
            val keyKind = if (CalibrationGrid.isSharpKey(targetKey)) "黑键/升降音" else "白键"
            val promptSub = "$pitchName ($keyKind) · 按住拖动瞄准，松手落点"
            promptTitlePaint.color = palette.onSurface
            canvas.drawText(promptMain, textX, badgeRect.top + dp(17f), promptTitlePaint)
            canvas.drawText(promptSub, textX, badgeRect.top + dp(35f), promptSubPaint)
        } else {
            promptTitlePaint.color = palette.primary
            canvas.drawText("全部 $totalCount 音已拾取完成！", textX, badgeRect.top + dp(17f), promptTitlePaint)
            canvas.drawText("拖动圆点可微调 · 点圆点可试音 · 点保存命名", textX, badgeRect.top + dp(35f), promptSubPaint)
        }

        // Progress bar
        val barTop = badgeRect.bottom + dp(10f)
        val barH = dp(5f)
        val barLeft = hudRect.left + dp(14f)
        val barRight = hudRect.right - dp(14f)
        canvas.drawRoundRect(barLeft, barTop, barRight, barTop + barH, dp(3f), dp(3f), progressTrackPaint)
        val frac = (pickedCount.toFloat() / totalCount.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (frac > 0f) {
            val fillRight = barLeft + (barRight - barLeft) * frac
            canvas.drawRoundRect(barLeft, barTop, fillRight, barTop + barH, dp(3f), dp(3f), progressFillPaint)
        }

        // Action Buttons Row
        val btnCy = btnSaveRect.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) * 0.5f

        val canUndo = pickHistory.isNotEmpty()
        btnBgPaint.color = if (canUndo) palette.secondaryContainer else palette.surfaceContainerHighest
        btnTextPaint.color = if (canUndo) palette.onSecondaryContainer else OverlayMd3Palette.withAlpha(palette.onSurfaceVariant, 110)
        canvas.drawRoundRect(btnUndoRect, dp(16f), dp(16f), btnBgPaint)
        canvas.drawText("撤销", btnUndoRect.centerX(), btnCy, btnTextPaint)

        val canSkip = targetKey != null
        btnBgPaint.color = if (canSkip) palette.secondaryContainer else palette.surfaceContainerHighest
        btnTextPaint.color = if (canSkip) palette.onSecondaryContainer else OverlayMd3Palette.withAlpha(palette.onSurfaceVariant, 110)
        canvas.drawRoundRect(btnSkipRect, dp(16f), dp(16f), btnBgPaint)
        canvas.drawText("跳过", btnSkipRect.centerX(), btnCy, btnTextPaint)

        btnBgPaint.color = palette.surfaceContainerHighest
        btnTextPaint.color = palette.onSurface
        canvas.drawRoundRect(btnResetRect, dp(16f), dp(16f), btnBgPaint)
        canvas.drawText("重拾", btnResetRect.centerX(), btnCy, btnTextPaint)

        val isComplete = pickedCount >= totalCount
        btnBgPaint.color = if (pickedCount > 0) palette.primary else palette.surfaceContainerHighest
        btnTextPaint.color = if (pickedCount > 0) palette.onPrimary else OverlayMd3Palette.withAlpha(palette.onSurfaceVariant, 110)
        canvas.drawRoundRect(btnSaveRect, dp(16f), dp(16f), btnBgPaint)
        canvas.drawText(if (isComplete) "保存方案" else "保存", btnSaveRect.centerX(), btnCy, btnTextPaint)

        btnBgPaint.color = palette.surfaceContainerHighest
        btnTextPaint.color = palette.onSurface
        canvas.drawRoundRect(btnCancelRect, dp(16f), dp(16f), btnBgPaint)
        canvas.drawText("关闭", btnCancelRect.centerX(), btnCy, btnTextPaint)
    }

    private fun hitTestMarker(x: Float, y: Float): String? {
        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        val hitR = dp(20f)
        // Check sharps first (drawn on top)
        for (pass in 0..1) {
            val checkSharps = pass == 0
            for (k in orderedKeys.reversed()) {
                if (CalibrationGrid.isSharpKey(k) != checkSharps) continue
                val pt = pickedPoints[k] ?: continue
                val mx = pt.first * w
                val my = pt.second * h
                if (hypot((x - mx).toDouble(), (y - my).toDouble()) <= hitR) {
                    return k
                }
            }
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        val slop = dp(8f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateHudGeometry()
                downX = x
                downY = y
                movedBeyondSlop = false
                aimingActive = false
                activeMarkerKey = null
                markerOrigNorm = null

                touchMode = when {
                    btnCancelRect.contains(x, y) -> TouchMode.BTN_CANCEL
                    btnSaveRect.contains(x, y) -> TouchMode.BTN_SAVE
                    btnResetRect.contains(x, y) -> TouchMode.BTN_RESET
                    btnSkipRect.contains(x, y) -> TouchMode.BTN_SKIP
                    btnUndoRect.contains(x, y) -> TouchMode.BTN_UNDO
                    modeChipRect.contains(x, y) -> TouchMode.BTN_MODE
                    switchGridRect.contains(x, y) -> TouchMode.BTN_SWITCH_GRID
                    hudRect.contains(x, y) -> {
                        hudStartLeft = hudLeft
                        hudStartTop = hudTop
                        TouchMode.DRAG_HUD
                    }
                    else -> {
                        val hitKey = hitTestMarker(x, y)
                        if (hitKey != null) {
                            activeMarkerKey = hitKey
                            markerOrigNorm = pickedPoints[hitKey]
                            TouchMode.DRAG_MARKER
                        } else if (currentTargetKey() != null) {
                            aimingActive = true
                            aimX = x
                            aimY = y
                            invalidate()
                            TouchMode.AIM_NEW_POINT
                        } else {
                            TouchMode.NONE
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - downX
                val dy = y - downY
                if (hypot(dx.toDouble(), dy.toDouble()) > slop) {
                    movedBeyondSlop = true
                }
                when (touchMode) {
                    TouchMode.DRAG_HUD -> {
                        hudLeft = hudStartLeft + dx
                        hudTop = hudStartTop + dy
                        invalidate()
                    }
                    TouchMode.AIM_NEW_POINT -> {
                        aimX = x.coerceIn(0f, w)
                        aimY = y.coerceIn(0f, h)
                        invalidate()
                    }
                    TouchMode.DRAG_MARKER -> {
                        if (movedBeyondSlop) {
                            val k = activeMarkerKey
                            if (k != null) {
                                aimingActive = true
                                aimX = x.coerceIn(0f, w)
                                aimY = y.coerceIn(0f, h)
                                pickedPoints[k] = Pair(
                                    (aimX / w).coerceIn(0.01f, 0.99f),
                                    (aimY / h).coerceIn(0.01f, 0.99f)
                                )
                                invalidate()
                            }
                        }
                    }
                    else -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val isCancel = event.actionMasked == MotionEvent.ACTION_CANCEL
                if (!isCancel) {
                    when (touchMode) {
                        TouchMode.BTN_CANCEL -> if (!movedBeyondSlop) onCancel()
                        TouchMode.BTN_SAVE -> if (!movedBeyondSlop && pickedCountForCurrentMode() > 0) {
                            onRequestSave(buildLiveGrid(), keyboardMode)
                        }
                        TouchMode.BTN_RESET -> if (!movedBeyondSlop) {
                            pickedPoints.clear()
                            pickHistory.clear()
                            currentIndex = 0
                            hasAdvancedOnce = false
                            promptPulseStartMs = SystemClock.uptimeMillis()
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            invalidate()
                        }
                        TouchMode.BTN_UNDO -> if (!movedBeyondSlop && pickHistory.isNotEmpty()) {
                            val lastKey = pickHistory.removeLast()
                            pickedPoints.remove(lastKey)
                            val idx = orderedKeys.indexOf(lastKey)
                            if (idx >= 0) currentIndex = idx
                            promptPulseStartMs = SystemClock.uptimeMillis()
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            invalidate()
                        }
                        TouchMode.BTN_SKIP -> if (!movedBeyondSlop && currentTargetKey() != null) {
                            hasAdvancedOnce = true
                            currentIndex = (currentIndex + 1).coerceAtMost(orderedKeys.size)
                            if (currentIndex >= orderedKeys.size) {
                                currentIndex = findNextUnpickedIndex(0)
                            }
                            promptPulseStartMs = SystemClock.uptimeMillis()
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            invalidate()
                        }
                        TouchMode.BTN_MODE -> if (!movedBeyondSlop) {
                            val next = if (keyboardMode == KeyboardMode.KEYS_36) KeyboardMode.KEYS_21 else KeyboardMode.KEYS_36
                            setKeyboardMode(next)
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        TouchMode.BTN_SWITCH_GRID -> if (!movedBeyondSlop) {
                            onSwitchToGridMode()
                        }
                        TouchMode.AIM_NEW_POINT -> {
                            val target = currentTargetKey()
                            if (target != null && !hudRect.contains(aimX, aimY)) {
                                val nx = (aimX / w).coerceIn(0.01f, 0.99f)
                                val ny = (aimY / h).coerceIn(0.01f, 0.99f)
                                pickedPoints[target] = Pair(nx, ny)
                                pickHistory.remove(target)
                                pickHistory.addLast(target)
                                hasAdvancedOnce = true
                                rippleX = aimX
                                rippleY = aimY
                                rippleStartMs = SystemClock.uptimeMillis()
                                promptPulseStartMs = SystemClock.uptimeMillis()
                                currentIndex = findNextUnpickedIndex(currentIndex + 1)
                                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        TouchMode.DRAG_MARKER -> {
                            val k = activeMarkerKey
                            if (k != null) {
                                if (!movedBeyondSlop) {
                                    // Tap placed marker -> audition note via RPC pass-through
                                    flashKey(k)
                                    onAuditionKey(k, buildLiveGrid())
                                } else {
                                    rippleX = aimX
                                    rippleY = aimY
                                    rippleStartMs = SystemClock.uptimeMillis()
                                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                        else -> Unit
                    }
                } else if (touchMode == TouchMode.DRAG_MARKER) {
                    val k = activeMarkerKey
                    val orig = markerOrigNorm
                    if (k != null && orig != null) {
                        pickedPoints[k] = orig
                    }
                }
                aimingActive = false
                activeMarkerKey = null
                markerOrigNorm = null
                touchMode = TouchMode.NONE
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
