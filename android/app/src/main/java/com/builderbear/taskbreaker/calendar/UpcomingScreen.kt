package com.builderbear.taskbreaker.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.builderbear.taskbreaker.ui.ChakraPetch
import com.builderbear.taskbreaker.ui.Panel
import com.builderbear.taskbreaker.ui.PrimaryButton
import com.builderbear.taskbreaker.ui.ScreenTitle
import com.builderbear.taskbreaker.ui.SecondaryButton
import com.builderbear.taskbreaker.ui.SectionLabel
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.TbType
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun UpcomingScreen(vm: UpcomingViewModel, onChooseCalendars: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read when the page comes back into view (permissions or settings may have changed meanwhile).
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    // "in 25 min" labels count down, so refresh the clock every 30 seconds.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Upcoming", "Your next appointments. You'll get a reminder ${AppointmentReminders.LEAD_MINUTES} min before each one.")

        if (!s.loaded) return@Column

        // Problems first, each with the button that fixes it. Reminders must never fail silently.
        when {
            !s.hasPermission -> Warning(
                "Taskbreaker can't see your calendar yet, so it can't remind you about appointments.",
                action = "Allow calendar access",
                onAction = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                secondary = "App settings",
                onSecondary = { openAppSettings(context) },
            )
            !s.calendarsChosen -> Warning(
                "Choose which calendars to watch. Until you do, there are no appointment reminders.",
                action = "Choose calendars",
                onAction = onChooseCalendars,
            )
        }
        if (!s.canNotify) {
            Warning(
                "Notifications are off, so appointment reminders can't reach you.",
                action = "Turn on notifications",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )
        } else if (!s.exactAlarms) {
            Warning("Android only allows approximate alarms right now, so reminders may arrive a few minutes late.")
        }

        if (!s.hasPermission || !s.calendarsChosen) return@Column

        val list = s.appointments
        if (list.isEmpty()) {
            Panel {
                SectionLabel("All clear")
                Text("No appointments in the next 7 days.", color = TbColors.Ink)
            }
            return@Column
        }

        // The next appointment that has a start time gets the big glowing card.
        val next = list.firstOrNull { !it.allDay && it.begin > now } ?: list.first()
        NextUpCard(next, now)

        // Everything else, grouped by day.
        val rest = list.filter { it !== next }
        if (rest.isNotEmpty()) {
            Panel {
                var lastDay = ""
                rest.forEachIndexed { i, a ->
                    val day = dayLabel(context, a, now)
                    if (day != lastDay) {
                        if (i > 0) HorizontalDivider(color = TbColors.Line)
                        SectionLabel(day, color = TbColors.Lime)
                        lastDay = day
                    }
                    AppointmentRow(context, a)
                }
            }
        }
    }
}

@Composable
private fun NextUpCard(a: Appointment, now: Long) {
    val context = LocalContext.current
    Panel(borderColor = TbColors.Lime2, accent = true) {
        val untilText = when {
            a.allDay -> dayLabel(context, a, now)
            a.begin <= now -> "happening now"
            else -> "in " + formatDuration(a.begin - now)
        }
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = TbColors.Lime)); append("NEXT UP"); pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = TbColors.Muted)); append(" · ${untilText.uppercase()}"); pop()
            },
            style = TbType.label,
        )
        Text(a.title, color = TbColors.Ink, style = TbType.objective)
        Text(timeRange(context, a, now), color = TbColors.Ink, fontFamily = ChakraPetch, fontSize = 16.sp)
        if (a.location.isNotBlank()) Text(a.location, color = TbColors.Muted)
        if (!a.allDay && a.begin > now) {
            val reminder = AppointmentReminders.reminderTime(a)
            val reminderText = if (reminder <= now) "Reminder sent" else "Reminder at " + DateFormat.getTimeFormat(context).format(reminder)
            SectionLabel(reminderText, color = TbColors.Lime2)
        }
        if (a.location.isNotBlank()) {
            PrimaryButton("Navigate", onClick = { context.startActivity(AppointmentReminders.mapsDirections(a.location)) })
        }
    }
}

@Composable
private fun AppointmentRow(context: Context, a: Appointment) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Small dot in the calendar's own color, so you can tell calendars apart.
        Box(Modifier.padding(top = 8.dp).size(10.dp).background(Color(a.color), CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (a.allDay) "All day" else DateFormat.getTimeFormat(context).format(a.begin),
                color = TbColors.Muted,
                fontFamily = ChakraPetch,
                fontSize = 14.sp,
            )
            Text(a.title, color = TbColors.Ink, fontSize = 17.sp)
            if (a.location.isNotBlank()) Text(a.location, color = TbColors.Muted, fontSize = 14.sp, lineHeight = 19.sp)
        }
        if (a.location.isNotBlank()) {
            SecondaryButton("Go", onClick = { context.startActivity(AppointmentReminders.mapsDirections(a.location)) }, compact = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Warning(
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Panel(borderColor = TbColors.Hot) {
        Text(message, color = TbColors.Hot)
        if (action != null || secondary != null) {
            // FlowRow puts the second button on its own line when both don't fit side by side.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (action != null) SecondaryButton(action, onClick = onAction)
                if (secondary != null) SecondaryButton(secondary, onClick = onSecondary)
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

/** "Today", "Tomorrow", or e.g. "Mon, Nov 24". All-day events are stored in UTC, so they're read that way. */
private fun dayLabel(context: Context, a: Appointment, now: Long): String {
    val dayOf = { millis: Long, utc: Boolean ->
        Calendar.getInstance(if (utc) java.util.TimeZone.getTimeZone("UTC") else java.util.TimeZone.getDefault()).apply { timeInMillis = millis }
    }
    val event = dayOf(a.begin, a.allDay)
    val today = dayOf(now, false)
    fun sameDay(x: Calendar, y: Calendar) = x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val localEvent = Calendar.getInstance().apply {
        set(event.get(Calendar.YEAR), event.get(Calendar.MONTH), event.get(Calendar.DAY_OF_MONTH), 12, 0)
    }
    return when {
        sameDay(localEvent, today) -> "Today"
        sameDay(localEvent, tomorrow) -> "Tomorrow"
        else -> DateUtils.formatDateTime(
            context,
            localEvent.timeInMillis,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
        )
    }
}

/** e.g. "Today · 2:30 – 3:30 PM" */
private fun timeRange(context: Context, a: Appointment, now: Long): String {
    val day = dayLabel(context, a, now)
    if (a.allDay) return "$day · All day"
    val fmt = DateFormat.getTimeFormat(context)
    return "$day · ${fmt.format(a.begin)} – ${fmt.format(a.end)}"
}

/** e.g. "25 min", "1 h 20 min", "2 days" */
private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(1)
    val hours = minutes / 60
    return when {
        hours >= 48 -> "${hours / 24} days"
        hours >= 1 -> if (minutes % 60 == 0L) "$hours h" else "$hours h ${minutes % 60} min"
        else -> "$minutes min"
    }
}
