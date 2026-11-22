package com.builderbear.taskbreaker.calendar

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.builderbear.taskbreaker.timer.TimerAlarm

/**
 * Keeps appointment reminders in step with the calendar while the app is closed, using Android's
 * built-in JobScheduler (no extra library). Two jobs:
 *
 *  - CONTENT: Android runs it whenever anything in the calendar changes (an event added, moved or
 *    deleted, here or synced from Google). Android only runs this kind of job once, so it re-arms itself.
 *  - PERIODIC: every few hours, as a safety net and to reach appointments beyond the 3-day alarm window.
 */
class CalendarSyncJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        AppointmentReminders.reschedule(this)
        if (params.jobId == CONTENT_JOB) ensureScheduled(this)
        return false // all done; nothing still running in the background
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val CONTENT_JOB = 2001
        private const val PERIODIC_JOB = 2002
        private const val PERIOD_MS = 6L * 60 * 60 * 1000

        /** Make sure both jobs are set up. Safe to call as often as you like; does nothing without calendar access. */
        fun ensureScheduled(context: Context) {
            if (!CalendarRepository.hasPermission(context)) return
            try {
                armContentTrigger(context)
            } catch (e: SecurityException) {
                return // access was just taken away; the periodic job isn't useful without it either
            }
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(PERIODIC_JOB) == null) {
                scheduler.schedule(
                    JobInfo.Builder(PERIODIC_JOB, ComponentName(context, CalendarSyncJob::class.java))
                        .setPeriodic(PERIOD_MS)
                        .setPersisted(true) // survives a restart
                        .build(),
                )
            }
        }

        private fun armContentTrigger(context: Context) {
            val job = JobInfo.Builder(CONTENT_JOB, ComponentName(context, CalendarSyncJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS),
                )
                // Wait for changes to settle (a sync often changes many events at once), but no more than a minute.
                .setTriggerContentUpdateDelay(5_000)
                .setTriggerContentMaxDelay(60_000)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }
    }
}

/**
 * The phone forgets every alarm when it restarts (and their times can be off after the clock or time
 * zone changes), so this puts them back: the running focus timer and the appointment reminders.
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerAlarm.restore(context)
        AppointmentReminders.reschedule(context)
        CalendarSyncJob.ensureScheduled(context)
    }
}
