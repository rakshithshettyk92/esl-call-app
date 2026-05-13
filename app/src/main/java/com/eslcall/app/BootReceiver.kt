package com.eslcall.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-subscribes to the per-store FCM topic after the device reboots. The actual
 * topic name depends on which store the user picked (Session knows). No-op when
 * no store is selected — alerts shouldn't flow until the user picks one.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            if (Session.hasStoreSelected(context)) {
                Session.resubscribeCurrentTopic(context)
                Log.i("BootReceiver", "Re-subscribed to current per-store FCM topic")
            } else {
                Log.i("BootReceiver", "No store selected — skipping FCM re-subscribe")
            }
        }
    }
}
