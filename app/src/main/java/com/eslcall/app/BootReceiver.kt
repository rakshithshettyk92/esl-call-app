package com.eslcall.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Re-subscribes to the FCM topic after the device reboots.
 * FCM subscriptions persist, but this ensures we're always subscribed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            FirebaseMessaging.getInstance().subscribeToTopic("employee-calls")
                .addOnSuccessListener { Log.i("BootReceiver", "Re-subscribed to FCM topic") }
        }
    }
}
