package com.builderbear.taskbreaker.breakdown

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.builderbear.taskbreaker.ai.ClaudeClient
import com.builderbear.taskbreaker.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Step(val text: String, val minutes: Int, val done: Boolean = false)

data class BreakdownState(
    val task: String = "",
    val steps: List<Step> = emptyList(),
    val example: Boolean = false, // true while the built-in example list is showing
    val level: Int = 3,           // "How stuck?" slider, 1..5
    val busy: Boolean = false,    // waiting for Claude
    val status: String = "",      // message under the button
) {
    /** Index of the first step not done yet, or -1 if all are done. */
    val current: Int get() = steps.indexOfFirst { !it.done }
}

private val EXAMPLE = BreakdownState(
    task = "Clean out the garage",
    example = true,
    steps = listOf(
        Step("Put on shoes and open the garage door", 2, done = true),
        Step("Grab three bags: keep, donate, trash", 3),
        Step("Clear only the shelf nearest the door", 10),
        Step("Carry the trash bag to the bin", 2),
        Step("Load the donate bag into the car", 3),
    ),
)

class BreakdownViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("breakdown", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BreakdownState> = _state.asStateFlow()

    // ---------- saving and loading (as JSON text in the app's private storage) ----------

    private fun load(): BreakdownState {
        val raw = prefs.getString("data", null) ?: return EXAMPLE
        return try {
            val obj = JSONObject(raw)
            val arr = obj.getJSONArray("steps")
            BreakdownState(
                task = obj.optString("task"),
                example = obj.optBoolean("example"),
                steps = (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    Step(s.getString("text"), s.optInt("min", 5), s.optBoolean("done"))
                },
            )
        } catch (e: Exception) {
            EXAMPLE // Saved data was unreadable. Start fresh rather than crash.
        }
    }

    private fun update(newState: BreakdownState) {
        _state.value = newState
        val arr = JSONArray()
        newState.steps.forEach { arr.put(JSONObject().put("text", it.text).put("min", it.minutes).put("done", it.done)) }
        val obj = JSONObject().put("task", newState.task).put("example", newState.example).put("steps", arr)
        prefs.edit().putString("data", obj.toString()).apply()
    }

    // ---------- actions the screen can call ----------

    fun setLevel(level: Int) = update(_state.value.copy(level = level.coerceIn(1, 5)))

    fun toggleDone(i: Int) = editSteps { it[i] = it[i].copy(done = !it[i].done) }

    fun markDone(i: Int) = editSteps { it[i] = it[i].copy(done = true) }

    fun delete(i: Int) = editSteps { it.removeAt(i) }

    fun isOpen(i: Int): Boolean = _state.value.steps.getOrNull(i)?.done == false

    fun addStep(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val s = _state.value
        // Adding your own step replaces the example list.
        val base = if (s.example) s.copy(task = "", steps = emptyList(), example = false) else s
        update(base.copy(steps = base.steps + Step(t, 5)))
    }

    fun clearAll() = update(_state.value.copy(task = "", steps = emptyList(), example = false, status = ""))

    private fun editSteps(change: (MutableList<Step>) -> Unit) {
        val s = _state.value
        val list = s.steps.toMutableList()
        change(list)
        update(s.copy(steps = list, example = false))
    }

    // ---------- AI ----------

    fun breakDown(task: String) {
        val t = task.trim()
        if (t.isEmpty()) return setStatus("Type the task first. Messy wording is fine.")
        askClaude(t, _state.value.level, parent = null) { steps ->
            update(_state.value.copy(task = t, steps = steps, example = false))
        }
    }

    /** "Still too big": replace one step with smaller ones, at one notch more stuck. */
    fun split(i: Int) {
        val s = _state.value
        val step = s.steps.getOrNull(i) ?: return
        askClaude(step.text, (s.level + 1).coerceAtMost(5), parent = s.task.ifBlank { null }) { steps ->
            val list = _state.value.steps.toMutableList()
            if (i < list.size) {
                list.removeAt(i)
                list.addAll(i, steps)
            }
            update(_state.value.copy(steps = list, example = false))
        }
    }

    private fun askClaude(text: String, level: Int, parent: String?, onSteps: (List<Step>) -> Unit) {
        if (_state.value.busy) return
        val key = SettingsStore.apiKey(context)
        if (key.isBlank()) return setStatus("Add your Claude API key in Settings to use AI. You can still add steps by hand.")

        update(_state.value.copy(busy = true, status = "Breaking it into small pieces…"))
        viewModelScope.launch {
            // Dispatchers.IO = a background thread meant for network and disk work.
            val result = withContext(Dispatchers.IO) { ClaudeClient.breakDown(key, text, level, parent) }
            result.onSuccess { steps ->
                onSteps(steps)
                update(_state.value.copy(busy = false, status = ""))
            }.onFailure { error ->
                update(_state.value.copy(busy = false, status = error.message ?: "That didn't work."))
            }
        }
    }

    private fun setStatus(message: String) = update(_state.value.copy(status = message))
}
