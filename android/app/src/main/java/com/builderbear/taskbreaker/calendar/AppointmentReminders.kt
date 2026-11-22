package com.builderbear.taskbreaker.calendar

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.builderbear.taskbreaker.MainActivity
import com.builderbear.taskbreaker.R
import com.builderbear.taskbreaker.settings.SettingsStore
import com.builderbear.taskbreaker.timer.TimerNotifications
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Schedules a system alarm for each upcoming appointment, so a reminder pops up even when the app
 * is closed or the phone is asleep (same approach as the focus timer).
 *
 * The schedule is rebuilt from scratch whenever something might have changed: the app opens, the
 * calendar changes, the phone restarts, or a reminder fires. Rebuilding is cheap and means moved or
 * deleted appointments can never leave a stale reminder behind.
 */
object AppointmentReminders {

    /** Remind at least this long before an appointment starts. */
    const val LEAD_MINUTES = 20

    /** Phase 2: when the drive time is known, also remind this long before you need to leave. */
    const val LEAVE_BUFFER_MINUTES = 15

    /** How far ahead to set alarms. A background job rebuilds the schedule every few hours, so 3 days is plenty. */
    private const val WINDOW_MS = 3L * 24 * 60 * 60 * 1000

    private const val PREFS = "appointment_reminders"
    private const val KEY_SCHEDULED = "scheduled_codes"
    private const val KEY_NOTIFIED = "notified_keys"
    private const val CHANNEL_ID = "appointments"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_BEGIN = "begin"

    /**
     * When to remind for an appointment: whichever comes first of
     *   - 20 minutes before it starts, or
     *   - 15 minutes before you need to leave (only once Phase 2 knows the drive time).
     */
    fun reminderTime(appointment: Appointment, driveMinutes: Int? = null): Long {
        val byLead = appointment.begin - LEAD_MINUTES * 60_000L
        if (driveMinutes == null) return byLead
        val byLeave = appointment.begin - (driveMinutes + LEAVE_BUFFER_MINUTES) * 60_000L
        return minOf(byLead, byLeave)
    }

    /** Reminder time using the last saved drive estimate (no network), for showing on screen. */
    fun reminderTimeFor(context: Context, appointment: Appointment): Long =
        reminderTime(appointment, DriveTime.cached(context, appointment)?.minutes)

    /** The latest time to set off by car, or null if the drive time isn't known. */
    fun leaveBy(appointment: Appointment, driveMinutes: Int?): Long? =
        driveMinutes?.let { appointment.begin - it * 60_000L }

    /** What happened when the schedule was rebuilt, so the Upcoming page can say so. */
    data class Status(val scheduled: Int, val exactAlarms: Boolean)

