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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shows all currently active (un-acknowledged) alerts in a scrollable list.
 * Launched automatically when 2+ alerts are queued simultaneously.
 * Each item has individual "On My Way" and "Dismiss" buttons.
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

        tvSubtitle  = findViewById(R.id.tvActiveCallsSubtitle)
        layoutEmpty = findViewById(R.id.layoutActiveEmpty)
        recycler    = findViewById(R.id.recyclerActiveCalls)

        findViewById<ImageButton>(R.id.btnBackActiveCalls).setOnClickListener { navigateToMain() }

        adapter = ActiveCallsAdapter(
            items        = emptyList(),
            onAcknowledge = { alert -> acknowledgeAlert(alert) },
            onDismiss     = { alert -> dismissAlert(alert) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter       = adapter
    }

    override fun onResume() {
        super.onResume()
        AppForegroundTracker.isInForeground = true
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
        AppForegroundTracker.isInForeground = false
        tickHandler.removeCallbacks(tickRunnable)
        unregisterReceiver(refreshReceiver)
        unregisterReceiver(cancelReceiver)
    }

    // -------------------------------------------------------------------------
    // List management
    // -------------------------------------------------------------------------

    private fun refreshList() {
        // Expire any alerts whose 60-second window has already passed
        val now = System.currentTimeMillis()
        AlertQueueStore.loadAll(this)
            .filter { !AcknowledgedStore.isAcknowledged(this, it.labelCode) }
            .filter  { (now - it.receivedAt) >= Constants.ALERT_TIMEOUT_MS }
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

        tvSubtitle.text = if (alerts.isEmpty()) "All handled"
        else "${alerts.size} call${if (alerts.size > 1) "s" else ""} waiting for response"

        if (alerts.isEmpty()) {
            nm.cancel(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID)
            navigateToMain()
        } else {
            recycler.visibility    = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            adapter.updateItems(alerts)
            updateGroupedNotification(alerts)
        }
    }

    private fun updateGroupedNotification(alerts: List<PendingAlert>) {
        val count = alerts.size
        val style = androidx.core.app.NotificationCompat.InboxStyle()
            .setBigContentTitle("$count Active Employee Call${if (count > 1) "s" else ""}")
        alerts.forEach { style.addLine(it.message) }

        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, ActiveCallsActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notif = androidx.core.app.NotificationCompat
            .Builder(this, MyFirebaseMessagingService.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF001C3D.toInt())
            .setContentTitle("$count Active Employee Call${if (count > 1) "s" else ""}")
            .setContentText("Tap to view and respond")
            .setStyle(style)
            // Low priority — user is already on this screen, no heads-up banner needed.
            // The notification stays in the shade to maintain the app icon badge count.
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setNumber(count)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
        nm.notify(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID, notif)
    }

    // -------------------------------------------------------------------------
    // Acknowledge
    // -------------------------------------------------------------------------

    private fun acknowledgeAlert(alert: PendingAlert) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("companyCode", alert.companyCode)
                    put("labelCode",   alert.labelCode)
                }.toString()

                val conn = (URL("${Constants.RELAY_URL}/esl/acknowledge")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty(Constants.AUTH_HEADER, Constants.AUTH_KEY)
                    doOutput        = true
                    connectTimeout  = 10_000
                    readTimeout     = 10_000
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                val code = conn.responseCode
                if (code < 400) conn.inputStream.bufferedReader().readText()
                else conn.errorStream.bufferedReader().readText()

                runOnUiThread {
                    when (code) {
                        200 -> {
                            AcknowledgedStore.markAcknowledged(this, alert.labelCode)
                            AlertHistoryStore.save(this, AlertHistoryItem(
                                message     = alert.message,
                                companyCode = alert.companyCode,
                                labelCode   = alert.labelCode,
                                timestamp   = System.currentTimeMillis(),
                                status      = AlertStatus.ACKNOWLEDGED
                            ))
                            AlertQueueStore.removeByLabelCode(this, alert.labelCode)
                            nm.cancel(alert.notificationId)
                            refreshList()
                        }
                        409 -> {
                            // Already handled by another device
                            AlertQueueStore.removeByLabelCode(this, alert.labelCode)
                            refreshList()
                            Toast.makeText(this,
                                "Already acknowledged by another device",
                                Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            adapter.setItemIdle(alert.labelCode)
                            Toast.makeText(this,
                                "Could not reach server — try again",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adapter.setItemIdle(alert.labelCode)
                    Toast.makeText(this, "Network error — try again", Toast.LENGTH_SHORT).show()
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

    @Deprecated("Overriding for back behaviour")
    override fun onBackPressed() = navigateToMain()

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
        refreshList()
    }
}
