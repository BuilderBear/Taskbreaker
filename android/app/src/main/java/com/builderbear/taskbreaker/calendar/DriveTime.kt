package com.builderbear.taskbreaker.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.builderbear.taskbreaker.settings.SettingsStore
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.ceil

/** A drive-time estimate for one appointment. [minutes] is null when it couldn't be worked out. */
data class DriveInfo(
    val minutes: Int?,
    val error: String?,   // why the last lookup failed, in plain words (null if it worked)
    val fetchedAt: Long,  // when we last asked Google
    val from: String,     // "current location" or "work"
)

/**
 * Works out how long it takes to drive to an appointment, with traffic, using Google's Routes API.
 *
 * What's sent to Google: the appointment's address and the starting point (the phone's location
 * if available, otherwise the work address from Settings), plus the Maps key. The owner agreed to this.
 *
 * Answers are remembered (cached) so Google isn't asked more than needed: every 2 hours for
 * appointments later in the day, every 10 minutes once it's getting close to leaving time.
 * Every function here uses the network, so call them off the main thread.
 */
object DriveTime {

    /** Only look up drives for appointments starting within this long. Keeps requests (and cost) down. */
    const val LOOKAHEAD_MS = 12L * 60 * 60 * 1000

    private const val PREFS = "drive_times"
    private const val TIMEOUT_MS = 8_000
    private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"

