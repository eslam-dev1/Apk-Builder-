package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF282C5E),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF002027),
    secondaryContainer = Color(0xFF0E3A47),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = NeonViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3B1D5E),
    onTertiaryContainer = Color(0xFFF3E8FF),
    background = StudioObsidian,
    onBackground = Color(0xFFF1F5F9),
    surface = StudioSurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = StudioSurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = StudioCardBorderDark,
    error = CoralError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigoLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = SecondaryCyanLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = TertiaryVioletLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF3B0764),
    background = StudioBackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = StudioSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = StudioSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = CoralError,
    onError = Color.White
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
