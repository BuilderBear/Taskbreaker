package com.builderbear.taskbreaker.timer

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.res.ResourcesCompat
import com.builderbear.taskbreaker.R
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.limeLayer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The glowing dial. Sizes are in the same units as the web version's SVG (a 260-unit-wide box),
 * scaled to whatever size the screen gives us.
 *
 * One lap of the dial = 60 minutes. Longer times wrap around: each extra hour is drawn on top in
 * a lighter green (see limeLayer), so 2h40m shows a full lime ring with a lighter 40-minute band.
 *
 * When [draggable], dragging a finger around the dial sets the time: the green edge jumps to the
 * finger and follows it, and each clockwise lap past 12 o'clock adds an hour (counter-clockwise takes one off).
 */
@Composable
fun TimerDial(
    minutes: Float,
    finished: Boolean,
    draggable: Boolean,
    onDragMinutes: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // When time is up the wedge pulses 5 times, then stops (like the web version), unless the
    // phone's "remove animations" accessibility setting is on.
    val pulse = remember { Animatable(1f) }
    val context = LocalContext.current
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
    LaunchedEffect(finished) {
        pulse.snapTo(1f)
        if (finished && !reduceMotion) {
            repeat(5) {
                pulse.animateTo(0.2f, tween(450))
                pulse.animateTo(1f, tween(450))
            }
        }
    }
    val blink = pulse.value

    // Tick numbers in Chakra Petch, like the web dial.
    val numberFont = remember { ResourcesCompat.getFont(context, R.font.chakra_petch_bold) ?: Typeface.DEFAULT_BOLD }

    // The gesture code below keeps running for the whole drag, so it reads the latest values through these.
    val currentMinutes by rememberUpdatedState(minutes)
    val emit by rememberUpdatedState(onDragMinutes)
    val end by rememberUpdatedState(onDragEnd)
    val haptics = LocalHapticFeedback.current

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = "Timer dial. Drag around it to set the time." }
            .pointerInput(draggable) {
                if (!draggable) return@pointerInput
                val maxMinutes = TimerViewModel.MAX_MINUTES.toFloat()
                var value = 0f      // minutes, unrounded, so slow drags still add up
                var lastAngle = 0f  // finger position last time, in degrees clockwise from 12 o'clock
                var lastSent = -1

                fun angleOf(p: Offset): Float {
                    val dx = (p.x - size.width / 2f).toDouble()
                    val dy = (size.height / 2f - p.y).toDouble()
                    val deg = Math.toDegrees(atan2(dx, dy)).toFloat() // 0 at 12 o'clock, positive clockwise
                    return (deg + 360f) % 360f
                }

                fun send() {
                    val m = value.roundToInt()
                    if (m != lastSent) {
                        lastSent = m
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) // light tick per minute
                        emit(m)
                    }
                }

                detectDragGestures(
                    onDragStart = { start ->
                        lastAngle = angleOf(start)
                        // Jump the edge to the finger, staying on whichever lap is closest to the current
                        // time, so touching "15" on a 1h10m timer gives 1h15m rather than 15m.
                        val fingerMinutes = lastAngle / 6f
                        val lap = floor(currentMinutes / 60f)
                        value = listOf(lap - 1, lap, lap + 1)
                            .map { it * 60f + fingerMinutes }
                            .filter { it in 0f..maxMinutes }
                            .minByOrNull { abs(it - currentMinutes) } ?: fingerMinutes
                        lastSent = -1
                        send()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        // Android bundles finger positions that arrive between screen frames. Walk through
                        // every one of them (not just the latest) so a fast swipe can't skip half a lap
                        // and be mistaken for turning the other way.
                        val positions = change.historical.map { it.position } + change.position
                        for (p in positions) {
                            val a = angleOf(p)
                            // How far the finger turned since last time, the short way round, so crossing
                            // 12 o'clock counts as a small step instead of a 359-degree jump.
                            var delta = a - lastAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            lastAngle = a
                            value = (value + delta / 6f).coerceIn(0f, maxMinutes) // 6 degrees = 1 minute
                        }
                        send()
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                )
            },
    ) {
        val c = center
        val unit = size.minDimension / 260f
        fun r(v: Float) = v * unit
        fun point(radius: Float, angleRad: Double) =
            Offset(c.x + r(radius) * sin(angleRad).toFloat(), c.y - r(radius) * cos(angleRad).toFloat())

        // Rings
        drawCircle(TbColors.Track, r(118f), c, style = Stroke(width = unit))
        drawCircle(
            if (finished) TbColors.Hot else TbColors.Line,
            r(112f),
            c,
            style = Stroke(width = 3 * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14 * unit, 6 * unit))),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF161A10), Color(0xFF0A0B0C)), center = c, radius = r(94f)),
            r(94f),
            c,
        )
        drawCircle(TbColors.Line, r(94f), c, style = Stroke(width = unit))

        // Minute ticks, with a number every 5
        val labelPaint = Paint().apply {
            color = TbColors.Muted.toArgb()
            textSize = 10 * unit
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = numberFont
        }
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val big = i % 5 == 0
            drawLine(
                color = if (big) TbColors.Lime.copy(alpha = 0.8f) else Color(0xFF4A525A),
                start = point(if (big) 82f else 88f, a),
                end = point(94f, a),
                strokeWidth = (if (big) 1.8f else 0.8f) * unit,
            )
            if (big) {
                val p = point(103f, a)
                drawIntoCanvas { it.nativeCanvas.drawText("%02d".format(i), p.x, p.y + 3.5f * unit, labelPaint) }
            }
        }

        // The wedge of remaining time: a thick arc between radius 34 and 80, clockwise from 12 o'clock.
        // Drawn one hour-lap at a time, each later (lighter) lap on top of the earlier ones.
        val total = max(0f, minutes)
        val laps = ceil(total / 60f).toInt()
        val mid = r(57f)
        val topLeft = Offset(c.x - mid, c.y - mid)
        val arcSize = Size(mid * 2, mid * 2)
        val edge = TbColors.Bg.copy(alpha = 0.6f)
        for (hour in 1..laps) {
            val sweep = 360f * min(1f, total / 60f - (hour - 1))
            if (sweep <= 0f) continue
            if (hour == 1) drawWedgeGlow(topLeft, arcSize, sweep, unit, blink)
            drawArc(limeLayer(hour), -90f, sweep, false, topLeft, arcSize, alpha = blink, style = Stroke(width = r(46f)))
            if (hour > 1 && sweep < 360f) {
                // Thin dark edge where a lighter lap ends, so neighbouring greens stay distinct.
                val endAngle = Math.toRadians(sweep.toDouble())
                drawLine(edge, point(34f, endAngle), point(80f, endAngle), strokeWidth = 1.5f * unit)
            }
        }
        if (laps > 1) {
            // Mark 12 o'clock, where every lap starts.
            drawLine(edge, point(34f, 0.0), point(80f, 0.0), strokeWidth = 1.5f * unit)
        }

        // Hub
        drawCircle(TbColors.Bg, r(30f), c)
        drawCircle(TbColors.Line, r(30f), c, style = Stroke(width = unit))
        drawCircle(TbColors.Lime, r(6f), c)
    }
}

/**
 * The glow under the first lap. The web uses drop-shadow(0 0 6px lime/70%) + drop-shadow(0 0 18px lime/35%);
 * blurred copies of the wedge do the same. Blur needs Android 9+, so older phones get a soft halo instead.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWedgeGlow(
    topLeft: Offset,
    arcSize: Size,
    sweep: Float,
    unit: Float,
    blink: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            val oval = RectF(topLeft.x, topLeft.y, topLeft.x + arcSize.width, topLeft.y + arcSize.height)
            listOf(18f to 0.35f, 6f to 0.7f).forEach { (blur, alpha) ->
                val glow = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 46f * unit
                    color = TbColors.Lime.copy(alpha = alpha * blink).toArgb()
                    maskFilter = BlurMaskFilter(blur * unit, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawArc(oval, -90f, sweep, false, glow)
            }
        }
    } else {
        drawArc(TbColors.Lime, -90f, sweep, false, topLeft, arcSize, alpha = 0.25f * blink, style = Stroke(width = 54f * unit))
    }
}
