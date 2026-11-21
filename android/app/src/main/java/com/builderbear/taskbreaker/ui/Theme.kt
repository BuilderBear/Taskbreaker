package com.builderbear.taskbreaker.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.builderbear.taskbreaker.R

/**
 * The one place the app's colors are defined (same palette as the web version).
 * Change a value here and every screen follows. Don't hard-code colors elsewhere.
 */
object TbColors {
    val Bg = Color(0xFF07080A)       // page background
    val Panel = Color(0xFF111316)    // cards (top of their gradient)
    val PanelEnd = Color(0xFF0C0E10) // cards (bottom of their gradient)
    val Panel2 = Color(0xFF181B1F)   // secondary buttons
    val Line = Color(0xFF2A2F35)     // borders
    val Track = Color(0xFF1D2126)    // empty progress bar, outer dial ring
    val Field = Color(0xFF0B0D0F)    // text input background
    val Ink = Color(0xFFE8ECE6)      // main text
    val Muted = Color(0xFF8A929A)    // secondary text
    val Faint = Color(0xFF5D656C)    // placeholders, finished steps
    val Lime = Color(0xFFC6FF00)     // accent
    val LimeHover = Color(0xFFD9FF4D) // pressed primary button
    val Lime2 = Color(0xFF8FBF00)    // dim accent
    val Hot = Color(0xFFFF2E88)      // alerts and warnings
}

/* The same two fonts as the web version, bundled in res/font (SIL Open Font License, see android/fonts-license). */

/** Headings, buttons, labels and numbers: squared-off "tech" letters. */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Body text: narrow and easy to read. */
val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/** The lime glow the web version puts on the logo, timer readout and active buttons. */
object TbGlow {
    /** Text glow (the web's text-shadow). blurRadius is in screen pixels. */
    val text = Shadow(color = TbColors.Lime.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = 18f)
}

/** Text styles shared across screens, matching the web CSS. */
object TbType {
    /** Page titles (the web's h1). */
    val title = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = 0.03.em)

    /** Small uppercase headings (the web's h2 and card labels). */
    val label = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.1.em)

    /** Button text. */
    val button = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.06.em)

    /** The big glowing timer readout. */
    val readout = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 76.sp, color = TbColors.Lime, shadow = TbGlow.text)

    /** The current step on the objective card. */
    val objective = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp)
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

// Body text in Rajdhani, everything else in Chakra Petch, like the web page.
private val base = Typography()
private val body = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp)
private val typography = base.copy(
    bodyLarge = body,
    bodyMedium = body.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = body.copy(fontSize = 13.sp, lineHeight = 18.sp),
    titleLarge = base.titleLarge.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
)

// Angled ("cut") corners give the HUD look that clip-path gave the web version.
private val shapes = Shapes(
    small = CutCornerShape(8.dp),
    medium = CutCornerShape(12.dp),
    large = CutCornerShape(16.dp),
)

@Composable
fun TaskbreakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes, content = content)
}
