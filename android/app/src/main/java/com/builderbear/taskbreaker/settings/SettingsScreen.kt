package com.builderbear.taskbreaker.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.builderbear.taskbreaker.calendar.AppointmentReminders
import com.builderbear.taskbreaker.calendar.CalendarRepository
import com.builderbear.taskbreaker.calendar.CalendarSyncJob
import com.builderbear.taskbreaker.calendar.DeviceCalendar
import com.builderbear.taskbreaker.calendar.DriveTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.builderbear.taskbreaker.ai.ClaudeClient
import com.builderbear.taskbreaker.ui.FieldShape
import com.builderbear.taskbreaker.ui.Panel
import com.builderbear.taskbreaker.ui.PrimaryButton
import com.builderbear.taskbreaker.ui.ScreenTitle
import com.builderbear.taskbreaker.ui.SecondaryButton
import com.builderbear.taskbreaker.ui.SectionLabel
import com.builderbear.taskbreaker.ui.TbColors
import com.builderbear.taskbreaker.ui.tbFieldColors

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(SettingsStore.apiKey(context)) }
    var draft by remember { mutableStateOf(saved) }
    var show by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings", "Choose calendars for reminders, set up drive times, and set up the AI for Break it down.")

        CalendarPicker()

        DriveSettings()

        Panel {
            SectionLabel("Claude API key")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show", color = TbColors.Lime) }
                },
                colors = tbFieldColors(),
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Save", onClick = {
                    SettingsStore.setApiKey(context, draft)
                    saved = draft.trim()
                    draft = saved
                })
                SecondaryButton("Remove key", onClick = {
                    SettingsStore.setApiKey(context, "")
                    saved = ""
                    draft = ""
                })
            }
            Text(
                if (saved.isBlank()) "No key saved. AI breakdown is off, but you can still add steps by hand."
                else "Key saved on this phone.",
                color = if (saved.isBlank()) TbColors.Hot else TbColors.Lime,
                fontSize = 15.sp,
            )
        }

        Panel {
            SectionLabel("What this does")
            Text(
                "When you tap Break it down or Still too big, the task text (and nothing else) is sent to " +
                    "Anthropic's Claude API using your key. Each request costs a small amount on your Anthropic account. " +
                    "Get a key from the Claude Console on Anthropic's website.",
                color = TbColors.Muted,
                fontSize = 15.sp,
            )
            Text(
                "The key is stored only in this app's private storage on this phone, and is left out of phone backups.",
                color = TbColors.Muted,
                fontSize = 15.sp,
            )
            Text("Model: ${ClaudeClient.MODEL}", color = TbColors.Faint, fontSize = 13.sp)
        }
    }
}

/**
 * Lists the phone's calendars with a checkbox each. Checked ones are watched for appointment reminders.
 * Changing a box rebuilds the reminder schedule straight away.
 */
