package com.builderbear.taskbreaker.settings

import android.content.Context

/**
 * Stores the Claude API key on the phone, in the app's private storage (other apps can't read it).
 * Typing the key into the app, rather than building it into the code, means the key is never
 * in the GitHub repo or inside the installable app file.
 */
object SettingsStore {
    private const val FILE = "settings"
    private const val KEY_API = "claude_api_key"

    fun apiKey(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_API, "").orEmpty()

    fun setApiKey(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_API, key.trim()).apply()
    }

    private const val KEY_CALENDARS = "watched_calendar_ids"

    /** IDs of the phone calendars the owner chose to get appointment reminders from. Empty = none chosen yet. */
    fun calendarIds(context: Context): Set<Long> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(KEY_CALENDARS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun setCalendarIds(context: Context, ids: Set<Long>) {
        // commit() rather than apply(): reminders are rescheduled right after this and must see the new choice.
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_CALENDARS, ids.map { it.toString() }.toSet())
            .commit()
    }
}
