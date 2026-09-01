package com.eslcall.app

import android.content.Context
import androidx.core.content.edit

object DeviceSettings {
    private const val PREFS = "device_settings"
    private const val KEY_ALERT_TIMEOUT = "alert_timeout_seconds"
    private const val KEY_AUTH_POLL = "auth_poll_seconds"
    private const val KEY_KEEP_SCREEN_ON = "keep_ready_screen_on"
    private const val KEY_SESSION_TIMEOUT_HOURS = "session_timeout_hours"

    val sessionTimeoutOptions = listOf(0, 1, 2, 12, 24)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun alertTimeoutMs(context: Context): Long =
        prefs(context).getInt(KEY_ALERT_TIMEOUT, BuildConfig.ALERT_TIMEOUT_SECONDS)
            .coerceIn(15, 600) * 1_000L

    fun authPollIntervalMs(context: Context): Long =
        prefs(context).getInt(KEY_AUTH_POLL, BuildConfig.AUTH_POLL_INTERVAL_SECONDS)
            .coerceIn(30, 3_600) * 1_000L

    fun keepReadyScreenOn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, BuildConfig.KEEP_READY_SCREEN_ON)

    fun sessionTimeoutHours(context: Context): Int =
        prefs(context).getInt(KEY_SESSION_TIMEOUT_HOURS, 0)
            .takeIf { it in sessionTimeoutOptions } ?: 0

    fun sessionTimeoutMs(context: Context): Long? =
        sessionTimeoutHours(context).takeIf { it > 0 }?.times(60L * 60L * 1_000L)

    fun sessionTimeoutLabel(hours: Int): String = when (hours) {
        0 -> "Never (recommended)"
        1 -> "1 hour"
        2 -> "2 hours"
        12 -> "12 hours"
        24 -> "24 hours"
        else -> "Never (recommended)"
    }

    fun save(
        context: Context,
        alertSeconds: Int,
        pollSeconds: Int,
        keepScreenOn: Boolean,
        sessionTimeoutHours: Int,
    ) {
        prefs(context).edit {
            putInt(KEY_ALERT_TIMEOUT, alertSeconds.coerceIn(15, 600))
            putInt(KEY_AUTH_POLL, pollSeconds.coerceIn(30, 3_600))
            putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn)
            putInt(KEY_SESSION_TIMEOUT_HOURS,
                sessionTimeoutHours.takeIf { it in sessionTimeoutOptions } ?: 0)
        }
    }
}
