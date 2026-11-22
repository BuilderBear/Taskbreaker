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

    /** Re-reads the calendar and rebuilds the reminder alarms. Reading runs off the main thread. */
    fun refresh() {
        watchCalendarIfAllowed()
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val ids = SettingsStore.calendarIds(context)
                val status = AppointmentReminders.reschedule(context)
                if (CalendarRepository.hasPermission(context)) CalendarSyncJob.ensureScheduled(context)
                UpcomingState(
                    loaded = true,
                    hasPermission = CalendarRepository.hasPermission(context),
                    calendarsChosen = ids.isNotEmpty(),
                    canNotify = TimerNotifications.canNotify(context),
                    exactAlarms = status.exactAlarms,
                    // Start an hour back so an appointment that's under way still shows.
                    appointments = CalendarRepository.appointments(context, ids, now - 60 * 60_000L, now + 7L * 24 * 60 * 60_000L)
                        .filter { it.end > now },
                )
            }
            _state.value = newState
        }
    }
}
