package com.builderbear.taskbreaker.timer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Everything the timer screen shows, in one snapshot.
 *
 * Like the web version, a running timer stores its finish time (endAtMillis) rather than counting
 * down tick by tick, so it stays accurate even if the phone pauses the app for a while.
 */
data class TimerState(
    val totalSec: Int = 25 * 60,
    val endAtMillis: Long = 0L,         // finish time while running; 0 when stopped or paused
    val remainingMs: Long = 25 * 60_000L, // time left while stopped or paused
    val label: String = "",             // the step being worked on, if any
    val linkedStep: Int? = null,        // index of that step in the Breakdown list
    val finished: Boolean = false,
    val alarmExact: Boolean = true,
) {
    val running: Boolean get() = endAtMillis > 0L

    fun remainingAt(nowMillis: Long): Long = if (running) max(0L, endAtMillis - nowMillis) else remainingMs
}

/**
 * A ViewModel holds a screen's data and logic, and survives screen rotation.
 * The timer is also saved to storage so it survives the app being closed.
 */
class TimerViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("timer", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private fun load(): TimerState {
        val total = prefs.getInt("total", 25 * 60)
        val endAt = prefs.getLong("endAt", 0L)
        val remaining = prefs.getLong("remainingMs", total * 1000L)
        val label = prefs.getString("label", "").orEmpty()
        val now = System.currentTimeMillis()
        return if (endAt in 1..now) {
            // The timer ran out while the app was closed. The alarm already showed the notification.
            TimerState(totalSec = total, remainingMs = 0L, label = label, finished = true)
        } else {
            TimerState(totalSec = total, endAtMillis = endAt, remainingMs = remaining, label = label)
        }
    }

    private fun update(newState: TimerState) {
        _state.value = newState
        prefs.edit()
            .putInt("total", newState.totalSec)
            .putLong("endAt", newState.endAtMillis)
            .putLong("remainingMs", newState.remainingMs)
            .putString("label", newState.label)
            .apply()
    }

    fun start() {
        val s = _state.value
        if (s.running) return
        val remaining = if (s.remainingMs <= 0L) s.totalSec * 1000L else s.remainingMs
        val endAt = System.currentTimeMillis() + remaining
        val exact = TimerAlarm.schedule(context, endAt, s.label)
        update(s.copy(endAtMillis = endAt, remainingMs = remaining, finished = false, alarmExact = exact))
    }

    fun pause() {
        val s = _state.value
        if (!s.running) return
        TimerAlarm.cancel(context)
        update(s.copy(endAtMillis = 0L, remainingMs = s.remainingAt(System.currentTimeMillis())))
    }

    /** Sets a new length. Stops any running countdown. */
    fun setTimer(minutes: Int, label: String = "", linkedStep: Int? = null) {
        TimerAlarm.cancel(context)
        val total = minutes.coerceIn(1, 240) * 60
        update(TimerState(totalSec = total, remainingMs = total * 1000L, label = label, linkedStep = linkedStep))
    }

    fun reset() {
        val s = _state.value
        setTimer(s.totalSec / 60, s.label, s.linkedStep)
    }

    /** Called a few times a second by the screen while running, to notice when time is up. */
    fun onTick(nowMillis: Long) {
        val s = _state.value
        if (s.running && nowMillis >= s.endAtMillis) {
            // The system alarm plays the sound and vibration; here we only update the screen.
            update(s.copy(endAtMillis = 0L, remainingMs = 0L, finished = true))
        }
    }

    /** Forget the link to a Breakdown step (used after that step is marked done). */
    fun clearLink() {
        setTimer(_state.value.totalSec / 60)
    }
}
