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
        const val ALERT_CHANNEL_ID      = "esl_alert_channel"
        const val ALERT_NOTIFICATION_ID = 1002
        const val TAG = "FCMService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM received from: ${remoteMessage.from}")

        val data        = remoteMessage.data
        val message     = data["message"]     ?: "Employee Call"
        val companyCode = data["companyCode"] ?: ""
        val labelCode   = data["labelCode"]   ?: ""

        triggerAlert(message, companyCode, labelCode)
    }

    private fun triggerAlert(message: String, companyCode: String, labelCode: String) {
        ensureAlertChannel()

        // Full-screen intent — launches AlertActivity
        val alertIntent = Intent(this, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_MESSAGE,      message)
            putExtra(AlertActivity.EXTRA_COMPANY_CODE, companyCode)
            putExtra(AlertActivity.EXTRA_LABEL_CODE,   labelCode)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "On My Way" notification action — fires OnMyWayReceiver
        val onMyWayIntent = Intent(this, OnMyWayReceiver::class.java).apply {
            action = OnMyWayReceiver.ACTION_ON_MY_WAY
            putExtra(OnMyWayReceiver.EXTRA_COMPANY_CODE, companyCode)
            putExtra(OnMyWayReceiver.EXTRA_LABEL_CODE,   labelCode)
        }
        val onMyWayPendingIntent = PendingIntent.getBroadcast(
            this, System.currentTimeMillis().toInt(), onMyWayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Employee Call")
            .setContentText(message)
            // Expanded notification shows full message
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)  // tap banner → open AlertActivity
            // "On My Way" action button in both collapsed and expanded notification
            .addAction(android.R.drawable.ic_menu_directions, "On My Way", onMyWayPendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALERT_NOTIFICATION_ID, notification)

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
