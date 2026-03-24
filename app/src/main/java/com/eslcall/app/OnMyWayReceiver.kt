package com.eslcall.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles "On My Way" tapped from the notification action button.
 * Immediately marks the label as acknowledged locally and dismisses
 * the AlertActivity popup — then calls the relay in the background.
 */
class OnMyWayReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ON_MY_WAY   = "com.eslcall.app.ON_MY_WAY"
        const val EXTRA_COMPANY_CODE = "extra_company_code"
        const val EXTRA_LABEL_CODE   = "extra_label_code"
        const val TAG                = "OnMyWayReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val companyCode = intent.getStringExtra(EXTRA_COMPANY_CODE) ?: return
        val labelCode   = intent.getStringExtra(EXTRA_LABEL_CODE)   ?: return

        // Dismiss the notification for this specific label
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationIdFor(labelCode))

        // Mark acknowledged locally immediately — AlertActivity checks this on open
        AcknowledgedStore.markAcknowledged(context, labelCode)

        // Send local cancel broadcast so AlertActivity dismisses instantly
        // (without waiting for the FCM round-trip from the relay)
        context.sendBroadcast(Intent(MyFirebaseMessagingService.ACTION_CANCEL_ALERT).apply {
            putExtra(MyFirebaseMessagingService.EXTRA_CANCEL_LABEL_CODE, labelCode)
        })

        // Call relay in background to trigger ESL actions
        Thread {
            try {
                val body = JSONObject().apply {
                    put("companyCode", companyCode)
                    put("labelCode", labelCode)
                }.toString()

                val conn = (URL("${Constants.RELAY_URL}/esl/acknowledge")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty(Constants.AUTH_HEADER, Constants.AUTH_KEY)
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout    = 10_000
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                val responseCode = conn.responseCode
                val response     = if (responseCode < 400)
                    conn.inputStream.bufferedReader().readText()
                else
                    conn.errorStream.bufferedReader().readText()
                Log.i(TAG, "Acknowledge response ($responseCode): $response")
            } catch (e: Exception) {
                Log.e(TAG, "Acknowledge failed: ${e.message}")
            }
        }.start()
    }
}

/** Stable notification ID derived from the label code. */
fun notificationIdFor(labelCode: String): Int =
    if (labelCode.isBlank()) MyFirebaseMessagingService.ALERT_NOTIFICATION_ID
    else (labelCode.hashCode() and 0x7FFFFFFF) + 1000
