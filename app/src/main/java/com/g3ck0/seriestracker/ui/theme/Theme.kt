package com.g3ck0.seriestracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Indigo = Color(0xFF4C5DF5)
private val IndigoDark = Color(0xFFB3BDFF)
private val Magenta = Color(0xFFD65DB1)
private val MagentaDark = Color(0xFFFFB0E4)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Magenta,
    tertiary = Color(0xFF00897B),
)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    secondary = MagentaDark,
    tertiary = Color(0xFF80CBC4),
)

@Composable
fun SeriesTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
