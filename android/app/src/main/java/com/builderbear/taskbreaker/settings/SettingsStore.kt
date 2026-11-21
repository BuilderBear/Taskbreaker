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
}
