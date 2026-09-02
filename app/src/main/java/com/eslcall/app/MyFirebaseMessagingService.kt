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
        const val ALERT_CHANNEL_ID        = "esl_alert_channel_v4"
        const val STATUS_CHANNEL_ID       = "esl_status_channel"
        const val ALERT_NOTIFICATION_ID   = 1002   // fallback only
        const val GROUPED_NOTIFICATION_ID = 998    // used when 2+ alerts active
        const val TAG                     = "FCMService"
        const val ACTION_CANCEL_ALERT     = "com.eslcall.app.CANCEL_ALERT"
        const val ACTION_SWITCH_TO_LIST   = "com.eslcall.app.SWITCH_TO_LIST"
        const val ACTION_ACTIVE_LIST_CHANGED = "com.eslcall.app.ACTIVE_LIST_CHANGED"
        const val EXTRA_CANCEL_LABEL_CODE = "cancel_label_code"
        const val EXTRA_CANCEL_CLAIMED_BY = "cancel_claimed_by"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM received from: ${remoteMessage.from}")
        val data = remoteMessage.data

        if (data["type"] == "cancel") {
            val labelCode = data["labelCode"] ?: ""
            val claimedBy = data["claimedBy"].orEmpty()
            Log.d(TAG, "FCM cancel for: $labelCode")
            val alreadyConfirmedHere = AcknowledgedStore.isAcknowledged(this, labelCode)
            val acceptedByCurrentAssociate = claimedBy.isNotBlank() &&
                claimedBy.equals(Session.username(this), ignoreCase = true)
            val handledHere = alreadyConfirmedHere || acceptedByCurrentAssociate
            val queued = AlertQueueStore.loadAll(this).firstOrNull { it.labelCode == labelCode }
            val previous = AlertHistoryStore.load(this).firstOrNull { it.labelCode == labelCode }
            AlertHistoryStore.removeByLabelCode(this, labelCode)
            val sourceMessage = queued?.message ?: previous?.message
            if (!handledHere && sourceMessage != null) {
                    AlertHistoryStore.save(this, AlertHistoryItem(
                        message = sourceMessage,
                        companyCode = queued?.companyCode ?: previous?.companyCode.orEmpty(),
                        labelCode = labelCode,
                        timestamp = System.currentTimeMillis(),
                        status = AlertStatus.HANDLED_BY_OTHER,
                        handledBy = claimedBy.takeIf { it.isNotBlank() },
                    ))
            } else if (acceptedByCurrentAssociate && !alreadyConfirmedHere && sourceMessage != null) {
                AlertHistoryStore.save(this, AlertHistoryItem(
                    message = sourceMessage,
                    companyCode = queued?.companyCode ?: previous?.companyCode.orEmpty(),
                    labelCode = labelCode,
                    timestamp = System.currentTimeMillis(),
                    status = AlertStatus.ACKNOWLEDGED,
                    handledBy = claimedBy,
                ))
            }
            AcknowledgedStore.markAcknowledged(this, labelCode)
            AlertQueueStore.removeByLabelCode(this, labelCode)
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationIdFor(labelCode))
            val remainingAlerts = AlertQueueStore.loadAll(this)
            if (remainingAlerts.isEmpty() || AppForegroundTracker.isInForeground) {
                notificationManager.cancel(GROUPED_NOTIFICATION_ID)
            } else {
                ensureStatusChannel()
                postGroupedNotification(notificationManager, remainingAlerts, alerting = false)
            }
            sendBroadcast(Intent(ACTION_CANCEL_ALERT).apply {
                setPackage(packageName)
                putExtra(EXTRA_CANCEL_LABEL_CODE, labelCode)
                putExtra(EXTRA_CANCEL_CLAIMED_BY, claimedBy)
            })
            sendBroadcast(Intent(ACTION_ACTIVE_LIST_CHANGED).setPackage(packageName))
            return
        }

        val message     = data["message"]     ?: "Customer help needed"
        val callId      = data["callId"]      ?: ""
        val companyCode = data["companyCode"] ?: ""
        val labelCode   = data["labelCode"]   ?: ""
        // New button press — clear any stale acknowledgement so the alert shows fresh
        if (labelCode.isNotBlank()) AcknowledgedStore.clear(this, labelCode)
        triggerAlert(callId, message, companyCode, labelCode)
    }

    private fun triggerAlert(callId: String, message: String, companyCode: String, labelCode: String) {
        ensureAlertChannel()
        ensureStatusChannel()

        val notifId = notificationIdFor(labelCode)
        val nm      = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Enqueue BEFORE checking size
        AlertQueueStore.enqueue(
            this, PendingAlert(
                id             = UUID.randomUUID().toString(),
                callId         = callId,
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
        sendBroadcast(Intent(ACTION_ACTIVE_LIST_CHANGED).setPackage(packageName))

        if (queueSize == 1) {
            val alertIntent = Intent(this, AlertActivity::class.java).apply {
                putExtra(AlertActivity.EXTRA_MESSAGE,         message)
                putExtra(AlertActivity.EXTRA_COMPANY_CODE,    companyCode)
                putExtra(AlertActivity.EXTRA_LABEL_CODE,      labelCode)
                putExtra(AlertActivity.EXTRA_NOTIFICATION_ID, notifId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (appForeground) {
                // The alert screen is already the notification while the app is visible.
                // Do not also post a high-importance banner for the same call.
                nm.cancel(notifId)
                startActivity(alertIntent)
            } else {
                val fullScreenPI = PendingIntent.getActivity(
                    this, notifId, alertIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val onMyWayIntent = Intent(this, OnMyWayReceiver::class.java).apply {
                    action = OnMyWayReceiver.ACTION_ON_MY_WAY
                    putExtra(OnMyWayReceiver.EXTRA_COMPANY_CODE, companyCode)
                    putExtra(OnMyWayReceiver.EXTRA_LABEL_CODE,   labelCode)
                    putExtra(OnMyWayReceiver.EXTRA_CALL_ID,      callId)
                }
                val onMyWayPI = PendingIntent.getBroadcast(
                    this, notifId, onMyWayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFF2F006D.toInt())
                    .setContentTitle("Employee Call")
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(fullScreenPI)
                    .setNumber(1)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPI, true)
                    .addAction(android.R.drawable.ic_menu_directions, "I'm on my way", onMyWayPI)
                    .build()
                nm.notify(notifId, notification)
            }

        } else {
            AlertQueueStore.loadAll(this).forEach { nm.cancel(it.notificationId) }
            val allAlerts = AlertQueueStore.loadAll(this)
            if (appForeground) {
                nm.cancel(GROUPED_NOTIFICATION_ID)
                startActivity(Intent(this, ActiveCallsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            } else {
                postGroupedNotification(nm, allAlerts, alerting = true)
            }
        }
    }

    private fun postGroupedNotification(
        notificationManager: NotificationManager,
        alerts: List<PendingAlert>,
        alerting: Boolean,
    ) {
        if (alerts.isEmpty()) {
            notificationManager.cancel(GROUPED_NOTIFICATION_ID)
            return
        }
        val count = alerts.size
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("$count Active Employee Call${if (count > 1) "s" else ""}")
        alerts.forEach { inboxStyle.addLine(it.message) }
        val activeCallsPI = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ActiveCallsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = if (alerting) ALERT_CHANNEL_ID else STATUS_CHANNEL_ID
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2F006D.toInt())
            .setContentTitle("$count Active Employee Call${if (count > 1) "s" else ""}")
            .setContentText("Tap to view and respond")
            .setStyle(inboxStyle)
            .setContentIntent(activeCallsPI)
            .setNumber(count)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(!alerting)
        if (alerting) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
        }
        val remainingMs = alerts.maxOf {
            DeviceSettings.alertTimeoutMs(this) - (System.currentTimeMillis() - it.receivedAt)
        }
        if (remainingMs > 0) builder.setTimeoutAfter(remainingMs)
        notificationManager.notify(GROUPED_NOTIFICATION_ID, builder.build())
    }

    private fun ensureStatusChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(STATUS_CHANNEL_ID) != null) return
        // IMPORTANCE_DEFAULT = shows in shade + badge, but NO heads-up banner
        NotificationChannel(STATUS_CHANNEL_ID, "Active Call Status",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
        }.also { nm.createNotificationChannel(it) }
    }

    private fun ensureAlertChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(ALERT_CHANNEL_ID) != null) return
        val ringtoneUri = android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_RINGTONE,
        )
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        NotificationChannel(ALERT_CHANNEL_ID, "Employee Call Alerts",
            NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(ringtoneUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 150, 400, 150, 800)
            enableLights(true)
            setBypassDnd(true)
        }.also { nm.createNotificationChannel(it) }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed; registering device with relay")
        RelayApi.registerDeviceTokenAsync(this, token)
    }
}
