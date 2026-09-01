package com.eslcall.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Queues notification-action acknowledgements for durable, network-aware delivery. */
class OnMyWayReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ON_MY_WAY = "com.eslcall.app.ON_MY_WAY"
        const val EXTRA_COMPANY_CODE = "extra_company_code"
        const val EXTRA_LABEL_CODE = "extra_label_code"
        const val EXTRA_CALL_ID = "extra_call_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val company = intent.getStringExtra(EXTRA_COMPANY_CODE).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL_CODE).orEmpty()
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val store = Session.storeCode(context).orEmpty()
        if (company.isBlank() || store.isBlank() || label.isBlank()) return

        AcknowledgeWorker.enqueue(context, company, store, label, callId)
        Toast.makeText(context, "Sending responseâ€¦", Toast.LENGTH_SHORT).show()
    }
}

/** Stable notification ID derived from the label code. */
fun notificationIdFor(labelCode: String): Int =
    if (labelCode.isBlank()) MyFirebaseMessagingService.ALERT_NOTIFICATION_ID
    else (labelCode.hashCode() and 0x7FFFFFFF) + 1000
