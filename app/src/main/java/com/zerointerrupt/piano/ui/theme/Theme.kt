package com.zerointerrupt.piano.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.zerointerrupt.piano.model.AppThemeMode

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF006494),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = Color(0xFF50606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5F6),
    onSecondaryContainer = Color(0xFF0C1D29),
    tertiary = Color(0xFF006B58),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF7AF8DA),
    onTertiaryContainer = Color(0xFF002019),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8ED),
    surfaceContainerHighest = Color(0xFFDFE3E8),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE)
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FCEFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B70),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFB7C9D9),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF394956),
    onSecondaryContainer = Color(0xFFD3E5F6),
    tertiary = Color(0xFF5ADBBF),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF005142),
    onTertiaryContainer = Color(0xFF7AF8DA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E2E8),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2F),
    surfaceContainerHighest = Color(0xFF31353A),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

fun TextStyle.withTabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val ExpressiveTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.withTabular(),
        titleLarge = titleLarge.withTabular(),
        titleMedium = titleMedium.withTabular(),
        titleSmall = titleSmall.withTabular(),
        bodyLarge = bodyLarge.withTabular(),
        bodyMedium = bodyMedium.withTabular(),
        bodySmall = bodySmall.withTabular(),
        labelLarge = labelLarge.withTabular(),
        labelMedium = labelMedium.withTabular(),
        labelSmall = labelSmall.withTabular()
    )
}

@Composable
fun resolveIsDarkTheme(themeMode: AppThemeMode): Boolean = when (themeMode) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
}

@Composable
fun ZeroInterruptPianoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content
    )
}
