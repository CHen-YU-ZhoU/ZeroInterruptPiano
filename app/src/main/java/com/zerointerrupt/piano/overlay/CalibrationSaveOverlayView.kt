package com.zerointerrupt.piano.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Focusable Material Design 3 overlay dialog allowing users to name & save a calibration profile
 * directly from the overlay (overwrite current profile or save as a brand-new named game profile).
 */
@SuppressLint("ViewConstructor")
class CalibrationSaveOverlayView(
    context: Context,
    palette: OverlayMd3Palette,
    currentProfileName: String,
    subtitle: String,
    private val onSaveOverwrite: (name: String) -> Unit,
    private val onSaveAsNew: (name: String) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).roundToInt()

    private val nameInput: EditText

    init {
        setBackgroundColor(Color.argb(130, 0, 0, 0))
        isClickable = true
        isFocusable = true

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(16f))
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1f), OverlayMd3Palette.withAlpha(palette.outlineVariant, 175))
            }
            elevation = dp(12f).toFloat()
            isClickable = true
        }

        val titleTv = TextView(context).apply {
            text = "保存校准方案"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.onSurface)
        }

        val subTv = TextView(context).apply {
            text = subtitle
            textSize = 12f
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, dp(4f), 0, dp(12f))
        }

        nameInput = EditText(context).apply {
            setText(currentProfileName)
            setSelection(text.length)
            hint = "输入游戏/布局名称（如 原神、心动小镇）"
            textSize = 14.5f
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(24))
            setTextColor(palette.onSurface)
            setHintTextColor(OverlayMd3Palette.withAlpha(palette.onSurfaceVariant, 160))
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerLow)
                cornerRadius = dp(14f).toFloat()
                setStroke(dp(1.2f), palette.primary)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14f)
            }
        }

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val rippleAlpha = if (palette.isDark) 84 else 68

        val cancelBtn = TextView(context).apply {
            text = "取消"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            setTextColor(palette.onSurfaceVariant)
            setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
            background = OverlayMd3Palette.createRipplePill(
                bgColor = Color.TRANSPARENT,
                rippleColor = palette.onSurface,
                radiusPx = dp(999f).toFloat(),
                rippleAlpha = rippleAlpha
            )
            setOnClickListener {
                hideIme()
                onCancel()
            }
        }

        val overwriteBtn = TextView(context).apply {
            text = "覆盖当前"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            setTextColor(palette.onSecondaryContainer)
            setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
            background = OverlayMd3Palette.createRipplePill(
                bgColor = palette.secondaryContainer,
                rippleColor = palette.onSecondaryContainer,
                radiusPx = dp(999f).toFloat(),
                rippleAlpha = rippleAlpha
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8f)
            }
            setOnClickListener {
                hideIme()
                val name = nameInput.text?.toString()?.trim().orEmpty().ifBlank { currentProfileName }
                onSaveOverwrite(name)
            }
        }

        val saveNewBtn = TextView(context).apply {
            text = "另存为新方案"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            setTextColor(palette.onPrimary)
            setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
            background = OverlayMd3Palette.createRipplePill(
                bgColor = palette.primary,
                rippleColor = palette.onPrimary,
                radiusPx = dp(999f).toFloat(),
                rippleAlpha = rippleAlpha
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8f)
            }
            setOnClickListener {
                hideIme()
                val name = nameInput.text?.toString()?.trim().orEmpty().ifBlank { currentProfileName }
                onSaveAsNew(name)
            }
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(overwriteBtn)
        btnRow.addView(saveNewBtn)

        card.addView(titleTv)
        card.addView(subTv)
        card.addView(nameInput)
        card.addView(btnRow)

        val cardLp = LayoutParams(dp(336f), LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(card, cardLp)

        setOnClickListener {
            hideIme()
            onCancel()
        }

        post {
            nameInput.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideIme() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(nameInput.windowToken, 0)
    }
}
