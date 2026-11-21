package com.builderbear.taskbreaker.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The one place the app's colors are defined (same palette as the web version).
 * Change a value here and every screen follows. Don't hard-code colors elsewhere.
 */
object TbColors {
    val Bg = Color(0xFF07080A)       // page background
    val Panel = Color(0xFF111316)    // cards
    val Panel2 = Color(0xFF181B1F)   // secondary buttons
    val Line = Color(0xFF2A2F35)     // borders
    val Field = Color(0xFF0B0D0F)    // text input background
    val Ink = Color(0xFFE8ECE6)      // main text
    val Muted = Color(0xFF8A929A)    // secondary text
    val Faint = Color(0xFF5D656C)    // placeholders, finished steps
    val Lime = Color(0xFFC6FF00)     // accent
    val Lime2 = Color(0xFF8FBF00)    // dim accent
    val Hot = Color(0xFFFF2E88)      // alerts and warnings
}

private val colorScheme = darkColorScheme(
    primary = TbColors.Lime,
    onPrimary = Color.Black,
    secondary = TbColors.Lime2,
    background = TbColors.Bg,
    onBackground = TbColors.Ink,
    surface = TbColors.Panel,
    onSurface = TbColors.Ink,
    surfaceVariant = TbColors.Panel2,
    onSurfaceVariant = TbColors.Muted,
    outline = TbColors.Line,
    error = TbColors.Hot,
)

// Angled ("cut") corners give the HUD look that clip-path gave the web version.
private val shapes = Shapes(
    small = CutCornerShape(8.dp),
    medium = CutCornerShape(12.dp),
    large = CutCornerShape(16.dp),
)

@Composable
fun TaskbreakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, shapes = shapes, content = content)
}
