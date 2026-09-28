package com.smartstorage.cleaner.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.compose.runtime.staticCompositionLocalOf
import com.smartstorage.cleaner.MainActivity
import com.smartstorage.cleaner.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Weekly Smart Clean reminder: opt-in, a weekly WorkManager slot whose text comes from the last
 * completed scan (read when it fires, so it's always the latest). There's no silent background scan —
 * the screen says so — and nothing leaves the device. Mirrors WeeklyCleanReminder.swift.
 */
class WeeklyCleanReminder(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("keepspace", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(val enabled: Boolean, val schedule: WeeklyCleanSchedule, val lastScan: Long?, val potentialBytes: Long?)

    /** Whether the system allows us to post (always true before Android 13). */
    fun canNotify(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Call after the permission question is answered. Stays off if notifications aren't allowed. */
    fun setEnabled(on: Boolean) {
        val enable = on && canNotify()
        prefs.edit { putBoolean(KEY_ENABLED, enable) }
        if (enable) schedule() else WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        refresh()
    }

    fun setSchedule(new: WeeklyCleanSchedule) {
        prefs.edit { putInt(KEY_WEEKDAY, new.weekday); putInt(KEY_HOUR, new.hour); putInt(KEY_MINUTE, new.minute) }
        if (_state.value.enabled) schedule()
        refresh()
    }

    /** Called after every completed scan so the next reminder quotes the latest result. */
    fun recordScan(potentialBytes: Long, at: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_BYTES, potentialBytes); putLong(KEY_LAST_SCAN, at) }
        refresh()
    }

    /** Posts the reminder right away, so it can be seen without waiting for the weekly slot. */
    fun sendTest() = notifyNow(app)

    private fun schedule() {
        val delay = _state.value.schedule.nextFire(System.currentTimeMillis()) - System.currentTimeMillis()
        val request = PeriodicWorkRequestBuilder<Worker>(7, TimeUnit.DAYS).setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        // UPDATE keeps one slot and moves it when the person changes the day or time.
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun refresh() { _state.value = read() }

    private fun read() = State(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        schedule = WeeklyCleanSchedule(prefs.getInt(KEY_WEEKDAY, 7), prefs.getInt(KEY_HOUR, 10), prefs.getInt(KEY_MINUTE, 0)),
        lastScan = if (prefs.contains(KEY_LAST_SCAN)) prefs.getLong(KEY_LAST_SCAN, 0) else null,
        potentialBytes = if (prefs.contains(KEY_BYTES)) prefs.getLong(KEY_BYTES, 0) else null,
    )

    /** Runs weekly; posts the reminder if it is still on and allowed. */
    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val prefs = applicationContext.getSharedPreferences("keepspace", Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_ENABLED, false)) notifyNow(applicationContext)
            return Result.success()
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_CLEANUP_PLAN = "cleanupPlan"
        private const val WORK_NAME = "weeklySmartClean"
        private const val CHANNEL_ID = "weekly_smart_clean"
        private const val NOTIFICATION_ID = 1001
        private const val KEY_ENABLED = "weeklyClean.enabled"
        private const val KEY_WEEKDAY = "weeklyClean.weekday"
        private const val KEY_HOUR = "weeklyClean.hour"
        private const val KEY_MINUTE = "weeklyClean.minute"
        private const val KEY_LAST_SCAN = "weeklyClean.lastScan"
        private const val KEY_BYTES = "weeklyClean.potentialBytes"

        fun notifyNow(context: Context) {
            val prefs = context.getSharedPreferences("keepspace", Context.MODE_PRIVATE)
            val lastScan = if (prefs.contains(KEY_LAST_SCAN)) prefs.getLong(KEY_LAST_SCAN, 0) else null
            val bytes = if (prefs.contains(KEY_BYTES)) prefs.getLong(KEY_BYTES, 0) else null
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Weekly Smart Clean", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A weekly reminder to review safe cleanup suggestions"
                },
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_CLEANUP_PLAN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_keepspace)
                .setContentTitle(WeeklyCleanMessage.TITLE)
                .setContentText(WeeklyCleanMessage.body(bytes, lastScan))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}

val LocalWeeklyReminder = staticCompositionLocalOf<WeeklyCleanReminder> { error("WeeklyCleanReminder not provided") }
