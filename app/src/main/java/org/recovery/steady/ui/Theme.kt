package org.recovery.steady.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeColor(
    val id: String,
    val label: String,
    val preview: Color,
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
) {
    SAGE("sage", "Sage", Color(0xFF496B58), Color(0xFF496B58), Color(0xFFD4E8D9), Color(0xFF9DC9AC), Color(0xFF31523F)),
    OCEAN("ocean", "Ocean", Color(0xFF35658A), Color(0xFF35658A), Color(0xFFD2E6F6), Color(0xFF9BCBEE), Color(0xFF234B69)),
    INDIGO("indigo", "Indigo", Color(0xFF575A91), Color(0xFF575A91), Color(0xFFE1E0FF), Color(0xFFC1C2FF), Color(0xFF3F426F)),
    ROSE("rose", "Rose", Color(0xFF8A5262), Color(0xFF8A5262), Color(0xFFF8DCE4), Color(0xFFFFB2C6), Color(0xFF693B49)),
    AMBER("amber", "Amber", Color(0xFF806020), Color(0xFF806020), Color(0xFFF7E2AE), Color(0xFFEBC36E), Color(0xFF5E4615)),
    ;

    companion object {
        fun fromId(id: String): ThemeColor = entries.firstOrNull { it.id == id } ?: SAGE
    }
}

@Composable
fun SteadyTheme(themeColorId: String = ThemeColor.SAGE.id, content: @Composable () -> Unit) {
    val palette = ThemeColor.fromId(themeColorId)
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = palette.darkPrimary,
            onPrimary = Color(0xFF10251A),
            primaryContainer = palette.darkContainer,
            onPrimaryContainer = Color(0xFFF5F3F7),
            secondary = Color(0xFFD1C5B5),
            background = Color(0xFF111416),
            surface = Color(0xFF111416),
            surfaceVariant = Color(0xFF292D30),
            error = Color(0xFFFFB4AB),
        )
    } else {
        lightColorScheme(
            primary = palette.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = palette.lightContainer,
            onPrimaryContainer = Color(0xFF17201A),
            secondary = Color(0xFF6D6558),
            background = Color(0xFFF8F7F2),
            surface = Color(0xFFF8F7F2),
            surfaceVariant = Color(0xFFE9E7DF),
            error = Color(0xFF8B4A46),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
