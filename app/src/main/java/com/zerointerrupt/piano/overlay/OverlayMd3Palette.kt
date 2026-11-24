package com.zerointerrupt.piano.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.zerointerrupt.piano.model.AppThemeMode
import com.zerointerrupt.piano.model.PianoSettings

data class OverlayMd3Palette(
    val isDark: Boolean,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val error: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val outline: Int,
    val outlineVariant: Int
) {
    companion object {
        fun resolve(context: Context, settings: PianoSettings): OverlayMd3Palette {
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val dark = when (settings.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cs = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                return OverlayMd3Palette(
                    isDark = dark,
                    primary = cs.primary.toArgb(),
                    onPrimary = cs.onPrimary.toArgb(),
                    primaryContainer = cs.primaryContainer.toArgb(),
                    onPrimaryContainer = cs.onPrimaryContainer.toArgb(),
                    secondary = cs.secondary.toArgb(),
                    onSecondary = cs.onSecondary.toArgb(),
                    secondaryContainer = cs.secondaryContainer.toArgb(),
                    onSecondaryContainer = cs.onSecondaryContainer.toArgb(),
                    tertiary = cs.tertiary.toArgb(),
                    onTertiary = cs.onTertiary.toArgb(),
                    tertiaryContainer = cs.tertiaryContainer.toArgb(),
                    onTertiaryContainer = cs.onTertiaryContainer.toArgb(),
                    error = cs.error.toArgb(),
                    errorContainer = cs.errorContainer.toArgb(),
                    onErrorContainer = cs.onErrorContainer.toArgb(),
                    surface = cs.surface.toArgb(),
                    onSurface = cs.onSurface.toArgb(),
                    onSurfaceVariant = cs.onSurfaceVariant.toArgb(),
                    surfaceContainerLow = cs.surfaceContainerLow.toArgb(),
                    surfaceContainer = cs.surfaceContainer.toArgb(),
                    surfaceContainerHigh = cs.surfaceContainerHigh.toArgb(),
                    surfaceContainerHighest = cs.surfaceContainerHighest.toArgb(),
                    outline = cs.outline.toArgb(),
                    outlineVariant = cs.outlineVariant.toArgb()
                )
            }

            return if (dark) FALLBACK_DARK else FALLBACK_LIGHT
        }

        fun withAlpha(color: Int, alpha: Int): Int {
            val a = alpha.coerceIn(0, 255)
            return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        }

        fun createRipplePill(
            bgColor: Int,
            rippleColor: Int,
            radiusPx: Float,
            strokeColor: Int = Color.TRANSPARENT,
            strokeWidthPx: Int = 0,
            rippleAlpha: Int = 76
        ): Drawable {
            val content = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = radiusPx
                if (strokeWidthPx > 0 && strokeColor != Color.TRANSPARENT) {
                    setStroke(strokeWidthPx, strokeColor)
                }
            }
            val mask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = radiusPx
            }
            return RippleDrawable(
                ColorStateList.valueOf(withAlpha(rippleColor, rippleAlpha)),
                content,
                mask
            )
        }

        private val FALLBACK_LIGHT = OverlayMd3Palette(
            isDark = false,
            primary = 0xFF006494.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFFCBE6FF.toInt(),
            onPrimaryContainer = 0xFF001E30.toInt(),
            secondary = 0xFF50606F.toInt(),
            onSecondary = 0xFFFFFFFF.toInt(),
            secondaryContainer = 0xFFD3E5F6.toInt(),
            onSecondaryContainer = 0xFF0C1D29.toInt(),
            tertiary = 0xFF006B58.toInt(),
            onTertiary = 0xFFFFFFFF.toInt(),
            tertiaryContainer = 0xFF7AF8DA.toInt(),
            onTertiaryContainer = 0xFF002019.toInt(),
            error = 0xFFBA1A1A.toInt(),
            errorContainer = 0xFFFFDAD6.toInt(),
            onErrorContainer = 0xFF410002.toInt(),
            surface = 0xFFF7F9FC.toInt(),
            onSurface = 0xFF181C20.toInt(),
            onSurfaceVariant = 0xFF41474D.toInt(),
            surfaceContainerLow = 0xFFF1F4F9.toInt(),
            surfaceContainer = 0xFFEBEEF3.toInt(),
            surfaceContainerHigh = 0xFFE5E8ED.toInt(),
            surfaceContainerHighest = 0xFFDFE3E8.toInt(),
            outline = 0xFF71787E.toInt(),
            outlineVariant = 0xFFC1C7CE.toInt()
        )

        private val FALLBACK_DARK = OverlayMd3Palette(
            isDark = true,
            primary = 0xFF8FCEFF.toInt(),
            onPrimary = 0xFF00344F.toInt(),
            primaryContainer = 0xFF004B70.toInt(),
            onPrimaryContainer = 0xFFCBE6FF.toInt(),
            secondary = 0xFFB7C9D9.toInt(),
            onSecondary = 0xFF22323F.toInt(),
            secondaryContainer = 0xFF394956.toInt(),
            onSecondaryContainer = 0xFFD3E5F6.toInt(),
            tertiary = 0xFF5ADBBF.toInt(),
            onTertiary = 0xFF00382D.toInt(),
            tertiaryContainer = 0xFF005142.toInt(),
            onTertiaryContainer = 0xFF7AF8DA.toInt(),
            error = 0xFFFFB4AB.toInt(),
            errorContainer = 0xFF93000A.toInt(),
            onErrorContainer = 0xFFFFDAD6.toInt(),
            surface = 0xFF101418.toInt(),
            onSurface = 0xFFE0E2E8.toInt(),
            onSurfaceVariant = 0xFFC1C7CE.toInt(),
            surfaceContainerLow = 0xFF181C20.toInt(),
            surfaceContainer = 0xFF1C2024.toInt(),
            surfaceContainerHigh = 0xFF262A2F.toInt(),
            surfaceContainerHighest = 0xFF31353A.toInt(),
            outline = 0xFF8B9198.toInt(),
            outlineVariant = 0xFF41474D.toInt()
        )
    }
}