@Composable
private fun CalendarPicker() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var calendars by remember { mutableStateOf(emptyList<DeviceCalendar>()) }
    var chosen by remember { mutableStateOf(SettingsStore.calendarIds(context)) }

    fun reload() {
        hasPermission = CalendarRepository.hasPermission(context)
        calendars = CalendarRepository.calendars(context)
        // Drop choices for calendars that no longer exist (e.g. an account was removed).
        val existing = calendars.map { it.id }.toSet()
        if (hasPermission && chosen.any { it !in existing }) {
            chosen = chosen.filter { it in existing }.toSet()
            SettingsStore.setCalendarIds(context, chosen)
        }
    }
    LifecycleResumeEffect(Unit) {
        reload()
        onPauseOrDispose { }
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }

    Panel {
        SectionLabel("Calendars to watch")
        if (!hasPermission) {
            Text("Allow calendar access to choose which calendars give you appointment reminders.", color = TbColors.Muted, fontSize = 15.sp)
            PrimaryButton("Allow calendar access", onClick = { askCalendar.launch(Manifest.permission.READ_CALENDAR) })
            return@Panel
        }
        if (calendars.isEmpty()) {
            Text("No calendars found on this phone. Check that your Google account is syncing its calendar.", color = TbColors.Hot, fontSize = 15.sp)
            return@Panel
        }
        calendars.forEach { cal ->
            val checked = cal.id in chosen
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { on ->
                        chosen = if (on) chosen + cal.id else chosen - cal.id
                        val newChoice = chosen
                        scope.launch(Dispatchers.IO) {
                            SettingsStore.setCalendarIds(context, newChoice)
                            AppointmentReminders.reschedule(context)
                            CalendarSyncJob.ensureScheduled(context)
                        }
                    })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null, // the whole row handles the tap, for a bigger target
                    colors = CheckboxDefaults.colors(
                        checkedColor = TbColors.Lime,
                        checkmarkColor = Color.Black,
                        uncheckedColor = TbColors.Muted,
                    ),
                )
                Box(Modifier.size(12.dp).background(Color(cal.color), CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(cal.name, color = TbColors.Ink)
                    if (cal.account.isNotBlank() && cal.account != cal.name) {
                        Text(cal.account, color = TbColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Text(
            if (chosen.isEmpty()) "None chosen, so there are no appointment reminders yet."
            else "Reminders come ${AppointmentReminders.LEAD_MINUTES} min before appointments in the checked calendars. " +
                "Your calendar is only read on this phone; nothing is sent anywhere.",
            color = if (chosen.isEmpty()) TbColors.Hot else TbColors.Muted,
            fontSize = 15.sp,
        )
    }
}

/**
 * Phase 2 settings: the Google Maps key, the work address drives start from when the phone's
 * location isn't available, and the optional "use my current location" permission.
 * Saving anything rebuilds the reminder schedule so the new drive times take effect.
 */
@Composable
private fun DriveSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedKey by remember { mutableStateOf(SettingsStore.mapsKey(context)) }
    var keyDraft by remember { mutableStateOf(savedKey) }
    var showKey by remember { mutableStateOf(false) }
    var savedWork by remember { mutableStateOf(SettingsStore.workAddress(context)) }
    var workDraft by remember { mutableStateOf(savedWork) }
    var hasLocation by remember { mutableStateOf(DriveTime.hasLocationPermission(context)) }
    LifecycleResumeEffect(Unit) {
        hasLocation = DriveTime.hasLocationPermission(context)
        onPauseOrDispose { }
    }
    // Settings changed: forget old drive answers (and errors), then rebuild reminders with the new settings.
    fun rebuild() {
        scope.launch(Dispatchers.IO) {
            DriveTime.clearCache(context)
            AppointmentReminders.reschedule(context)
        }
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = DriveTime.hasLocationPermission(context)
        rebuild()
    }

    Panel {
        SectionLabel("Drive time (Google Maps)")
        Text(
            "For appointments with an address, Taskbreaker works out the drive with live traffic and " +
                "reminds you ${AppointmentReminders.LEAVE_BUFFER_MINUTES} min before you need to leave.",
            color = TbColors.Muted,
            fontSize = 15.sp,
        )

        Text("Google Maps API key", color = TbColors.Ink)
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            placeholder = { Text("AIza…") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show", color = TbColors.Lime) }
            },
            colors = tbFieldColors(),
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Save key", onClick = {
                SettingsStore.setMapsKey(context, keyDraft)
                savedKey = keyDraft.trim()
                keyDraft = savedKey
                rebuild()
            })
            SecondaryButton("Remove", onClick = {
                SettingsStore.setMapsKey(context, "")
                savedKey = ""
                keyDraft = ""
                rebuild()
            })
        }

        Text("Work address", color = TbColors.Ink)
        Text(
            "Drives start here when your phone's location isn't available (which is most of the time when the app is closed).",
            color = TbColors.Muted,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        OutlinedTextField(
            value = workDraft,
            onValueChange = { workDraft = it },
            placeholder = { Text("e.g. 123 Main St, Springfield") },
            colors = tbFieldColors(),
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton("Save work address", onClick = {
            SettingsStore.setWorkAddress(context, workDraft)
            savedWork = workDraft.trim()
            workDraft = savedWork
            rebuild()
        })

        if (hasLocation) {
            Text("Using your current location when it's available.", color = TbColors.Lime, fontSize = 15.sp)
        } else {
            SecondaryButton("Use my current location", onClick = {
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            })
        }

        // Status: what works and what's missing, in one line.
        val status = when {
            savedKey.isBlank() -> "No Maps key saved, so reminders use the plain ${AppointmentReminders.LEAD_MINUTES}-minute rule."
            savedWork.isBlank() && !hasLocation -> "Add your work address (or allow location) so drives have a starting point."
            else -> null
        }
        if (status != null) {
            Text(status, color = TbColors.Hot, fontSize = 15.sp)
        } else {
            // Set up isn't the same as working: the Upcoming page shows whether Google accepted the key.
            Text("Drive times are set up. The Upcoming page shows each drive, or what went wrong.", color = TbColors.Lime, fontSize = 15.sp)
        }
        Text(
            "To work out a drive, the appointment's address and your starting point (current location or work " +
                "address) are sent to Google with your key. Requests count toward your Google Cloud account.",
            color = TbColors.Faint,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}
