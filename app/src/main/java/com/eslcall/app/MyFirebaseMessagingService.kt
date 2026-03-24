package com.eslcall.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val ALERT_CHANNEL_ID        = "esl_alert_channel"
        const val ALERT_NOTIFICATION_ID   = 1002   // fallback only
        const val GROUPED_NOTIFICATION_ID = 998    // used when 2+ alerts active
        const val TAG                     = "FCMService"
        const val ACTION_CANCEL_ALERT     = "com.eslcall.app.CANCEL_ALERT"
        const val ACTION_SWITCH_TO_LIST   = "com.eslcall.app.SWITCH_TO_LIST"
        const val ACTION_ACTIVE_LIST_CHANGED = "com.eslcall.app.ACTIVE_LIST_CHANGED"
        const val EXTRA_CANCEL_LABEL_CODE = "cancel_label_code"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM received from: ${remoteMessage.from}")
        val data = remoteMessage.data

        if (data["type"] == "cancel") {
            val labelCode = data["labelCode"] ?: ""
            Log.d(TAG, "FCM cancel for: $labelCode")
            AcknowledgedStore.markAcknowledged(this, labelCode)
            AlertQueueStore.removeByLabelCode(this, labelCode)
            // Remove any MISSED entry that was saved before this cancel arrived
            AlertHistoryStore.removeByLabelCode(this, labelCode)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notificationIdFor(labelCode))
            sendBroadcast(Intent(ACTION_CANCEL_ALERT).apply {
                putExtra(EXTRA_CANCEL_LABEL_CODE, labelCode)
            })
            sendBroadcast(Intent(ACTION_ACTIVE_LIST_CHANGED))
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
        val nm      = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Enqueue BEFORE checking size
        AlertQueueStore.enqueue(
            this, PendingAlert(
                id             = UUID.randomUUID().toString(),
                message        = message,
                companyCode    = companyCode,
                labelCode      = labelCode,
                receivedAt     = System.currentTimeMillis(),
                notificationId = notifId
            )
        )

        val queueSize     = AlertQueueStore.size(this)
        val appForeground = AppForegroundTracker.isInForeground

        // Notify MainActivity / ActiveCallsActivity to refresh
        sendBroadcast(Intent(ACTION_ACTIVE_LIST_CHANGED))

        if (queueSize == 1) {
            // ── Single alert: full-screen popup + individual notification ──────
            val alertIntent = Intent(this, AlertActivity::class.java).apply {
                putExtra(AlertActivity.EXTRA_MESSAGE,         message)
                putExtra(AlertActivity.EXTRA_COMPANY_CODE,    companyCode)
                putExtra(AlertActivity.EXTRA_LABEL_CODE,      labelCode)
                putExtra(AlertActivity.EXTRA_NOTIFICATION_ID, notifId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val fullScreenPI = PendingIntent.getActivity(
                this, notifId, alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF001C3D.toInt())
                .setContentTitle("Employee Call")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(fullScreenPI)
                .setNumber(1)
                .setAutoCancel(true)
            if (appForeground) {
                // App is visible — activity launched directly, no banner needed
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            } else {
                val onMyWayIntent = Intent(this, OnMyWayReceiver::class.java).apply {
                    action = OnMyWayReceiver.ACTION_ON_MY_WAY
                    putExtra(OnMyWayReceiver.EXTRA_COMPANY_CODE, companyCode)
                    putExtra(OnMyWayReceiver.EXTRA_LABEL_CODE,   labelCode)
                }
                val onMyWayPI = PendingIntent.getBroadcast(
                    this, notifId, onMyWayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPI, true)
                    .addAction(android.R.drawable.ic_menu_directions, "On My Way", onMyWayPI)
            }
            nm.notify(notifId, builder.build())
            startActivity(alertIntent)

        } else {
            // ── Multiple alerts: cancel all individuals, show grouped ──────────
            AlertQueueStore.loadAll(this).forEach { nm.cancel(it.notificationId) }

            val allAlerts  = AlertQueueStore.loadAll(this)
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("$queueSize Active Employee Calls")
            allAlerts.forEach { inboxStyle.addLine(it.message) }

            val activeCallsPI = PendingIntent.getActivity(
                this, 0,
                Intent(this, ActiveCallsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val grouped = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF001C3D.toInt())
                .setContentTitle("$queueSize Active Employee Calls")
                .setContentText("Tap to view and respond")
                .setStyle(inboxStyle)
                // No heads-up banner when user is already looking at the list
                .setPriority(if (appForeground) NotificationCompat.PRIORITY_DEFAULT
                             else NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(activeCallsPI)
                .setNumber(queueSize)
                .setAutoCancel(false)
                .setOngoing(true)
                .build()
            nm.notify(GROUPED_NOTIFICATION_ID, grouped)

            // Tell AlertActivity (if open) to transition to the list screen
            sendBroadcast(Intent(ACTION_SWITCH_TO_LIST))
        }
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
