package com.zerointerrupt.piano.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.zerointerrupt.piano.model.CalibrationGrid
import com.zerointerrupt.piano.model.CalibrationMethod
import com.zerointerrupt.piano.model.KeyboardMode
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen transparent overlay view rendering a draggable & resizable 3x7 piano grid.
 * Supports direct cell tapping ("点击试音") with brief touch-passthrough handled by the service.
 */
@SuppressLint("ViewConstructor")
class CalibrationGridView(
    context: Context,
    initialGrid: CalibrationGrid,
    initialPalette: OverlayMd3Palette,
    initialKeyboardMode: KeyboardMode = KeyboardMode.KEYS_21,
    private val onAuditionKey: (keyId: String, grid: CalibrationGrid) -> Unit,
    private val onSaveGrid: (CalibrationGrid) -> Unit,
    private val onResetGrid: () -> CalibrationGrid,
    private val onSwitchToManualPick: () -> Unit = {},
    private val onCancel: () -> Unit
) : View(context) {

    private var grid: CalibrationGrid = initialGrid.clamped()
    private var palette: OverlayMd3Palette = initialPalette
    private var keyboardMode: KeyboardMode = initialKeyboardMode
    private var highlightedKey: String? = null
    private val clearHighlightRunnable = Runnable {
        highlightedKey = null
        invalidate()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val boxRect = RectF()
    private val toolbarRect = RectF()
    private val btnManualRect = RectF()
    private val btnResetRect = RectF()
    private val btnSaveRect = RectF()
    private val btnCancelRect = RectF()
    private val handleTL = RectF()
    private val handleBR = RectF()
    private val cellRects = LinkedHashMap<String, RectF>()

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 0, 0, 0)
    }
    private val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
    }
    private val cellFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(13f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val pillBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(12.5f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
    }

    private enum class DragTarget {
        NONE, MOVE_BOX, RESIZE_TL, RESIZE_BR, BUTTON_MANUAL, BUTTON_RESET, BUTTON_SAVE, BUTTON_CANCEL, CELL_TAP
    }

    private var dragTarget = DragTarget.NONE
    private var tappedKeyId: String? = null
    private var downX = 0f
    private var downY = 0f
    private var startGrid = grid
    private var movedBeyondSlop = false

    init {
        applyPalette(initialPalette)
    }

    fun applyPalette(newPalette: OverlayMd3Palette) {
        palette = newPalette
        gridBorderPaint.color = OverlayMd3Palette.withAlpha(palette.primary, 240)
        cellStrokePaint.color = OverlayMd3Palette.withAlpha(palette.primaryContainer, 195)
        centerDotPaint.color = OverlayMd3Palette.withAlpha(palette.primary, 235)
        hintPaint.color = palette.onSurfaceVariant
        pillBgPaint.color = OverlayMd3Palette.withAlpha(palette.surfaceContainerHigh, 244)
        pillStrokePaint.color = OverlayMd3Palette.withAlpha(palette.outlineVariant, 190)
        handlePaint.color = palette.primary
        handleStrokePaint.color = palette.onPrimary
        invalidate()
    }

    fun setGrid(newGrid: CalibrationGrid) {
        grid = newGrid.clamped()
        invalidate()
    }

    fun setKeyboardMode(mode: KeyboardMode) {
        if (keyboardMode != mode) {
            keyboardMode = mode
            invalidate()
        }
    }

    fun flashKey(keyId: String) {
        removeCallbacks(clearHighlightRunnable)
        highlightedKey = keyId
        invalidate()
        postDelayed(clearHighlightRunnable, 220L)
    }

    private fun updateGeometry() {
        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        boxRect.set(
            grid.leftNorm * w,
            grid.topNorm * h,
            grid.rightNorm * w,
            grid.bottomNorm * h
        )

        val cellW = boxRect.width() / 7f
        val cellH = boxRect.height() / 3f
        val inset = min(cellW, cellH) * 0.08f
        val rows = arrayOf("H", "M", "L")
        cellRects.clear()
        // Put black keys first so hit-testing checks diamonds before underlying white cells
        if (keyboardMode == KeyboardMode.KEYS_36) {
            val halfD = min(cellW, cellH) * 0.22f
            for (r in 0..2) {
                val cy = boxRect.top + (r + 0.26f) * cellH
                for (deg in CalibrationGrid.BLACK_KEY_DEGREES) {
                    val cx = boxRect.left + deg * cellW
                    val k = "${rows[r]}${deg}S"
                    cellRects[k] = RectF(cx - halfD, cy - halfD, cx + halfD, cy + halfD)
                }
            }
        }
        for (r in 0..2) {
            for (c in 0..6) {
                val k = "${rows[r]}${c + 1}"
                val l = boxRect.left + c * cellW + inset
                val t = boxRect.top + r * cellH + inset
                val rgt = boxRect.left + (c + 1) * cellW - inset
                val btm = boxRect.top + (r + 1) * cellH - inset
                cellRects[k] = RectF(l, t, rgt, btm)
            }
        }

        val hr = dp(18f)
        handleTL.set(boxRect.left - hr, boxRect.top - hr, boxRect.left + hr, boxRect.top + hr)
        handleBR.set(boxRect.right - hr, boxRect.bottom - hr, boxRect.right + hr, boxRect.bottom + hr)

        // Position toolbar directly above grid (or below if grid is near top)
        val tbW = dp(380f).coerceAtMost(w - dp(20f))
        val tbH = dp(44f)
        val cx = boxRect.centerX().coerceIn(tbW / 2f + dp(10f), w - tbW / 2f - dp(10f))
        val preferTop = boxRect.top - tbH - dp(14f)
        val tbTop = if (preferTop >= dp(12f)) preferTop else min(h - tbH - dp(12f), boxRect.bottom + dp(14f))
        toolbarRect.set(cx - tbW / 2f, tbTop, cx + tbW / 2f, tbTop + tbH)

        val pad = dp(5f)
        val btnW = dp(54f)
        val manualW = dp(68f)
        val saveW = dp(64f)
        val bTop = toolbarRect.top + pad
        val bBtm = toolbarRect.bottom - pad
        btnCancelRect.set(toolbarRect.right - pad - btnW, bTop, toolbarRect.right - pad, bBtm)
        btnSaveRect.set(btnCancelRect.left - pad - saveW, bTop, btnCancelRect.left - pad, bBtm)
        btnResetRect.set(btnSaveRect.left - pad - btnW, bTop, btnSaveRect.left - pad, bBtm)
        btnManualRect.set(btnResetRect.left - pad - manualW, bTop, btnResetRect.left - pad, bBtm)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        updateGeometry()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // Outer box
        canvas.drawRoundRect(boxRect, dp(16f), dp(16f), gridBorderPaint)

        // White keys first, then black-key diamonds on top in 36-key mode
        for ((k, rect) in cellRects) {
            if (k.endsWith("S")) continue
            val isLit = (k == highlightedKey)
            cellFillPaint.color = if (isLit) {
                OverlayMd3Palette.withAlpha(palette.tertiaryContainer, 215)
            } else {
                OverlayMd3Palette.withAlpha(palette.surfaceContainer, 128)
            }
            labelPaint.color = if (isLit) palette.onTertiaryContainer else palette.onSurface
            canvas.drawRoundRect(rect, dp(12f), dp(12f), cellFillPaint)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), cellStrokePaint)
            canvas.drawCircle(rect.centerX(), rect.centerY(), dp(3.5f), centerDotPaint)
            val labelY = rect.centerY() - dp(10f)
            canvas.drawText(k, rect.centerX(), labelY, labelPaint)
        }
        if (keyboardMode == KeyboardMode.KEYS_36) {
            for ((k, rect) in cellRects) {
                if (!k.endsWith("S")) continue
                val isLit = (k == highlightedKey)
                cellFillPaint.color = if (isLit) {
                    OverlayMd3Palette.withAlpha(palette.tertiary, 235)
                } else {
                    OverlayMd3Palette.withAlpha(palette.primaryContainer, 215)
                }
                labelPaint.color = if (isLit) palette.onTertiary else palette.onPrimaryContainer
                canvas.save()
                canvas.rotate(45f, rect.centerX(), rect.centerY())
                canvas.drawRoundRect(rect, dp(5f), dp(5f), cellFillPaint)
                canvas.drawRoundRect(rect, dp(5f), dp(5f), cellStrokePaint)
                canvas.restore()
                val textY = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) * 0.5f
                canvas.drawText(k.dropLast(1) + "#", rect.centerX(), textY, labelPaint)
            }
        }

        // Resize corner handles
        canvas.drawCircle(boxRect.left, boxRect.top, dp(11f), handlePaint)
        canvas.drawCircle(boxRect.left, boxRect.top, dp(11f), handleStrokePaint)
        canvas.drawCircle(boxRect.right, boxRect.bottom, dp(12f), handlePaint)
        canvas.drawCircle(boxRect.right, boxRect.bottom, dp(12f), handleStrokePaint)

        // Floating MD3 toolbar pill
        canvas.drawRoundRect(toolbarRect, dp(22f), dp(22f), pillBgPaint)
        canvas.drawRoundRect(toolbarRect, dp(22f), dp(22f), pillStrokePaint)
        val hintCenterX = (toolbarRect.left + btnManualRect.left) * 0.5f
        val textCenterY = toolbarRect.centerY() - (hintPaint.descent() + hintPaint.ascent()) * 0.5f
        if (btnManualRect.left - toolbarRect.left >= dp(64f)) {
            canvas.drawText("拖动对齐 · 试音", hintCenterX, textCenterY, hintPaint)
        }

        val btnCy = btnSaveRect.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) * 0.5f

        pillBtnPaint.color = palette.tertiaryContainer
        btnTextPaint.color = palette.onTertiaryContainer
        canvas.drawRoundRect(btnManualRect, dp(16f), dp(16f), pillBtnPaint)
        canvas.drawText("手动拾取", btnManualRect.centerX(), btnCy, btnTextPaint)

        pillBtnPaint.color = palette.secondaryContainer
        btnTextPaint.color = palette.onSecondaryContainer
        canvas.drawRoundRect(btnResetRect, dp(16f), dp(16f), pillBtnPaint)
        canvas.drawText("重置", btnResetRect.centerX(), btnCy, btnTextPaint)

        pillBtnPaint.color = palette.primary
        btnTextPaint.color = palette.onPrimary
        canvas.drawRoundRect(btnSaveRect, dp(16f), dp(16f), pillBtnPaint)
        canvas.drawText("保存", btnSaveRect.centerX(), btnCy, btnTextPaint)

        pillBtnPaint.color = palette.surfaceContainerHighest
        btnTextPaint.color = palette.onSurface
        canvas.drawRoundRect(btnCancelRect, dp(16f), dp(16f), pillBtnPaint)
        canvas.drawText("关闭", btnCancelRect.centerX(), btnCy, btnTextPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        val slop = dp(10f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateGeometry()
                downX = x
                downY = y
                startGrid = grid
                movedBeyondSlop = false
                tappedKeyId = null

                dragTarget = when {
                    btnCancelRect.contains(x, y) -> DragTarget.BUTTON_CANCEL
                    btnSaveRect.contains(x, y) -> DragTarget.BUTTON_SAVE
                    btnResetRect.contains(x, y) -> DragTarget.BUTTON_RESET
                    btnManualRect.contains(x, y) -> DragTarget.BUTTON_MANUAL
                    toolbarRect.contains(x, y) -> DragTarget.MOVE_BOX
                    handleBR.contains(x, y) -> DragTarget.RESIZE_BR
                    handleTL.contains(x, y) -> DragTarget.RESIZE_TL
                    boxRect.contains(x, y) -> {
                        tappedKeyId = cellRects.entries.firstOrNull { it.value.contains(x, y) }?.key
                        DragTarget.CELL_TAP
                    }
                    else -> DragTarget.NONE
                }
                return dragTarget != DragTarget.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - downX
                val dy = y - downY
                if (hypot(dx.toDouble(), dy.toDouble()) > slop) {
                    movedBeyondSlop = true
                }
                if (dragTarget == DragTarget.CELL_TAP && movedBeyondSlop) {
                    dragTarget = DragTarget.MOVE_BOX
                    tappedKeyId = null
                }

                when (dragTarget) {
                    DragTarget.MOVE_BOX -> {
                        val dnx = dx / w
                        val dny = dy / h
                        val gw = startGrid.rightNorm - startGrid.leftNorm
                        val gh = startGrid.bottomNorm - startGrid.topNorm
                        val nl = (startGrid.leftNorm + dnx).coerceIn(0.01f, 0.99f - gw)
                        val nt = (startGrid.topNorm + dny).coerceIn(0.01f, 0.99f - gh)
                        grid = CalibrationGrid(nl, nt, nl + gw, nt + gh).clamped()
                        invalidate()
                    }
                    DragTarget.RESIZE_BR -> {
                        val nr = (startGrid.rightNorm + dx / w).coerceIn(startGrid.leftNorm + 0.16f, 0.99f)
                        val nb = (startGrid.bottomNorm + dy / h).coerceIn(startGrid.topNorm + 0.12f, 0.99f)
                        grid = startGrid.copy(rightNorm = nr, bottomNorm = nb).clamped()
                        invalidate()
                    }
                    DragTarget.RESIZE_TL -> {
                        val nl = (startGrid.leftNorm + dx / w).coerceIn(0.01f, startGrid.rightNorm - 0.16f)
                        val nt = (startGrid.topNorm + dy / h).coerceIn(0.01f, startGrid.bottomNorm - 0.12f)
                        grid = startGrid.copy(leftNorm = nl, topNorm = nt).clamped()
                        invalidate()
                    }
                    else -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!movedBeyondSlop) {
                    when (dragTarget) {
                        DragTarget.BUTTON_CANCEL -> onCancel()
                        DragTarget.BUTTON_MANUAL -> onSwitchToManualPick()
                        DragTarget.BUTTON_SAVE -> onSaveGrid(grid.copy(calibrationMethod = CalibrationMethod.GRID))
                        DragTarget.BUTTON_RESET -> {
                            grid = onResetGrid().clamped()
                            invalidate()
                        }
                        DragTarget.CELL_TAP -> {
                            tappedKeyId?.let { k ->
                                flashKey(k)
                                onAuditionKey(k, grid.copy(calibrationMethod = CalibrationMethod.GRID))
                            }
                        }
                        else -> Unit
                    }
                }
                dragTarget = DragTarget.NONE
                tappedKeyId = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
