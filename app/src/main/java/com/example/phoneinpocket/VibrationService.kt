package com.example.phoneinpocket

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.phoneinpocket.VibrationService.Companion.DEFAULT_INTERVAL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VibrationState(
    val isRunning: Boolean = false,
    val intervalSeconds: Int = DEFAULT_INTERVAL,
    val patternId: String = VibrationPattern.CLICK.id,
    val nextVibrationAt: Long = 0L,
    val lastVibrationAt: Long = 0L,
)

class VibrationService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): VibrationService = this@VibrationService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val vibrator by lazy { VibrationPattern.defaultVibrator(this) }

    private val _state = MutableStateFlow(VibrationState())
    val state: StateFlow<VibrationState> = _state.asStateFlow()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.action == ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
            intent == null -> {
                stop()
                return START_NOT_STICKY
            }
            else -> {
                val interval = intent.getIntExtra(EXTRA_INTERVAL, _state.value.intervalSeconds)
                    .coerceIn(MIN_INTERVAL, MAX_INTERVAL)
                val patternId = intent.getStringExtra(EXTRA_PATTERN) ?: _state.value.patternId
                start(interval, patternId)
            }
        }
        return START_STICKY
    }

    fun start(intervalSeconds: Int, patternId: String) {
        _state.value = _state.value.copy(
            isRunning = true,
            intervalSeconds = intervalSeconds,
            patternId = patternId,
        )
        startForegroundCompat(buildNotification())
        acquireWakeLock()

        loopJob?.cancel()
        val pattern = VibrationPattern.fromId(patternId)
        loopJob = scope.launch {
            while (isActive) {
                pattern.execute(vibrator)
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(
                    nextVibrationAt = now + intervalSeconds * 1000L,
                    lastVibrationAt = now,
                )
                updateNotification()
                delay(intervalSeconds * 1000L)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _state.value = _state.value.copy(isRunning = false, nextVibrationAt = 0L)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val pattern = VibrationPattern.fromId(s.patternId)
        val patternTitle = pattern.getTitle(this)

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, VibrationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vibration)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, patternTitle, s.intervalSeconds))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    getString(R.string.notification_big_text, patternTitle, s.intervalSeconds)
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.btn_stop), stopIntent)
            .build()
    }

    private fun updateNotification() {
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP = "com.example.phoneinpocket.ACTION_STOP"
        const val EXTRA_INTERVAL = "extra_interval"
        const val EXTRA_PATTERN = "extra_pattern"
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 600
        const val DEFAULT_INTERVAL = 10

        private const val CHANNEL_ID = "vibration_timer"
        private const val NOTIFICATION_ID = 42
        private const val WAKELOCK_TAG = "PhoneInPocket::IntervalVibration"

        fun startIntent(context: Context, intervalSeconds: Int, patternId: String): Intent =
            Intent(context, VibrationService::class.java)
                .putExtra(EXTRA_INTERVAL, intervalSeconds)
                .putExtra(EXTRA_PATTERN, patternId)
    }
}