package com.visionvoice.patient.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DeepMedicalTeal,
    onPrimary = SurfaceContainerLowest,
    primaryContainer = SurfaceTeal,
    onPrimaryContainer = DarkTealPressed,
    inversePrimary = PrimaryFixedDim,

    secondary = WarmAmber,
    onSecondary = SurfaceContainerLowest,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = SecondaryOrange,

    tertiary = SuccessEmerald,
    onTertiary = SurfaceContainerLowest,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    background = CanvasWarmWhite,
    onBackground = DeepSlateText,

    surface = SurfaceContainerLowest,
    onSurface = DeepSlateText,
    surfaceVariant = SurfaceTeal,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = PrimaryTealDark,

    outline = OutlineSlate,
    outlineVariant = OutlineVariant,

    error = ClinicalError,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryFixedDim,
    onPrimary = DeepSlateText,
    primaryContainer = DarkTealPressed,
    onPrimaryContainer = OnPrimaryContainerTeal,

    secondary = WarmAmber,
    onSecondary = DeepSlateText,
    secondaryContainer = DarkAmber,
    onSecondaryContainer = AmberContainer,

    tertiary = SuccessEmerald,
    onTertiary = DeepSlateText,

    background = DeepSlateText,
    onBackground = CanvasWarmWhite,

    surface = SurfaceOnSurface,
    onSurface = CanvasWarmWhite,
    surfaceVariant = OnSurfaceVariant,
    onSurfaceVariant = OutlineVariant,

    outline = MutedSlate,
    outlineVariant = OutlineSlate,

    error = ClinicalError,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

@Composable
fun VisionVoicePatientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
