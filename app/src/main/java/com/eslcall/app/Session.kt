package com.eslcall.app

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Persists the current user/company/store and manages FCM topic subscription.
 * A user is signed into exactly one store at a time; switching stores
 * unsubscribes the previous per-store topic and subscribes to the new one.
 */
object Session {

    private const val PREFS         = "esl_session"
    private const val KEY_USERNAME  = "username"
    private const val KEY_COMPANY   = "companyCode"
    private const val KEY_STORE     = "storeCode"
    private const val KEY_STORENAME = "storeName"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun username(ctx: Context):    String? = prefs(ctx).getString(KEY_USERNAME, null)
    fun companyCode(ctx: Context): String? = prefs(ctx).getString(KEY_COMPANY, null)
    fun storeCode(ctx: Context):   String? = prefs(ctx).getString(KEY_STORE, null)
    fun storeName(ctx: Context):   String? = prefs(ctx).getString(KEY_STORENAME, null)

    fun hasStoreSelected(ctx: Context): Boolean =
        !companyCode(ctx).isNullOrBlank() && !storeCode(ctx).isNullOrBlank()

    fun setUsername(ctx: Context, name: String) {
        prefs(ctx).edit { putString(KEY_USERNAME, name) }
    }

    /**
     * Saves the new company/store and swaps FCM topic subscriptions.
     * Topic format mirrors the relay's fcmSafeTopic(): non-alphanumeric chars
     * become underscores.
     */
    fun setStore(ctx: Context, company: String, storeCode: String, storeName: String) {
        val oldTopic = currentTopic(ctx)
        prefs(ctx).edit {
            putString(KEY_COMPANY, company.trim())
            putString(KEY_STORE,   storeCode.trim())
            putString(KEY_STORENAME, storeName.trim())
        }
        val newTopic = currentTopic(ctx)
        if (oldTopic != null && oldTopic != newTopic) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(oldTopic)
        }
        if (newTopic != null) {
            FirebaseMessaging.getInstance().subscribeToTopic(newTopic)
        }
    }

    /** Used on app launch to (re)subscribe in case Firebase forgot. Idempotent. */
    fun resubscribeCurrentTopic(ctx: Context) {
        currentTopic(ctx)?.let { FirebaseMessaging.getInstance().subscribeToTopic(it) }
    }

    fun clear(ctx: Context) {
        currentTopic(ctx)?.let { FirebaseMessaging.getInstance().unsubscribeFromTopic(it) }
        prefs(ctx).edit { clear() }
    }

    /** Clears the selected store but keeps the user logged in (used by "Switch store"). */
    fun clearStore(ctx: Context) {
        currentTopic(ctx)?.let { FirebaseMessaging.getInstance().unsubscribeFromTopic(it) }
        prefs(ctx).edit {
            remove(KEY_COMPANY)
            remove(KEY_STORE)
            remove(KEY_STORENAME)
        }
    }

    private fun currentTopic(ctx: Context): String? {
        val company = companyCode(ctx)?.takeIf { it.isNotBlank() } ?: return null
        val store   = storeCode(ctx)?.takeIf { it.isNotBlank() } ?: return null
        return fcmSafeTopic(Constants.FCM_TOPIC_PREFIX, company, store)
    }

    private fun fcmSafeTopic(vararg parts: String): String =
        parts.joinToString("-") { it.replace(Regex("[^A-Za-z0-9_~.%-]"), "_") }
}
