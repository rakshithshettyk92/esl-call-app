package com.eslcall.app

import android.content.Context
import androidx.core.content.edit

/**
 * Persists the associate relay session and selected company/store.
 */
object Session {

    private const val PREFS         = "esl_session"
    private const val KEY_USERNAME  = "username"
    private const val KEY_RELAY_SESSION_TOKEN = "relaySessionToken"
    private const val KEY_COMPANY   = "companyCode"
    private const val KEY_STORE     = "storeCode"
    private const val KEY_STORENAME = "storeName"
    private const val KEY_STARTED_AT = "sessionStartedAt"
    private const val KEY_LOCAL_SIGNED_OUT = "locallySignedOut"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun username(ctx: Context):    String? = prefs(ctx).getString(KEY_USERNAME, null)
    fun relaySessionToken(ctx: Context): String? = prefs(ctx).getString(KEY_RELAY_SESSION_TOKEN, null)
    fun companyCode(ctx: Context): String? = prefs(ctx).getString(KEY_COMPANY, null)
    fun storeCode(ctx: Context):   String? = prefs(ctx).getString(KEY_STORE, null)
    fun storeName(ctx: Context):   String? = prefs(ctx).getString(KEY_STORENAME, null)

    fun hasStoreSelected(ctx: Context): Boolean =
        !companyCode(ctx).isNullOrBlank() && !storeCode(ctx).isNullOrBlank()

    fun setLogin(ctx: Context, name: String, sessionToken: String) {
        prefs(ctx).edit {
            putString(KEY_USERNAME, name)
            putString(KEY_RELAY_SESSION_TOKEN, sessionToken)
            putLong(KEY_STARTED_AT, System.currentTimeMillis())
            remove(KEY_LOCAL_SIGNED_OUT)
        }
    }

    fun wasLocallySignedOut(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LOCAL_SIGNED_OUT, false)

    fun restartExpiryClock(ctx: Context) {
        if (username(ctx) != null) {
            prefs(ctx).edit { putLong(KEY_STARTED_AT, System.currentTimeMillis()) }
        }
    }

    fun isExpired(ctx: Context, timeoutMs: Long?): Boolean {
        if (timeoutMs == null || username(ctx) == null) return false
        val startedAt = prefs(ctx).getLong(KEY_STARTED_AT, 0L)
        if (startedAt <= 0L) {
            restartExpiryClock(ctx)
            return false
        }
        return System.currentTimeMillis() - startedAt >= timeoutMs
    }

    /**
     * Saves the new company/store and swaps FCM topic subscriptions.
     * Topic format mirrors the relay's fcmSafeTopic(): non-alphanumeric chars
     * become underscores.
     */
    fun setStore(ctx: Context, company: String, storeCode: String, storeName: String) {
        prefs(ctx).edit {
            putString(KEY_COMPANY, company.trim())
            putString(KEY_STORE,   storeCode.trim())
            putString(KEY_STORENAME, storeName.trim())
        }
        RelayApi.registerCurrentDeviceAsync(ctx)
    }

    /** Used on app launch to (re)subscribe in case Firebase forgot. Idempotent. */
    fun resubscribeCurrentTopic(ctx: Context) {
        RelayApi.registerCurrentDeviceAsync(ctx)
    }

    fun clear(ctx: Context) {
        val sessionToken = relaySessionToken(ctx)
        RelayApi.unregisterCurrentDeviceAsync(sessionToken)
        RelayApi.logoutSessionAsync(sessionToken)
        prefs(ctx).edit {
            clear()
            putBoolean(KEY_LOCAL_SIGNED_OUT, true)
        }
    }

    /** Clears the selected store but keeps the user logged in (used by "Switch store"). */
    fun clearStore(ctx: Context) {
        RelayApi.unregisterCurrentDeviceAsync(relaySessionToken(ctx))
        prefs(ctx).edit {
            remove(KEY_COMPANY)
            remove(KEY_STORE)
            remove(KEY_STORENAME)
        }
    }

}
