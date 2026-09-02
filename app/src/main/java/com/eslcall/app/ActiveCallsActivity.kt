package com.eslcall.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

/**
 * Shows all currently active (un-acknowledged) alerts in a scrollable list.
 * Launched automatically when 2+ alerts are queued simultaneously.
 * Each item has individual "I'm on my way" and "Not taking" buttons.
 */
class ActiveCallsActivity : AppCompatActivity() {

    private lateinit var tvSubtitle:    TextView
    private lateinit var layoutEmpty:   LinearLayout
    private lateinit var recycler:      RecyclerView
    private lateinit var adapter:       ActiveCallsAdapter

    private val nm get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private val tickHandler  = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val expired = adapter.tick()
            if (expired.isNotEmpty()) {
                expired.forEach { alert ->
                    AlertHistoryStore.save(this@ActiveCallsActivity, AlertHistoryItem(
                        message     = alert.message,
                        companyCode = alert.companyCode,
                        labelCode   = alert.labelCode,
                        timestamp   = System.currentTimeMillis(),
                        status      = AlertStatus.MISSED
                    ))
                    AlertQueueStore.removeByLabelCode(this@ActiveCallsActivity, alert.labelCode)
                    nm.cancel(alert.notificationId)
                }
                refreshList()
            }
            tickHandler.postDelayed(this, 1_000)
        }
    }

    // Refresh when new alert arrives or one is cancelled
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshList()
    }

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val label = intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_CANCEL_LABEL_CODE)
                ?: return
            AcknowledgedStore.markAcknowledged(this@ActiveCallsActivity, label)
            AlertQueueStore.removeByLabelCode(this@ActiveCallsActivity, label)
            refreshList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_calls)
        findViewById<View>(R.id.activeCallsHeader).applyStatusBarInset()

        tvSubtitle  = findViewById(R.id.tvActiveCallsSubtitle)
        layoutEmpty = findViewById(R.id.layoutActiveEmpty)
        recycler    = findViewById(R.id.recyclerActiveCalls)

        findViewById<ImageButton>(R.id.btnBackActiveCalls).setOnClickListener { navigateToMain() }

        adapter = ActiveCallsAdapter(
            items        = emptyList(),
            timeoutMs    = DeviceSettings.alertTimeoutMs(this),
            onAcknowledge = { alert -> acknowledgeAlert(alert) },
            onDismiss     = { alert -> dismissAlert(alert) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter       = adapter
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = navigateToMain()
            })
    }

    override fun onResume() {
        super.onResume()
        nm.cancel(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID)
        ContextCompat.registerReceiver(
            this, refreshReceiver,
            IntentFilter(MyFirebaseMessagingService.ACTION_ACTIVE_LIST_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, cancelReceiver,
            IntentFilter(MyFirebaseMessagingService.ACTION_CANCEL_ALERT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshList()
        tickHandler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(tickRunnable)
        unregisterReceiver(refreshReceiver)
        unregisterReceiver(cancelReceiver)
        val activeAlerts = AlertQueueStore.loadAll(this)
            .filter { !AcknowledgedStore.isAcknowledged(this, it.labelCode) }
        if (activeAlerts.isNotEmpty()) updateGroupedNotification(activeAlerts)
    }

    // -------------------------------------------------------------------------
    // List management
    // -------------------------------------------------------------------------

    private fun refreshList() {
        // Expire any alerts whose 60-second window has already passed
        val now = System.currentTimeMillis()
        AlertQueueStore.loadAll(this)
            .filter { !AcknowledgedStore.isAcknowledged(this, it.labelCode) }
            .filter  { (now - it.receivedAt) >= DeviceSettings.alertTimeoutMs(this) }
            .forEach { alert ->
                AlertHistoryStore.save(this, AlertHistoryItem(
                    message     = alert.message,
                    companyCode = alert.companyCode,
                    labelCode   = alert.labelCode,
                    timestamp   = now,
                    status      = AlertStatus.MISSED
                ))
                AlertQueueStore.removeByLabelCode(this, alert.labelCode)
                nm.cancel(alert.notificationId)
            }

        val alerts = AlertQueueStore.loadAll(this)
            .filter { !AcknowledgedStore.isAcknowledged(this, it.labelCode) }

        tvSubtitle.text = if (alerts.isEmpty()) "All calls handled"
        else "${alerts.size} active call${if (alerts.size > 1) "s" else ""} waiting"

        if (alerts.isEmpty()) {
            nm.cancel(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID)
            navigateToMain()
        } else {
            recycler.visibility    = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            adapter.updateItems(alerts)
            alerts.forEach { nm.cancel(it.notificationId) }
            nm.cancel(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID)
        }
    }

    private fun updateGroupedNotification(alerts: List<PendingAlert>) {
        if (nm.getNotificationChannel(MyFirebaseMessagingService.STATUS_CHANNEL_ID) == null) {
            nm.createNotificationChannel(android.app.NotificationChannel(
                MyFirebaseMessagingService.STATUS_CHANNEL_ID,
                "Active Call Status",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            })
        }
        val count = alerts.size
        val style = androidx.core.app.NotificationCompat.InboxStyle()
            .setBigContentTitle("$count Active Employee Call${if (count > 1) "s" else ""}")
        alerts.forEach { style.addLine(it.message) }

        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, ActiveCallsActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )
        // STATUS_CHANNEL_ID has IMPORTANCE_DEFAULT — sits in the shade for badge
        // count only, guaranteed never to pop up as a heads-up banner.
        val notif = androidx.core.app.NotificationCompat
            .Builder(this, MyFirebaseMessagingService.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2F006D.toInt())
            .setContentTitle("$count Active Employee Call${if (count > 1) "s" else ""}")
            .setContentText("Tap to view and respond")
            .setStyle(style)
            .setContentIntent(pi)
            .setNumber(count)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(alerts.maxOf {
                (DeviceSettings.alertTimeoutMs(this) -
                    (System.currentTimeMillis() - it.receivedAt)).coerceAtLeast(1L)
            })
            .build()
        nm.notify(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID, notif)
    }

    // -------------------------------------------------------------------------
    // Acknowledge
    // -------------------------------------------------------------------------

    private fun acknowledgeAlert(alert: PendingAlert) {
        if (alert.companyCode.isBlank() && alert.labelCode.startsWith("PREVIEW-")) {
            AcknowledgedStore.markAcknowledged(this, alert.labelCode)
            AlertHistoryStore.save(this, AlertHistoryItem(
                message = alert.message,
                companyCode = alert.companyCode,
                labelCode = alert.labelCode,
                timestamp = System.currentTimeMillis(),
                status = AlertStatus.ACKNOWLEDGED,
                handledBy = Session.username(this),
            ))
            AlertQueueStore.removeByLabelCode(this, alert.labelCode)
            nm.cancel(alert.notificationId)
            Toast.makeText(this, "Preview response confirmed", Toast.LENGTH_SHORT).show()
            refreshList()
            return
        }
        Thread {
            try {
                val storeCode = Session.storeCode(this).orEmpty()
                val body = JSONObject().apply {
                    put("callId",      alert.callId)
                    put("companyCode", alert.companyCode)
                    put("storeCode",   storeCode)
                    put("labelCode",   alert.labelCode)
                }
                val (code, claimedBy) = try {
                    val response = RelayApi.postJson(Constants.PATH_ESL_ACKNOWLEDGE, body)
                    200 to response.optString("claimedBy")
                } catch (e: RelayHttpException) {
                    e.statusCode to e.responseBody?.optString("claimedBy").orEmpty()
                }

                runOnUiThread {
                    when (code) {
                        200 -> {
                            AcknowledgedStore.markAcknowledged(this, alert.labelCode)
                            AlertHistoryStore.save(this, AlertHistoryItem(
                                message     = alert.message,
                                companyCode = alert.companyCode,
                                labelCode   = alert.labelCode,
                                timestamp   = System.currentTimeMillis(),
                                status      = AlertStatus.ACKNOWLEDGED,
                                handledBy   = claimedBy.ifBlank { Session.username(this) },
                            ))
                            AlertQueueStore.removeByLabelCode(this, alert.labelCode)
                            nm.cancel(alert.notificationId)
                            Toast.makeText(this,
                                "Response confirmed - this call is assigned to you",
                                Toast.LENGTH_SHORT).show()
                            refreshList()
                        }
                        409 -> {
                            AcknowledgedStore.markAcknowledged(this, alert.labelCode)
                            AlertHistoryStore.removeByLabelCode(this, alert.labelCode)
                            AlertHistoryStore.save(this, AlertHistoryItem(
                                message = alert.message,
                                companyCode = alert.companyCode,
                                labelCode = alert.labelCode,
                                timestamp = System.currentTimeMillis(),
                                status = AlertStatus.HANDLED_BY_OTHER,
                                handledBy = claimedBy.takeIf { it.isNotBlank() },
                            ))
                            AlertQueueStore.removeByLabelCode(this, alert.labelCode)
                            refreshList()
                            Toast.makeText(this,
                                if (claimedBy.isBlank()) "Attended by another associate"
                                else "Attended by $claimedBy",
                                Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            adapter.setItemIdle(alert.labelCode)
                            Toast.makeText(this,
                                "Could not reach server. Try again.",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adapter.setItemIdle(alert.labelCode)
                    Toast.makeText(this, "Network error. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // Navigation — always go home, clearing any stale AlertActivity from stack
    // -------------------------------------------------------------------------

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    // -------------------------------------------------------------------------
    // Dismiss
    // -------------------------------------------------------------------------

    private fun dismissAlert(alert: PendingAlert) {
        AlertHistoryStore.save(this, AlertHistoryItem(
            message     = alert.message,
            companyCode = alert.companyCode,
            labelCode   = alert.labelCode,
            timestamp   = System.currentTimeMillis(),
            status      = AlertStatus.DISMISSED
        ))
        AlertQueueStore.removeByLabelCode(this, alert.labelCode)
        nm.cancel(alert.notificationId)
        Toast.makeText(this,
            "Not taking this call. Other associates can still respond.",
            Toast.LENGTH_SHORT).show()
        refreshList()
    }
}