    fun isConfigured(context: Context): Boolean = SettingsStore.mapsKey(context).isNotBlank()

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The last saved estimate, without asking Google. Safe to call anywhere (no network). */
    fun cached(context: Context, appointment: Appointment): DriveInfo? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(cacheKey(appointment), null) ?: return null
        return try {
            val o = JSONObject(raw)
            DriveInfo(
                minutes = if (o.has("minutes")) o.getInt("minutes") else null,
                error = if (o.has("error")) o.getString("error") else null,
                fetchedAt = o.getLong("fetchedAt"),
                from = o.optString("from"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A drive estimate for [appointment], asking Google when the saved one is missing or too old.
     * Returns null when drive time doesn't apply (no address, no Maps key, all-day, or more than
     * 12 hours away; the reminder then uses the plain 20-minute rule).
     */
    fun estimate(context: Context, appointment: Appointment, now: Long = System.currentTimeMillis()): DriveInfo? {
        if (appointment.location.isBlank() || appointment.allDay || !isConfigured(context)) return null
        val previous = cached(context, appointment)
        if (appointment.begin <= now || appointment.begin - now > LOOKAHEAD_MS) return previous

        // Close to leaving time (within the last drive estimate + 2 hours), refresh often, because
        // traffic changes. Earlier in the day, every 2 hours is plenty.
        // A lookup that failed is retried after 10 minutes too, rather than waiting 2 hours.
        val closeWindow = ((previous?.minutes ?: 30) + 120) * 60_000L
        val failed = previous?.error != null
        val maxAge = if (failed || appointment.begin - now < closeWindow) 10 * 60_000L else 2 * 60 * 60_000L
        if (previous != null && now - previous.fetchedAt < maxAge) return previous

        val fresh = fetch(context, appointment, now, previous?.minutes)
        // If the lookup failed, keep using the last good number (better than nothing) but record the
        // problem so the Upcoming page can show it. We'll try again after maxAge.
        val result = if (fresh.minutes == null && previous?.minutes != null) {
            previous.copy(error = fresh.error, fetchedAt = now)
        } else {
            fresh
        }
        save(context, appointment, result)
        return result
    }

    /**
     * Forget every saved drive time. Called when the Maps key, work address or location access
     * changes, so the next check uses the new settings instead of an old answer (or an old error).
     */
    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun cacheKey(a: Appointment) = "${a.key}|${a.location}" // a changed address means a new lookup

    private fun save(context: Context, appointment: Appointment, info: DriveInfo) {
        val o = JSONObject().put("fetchedAt", info.fetchedAt).put("from", info.from)
        info.minutes?.let { o.put("minutes", it) }
        info.error?.let { o.put("error", it) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(cacheKey(appointment), o.toString())
        // Tidy up entries for appointments that are long over (the begin time is inside the key).
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60_000L
        prefs.all.keys.filter { key -> (key.substringAfter('@').substringBefore('|').toLongOrNull() ?: 0L) < dayAgo }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /** One request to Google's Routes API. Never throws: problems come back as [DriveInfo.error]. */
    private fun fetch(context: Context, appointment: Appointment, now: Long, guessMinutes: Int?): DriveInfo {
        val origin = origin(context)
            ?: return DriveInfo(null, "No starting point. Add your work address in Settings (or allow location).", now, "")

        val body = JSONObject()
            .put("origin", origin.first)
            .put("destination", JSONObject().put("address", appointment.location))
            .put("travelMode", "DRIVE")
            .put("routingPreference", "TRAFFIC_AWARE")
        // Ask for the traffic expected when you'd actually leave, if that's still ahead.
        val leaveGuess = appointment.begin - ((guessMinutes ?: 30) + AppointmentReminders.LEAVE_BUFFER_MINUTES) * 60_000L
        if (leaveGuess > now + 2 * 60_000L) body.put("departureTime", Instant.ofEpochMilli(leaveGuess).toString())

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", SettingsStore.mapsKey(context))
                // Only ask for the one number we need (a smaller, cheaper answer).
                setRequestProperty("X-Goog-FieldMask", "routes.duration")
                // The key is locked to this app. Google checks these two headers to confirm the
                // request really comes from Taskbreaker (package name + signing certificate).
                setRequestProperty("X-Android-Package", context.packageName)
                signingCertSha1(context)?.let { setRequestProperty("X-Android-Cert", it) }
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            if (code != 200) {
                val detail = try {
                    JSONObject(connection.errorStream?.bufferedReader()?.readText().orEmpty())
                        .optJSONObject("error")?.optString("message").orEmpty()
                } catch (e: Exception) {
                    ""
                }
                // Google reports a bad key as 400 (not 403), so check its message before blaming the address.
                val keyProblem = code == 401 || code == 403 || detail.contains("API key", ignoreCase = true)
                val message = when {
                    keyProblem -> "Google refused the Maps key. Check it in Settings, that the Routes API is on, " +
                        "and that the key allows this app."
                    code == 400 -> "Google couldn't use this address. Check the appointment's location."
                    code == 429 -> "Too many drive-time requests right now. Will try again soon."
                    else -> "Google Maps error ($code). Will try again soon."
                }
                return DriveInfo(null, if (detail.isNotBlank()) "$message (Google says: $detail)" else message, now, origin.second)
            }

            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val route = json.optJSONArray("routes")?.optJSONObject(0)
                ?: return DriveInfo(null, "Google found no driving route to this address.", now, origin.second)
            // Duration comes back as text like "1234s" (seconds).
            val seconds = route.optString("duration").removeSuffix("s").toDoubleOrNull()
                ?: return DriveInfo(null, "Google sent back an answer we couldn't read.", now, origin.second)
            DriveInfo(ceil(seconds / 60.0).toInt(), null, now, origin.second)
        } catch (e: IOException) {
            DriveInfo(null, "Couldn't reach Google Maps (no internet?). Will try again soon.", now, origin.second)
        } catch (e: Exception) {
            DriveInfo(null, "Drive time lookup failed. Will try again soon.", now, origin.second)
        } finally {
            connection?.disconnect()
        }
    }

    /** Where the drive starts: the phone's recent location if we may use it, else the work address. */
    private fun origin(context: Context): Pair<JSONObject, String>? {
        currentLocation(context)?.let { loc ->
            val latLng = JSONObject().put("latitude", loc.latitude).put("longitude", loc.longitude)
            return JSONObject().put("location", JSONObject().put("latLng", latLng)) to "current location"
        }
        val work = SettingsStore.workAddress(context)
        if (work.isNotBlank()) return JSONObject().put("address", work) to "work"
        return null
    }

    /**
     * The phone's most recent known position, if it's under 30 minutes old. This doesn't switch on
     * GPS; it reuses a position Android already has. In the background Android usually won't share
     * it (we don't ask for "all the time" location), so background lookups start from your work address.
     */
    @SuppressLint("MissingPermission") // checked by hasLocationPermission() first
    private fun currentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return try {
            val manager = context.getSystemService(LocationManager::class.java)
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .filter { System.currentTimeMillis() - it.time < 30 * 60_000L }
                .maxByOrNull { it.time }
        } catch (e: Exception) {
            null
        }
    }

    /** SHA-1 fingerprint of the certificate this app was signed with, as Google expects it (hex, no colons). */
    private fun signingCertSha1(context: Context): String? = try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        signatures?.firstOrNull()?.toByteArray()?.let { bytes ->
            MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
        }
    } catch (e: Exception) {
        null
    }
}
