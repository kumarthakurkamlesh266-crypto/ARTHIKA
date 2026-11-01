package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ARTHIKA Light Theme Palette exclusively
private val ArthikaColorScheme = lightColorScheme(
    primary = ArthikaPrimary,
    onPrimary = ArthikaCard,
    primaryContainer = ArthikaSoftGreen,
    onPrimaryContainer = ArthikaDarkGreen,
    secondary = ArthikaDarkGreen,
    onSecondary = ArthikaCard,
    secondaryContainer = ArthikaSoftGreen,
    onSecondaryContainer = ArthikaDarkGreen,
    background = ArthikaBackground,
    onBackground = ArthikaTextPrimary,
    surface = ArthikaCard,
    onSurface = ArthikaTextPrimary,
    surfaceVariant = ArthikaBackground,
    onSurfaceVariant = ArthikaTextSecondary,
    outline = ArthikaBorder,
    outlineVariant = ArthikaBorder
)

@Composable
fun ArthikaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArthikaColorScheme,
        typography = ArthikaTypography, // uses Plus Jakarta Sans
        content = content
    )
}
