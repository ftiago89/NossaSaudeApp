package com.example.nossasaudeapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NossaSaudeColorScheme = lightColorScheme(
    primary = NsColors.Primary,
    onPrimary = NsColors.Surface,
    primaryContainer = NsColors.PrimarySoft,
    onPrimaryContainer = NsColors.PrimaryDark,
    secondary = NsColors.AccentTeal,
    onSecondary = NsColors.Surface,
    secondaryContainer = NsColors.AccentTealSoft,
    onSecondaryContainer = NsColors.AccentTeal,
    tertiary = NsColors.ConditionText,
    onTertiary = NsColors.Surface,
    background = NsColors.Background,
    onBackground = NsColors.TextPrimary,
    surface = NsColors.Surface,
    onSurface = NsColors.TextPrimary,
    surfaceVariant = NsColors.SurfaceWarm,
    onSurfaceVariant = NsColors.TextSecondary,
    outline = NsColors.Border,
    outlineVariant = NsColors.Border,
    error = NsColors.Danger,
    onError = NsColors.Surface,
    errorContainer = NsColors.DangerSoft,
    onErrorContainer = NsColors.Danger,
)

@Composable
fun NossaSaudeTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = NsColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = NsColors.Background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = NossaSaudeColorScheme,
        typography = NsTypography,
        content = content,
    )
}
