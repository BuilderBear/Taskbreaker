package com.builderbear.taskbreaker.calendar

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.builderbear.taskbreaker.settings.SettingsStore
import com.builderbear.taskbreaker.timer.TimerNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpcomingState(
    val loaded: Boolean = false,
    val hasPermission: Boolean = false,
    val calendarsChosen: Boolean = false,
    val canNotify: Boolean = true,
    val exactAlarms: Boolean = true,
    val appointments: List<Appointment> = emptyList(),
    val drives: Map<String, DriveInfo> = emptyMap(), // by Appointment.key
    val mapsConfigured: Boolean = false,
    val hasStartPoint: Boolean = false,
    val updatingDrives: Boolean = false,
)

/** Shows the next 7 days of appointments, and refreshes by itself when the calendar changes. */
class UpcomingViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()

    private val _state = MutableStateFlow(UpcomingState())
    val state: StateFlow<UpcomingState> = _state.asStateFlow()

    // Told by Android whenever calendar data changes (e.g. Google syncs a new event) while the app is open.
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    private var watching = false

    init {
        refresh()
    }

    override fun onCleared() {
        if (watching) context.contentResolver.unregisterContentObserver(observer)
    }

    /**
     * Start listening for calendar changes, but only once calendar access is allowed: newer Android
     * refuses (and crashes the app) if we try before that.
     */
    private fun watchCalendarIfAllowed() {
        if (watching || !CalendarRepository.hasPermission(context)) return
        try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            watching = true
        } catch (e: SecurityException) {
            // Access was taken away at the same moment. We'll try again on the next refresh.
        }
    }

    /**
     * Re-reads the calendar and rebuilds the reminder alarms, off the main thread, in two steps:
     * first show the list straight away using saved drive times, then rebuild the schedule (which may
     * ask Google for fresh drive times, taking a few seconds) and update the page again.
     */
    fun refresh() {
        watchCalendarIfAllowed()
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { read(exactAlarms = _state.value.exactAlarms, updating = true) }
            val status = withContext(Dispatchers.IO) {
                val s = AppointmentReminders.reschedule(context)
                CalendarSyncJob.ensureScheduled(context)
                s
            }
            _state.value = withContext(Dispatchers.IO) { read(exactAlarms = status.exactAlarms, updating = false) }
        }
    }

    private fun read(exactAlarms: Boolean, updating: Boolean): UpcomingState {
        val now = System.currentTimeMillis()
        val ids = SettingsStore.calendarIds(context)
        // Start an hour back so an appointment that's under way still shows.
        val appointments = CalendarRepository.appointments(context, ids, now - 60 * 60_000L, now + 7L * 24 * 60 * 60_000L)
            .filter { it.end > now }
        return UpcomingState(
            loaded = true,
            hasPermission = CalendarRepository.hasPermission(context),
            calendarsChosen = ids.isNotEmpty(),
            canNotify = TimerNotifications.canNotify(context),
            exactAlarms = exactAlarms,
            appointments = appointments,
            drives = appointments.mapNotNull { a -> DriveTime.cached(context, a)?.let { a.key to it } }.toMap(),
            mapsConfigured = DriveTime.isConfigured(context),
            hasStartPoint = SettingsStore.workAddress(context).isNotBlank() || DriveTime.hasLocationPermission(context),
            updatingDrives = updating && DriveTime.isConfigured(context),
        )
    }
}
