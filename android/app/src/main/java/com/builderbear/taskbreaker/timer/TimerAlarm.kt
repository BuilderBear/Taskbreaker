package com.builderbear.taskbreaker.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.builderbear.taskbreaker.MainActivity
import com.builderbear.taskbreaker.R

/**
 * Why this exists: a countdown running inside the app stops when Android puts the app to sleep.
 * So when the timer starts we also hand the finish time to the system's AlarmManager, which wakes
 * the phone and fires TimerAlarmReceiver at the right moment, even if the app was closed.
 * (The same approach will be the foundation for medication reminders.)
 */
object TimerAlarm {
    private const val REQUEST_ALARM = 100
    private const val REQUEST_SHOW = 101

    private fun alarmIntent(context: Context, label: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            Intent(context, TimerAlarmReceiver::class.java).putExtra(EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Schedules the "time's up" alert. Returns true if it's an exact alarm, false if Android only
     * allowed an inexact one (it may then arrive a few minutes late, and the screen says so).
     */
    fun schedule(context: Context, atMillis: Long, label: String): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context, label)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            try {
                // setAlarmClock is the most dependable kind of alarm: Android won't delay it to save battery.
                val show = PendingIntent.getActivity(
                    context, REQUEST_SHOW, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), pending)
                return true
            } catch (e: SecurityException) {
                // Permission was revoked between the check and the call. Fall through to the inexact alarm.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        return false
    }

    /**
     * After a phone restart: if a timer was running and hasn't finished, set its alarm again.
     * Reads the same saved values TimerViewModel writes ("timer" file, keys endAt and label).
     */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
        val endAt = prefs.getLong("endAt", 0L)
        if (endAt > System.currentTimeMillis()) schedule(context, endAt, prefs.getString("label", "").orEmpty())
    }

    fun cancel(context: Context) {
        // PendingIntents match on their target, not their extras, so an empty label finds the scheduled one.
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, ""))
    }

    const val EXTRA_LABEL = "label"
}

/** Runs when the alarm goes off. Its only job is to show the "time's up" notification. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerNotifications.showTimeUp(context, intent.getStringExtra(TimerAlarm.EXTRA_LABEL).orEmpty())
    }
}

object TimerNotifications {
    private const val CHANNEL_ID = "timer_done"
    private const val NOTIFICATION_ID = 1

    /** Android groups notifications into "channels" the user can tune in Settings. Safe to call repeatedly. */
    fun createChannel(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(CHANNEL_ID, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts you when a focus timer runs out."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 300)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // checked by canNotify() just above the call
    fun showTimeUp(context: Context, label: String) {
        createChannel(context)
        if (!canNotify(context)) return // The timer screen warns about this, so it isn't silent.
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time's up")
            .setContentText(if (label.isBlank()) "Nice work." else label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