    /**
     * Throws away every appointment alarm we set before and sets fresh ones from the calendar.
     * May ask Google for drive times, so call it off the main thread. @Synchronized makes overlapping
     * calls (e.g. the background job and the open app at the same moment) take turns instead of clashing.
     */
    @Synchronized
    fun reschedule(context: Context): Status {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // 1. Cancel the old alarms. (A PendingIntent is identified by its request code and target,
        //    so recreating it with the same code finds the scheduled one.)
        prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { code ->
            code.toIntOrNull()?.let { alarmManager.cancel(alarmIntent(context, it, null)) }
        }

        // 2. Set new ones for appointments in the next few days. All-day events have no start time
        //    to count back from, so they appear on the Upcoming page but don't get a reminder.
        val now = System.currentTimeMillis()
        val appointments = CalendarRepository
            .appointments(context, SettingsStore.calendarIds(context), now, now + WINDOW_MS)
            .filter { !it.allDay && it.begin > now }
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val codes = mutableSetOf<String>()
        fun setAlarm(code: Int, at: Long, appointment: Appointment, refresh: Boolean) {
            val pending = alarmIntent(context, code, appointment, refresh)
            try {
                if (canExact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
            codes += code.toString()
        }

        for (appointment in appointments) {
            // Phase 2: if there's an address and a Maps key, include the drive time (asks Google if needed).
            val drive = DriveTime.estimate(context, appointment, now)
            val at = reminderTime(appointment, drive?.minutes)
            val code = appointment.key.hashCode()
            if (at <= now) {
                // Already inside the reminder window (e.g. the appointment was just added, traffic got
                // worse, or the phone was off). Remind right away instead of not at all.
                notifyOnce(context, appointment)
                continue
            }
            setAlarm(code, at, appointment, refresh = false)

            // Extra "recheck" alarms that look up the drive time again with fresh traffic, so the
            // reminder can move earlier if the roads get worse. They don't show anything themselves.
            if (appointment.location.isNotBlank() && DriveTime.isConfigured(context)) {
                if (appointment.begin - now > DriveTime.LOOKAHEAD_MS) {
                    // Too far ahead to look up yet: recheck once it comes within range.
                    setAlarm(code + 1, appointment.begin - DriveTime.LOOKAHEAD_MS + 60_000L, appointment, refresh = true)
                } else {
                    val recheck30 = at - 30 * 60_000L
                    val recheck10 = at - 10 * 60_000L
                    if (recheck30 > now + 60_000L) setAlarm(code + 1, recheck30, appointment, refresh = true)
                    if (recheck10 > now + 60_000L) setAlarm(code + 2, recheck10, appointment, refresh = true)
                }
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, codes).apply()
        return Status(scheduled = appointments.size, exactAlarms = canExact)
    }

    const val EXTRA_REFRESH = "refresh_only"

    private fun alarmIntent(context: Context, code: Int, appointment: Appointment?, refresh: Boolean = false): PendingIntent {
        val intent = Intent(context, AppointmentAlarmReceiver::class.java)
        if (appointment != null) {
            intent.putExtra(EXTRA_EVENT_ID, appointment.eventId)
                .putExtra(EXTRA_BEGIN, appointment.begin)
                .putExtra(EXTRA_REFRESH, refresh)
        }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Shows the reminder for an appointment unless it was already shown. Remembering what was shown
     * stops duplicates when the schedule is rebuilt inside the reminder window.
     */
    fun notifyOnce(context: Context, appointment: Appointment) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        // Forget appointments that started more than a day ago, so this list stays small.
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
            .filter { key -> (key.substringAfter('@').toLongOrNull() ?: 0L) > now - 24 * 60 * 60 * 1000L }
            .toMutableSet()
        if (appointment.key in notified) return
        show(context, appointment)
        notified += appointment.key
        prefs.edit().putStringSet(KEY_NOTIFIED, notified).apply()
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Appointment reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminds you before appointments in the calendars you chose."
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // checked by TimerNotifications.canNotify() just below
    private fun show(context: Context, appointment: Appointment) {
        createChannel(context)
        if (!TimerNotifications.canNotify(context)) return // the Upcoming page warns about this, so it isn't silent
        val code = appointment.key.hashCode()
        val timeFormat = DateFormat.getTimeFormat(context)
        val time = timeFormat.format(appointment.begin)
        val now = System.currentTimeMillis()
        val minutes = max(0, ((appointment.begin - now) / 60_000.0).roundToInt())
        val whenText = if (minutes <= 0) "Starting now" else "Starts in $minutes min"

        val drive = DriveTime.cached(context, appointment)
        val driveMinutes = drive?.minutes
        val leave = leaveBy(appointment, driveMinutes)
        // When the drive is what sets the reminder time, lead with when to leave.
        val driveDecides = driveMinutes != null && reminderTime(appointment, driveMinutes) < reminderTime(appointment)
        val title: String
        val parts = mutableListOf<String>()
        if (driveDecides && leave != null) {
            title = if (leave <= now) "Leave now for ${appointment.title}" else "Leave by ${timeFormat.format(leave)} for ${appointment.title}"
            parts += "$driveMinutes min drive with traffic"
            parts += "Starts at $time"
        } else {
            title = "${appointment.title} at $time"
            parts += whenText
            if (driveMinutes != null) parts += "$driveMinutes min drive"
        }
        if (appointment.location.isNotBlank() && DriveTime.isConfigured(context) && driveMinutes == null) parts += "Drive time unknown"
        if (appointment.location.isNotBlank()) parts += appointment.location
        val text = parts.joinToString(" · ")

        val open = PendingIntent.getActivity(
            context,
            code,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PAGE, MainActivity.PAGE_UPCOMING)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (appointment.location.isNotBlank()) {
            builder.addAction(0, "Navigate", PendingIntent.getActivity(
                context, code, mapsDirections(appointment.location), PendingIntent.FLAG_IMMUTABLE,
            ))
        }
        NotificationManagerCompat.from(context).notify(code, builder.build())
    }

    /**
     * Opens Google Maps with driving directions to [address] (or the browser if Maps isn't installed).
     * This is a plain link, so it's free and needs no API key.
     */
    fun mapsDirections(address: String): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=" + Uri.encode(address)),
        )
}

/**
 * Runs [work] on a background thread from a BroadcastReceiver. Receivers start on the main thread,
 * where Android forbids network calls (drive-time lookups). goAsync() keeps the receiver alive
 * until the work calls finish().
 */
internal fun BroadcastReceiver.inBackground(work: () -> Unit) {
    val pending = goAsync()
    Thread {
        try {
            work()
        } finally {
            pending.finish()
        }
    }.start()
}

/** Runs when an appointment's reminder (or traffic recheck) alarm goes off. */
class AppointmentAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(AppointmentReminders.EXTRA_EVENT_ID, -1L)
        val begin = intent.getLongExtra(AppointmentReminders.EXTRA_BEGIN, -1L)
        val refreshOnly = intent.getBooleanExtra(AppointmentReminders.EXTRA_REFRESH, false)
        inBackground {
            if (!refreshOnly) {
                // Check the calendar one last time: the appointment may have been moved or deleted since
                // the alarm was set. Only remind if it's still there at the same time.
                val now = System.currentTimeMillis()
                val still = CalendarRepository
                    .appointments(context, SettingsStore.calendarIds(context), now - 60 * 60_000L, now + 24 * 60 * 60_000L)
                    .firstOrNull { it.eventId == eventId && it.begin == begin }
                if (still != null) AppointmentReminders.notifyOnce(context, still)
            }
            // Rebuild the schedule. For a recheck alarm this looks up the drive again with fresh
            // traffic, and moves (or immediately sends) the reminder if leaving time got earlier.
            AppointmentReminders.reschedule(context)
        }
    }
}
