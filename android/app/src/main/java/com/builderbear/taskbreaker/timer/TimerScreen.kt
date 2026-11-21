package com.builderbear.taskbreaker.timer

import android.Manifest
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.builderbear.taskbreaker.ui.Panel
import com.builderbear.taskbreaker.ui.PrimaryButton
import com.builderbear.taskbreaker.ui.ScreenTitle
import com.builderbear.taskbreaker.ui.SecondaryButton
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.tbFieldColors
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val PRESETS = listOf(5, 10, 15, 25, 45)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    vm: TimerViewModel,
    isStepOpen: (Int) -> Boolean,
    onLinkedStepDone: (Int) -> Unit,
    onBackToSteps: () -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // "now" drives the countdown display. While running, refresh it 4 times a second.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.running) {
        while (s.running) {
            now = System.currentTimeMillis()
            vm.onTick(now)
            delay(250)
        }
    }
    val remaining = s.remainingAt(now)

    // Re-check notification permission each time the screen comes back (the user may have changed it in Settings).
    var canNotify by remember { mutableStateOf(TimerNotifications.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = TimerNotifications.canNotify(context)
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && TimerNotifications.canNotify(context)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    fun startWithPermissionCheck() {
        if (!canNotify && !askedOnce && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedOnce = true
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.start()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Focus timer", "Watch the green drain. That's all you have to do.")

        // Reminders must never fail silently: say so plainly if the alert can't reach the user.
        if (!canNotify) {
            Panel(borderColor = TbColors.Hot) {
                Text(
                    "Notifications are off, so the timer can't alert you when the app is closed or the screen is off.",
                    color = TbColors.Hot,
                )
                SecondaryButton("Turn on notifications", onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                })
            }
        } else if (s.running && !s.alarmExact) {
            Panel(borderColor = TbColors.Hot) {
                Text(
                    "Android only allowed an approximate alarm, so the alert may arrive a few minutes late.",
                    color = TbColors.Hot,
                )
            }
        }

        val fraction = remaining / 1000f / max(3600, s.totalSec)
        TimerDial(
            fraction = fraction,
            finished = s.finished,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )

        Text(
            formatTime(remaining),
            color = TbColors.Lime,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            when {
                s.finished -> "Time's up. Nice work."
                s.label.isNotBlank() -> "Objective: ${s.label}"
                else -> ""
            },
            color = if (s.finished) TbColors.Ink else TbColors.Muted,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
        )

        val linked = s.linkedStep
        if (linked != null && isStepOpen(linked)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                SecondaryButton("✓ Step done, next step", onClick = { onLinkedStepDone(linked) }, compact = true)
                SecondaryButton("Back to steps", onClick = onBackToSteps, compact = true)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val goText = when {
                s.running -> "Pause"
                remaining in 1 until s.totalSec * 1000L -> "Resume"
                else -> "Start"
            }
            PrimaryButton(goText, onClick = { if (s.running) vm.pause() else startWithPermissionCheck() }, modifier = Modifier.widthIn(min = 140.dp))
            SecondaryButton("Reset", onClick = vm::reset)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESETS.forEach { m ->
                SecondaryButton("$m min", onClick = { vm.setTimer(m) }, selected = s.totalSec == m * 60, compact = true)
            }
        }

        // Custom length
        var custom by rememberSaveable { mutableStateOf((s.totalSec / 60).toString()) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                label = { Text("Custom min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = tbFieldColors(),
                modifier = Modifier.widthIn(max = 140.dp),
            )
            SecondaryButton("Set", onClick = { vm.setTimer(custom.toIntOrNull() ?: 25) })
        }
    }
}

/** mm:ss, or h:mm:ss for an hour or more. Rounds up so it never shows 0:00 while time is left. */
fun formatTime(ms: Long): String {
    val total = ceil(max(0L, ms) / 1000.0).toLong()
    val h = total / 3600
    val m = total % 3600 / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/**
 * The glowing dial. Sizes are in the same units as the web version's SVG (a 260-unit-wide box),
 * scaled to whatever size the screen gives us.
 */
@Composable
fun TimerDial(fraction: Float, finished: Boolean, modifier: Modifier = Modifier) {
    // When time is up, the wedge area blinks.
    val blink = if (finished) {
        val t = rememberInfiniteTransition(label = "blink")
        t.animateFloat(1f, 0.2f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blinkAlpha").value
    } else 1f

    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val unit = size.minDimension / 260f
        fun r(v: Float) = v * unit
        fun point(radius: Float, angleRad: Double) =
            Offset(c.x + r(radius) * sin(angleRad).toFloat(), c.y - r(radius) * cos(angleRad).toFloat())

        // Rings
        drawCircle(Color(0xFF1D2126), r(118f), c, style = Stroke(width = unit))
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
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
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

        // The lime wedge of remaining time: a thick arc between radius 34 and 80, clockwise from 12 o'clock.
        val f = min(1f, max(0f, fraction))
        if (f > 0f) {
            val mid = r(57f)
            val topLeft = Offset(c.x - mid, c.y - mid)
            val arcSize = Size(mid * 2, mid * 2)
            val sweep = 360f * f
            // soft glow behind, then the solid wedge
            drawArc(TbColors.Lime, -90f, sweep, false, topLeft, arcSize, alpha = 0.25f * blink, style = Stroke(width = r(52f)))
            drawArc(TbColors.Lime, -90f, sweep, false, topLeft, arcSize, alpha = blink, style = Stroke(width = r(46f)))
        }

        // Hub
        drawCircle(TbColors.Bg, r(30f), c)
        drawCircle(TbColors.Line, r(30f), c, style = Stroke(width = unit))
        drawCircle(TbColors.Lime, r(6f), c)
    }
}
