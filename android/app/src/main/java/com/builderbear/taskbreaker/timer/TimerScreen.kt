package com.builderbear.taskbreaker.timer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.builderbear.taskbreaker.ui.FieldShape
import com.builderbear.taskbreaker.ui.Panel
import com.builderbear.taskbreaker.ui.PrimaryButton
import com.builderbear.taskbreaker.ui.ScreenTitle
import com.builderbear.taskbreaker.ui.SecondaryButton
import com.builderbear.taskbreaker.ui.SectionLabel
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.TbType
import com.builderbear.taskbreaker.ui.tbFieldColors
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
        ScreenTitle("Focus timer", "Watch the green drain. That's all you have to do.", centered = true)

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

        // While a finger is dragging the dial, show that value instead of the saved one.
        // It's only saved (vm.setLength) when the finger lifts, so storage isn't written on every pixel.
        var dragMinutes by remember { mutableStateOf<Int?>(null) }
        val shownMs = dragMinutes?.let { it * 60_000L } ?: remaining

        // Like the web's min(100%, 520px, 62vh): cap the dial so the readout and Start button stay on screen.
        val maxDial = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
        TimerDial(
            minutes = shownMs / 60_000f,
            finished = s.finished && dragMinutes == null,
            // Dragging is off while the timer runs, so a stray finger can't wreck a countdown. Pause first.
            draggable = !s.running,
            onDragMinutes = { dragMinutes = it },
            onDragEnd = {
                dragMinutes?.let { vm.setLength(max(1, it)) }
                dragMinutes = null
            },
            modifier = Modifier.widthIn(max = minOf(maxDial, 420.dp)).fillMaxWidth(),
        )

        // tnum = "tabular numbers": every digit is the same width, so the readout doesn't jiggle as it counts.
        Text(formatTime(shownMs), style = TbType.readout.copy(fontFeatureSettings = "tnum"), maxLines = 1)
        Text(
            buildAnnotatedString {
                val bold = SpanStyle(color = TbColors.Ink, fontWeight = FontWeight.Bold)
                when {
                    dragMinutes != null -> append("Lift your finger to set it.")
                    s.finished -> { withStyle(bold) { append("Time's up.") }; append(" Nice work.") }
                    s.label.isNotBlank() -> { append("Objective: "); withStyle(bold) { append(s.label) } }
                    !s.running -> append("Drag the dial to set the time.")
                }
            },
            color = TbColors.Muted,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            modifier = Modifier.widthIn(max = 360.dp),
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
        // Keep the box in step when the length changes some other way (dragging the dial, a preset).
        LaunchedEffect(s.totalSec) { custom = (s.totalSec / 60).toString() }
        // Web layout: CUSTOM [ 25 ] min [SET]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Custom", style = TbType.label.copy(fontSize = 14.sp, letterSpacing = 0.06.em))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                colors = tbFieldColors(),
                shape = FieldShape,
                modifier = Modifier.width(90.dp),
            )
            Text("min", color = TbColors.Muted)
            SecondaryButton("Set", onClick = { vm.setTimer(custom.toIntOrNull() ?: 25) }, compact = true)
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
