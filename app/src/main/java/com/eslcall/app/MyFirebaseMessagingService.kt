package com.eslcall.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val ALERT_CHANNEL_ID        = "esl_alert_channel"
        const val ALERT_NOTIFICATION_ID   = 1002   // fallback only
        const val TAG                     = "FCMService"
        const val ACTION_CANCEL_ALERT     = "com.eslcall.app.CANCEL_ALERT"
        const val EXTRA_CANCEL_LABEL_CODE = "cancel_label_code"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM received from: ${remoteMessage.from}")

        val data = remoteMessage.data

        // Cancel message — dismiss the alert for this specific labelCode
        if (data["type"] == "cancel") {
            val labelCode = data["labelCode"] ?: ""
            Log.d(TAG, "FCM cancel received for labelCode: $labelCode")

            // Mark acknowledged locally so AlertActivity shows correct state on open
            AcknowledgedStore.markAcknowledged(this, labelCode)

            // Dismiss the tray notification for this label
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notificationIdFor(labelCode))

            // Broadcast so any open AlertActivity for this label dismisses itself
            sendBroadcast(Intent(ACTION_CANCEL_ALERT).apply {
                putExtra(EXTRA_CANCEL_LABEL_CODE, labelCode)
            })
            return
        }

        val message     = data["message"]     ?: "Employee Call"
        val companyCode = data["companyCode"] ?: ""
        val labelCode   = data["labelCode"]   ?: ""

        triggerAlert(message, companyCode, labelCode)
    }

    private fun triggerAlert(message: String, companyCode: String, labelCode: String) {
        ensureAlertChannel()

        val notifId = notificationIdFor(labelCode)

        // Full-screen intent — launches AlertActivity
        val alertIntent = Intent(this, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_MESSAGE,         message)
            putExtra(AlertActivity.EXTRA_COMPANY_CODE,    companyCode)
            putExtra(AlertActivity.EXTRA_LABEL_CODE,      labelCode)
            putExtra(AlertActivity.EXTRA_NOTIFICATION_ID, notifId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, notifId, alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "On My Way" notification action — fires OnMyWayReceiver
        val onMyWayIntent = Intent(this, OnMyWayReceiver::class.java).apply {
            action = OnMyWayReceiver.ACTION_ON_MY_WAY
            putExtra(OnMyWayReceiver.EXTRA_COMPANY_CODE, companyCode)
            putExtra(OnMyWayReceiver.EXTRA_LABEL_CODE,   labelCode)
        }
        val onMyWayPendingIntent = PendingIntent.getBroadcast(
            this, notifId, onMyWayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Employee Call")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_directions, "On My Way", onMyWayPendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)

        // Also launch AlertActivity directly when screen is on
        startActivity(alertIntent)
    }

    private fun ensureAlertChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(ALERT_CHANNEL_ID) != null) return
        NotificationChannel(ALERT_CHANNEL_ID, "Employee Call Alerts",
            NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            enableLights(true)
            setBypassDnd(true)
        }.also { nm.createNotificationChannel(it) }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed (using topic messaging)")
    }
}
