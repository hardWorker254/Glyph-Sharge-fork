package com.bleelblep.glyphsharge.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bleelblep.glyphsharge.R
import com.bleelblep.glyphsharge.glyph.GlyphManager
import com.bleelblep.glyphsharge.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class QuietHoursService : Service() {

    companion object {
        private const val TAG = "QuietHoursService"
        private const val NOTIFICATION_ID = 1004
        private const val NOTIFICATION_CHANNEL_ID = "QuietHoursServiceChannel"

        // Public actions – sent to the service by external callers (e.g. MainActivity)
        const val ACTION_START_QUIET_HOURS = "com.bleelblep.glyphsharge.START_QUIET_HOURS"
        const val ACTION_STOP_QUIET_HOURS  = "com.bleelblep.glyphsharge.STOP_QUIET_HOURS"

        // Internal broadcast actions – fired by AlarmManager, received by quietHoursReceiver
        const val ACTION_QUIET_HOURS_START = "com.bleelblep.glyphsharge.QUIET_HOURS_START"
        const val ACTION_QUIET_HOURS_END   = "com.bleelblep.glyphsharge.QUIET_HOURS_END"

        const val ACTION_STOP = "com.bleelblep.glyphsharge.QUIET_HOURS_STOP"

        // Must stay distinct: PendingIntent identity is (requestCode, action).
        private const val RC_START = 100
        private const val RC_END   = 101

        /**
         * How wide the degraded alarm is when exact alarms are unavailable.
         *
         * Android's own guidance for `setWindow` is a tenth of the interval;
         * for a daily do-not-disturb window that is a little over two hours,
         * which is too wide to be worth having. Half an hour is the smallest
         * value that still lets the system batch the two alarms together
         * across Doze, and it keeps the window's edges within a range nobody
         * would call broken.
         */
        private const val FALLBACK_WINDOW_MS = 30 * 60_000L

        /**
         * Whether this app may arm exact alarms right now.
         *
         * Read by the settings toggle so it can send the user to the one
         * system screen that grants it, instead of the feature quietly running
         * on a 30-minute window with nothing on screen to say why.
         *
         * False is the answer on every device that has not been told otherwise,
         * which is the normal state since Android 14 stopped granting the
         * permission at install. That is not a reason to hide the prompt: the
         * window still works, it just starts late.
         */
        fun canScheduleExactAlarms(context: Context): Boolean = runCatching {
            // `getSystemService` returns null where there is no AlarmManager at
            // all, and a `ContextWrapper` around nothing throws instead of
            // returning null — so the whole question is wrapped rather than
            // just the call on the manager. The settings toggle asks it while
            // the user is tapping, and there is no answer worth a crash.
            val manager = (context.getSystemService(ALARM_SERVICE) as? AlarmManager)
                ?: return@runCatching false
            manager.canScheduleExactAlarms()
        }.getOrDefault(defaultValue = false)
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphManager: GlyphManager

    // AtomicBoolean: safe to read/write from any thread without explicit locking
    private val quietHoursActive = AtomicBoolean(false)

    // Supervisor so one failing child doesn't cancel siblings
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val alarmManager: AlarmManager by lazy {
        getSystemService(ALARM_SERVICE) as AlarmManager
    }

    // Broadcast receiver – receives EXACT alarm broadcasts (getBroadcast PI)

    private val quietHoursReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_QUIET_HOURS_START -> {
                    Log.d(TAG, "Alarm: quiet hours starting")
                    // Exact alarms do not repeat, so the next occurrence is set here.
                    scheduleExactAlarm(isStartAlarm = true)
                    startQuietHours()
                }
                ACTION_QUIET_HOURS_END -> {
                    Log.d(TAG, "Alarm: quiet hours ending")
                    scheduleExactAlarm(isStartAlarm = false)
                    endQuietHours()
                }
            }
        }
    }

    // Lifecycle

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "QuietHoursService created")

        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_QUIET_HOURS_START)
            addAction(ACTION_QUIET_HOURS_END)
        }
        registerReceiver(quietHoursReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        // Satisfy Android's FGS requirement before any other work
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP_QUIET_HOURS -> {
                shutDown()
                return START_NOT_STICKY
            }
            // ACTION_START_QUIET_HOURS or null (e.g. restarted by OS) – fall through
            else -> {
                scheduleAlarms()
                syncCurrentState()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "QuietHoursService destroyed")
        runCatching { unregisterReceiver(quietHoursReceiver) }
        cancelAlarms()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Alarm scheduling (exact alarms, rescheduled each time they fire)

    /**
     * Schedules both start and end exact alarms for tomorrow's quiet hours window.
     * Called once when the service first starts or when settings change.
     */
    private fun scheduleAlarms() {
        if (!settingsRepository.isQuietHoursEnabled()) {
            Log.d(TAG, "Quiet hours disabled – skipping alarm scheduling")
            return
        }
        scheduleExactAlarm(isStartAlarm = true)
        scheduleExactAlarm(isStartAlarm = false)
    }

    /**
     * Schedules a single alarm for the next occurrence of [isStartAlarm].
     * If today's time has already passed, the alarm is pushed to tomorrow.
     *
     * Exact when it can be, a window when it cannot, and never a crash — see
     * [setAlarm] for why the second one matters more than it looks.
     */
    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleExactAlarm(isStartAlarm: Boolean) {
        if (!settingsRepository.isQuietHoursEnabled()) return

        val (hour, minute, requestCode, action) = if (isStartAlarm) {
            AlarmParams(
                settingsRepository.getQuietHoursStartHour(),
                settingsRepository.getQuietHoursStartMinute(),
                RC_START,
                ACTION_QUIET_HOURS_START,
            )
        } else {
            AlarmParams(
                settingsRepository.getQuietHoursEndHour(),
                settingsRepository.getQuietHoursEndMinute(),
                RC_END,
                ACTION_QUIET_HOURS_END,
            )
        }

        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Always push to the next valid future occurrence
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val pi = buildBroadcastPendingIntent(requestCode, action)
        val which = if (isStartAlarm) "START" else "END"

        setAlarm(triggerAt, pi, which)
    }

    /**
     * Arms one alarm, degrading instead of failing.
     *
     * Since Android 14, `SCHEDULE_EXACT_ALARM` is not granted at install: the
     * user has to turn on "Alarms & reminders" in special app access, and
     * `setExactAndAllowWhileIdle` throws `SecurityException` without it. That
     * exception is thrown on the main thread out of `onStartCommand`, so it
     * was an uncaught crash — the process died when the user enabled quiet
     * hours, and again on every reboot with it enabled, because
     * `BootCompletedReceiver` starts this service.
     *
     * The degraded path is a window rather than a failure because precision is
     * what quiet hours actually needs least: a "do not disturb" window that
     * starts up to [FALLBACK_WINDOW_MS] late is the feature working, and a
     * crash that happens whenever the feature is switched on is the feature
     * not existing. `VpnConnectedService` applies the same rule to its own
     * permission-gated call, which is why this one is the last to catch up.
     *
     * The `canScheduleExactAlarms` check is a courtesy that saves the log
     * noise; the `runCatching` is what actually guarantees no crash, because a
     * grant can also be revoked between the check and the call.
     */
    private fun setAlarm(triggerAt: Long, pi: PendingIntent, which: String) {
        val exactAllowed = runCatching { alarmManager.canScheduleExactAlarms() }
            .getOrDefault(defaultValue = false)

        if (exactAllowed) {
            val armed = runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }.isSuccess
            if (armed) {
                Log.d(TAG, "Scheduled $which exact alarm at $triggerAt")
                return
            }
            Log.w(TAG, "Exact $which alarm refused; falling back to a window")
        } else {
            Log.i(
                TAG,
                "No exact-alarm access; scheduling $which as a window. Quiet hours " +
                    "can start up to ${FALLBACK_WINDOW_MS / 60_000} min late.",
            )
        }

        // Also the safety net for the success path: `setWindow` needs no
        // permission, so it cannot refuse the way `setExactAndAllowWhileIdle`
        // just did.
        runCatching {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                FALLBACK_WINDOW_MS,
                pi,
            )
        }.onSuccess {
            Log.d(TAG, "Scheduled $which window alarm at $triggerAt")
        }.onFailure {
            Log.e(TAG, "Could not schedule the $which quiet hours alarm", it)
        }
    }

    private fun cancelAlarms() {
        alarmManager.cancel(buildBroadcastPendingIntent(RC_START, ACTION_QUIET_HOURS_START))
        alarmManager.cancel(buildBroadcastPendingIntent(RC_END, ACTION_QUIET_HOURS_END))
        Log.d(TAG, "Cancelled quiet hours alarms")
    }

    /** Creates a broadcast PendingIntent targeting this service's receiver. */
    private fun buildBroadcastPendingIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // State management

    /** Reconciles in-memory state with the real current time on (re)start. */
    private fun syncCurrentState() {
        val shouldBeActive = settingsRepository.isCurrentlyInQuietHours()
        when {
            shouldBeActive && !quietHoursActive.get() -> startQuietHours()
            !shouldBeActive && quietHoursActive.get() -> endQuietHours()
        }
    }

    private fun startQuietHours() {
        // compareAndSet: only proceeds if currently false → prevents double-entry
        if (!quietHoursActive.compareAndSet(false, true)) return

        Log.d(TAG, "Starting quiet hours – disabling glyphs")
        serviceScope.launch {
            runCatching {
                glyphManager.turnOffAll()
                if (glyphManager.isSessionActive) {
                    glyphManager.cleanup()
                    Log.d(TAG, "Glyph session cleaned up for quiet hours")
                }
            }.onFailure { Log.e(TAG, "Error starting quiet hours", it) }
        }
        updateNotification()
    }

    private fun endQuietHours() {
        // compareAndSet: only proceeds if currently true → prevents double-entry
        if (!quietHoursActive.compareAndSet(true, false)) return

        Log.d(TAG, "Ending quiet hours – restoring glyphs")
        serviceScope.launch {
            runCatching {
                if (!glyphManager.isSessionActive && settingsRepository.getGlyphServiceEnabled()) {
                    glyphManager.initialize()
                    glyphManager.openSession()
                    Log.d(TAG, "Glyph session restored after quiet hours")
                }
            }.onFailure { Log.e(TAG, "Error ending quiet hours", it) }
        }
        updateNotification()
    }

    private fun shutDown() {
        cancelAlarms()
        if (quietHoursActive.get()) endQuietHours()
        stopForegroundCompat()
        stopSelf()
    }

    // Notification helpers

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Quiet Hours Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Manages quiet hours for glyph animations"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Read all settings in one pass to minimise repository calls
        val startH = settingsRepository.getQuietHoursStartHour()
        val startM = settingsRepository.getQuietHoursStartMinute()
        val endH   = settingsRepository.getQuietHoursEndHour()
        val endM   = settingsRepository.getQuietHoursEndMinute()

        val status    = if (quietHoursActive.get()) "Active" else "Monitoring"
        val timeRange = "%d:%02d – %d:%02d".format(startH, startM, endH, endM)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Quiet Hours")
            .setContentText("$status • $timeRange")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // Re-posts the notification the service already owns as its foreground
    // notification (started in onStartCommand under the same NOTIFICATION_ID).
    //
    // The app asks for no notification permission, so on Android 13+ this
    // notice never reaches the drawer — the system guarantees it a Task
    // Manager entry and nothing more. That is the accepted trade for never
    // prompting. The suppression is still needed, because lint asks about
    // the notify() call itself, and the service refuses to start without
    // a notification.
    @SuppressLint("NotificationPermission")
    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // Helpers

    /** Simple data holder to avoid long parameter lists in scheduleExactAlarm. */
    private data class AlarmParams(
        val hour: Int,
        val minute: Int,
        val requestCode: Int,
        val action: String,
    )
}