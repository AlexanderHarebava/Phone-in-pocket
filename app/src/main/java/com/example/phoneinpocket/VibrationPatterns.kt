package com.example.phoneinpocket

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.StringRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

enum class VibrationPattern(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val visual: List<Float>,
) {
    CLICK("click", R.string.pattern_click_title, R.string.pattern_click_subtitle, listOf(0.25f, 0.9f, 0.25f)),
    TICK("tick", R.string.pattern_tick_title, R.string.pattern_tick_subtitle, listOf(0.18f, 0.35f, 0.18f, 0.35f, 0.18f)),
    DOUBLE_CLICK("double", R.string.pattern_double_title, R.string.pattern_double_subtitle, listOf(0.85f, 0.15f, 0.85f)),
    HEAVY_CLICK("heavy", R.string.pattern_heavy_title, R.string.pattern_heavy_subtitle, listOf(0.4f, 1f, 0.55f)),
    PULSE("pulse", R.string.pattern_pulse_title, R.string.pattern_pulse_subtitle, listOf(0.55f, 0.55f, 0.55f, 0.55f, 0.55f)),
    HEARTBEAT("heart", R.string.pattern_heartbeat_title, R.string.pattern_heartbeat_subtitle, listOf(1f, 0.25f, 0.7f));

    val title: String
        @Composable get() = stringResource(titleRes)

    val subtitle: String
        @Composable get() = stringResource(subtitleRes)

    fun getTitle(context: Context): String = context.getString(titleRes)
    fun getSubtitle(context: Context): String = context.getString(subtitleRes)

    fun execute(vibrator: Vibrator) {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = modernEffect()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrateWithUsage(vibrator, effect)
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            legacyExecute(vibrator)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun modernEffect(): VibrationEffect {
        return when (this) {
            CLICK -> VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
            TICK -> VibrationEffect.createOneShot(28, 80)
            DOUBLE_CLICK -> VibrationEffect.createWaveform(longArrayOf(0, 55, 90, 55), -1)
            HEAVY_CLICK -> VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE)
            PULSE -> VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
            HEARTBEAT -> VibrationEffect.createWaveform(longArrayOf(0, 90, 120, 70), -1)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun vibrateWithUsage(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attributes)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun legacyExecute(vibrator: Vibrator) {
        when (this) {
            TICK -> oneShot(vibrator, 30)
            CLICK -> oneShot(vibrator, 50)
            DOUBLE_CLICK -> waveform(vibrator, longArrayOf(0, 60, 90, 60))
            HEAVY_CLICK -> oneShot(vibrator, 70)
            PULSE -> oneShot(vibrator, 350)
            HEARTBEAT -> waveform(vibrator, longArrayOf(0, 90, 120, 70))
        }
    }

    companion object {
        fun fromId(id: String): VibrationPattern = entries.firstOrNull { it.id == id } ?: CLICK

        fun defaultVibrator(context: Context): Vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

        private fun oneShot(vibrator: Vibrator, millis: Long) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        }

        private fun waveform(vibrator: Vibrator, timings: LongArray) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }
}