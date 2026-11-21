package com.builderbear.taskbreaker.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/*
 * The web page's background, recreated:
 *   - a faint lime grid every 40px
 *   - a dark-green glow coming down from above the top edge
 *   - thin horizontal scanlines over everything, like an old CRT monitor
 *
 * drawWithCache builds the line pattern once per screen size and reuses it on every frame,
 * so the timer's 4-times-a-second updates don't redraw hundreds of lines from scratch.
 */

private val GridLine = Color(0xFFC6FF00).copy(alpha = 0.035f)
private val TopGlow = Color(0xFF1A2208)
private val Scanline = Color.Black.copy(alpha = 0.22f)

/** Paints the page background (color, top glow, grid) behind whatever this modifier is on. */
fun Modifier.hudBackground(): Modifier = this.drawWithCache {
    val step = 40.dp.toPx()
    val grid = Path().apply {
        var x = 0f
        while (x <= size.width) { moveTo(x, 0f); lineTo(x, size.height); x += step }
        var y = 0f
        while (y <= size.height) { moveTo(0f, y); lineTo(size.width, y); y += step }
    }
    // CSS: radial-gradient(ellipse at 50% -10%, #1a2208 0%, transparent 60%)
    val glowCenter = Offset(size.width / 2f, -0.1f * size.height)
    val glowRadius = 0.6f * hypot(size.width / 2f, size.height * 1.1f)
    val glow = Brush.radialGradient(listOf(TopGlow, Color.Transparent), center = glowCenter, radius = max(glowRadius, 1f))
    val hairline = Stroke(width = 1.dp.toPx())
    onDrawBehind {
        drawRect(TbColors.Bg)
        drawRect(glow)
        drawPath(grid, GridLine, style = hairline)
    }
}

/**
 * The scanline layer. Put it last inside a full-screen Box so it sits on top of the content.
 * It draws only, so taps pass straight through to the buttons underneath.
 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer() // own drawing layer: content changing underneath doesn't redraw the lines
            .drawWithCache {
                val period = 3.dp.toPx()
                val line = 1.dp.toPx()
                val lines = Path().apply {
                    var y = 0f
                    while (y < size.height) { addRect(Rect(0f, y, size.width, y + line)); y += period }
                }
                onDrawBehind { drawPath(lines, Scanline) }
            },
    )
}
