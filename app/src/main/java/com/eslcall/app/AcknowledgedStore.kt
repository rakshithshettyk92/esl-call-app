package com.eslcall.app

import android.content.Context

/**
 * Tracks locally which labelCodes have been acknowledged (On My Way tapped)
 * so AlertActivity can detect it was already handled — even if the phone
 * itself sent the acknowledgement from the notification banner.
 */
object AcknowledgedStore {

    private const val PREFS = "acked_labels"
    private const val TTL   = 1 * 60 * 1000L // 1 minute (testing) — same as relay TTL

    fun markAcknowledged(context: Context, labelCode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(labelCode, System.currentTimeMillis()).apply()
    }

    fun clear(context: Context, labelCode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(labelCode).apply()
    }

    fun isAcknowledged(context: Context, labelCode: String): Boolean {
        if (labelCode.isBlank()) return false
        val ts = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(labelCode, 0L)
        return ts > 0L && (System.currentTimeMillis() - ts) < TTL
    }
}
