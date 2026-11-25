package com.zerointerrupt.piano.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.zerointerrupt.piano.model.CalibrationMethod
import com.zerointerrupt.piano.model.CalibrationProfile
import com.zerointerrupt.piano.model.KeyboardMode
import com.zerointerrupt.piano.model.PianoScore
import com.zerointerrupt.piano.model.PianoSettings
import com.zerointerrupt.piano.player.PlaybackSnapshot
import com.zerointerrupt.piano.player.PlaybackStatus
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Material Design 3 draggable floating piano controller bar with crisp vector icons,
 * full MD3 ripple feedback on all interactive buttons, real-time speed control,
 * collapse-to-pill toggle, inline score picker drawer, and calibration launcher.
 */
@SuppressLint("ViewConstructor")
class FloatingPlayerBarView(
    context: Context,
    initialPalette: OverlayMd3Palette,
    private val onDragMove: (dx: Int, dy: Int) -> Unit,
    private val onTogglePlayPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onAdjustSpeed: (newSpeed: Float) -> Unit,
    private val onSelectScore: (scoreId: String) -> Unit,
    private val onOpenCalibration: () -> Unit,
    private val onOpenManualCalibration: () -> Unit = {},
    private val onSelectCalibrationProfile: (profileId: String) -> Unit = {},
    private val onCloseOverlay: () -> Unit,
    private val onSizeChangedHint: () -> Unit = {},
    private val onToggleKeyboardMode: (() -> Unit)? = null
) : LinearLayout(context) {

    private class MaxHeightScrollView(
        context: Context,
        private val maxHeightPx: Int
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cappedSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, cappedSpec)
        }
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).roundToInt()

    private var palette: OverlayMd3Palette = initialPalette
    private var collapsed = false
    private var pickerExpanded = false
    private var calPanelExpanded = false
    private var currentSpeed = 1.0f
    private var currentScores: List<PianoScore> = emptyList()
    private var currentSelectedScoreId: String? = null
    private var currentProfiles: List<CalibrationProfile> = emptyList()
    private var currentActiveProfileId: String? = null
    private var lastSnapshot: PlaybackSnapshot = PlaybackSnapshot()
    private var lastPickerSignature: String = ""
    private var lastCalSignature: String = ""

    private val expandedContainer: LinearLayout
    private val collapsedPill: TextView

    private val dragGrip: ImageView
    private val titleChip: LinearLayout
    private val titleView: TextView
    private val titleArrowView: ImageView
    private val timeView: TextView
    private val collapseBtn: TextView
    private val closeBtn: ImageView

    private val progressBar: ProgressBar
    private val errorView: TextView

    private val playPauseBtn: TextView
    private val stopBtn: ImageView
    private val speedStepperContainer: LinearLayout
    private val speedDownBtn: ImageView
    private val speedTextView: TextView
    private val speedUpBtn: ImageView
    private val calBtn: TextView

    private val pickerSection: LinearLayout
    private val pickerDivider: View
    private val pickerHeaderTitle: TextView
    private val modeChip: TextView
    private val prevScoreBtn: TextView
    private val nextScoreBtn: TextView
    private val scoreListContainer: LinearLayout

    private val calSection: LinearLayout
    private val calDivider: View
    private val calHeaderTitle: TextView
    private val calGridLaunchBtn: TextView
    private val calManualLaunchBtn: TextView
    private val calProfileListContainer: LinearLayout

    init {
        orientation = VERTICAL
        elevation = dp(10f).toFloat()

        expandedContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(10f), dp(9f), dp(10f), dp(10f))
        }

        // Top row: crisp 6-dot drag handle + ripple title chip + time + collapse + vector close
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        dragGrip = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "拖动悬浮窗"
            layoutParams = LayoutParams(dp(20f), dp(28f)).apply {
                marginEnd = dp(4f)
            }
        }
        attachDragListener(dragGrip)

        titleChip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(10f), dp(5f), dp(8f), dp(5f))
            layoutParams = LayoutParams(0, dp(30f), 1f)
        }

        titleView = TextView(context).apply {
            text = "卡农（完整钢琴版）"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        titleArrowView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "展开曲目列表"
            layoutParams = LayoutParams(dp(18f), dp(18f)).apply {
                marginStart = dp(4f)
            }
        }

        titleChip.addView(titleView)
        titleChip.addView(titleArrowView)
        attachDragOrTapListener(titleChip) {
            setPickerExpanded(!pickerExpanded)
        }

        timeView = TextView(context).apply {
            text = "0:00 / 4:05"
            textSize = 10.5f
            fontFeatureSettings = "tnum"
            setPadding(dp(7f), 0, dp(7f), 0)
        }

        collapseBtn = makeMiniChip("收起") {
            setCollapsed(true)
        }

        closeBtn = makeIconBtn(dp(28f), dp(26f), "关闭悬浮窗") {
            onCloseOverlay()
        }.apply {
            (layoutParams as MarginLayoutParams).marginStart = dp(5f)
        }

        topRow.addView(dragGrip)
        topRow.addView(titleChip)
        topRow.addView(timeView)
        topRow.addView(collapseBtn)
        topRow.addView(closeBtn)

        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(4.5f)).apply {
                topMargin = dp(8f)
                bottomMargin = dp(8f)
            }
        }

        errorView = TextView(context).apply {
            textSize = 11f
            visibility = View.GONE
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8f)
            }
        }


        // Controls row: Play/Pause, Stop, MD3 Speed Stepper, Calibration

        val ctrlRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        playPauseBtn = makeActionBtn("播放") {
            onTogglePlayPause()
        }.apply {
            compoundDrawablePadding = dp(5f)
            setPadding(dp(12f), 0, dp(14f), 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(34f))
        }

        stopBtn = makeIconBtn(dp(34f), dp(34f), "停止") {
            onStop()
        }.apply {
            (layoutParams as MarginLayoutParams).marginStart = dp(6f)
        }

        speedStepperContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(34f)).apply {
                marginStart = dp(8f)
            }
        }

        speedDownBtn = makeIconBtn(dp(28f), dp(28f), "减速") {
            onAdjustSpeed((currentSpeed - 0.1f).coerceAtLeast(PianoSettings.MIN_SPEED))
        }

        speedTextView = TextView(context).apply {
            text = "1.0x"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
            gravity = Gravity.CENTER
            isClickable = true
            setPadding(dp(9f), 0, dp(9f), 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply {
                marginStart = dp(2f)
                marginEnd = dp(2f)
            }
            setOnClickListener {
                val presets = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val next = presets.firstOrNull { it > currentSpeed + 0.04f } ?: presets.first()
                onAdjustSpeed(next)
            }
        }

        speedUpBtn = makeIconBtn(dp(28f), dp(28f), "加速") {
            onAdjustSpeed((currentSpeed + 0.1f).coerceAtMost(PianoSettings.MAX_SPEED))
        }

        speedStepperContainer.addView(speedDownBtn)
        speedStepperContainer.addView(speedTextView)
        speedStepperContainer.addView(speedUpBtn)

        val ctrlSpacer = View(context).apply {
            layoutParams = LayoutParams(0, 1, 1f)
        }

        calBtn = makeActionBtn("校准") {
            setCalPanelExpanded(!calPanelExpanded)
        }.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(34f)).apply {
                marginStart = dp(6f)
            }
        }

        ctrlRow.addView(playPauseBtn)
        ctrlRow.addView(stopBtn)
        ctrlRow.addView(speedStepperContainer)
        ctrlRow.addView(ctrlSpacer)
        ctrlRow.addView(calBtn)

        // Expandable MD3 score picker drawer
        pickerSection = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            }
        }

        pickerDivider = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)).apply {
                bottomMargin = dp(8f)
            }
        }

        val pickerHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6f)
            }
        }

        pickerHeaderTitle = TextView(context).apply {
            text = "切换曲目"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        modeChip = makeMiniChip("21键目录 ⇄") {
            onToggleKeyboardMode?.invoke()
        }.apply {
            (layoutParams as MarginLayoutParams).marginEnd = dp(6f)
        }

        prevScoreBtn = makeMiniChip("‹ 上一曲") {
            cycleScore(-1)
        }

        nextScoreBtn = makeMiniChip("下一曲 ›") {
            cycleScore(1)
        }.apply {
            (layoutParams as MarginLayoutParams).marginStart = dp(6f)
        }

        pickerHeaderRow.addView(pickerHeaderTitle)
        pickerHeaderRow.addView(modeChip)
        pickerHeaderRow.addView(prevScoreBtn)
        pickerHeaderRow.addView(nextScoreBtn)

        scoreListContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val scoreScroll = MaxHeightScrollView(context, dp(192f)).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addView(scoreListContainer)
        }

        pickerSection.addView(pickerDivider)
        pickerSection.addView(pickerHeaderRow)
        pickerSection.addView(scoreScroll)

        // Expandable Calibration Mode & Saved Profiles Drawer
        calSection = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            }
        }

        calDivider = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)).apply {
                bottomMargin = dp(8f)
            }
        }

        val calHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6f)
            }
        }

        calHeaderTitle = TextView(context).apply {
            text = "校准方案"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        calGridLaunchBtn = makeMiniChip("网格校准") {
            setCalPanelExpanded(false)
            onOpenCalibration()
        }

        calManualLaunchBtn = makeMiniChip("手动拾取") {
            setCalPanelExpanded(false)
            onOpenManualCalibration()
        }.apply {
            (layoutParams as MarginLayoutParams).marginStart = dp(6f)
        }

        calHeaderRow.addView(calHeaderTitle)
        calHeaderRow.addView(calGridLaunchBtn)
        calHeaderRow.addView(calManualLaunchBtn)

        calProfileListContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val calProfileScroll = MaxHeightScrollView(context, dp(152f)).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addView(calProfileListContainer)
        }

        calSection.addView(calDivider)
        calSection.addView(calHeaderRow)
        calSection.addView(calProfileScroll)

        expandedContainer.addView(topRow)
        expandedContainer.addView(progressBar)
        expandedContainer.addView(errorView)
        expandedContainer.addView(ctrlRow)
        expandedContainer.addView(pickerSection)
        expandedContainer.addView(calSection)

        collapsedPill = TextView(context).apply {
            text = "1.0x"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
            gravity = Gravity.CENTER_VERTICAL
            compoundDrawablePadding = dp(5f)
            isClickable = true
            setPadding(dp(13f), dp(8f), dp(14f), dp(8f))
            visibility = View.GONE
        }
        attachDragOrTapListener(collapsedPill) {
            setCollapsed(false)
        }

        addView(expandedContainer, LayoutParams(dp(356f), LayoutParams.WRAP_CONTENT))
        addView(collapsedPill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        applyPalette(initialPalette)
    }

    private fun makeMiniChip(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            setPadding(dp(10f), 0, dp(10f), 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(26f))
            setOnClickListener { onClick() }
        }

    private fun makeActionBtn(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            setPadding(dp(13f), 0, dp(13f), 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(34f))
            setOnClickListener { onClick() }
        }

    private fun makeIconBtn(widthPx: Int, heightPx: Int, desc: String, onClick: () -> Unit): ImageView =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = desc
            isClickable = true
            layoutParams = LayoutParams(widthPx, heightPx)
            setOnClickListener { onClick() }
        }

    private fun setCollapsed(value: Boolean) {
        collapsed = value
        if (collapsed) {
            pickerExpanded = false
            pickerSection.visibility = View.GONE
            calPanelExpanded = false
            calSection.visibility = View.GONE
            updateTitleChevronIcon()
        }
        expandedContainer.visibility = if (collapsed) View.GONE else View.VISIBLE
        collapsedPill.visibility = if (collapsed) View.VISIBLE else View.GONE
        updateRootBackground()
        onSizeChangedHint()
    }

    private fun setPickerExpanded(value: Boolean) {
        pickerExpanded = value
        if (pickerExpanded) {
            calPanelExpanded = false
            calSection.visibility = View.GONE
        }
        pickerSection.visibility = if (pickerExpanded) View.VISIBLE else View.GONE
        updateTitleChevronIcon()
        if (pickerExpanded) {
            rebuildScoreListIfNeeded(force = false)
        }
        onSizeChangedHint()
    }

    private fun setCalPanelExpanded(value: Boolean) {
        calPanelExpanded = value
        if (calPanelExpanded) {
            pickerExpanded = false
            pickerSection.visibility = View.GONE
            updateTitleChevronIcon()
        }
        calSection.visibility = if (calPanelExpanded) View.VISIBLE else View.GONE
        if (calPanelExpanded) {
            rebuildCalProfileListIfNeeded(force = false)
        }
        onSizeChangedHint()
    }

    private fun updateTitleChevronIcon() {
        titleArrowView.setImageDrawable(
            OverlayIcons.chevron(palette.primary, density, pointUp = pickerExpanded)
        )
    }

    private fun updateCollapsedPillIcon(isPlaying: Boolean) {
        val icon = if (isPlaying) {
            OverlayIcons.activeDot(palette.tertiary, density)
        } else {
            OverlayIcons.musicNote(palette.onPrimaryContainer, density)
        }
        collapsedPill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    private fun cycleScore(delta: Int) {
        if (currentScores.isEmpty()) return
        val curIdx = currentScores.indexOfFirst { it.id == currentSelectedScoreId }
        val nextIdx = if (curIdx < 0) {
            0
        } else {
            (curIdx + delta + currentScores.size) % currentScores.size
        }
        onSelectScore(currentScores[nextIdx].id)
    }

    fun applyPalette(newPalette: OverlayMd3Palette) {
        palette = newPalette
        updateRootBackground()

        val rippleAlpha = if (palette.isDark) 84 else 68

        dragGrip.setImageDrawable(
            OverlayIcons.dragHandle(OverlayMd3Palette.withAlpha(palette.onSurfaceVariant, 190), density)
        )

        titleChip.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.surfaceContainerHigh,
            rippleColor = palette.primary,
            radiusPx = dp(999f).toFloat(),
            strokeColor = OverlayMd3Palette.withAlpha(palette.outlineVariant, 150),
            strokeWidthPx = dp(1f),
            rippleAlpha = rippleAlpha
        )
        titleView.setTextColor(palette.onSurface)
        updateTitleChevronIcon()
        timeView.setTextColor(palette.onSurfaceVariant)

        collapseBtn.setTextColor(palette.onSecondaryContainer)
        collapseBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.secondaryContainer,
            rippleColor = palette.onSecondaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )

        closeBtn.setImageDrawable(OverlayIcons.close(palette.onSurfaceVariant, density))
        closeBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.surfaceContainerHighest,
            rippleColor = palette.onSurface,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )

        stopBtn.setImageDrawable(OverlayIcons.stop(palette.onSecondaryContainer, density))
        stopBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.secondaryContainer,
            rippleColor = palette.onSecondaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )

        speedStepperContainer.background = GradientDrawable().apply {
            setColor(palette.surfaceContainerHigh)
            cornerRadius = dp(999f).toFloat()
            setStroke(dp(1f), OverlayMd3Palette.withAlpha(palette.outlineVariant, 140))
        }
        speedDownBtn.setImageDrawable(OverlayIcons.minus(palette.onSurface, density))
        speedDownBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = Color.TRANSPARENT,
            rippleColor = palette.primary,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )
        speedTextView.setTextColor(palette.onTertiaryContainer)
        speedTextView.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.tertiaryContainer,
            rippleColor = palette.onTertiaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )
        speedUpBtn.setImageDrawable(OverlayIcons.plus(palette.onSurface, density))
        speedUpBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = Color.TRANSPARENT,
            rippleColor = palette.primary,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )

        calBtn.setTextColor(palette.onSecondaryContainer)
        calBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.secondaryContainer,
            rippleColor = palette.onSecondaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )

        errorView.setTextColor(palette.onErrorContainer)
        errorView.background = GradientDrawable().apply {
            setColor(palette.errorContainer)
            cornerRadius = dp(12f).toFloat()
        }

        pickerDivider.setBackgroundColor(OverlayMd3Palette.withAlpha(palette.outlineVariant, 150))
        pickerHeaderTitle.setTextColor(palette.onSurfaceVariant)
        modeChip.setTextColor(palette.onSecondaryContainer)
        modeChip.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.secondaryContainer,
            rippleColor = palette.onSecondaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )
        prevScoreBtn.setTextColor(palette.onSecondaryContainer)
        prevScoreBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.secondaryContainer,
            rippleColor = palette.onSecondaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )
        nextScoreBtn.setTextColor(palette.onSecondaryContainer)
        nextScoreBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.secondaryContainer,
            rippleColor = palette.onSecondaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )

        calDivider.setBackgroundColor(OverlayMd3Palette.withAlpha(palette.outlineVariant, 150))
        calHeaderTitle.setTextColor(palette.onSurfaceVariant)
        calGridLaunchBtn.setTextColor(palette.onSecondaryContainer)
        calGridLaunchBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.secondaryContainer,
            rippleColor = palette.onSecondaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )
        calManualLaunchBtn.setTextColor(palette.onPrimary)
        calManualLaunchBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = palette.primary,
            rippleColor = palette.onPrimary,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )

        collapsedPill.setTextColor(palette.onPrimaryContainer)
        collapsedPill.background = OverlayMd3Palette.createRipplePill(
            bgColor = Color.TRANSPARENT,
            rippleColor = palette.onPrimaryContainer,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )
        updateCollapsedPillIcon(lastSnapshot.status == PlaybackStatus.PLAYING)

        applyPlayButtonStyle(lastSnapshot.status)
        applyProgressDrawable(lastSnapshot.status == PlaybackStatus.PLAYING)
        if (pickerExpanded) {
            rebuildScoreListIfNeeded(force = true)
        } else {
            lastPickerSignature = ""
        }
        if (calPanelExpanded) {
            rebuildCalProfileListIfNeeded(force = true)
        } else {
            lastCalSignature = ""
        }
    }

    private fun updateRootBackground() {
        val bgColor = if (collapsed) {
            OverlayMd3Palette.withAlpha(palette.primaryContainer, 246)
        } else {
            OverlayMd3Palette.withAlpha(palette.surfaceContainer, 246)
        }
        val strokeColor = if (collapsed) {
            OverlayMd3Palette.withAlpha(palette.primary, 130)
        } else {
            OverlayMd3Palette.withAlpha(palette.outlineVariant, 175)
        }
        background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = if (collapsed) dp(999f).toFloat() else dp(24f).toFloat()
            setStroke(dp(1f), strokeColor)
        }
    }

    private fun applyPlayButtonStyle(status: PlaybackStatus) {
        val isPlaying = status == PlaybackStatus.PLAYING
        val bg = if (isPlaying) palette.tertiary else palette.primary
        val fg = if (isPlaying) palette.onTertiary else palette.onPrimary
        val rippleAlpha = if (palette.isDark) 84 else 68
        playPauseBtn.setTextColor(fg)
        playPauseBtn.background = OverlayMd3Palette.createRipplePill(
            bgColor = bg,
            rippleColor = fg,
            radiusPx = dp(999f).toFloat(),
            rippleAlpha = rippleAlpha
        )
        val icon = if (isPlaying) {
            OverlayIcons.pause(fg, density)
        } else {
            OverlayIcons.play(fg, density)
        }
        playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    private fun applyProgressDrawable(isPlaying: Boolean) {
        val track = GradientDrawable().apply {
            setColor(palette.surfaceContainerHighest)
            cornerRadius = dp(999f).toFloat()
        }
        val fill = GradientDrawable().apply {
            setColor(if (isPlaying) palette.tertiary else palette.primary)
            cornerRadius = dp(999f).toFloat()
        }
        val clip = ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)
        val layer = LayerDrawable(arrayOf(track, clip)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        val curProg = progressBar.progress
        progressBar.progressDrawable = layer
        progressBar.progress = curProg
    }

    fun updateState(
        scores: List<PianoScore>,
        selectedScore: PianoScore?,
        snapshot: PlaybackSnapshot,
        keyboardMode: KeyboardMode = KeyboardMode.KEYS_21,
        profiles: List<CalibrationProfile> = currentProfiles,
        activeProfileId: String? = currentActiveProfileId
    ) {
        val prevStatus = lastSnapshot.status
        lastSnapshot = snapshot
        currentScores = scores
        currentSelectedScoreId = selectedScore?.id
        currentSpeed = snapshot.speed
        currentProfiles = profiles
        currentActiveProfileId = activeProfileId

        titleView.text = selectedScore?.title ?: "未选择曲目"
        pickerHeaderTitle.text = "切换曲目 (${scores.size})"
        calHeaderTitle.text = "校准方案 (${profiles.size})"
        modeChip.text = if (keyboardMode == KeyboardMode.KEYS_36) "36键目录 ⇄" else "21键目录 ⇄"

        val curSec = ((snapshot.positionMs / currentSpeed) / 1000f).roundToInt().coerceAtLeast(0)
        val totSec = ((snapshot.durationMs / currentSpeed) / 1000f).roundToInt().coerceAtLeast(0)
        timeView.text = "%d:%02d / %d:%02d".format(curSec / 60, curSec % 60, totSec / 60, totSec % 60)

        val prog = if (snapshot.durationMs > 0) {
            ((snapshot.positionMs * 1000L) / snapshot.durationMs).toInt().coerceIn(0, 1000)
        } else 0
        progressBar.progress = prog

        val rawSpeed = "%.2f".format(currentSpeed)
        val speedStr = (if (rawSpeed.endsWith("0")) rawSpeed.dropLast(1) else rawSpeed) + "x"
        speedTextView.text = speedStr
        collapsedPill.text = speedStr

        playPauseBtn.text = when (snapshot.status) {
            PlaybackStatus.PLAYING -> "暂停"
            PlaybackStatus.PAUSED -> "继续"
            else -> "播放"
        }

        if (prevStatus != snapshot.status) {
            applyPlayButtonStyle(snapshot.status)
            applyProgressDrawable(snapshot.status == PlaybackStatus.PLAYING)
            updateCollapsedPillIcon(snapshot.status == PlaybackStatus.PLAYING)
        }

        if (snapshot.status == PlaybackStatus.ERROR && snapshot.message.isNotBlank()) {
            errorView.text = snapshot.message
            errorView.visibility = View.VISIBLE
        } else {
            errorView.visibility = View.GONE
        }

        if (pickerExpanded) {
            rebuildScoreListIfNeeded(force = false)
        }
        if (calPanelExpanded) {
            rebuildCalProfileListIfNeeded(force = false)
        }
    }

    private fun rebuildCalProfileListIfNeeded(force: Boolean) {
        val sig = buildString {
            append(palette.hashCode()).append('|')
            append(currentActiveProfileId.orEmpty()).append('|')
            for (p in currentProfiles) {
                append(p.id).append(':').append(p.name).append(':').append(p.keyboardMode.name)
                    .append(':').append(p.grid.calibrationMethod.name).append(',')
            }
        }
        if (!force && sig == lastCalSignature) return
        lastCalSignature = sig

        calProfileListContainer.removeAllViews()
        val rippleAlpha = if (palette.isDark) 84 else 68
        currentProfiles.forEachIndexed { idx, profile ->
            val selected = profile.id == currentActiveProfileId
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setPadding(dp(10f), dp(7f), dp(10f), dp(7f))
                background = OverlayMd3Palette.createRipplePill(
                    bgColor = if (selected) palette.primaryContainer else palette.surfaceContainerLow,
                    rippleColor = palette.primary,
                    radiusPx = dp(14f).toFloat(),
                    strokeColor = if (selected) palette.primary else Color.TRANSPARENT,
                    strokeWidthPx = if (selected) dp(1f) else 0,
                    rippleAlpha = rippleAlpha
                )
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (idx > 0) topMargin = dp(5f)
                }
                setOnClickListener {
                    onSelectCalibrationProfile(profile.id)
                    setCalPanelExpanded(false)
                }
            }

            val nameTv = TextView(context).apply {
                text = if (selected) "✓ ${profile.name}" else profile.name
                textSize = 12.5f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) palette.onPrimaryContainer else palette.onSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }

            val badgeText = "${profile.keyboardMode.label}·${if (profile.grid.calibrationMethod == CalibrationMethod.MANUAL_PICK) "手动" else "网格"}"
            val badgeTv = TextView(context).apply {
                text = badgeText
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(7f), dp(2.5f), dp(7f), dp(2.5f))
                setTextColor(if (selected) palette.onPrimary else palette.onSecondaryContainer)
                background = GradientDrawable().apply {
                    setColor(if (selected) palette.primary else palette.secondaryContainer)
                    cornerRadius = dp(999f).toFloat()
                }
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8f)
                }
            }

            row.addView(nameTv)
            row.addView(badgeTv)
            calProfileListContainer.addView(row)
        }
    }

    private fun rebuildScoreListIfNeeded(force: Boolean) {
        val sig = buildString {
            append(palette.hashCode()).append('|')
            append(currentSelectedScoreId.orEmpty()).append('|')
            append("%.2f".format(currentSpeed)).append('|')
            for (s in currentScores) {
                append(s.id).append(':').append(s.title).append(',')
            }
        }
        if (!force && sig == lastPickerSignature) return
        lastPickerSignature = sig

        scoreListContainer.removeAllViews()
        if (currentScores.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "暂无可用曲目"
                textSize = 12f
                setTextColor(palette.onSurfaceVariant)
                gravity = Gravity.CENTER
                setPadding(dp(8f), dp(12f), dp(8f), dp(12f))
            }
            scoreListContainer.addView(emptyTv)
            return
        }

        val rippleAlpha = if (palette.isDark) 84 else 68
        currentScores.forEachIndexed { idx, score ->
            val selected = score.id == currentSelectedScoreId
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                background = OverlayMd3Palette.createRipplePill(
                    bgColor = if (selected) palette.primaryContainer else palette.surfaceContainerLow,
                    rippleColor = palette.primary,
                    radiusPx = dp(14f).toFloat(),
                    strokeColor = if (selected) palette.primary else Color.TRANSPARENT,
                    strokeWidthPx = if (selected) dp(1f) else 0,
                    rippleAlpha = rippleAlpha
                )
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    if (idx > 0) topMargin = dp(5f)
                }
                setOnClickListener {
                    onSelectScore(score.id)
                    setPickerExpanded(false)
                }
            }

            val textCol = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }

            val itemTitle = TextView(context).apply {
                text = if (selected) "✓ ${score.title}" else score.title
                textSize = 12.5f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) palette.onPrimaryContainer else palette.onSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val itemSub = TextView(context).apply {
                text = "${score.composer} · ${score.formattedDuration(currentSpeed)} · ${score.maxChordOnset}键和弦"
                textSize = 10.5f
                setTextColor(
                    if (selected) OverlayMd3Palette.withAlpha(palette.onPrimaryContainer, 200)
                    else palette.onSurfaceVariant
                )
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            textCol.addView(itemTitle)
            textCol.addView(itemSub)

            val fitBadge = TextView(context).apply {
                text = "${score.diatonicPct}%"
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                fontFeatureSettings = "tnum"
                setPadding(dp(7f), dp(3f), dp(7f), dp(3f))
                val (badgeBg, badgeFg) = when {
                    score.diatonicPct >= 93f -> palette.tertiaryContainer to palette.onTertiaryContainer
                    score.diatonicPct >= 88f -> palette.secondaryContainer to palette.onSecondaryContainer
                    else -> palette.errorContainer to palette.onErrorContainer
                }
                setTextColor(badgeFg)
                background = GradientDrawable().apply {
                    setColor(badgeBg)
                    cornerRadius = dp(999f).toFloat()
                }
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8f)
                }
            }

            row.addView(textCol)
            row.addView(fitBadge)
            scoreListContainer.addView(row)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(target: View) {
        attachDragOrTapListener(target, onTap = null)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragOrTapListener(target: View, onTap: (() -> Unit)?) {
        var lastRawX = 0f
        var lastRawY = 0f
        var startRawX = 0f
        var startRawY = 0f
        var moved = false
        target.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    startRawX = ev.rawX
                    startRawY = ev.rawY
                    moved = false
                    v.drawableHotspotChanged(ev.x, ev.y)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - lastRawX).roundToInt()
                    val dy = (ev.rawY - lastRawY).roundToInt()
                    if (hypot((ev.rawX - startRawX).toDouble(), (ev.rawY - startRawY).toDouble()) > dp(6f)) {
                        moved = true
                        v.isPressed = false
                    } else {
                        v.drawableHotspotChanged(ev.x, ev.y)
                    }
                    if (dx != 0 || dy != 0) {
                        onDragMove(dx, dy)
                        lastRawX = ev.rawX
                        lastRawY = ev.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (ev.actionMasked == MotionEvent.ACTION_UP && !moved) {
                        onTap?.invoke()
                    }
                    true
                }
                else -> false
            }
        }
    }
}
