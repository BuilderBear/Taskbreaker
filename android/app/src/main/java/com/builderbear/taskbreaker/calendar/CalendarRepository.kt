package com.builderbear.taskbreaker.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** One calendar on the phone (e.g. "Personal", "Work", "Holidays"). */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int)

/** One occurrence of an event. A weekly meeting shows up once per week, each with its own begin time. */
data class Appointment(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val location: String,
    val begin: Long, // milliseconds since 1970, like System.currentTimeMillis()
    val end: Long,
    val allDay: Boolean,
    val color: Int,
) {
    /** Identifies this exact occurrence (event + start time). */
    val key: String get() = "$eventId@$begin"
}

/**
 * Reads the Google Calendar data that Android already keeps synced on the phone, through Android's
 * Calendar Provider (a built-in database every app can read once the owner allows calendar access).
 * Nothing is sent anywhere, and there's no Google sign-in: Google's own sync keeps this copy up to date.
 */
object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Every calendar on the phone, for the "which calendars to watch" picker. */
    fun calendars(context: Context): List<DeviceCalendar> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val sort = "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}"
        val result = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, sort)?.use { c ->
            while (c.moveToNext()) {
                result += DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty().ifBlank { "Untitled calendar" },
                    account = c.getString(2).orEmpty(),
                    color = c.getInt(3),
                )
            }
        }
        return result
    }

    /**
     * Appointments in the chosen calendars between [from] and [to], earliest first.
     * Skips events that were cancelled or that the owner declined.
     */
    fun appointments(context: Context, calendarIds: Set<Long>, from: Long, to: Long): List<Appointment> {
        if (!hasPermission(context) || calendarIds.isEmpty()) return emptyList()
        // The "Instances" table expands repeating events into individual occurrences for a time range.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
        )
        // The IDs are numbers we stored ourselves, so building the list into the query text is safe.
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",")})"
        val result = mutableListOf<Appointment>()
        context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val declined = c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                val cancelled = c.getInt(9) == CalendarContract.Events.STATUS_CANCELED
                if (declined || cancelled) continue
                result += Appointment(
                    eventId = c.getLong(0),
                    calendarId = c.getLong(1),
                    title = c.getString(2).orEmpty().ifBlank { "(No title)" },
                    location = c.getString(3).orEmpty().trim(),
                    begin = c.getLong(4),
                    end = c.getLong(5),
                    allDay = c.getInt(6) == 1,
                    color = c.getInt(7),
                )
            }
        }
        return result
    }
}
