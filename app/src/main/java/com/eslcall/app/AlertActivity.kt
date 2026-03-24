package com.eslcall.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Full-screen alert shown when an ESL button press is received.
 *
 * Driven by AlertQueueStore — all incoming alerts are queued and shown
 * one at a time. Dismissing / acknowledging the current alert automatically
 * loads the next one. No alerts are silently lost.
 */
class AlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MESSAGE         = "extra_message"
        const val EXTRA_COMPANY_CODE    = "extra_company_code"
        const val EXTRA_LABEL_CODE      = "extra_label_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private val AUTO_DISMISS_MS = Constants.ALERT_TIMEOUT_MS
    }

    private var countDownTimer:         CountDownTimer? = null
    private var currentLabelCode:       String = ""
    private var currentNotifId:         Int    = MyFirebaseMessagingService.ALERT_NOTIFICATION_ID
    private var currentReceivedAt:      Long   = 0L
    private var isTransitioningToList:  Boolean = false

    private var ringtone:               Ringtone? = null
    private val ringtoneHandler         = Handler(Looper.getMainLooper())

    // When 2nd alert arrives, transition to the active calls list screen
    private val switchToListReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isTransitioningToList = true
            startActivity(Intent(this@AlertActivity, ActiveCallsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }

    // Listens for cancel broadcasts (another device acknowledged, or banner On My Way)
    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cancelLabel = intent
                ?.getStringExtra(MyFirebaseMessagingService.EXTRA_CANCEL_LABEL_CODE) ?: ""
            when {
                cancelLabel == currentLabelCode -> {
                    // Current alert was handled elsewhere
                    countDownTimer?.cancel()
                    showAlreadyAcknowledged()
                }
                cancelLabel.isNotBlank() -> {
                    // A queued (not yet shown) alert was handled elsewhere — remove it
                    AlertQueueStore.removeByLabelCode(this@AlertActivity, cancelLabel)
                    updatePendingBadge()
                }
            }
        }
    }

    private lateinit var btnOnMyWay:        Button
    private lateinit var btnDismiss:        Button
    private lateinit var progressCountdown: CircularProgressIndicator
    private lateinit var tvCountdown:       TextView
    private lateinit var tvAutoDismiss:     TextView
    private lateinit var layoutPendingBadge:LinearLayout
    private lateinit var tvPendingCount:    TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alert)

        btnOnMyWay         = findViewById(R.id.btnOnMyWay)
        btnDismiss         = findViewById(R.id.btnDismiss)
        progressCountdown  = findViewById(R.id.progressCountdown)
        tvCountdown        = findViewById(R.id.tvCountdown)
        tvAutoDismiss      = findViewById(R.id.tvAutoDismiss)
        layoutPendingBadge = findViewById(R.id.layoutPendingBadge)
        tvPendingCount     = findViewById(R.id.tvPendingCount)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { navigateToMain() }
        btnDismiss.setOnClickListener { dismissCurrent(AlertStatus.DISMISSED) }

        ContextCompat.registerReceiver(
            this, cancelReceiver,
            IntentFilter(MyFirebaseMessagingService.ACTION_CANCEL_ALERT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, switchToListReceiver,
            IntentFilter(MyFirebaseMessagingService.ACTION_SWITCH_TO_LIST),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        showNextFromQueue()
    }

    // onNewIntent is called when a second alert arrives while this activity is at the top.
    // The new alert is already enqueued by MyFirebaseMessagingService — just update the badge.
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        updatePendingBadge()
    }

    // -------------------------------------------------------------------------
    // Queue-driven display
    // -------------------------------------------------------------------------

    private fun showNextFromQueue() {
        val alert = AlertQueueStore.peek(this)
        if (alert == null) {
            finish()
            return
        }

        // Skip if already acknowledged (e.g. handled via banner on this device)
        if (AcknowledgedStore.isAcknowledged(this, alert.labelCode)) {
            AlertQueueStore.dequeue(this)
            showNextFromQueue()
            return
        }

        currentLabelCode  = alert.labelCode
        currentNotifId    = alert.notificationId
        currentReceivedAt = alert.receivedAt

        findViewById<TextView>(R.id.tvAlertMessage).text = alert.message

        btnOnMyWay.isEnabled = true
        btnOnMyWay.text      = "On My Way"
        btnDismiss.isEnabled = true

        btnOnMyWay.setOnClickListener {
            it.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.CONFIRM
                else
                    HapticFeedbackConstants.VIRTUAL_KEY
            )
            if (alert.companyCode.isNotBlank() && alert.labelCode.isNotBlank()) {
                triggerAcknowledge(alert.companyCode, alert.labelCode, alert.message)
            } else {
                dismissCurrent(AlertStatus.DISMISSED)
            }
        }

        updatePendingBadge()
        // Timer counts down the time remaining since the FCM message was received,
        // not since this screen opened.
        restartCountdown()
        startRingtone()
    }

    private fun dismissCurrent(status: AlertStatus) {
        stopRingtone()
        val alert = AlertQueueStore.dequeue(this)
        if (alert != null) {
            AlertHistoryStore.save(
                this, AlertHistoryItem(
                    message     = alert.message,
                    companyCode = alert.companyCode,
                    labelCode   = alert.labelCode,
                    timestamp   = System.currentTimeMillis(),
                    status      = status
                )
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(alert.notificationId)
        }

        countDownTimer?.cancel()

        if (AlertQueueStore.size(this) > 0) {
            showNextFromQueue()
        } else {
            finish()
        }
    }

    private fun updatePendingBadge() {
        val pending = AlertQueueStore.size(this) - 1   // minus the one currently shown
        if (pending > 0) {
            layoutPendingBadge.visibility = View.VISIBLE
            tvPendingCount.text =
                "⚡ $pending more alert${if (pending > 1) "s" else ""} waiting — handle this one first"
        } else {
            layoutPendingBadge.visibility = View.GONE
        }
    }

    // -------------------------------------------------------------------------
    // Countdown timer
    // -------------------------------------------------------------------------

    private fun restartCountdown() {
        countDownTimer?.cancel()
        val remainingMs = AUTO_DISMISS_MS - (System.currentTimeMillis() - currentReceivedAt)
        if (remainingMs <= 0) {
            // Time already expired while the screen wasn't open — mark missed immediately
            dismissCurrent(AlertStatus.MISSED)
            return
        }
        countDownTimer = object : CountDownTimer(remainingMs, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1_000).toInt()
                val progress    = (millisUntilFinished * 100 / AUTO_DISMISS_MS).toInt()
                tvCountdown.text           = secondsLeft.toString()
                tvAutoDismiss.text         = "Auto-closing in ${secondsLeft}s"
                progressCountdown.progress = progress
            }
            override fun onFinish() {
                dismissCurrent(AlertStatus.MISSED)
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // On My Way — call relay
    // -------------------------------------------------------------------------

    private fun triggerAcknowledge(companyCode: String, labelCode: String, message: String) {
        stopRingtone()
        btnOnMyWay.isEnabled = false
        btnOnMyWay.text      = "Sending..."
        btnDismiss.isEnabled = false
        countDownTimer?.cancel()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(currentNotifId)

        Thread {
            try {
                val body = JSONObject().apply {
                    put("companyCode", companyCode)
                    put("labelCode",   labelCode)
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

                val responseCode = conn.responseCode
                if (responseCode < 400) conn.inputStream.bufferedReader().readText()
                else conn.errorStream.bufferedReader().readText()

                runOnUiThread {
                    when (responseCode) {
                        200 -> {
                            AcknowledgedStore.markAcknowledged(this, labelCode)
                            AlertHistoryStore.save(
                                this, AlertHistoryItem(
                                    message     = message,
                                    companyCode = companyCode,
                                    labelCode   = labelCode,
                                    timestamp   = System.currentTimeMillis(),
                                    status      = AlertStatus.ACKNOWLEDGED
                                )
                            )
                            AlertQueueStore.dequeue(this)
                            btnOnMyWay.text = "On My Way ✓"
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (AlertQueueStore.size(this) > 0) {
                                    showNextFromQueue()
                                } else {
                                    finish()
                                }
                            }, 1_500)
                        }
                        409 -> showAlreadyAcknowledged()
                        else -> {
                            btnOnMyWay.isEnabled = true
                            btnOnMyWay.text      = "On My Way"
                            btnDismiss.isEnabled = true
                            restartCountdown()
                            Toast.makeText(this, "Could not reach server — try again",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnOnMyWay.isEnabled = true
                    btnOnMyWay.text      = "On My Way"
                    btnDismiss.isEnabled = true
                    restartCountdown()
                    Toast.makeText(this, "Could not reach server — try again",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // Already acknowledged (by another device or banner tap)
    // -------------------------------------------------------------------------

    private fun showAlreadyAcknowledged() {
        btnOnMyWay.isEnabled = false
        btnOnMyWay.text      = "Already Acknowledged"
        btnDismiss.isEnabled = true
        tvAutoDismiss.text   = "Acknowledged by another device"
        progressCountdown.progress = 0

        Handler(Looper.getMainLooper()).postDelayed({
            // Remove from queue without saving to history (another device handled it)
            AlertQueueStore.dequeue(this)
            if (AlertQueueStore.size(this) > 0) {
                showNextFromQueue()
            } else {
                finish()
            }
        }, 2_500)
    }

    // -------------------------------------------------------------------------
    // Back button = go home (keep alert in queue)
    // -------------------------------------------------------------------------

    @Deprecated("Overriding for back behaviour")
    override fun onBackPressed() {
        navigateToMain()
    }

    private fun navigateToMain() {
        stopRingtone()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    // -------------------------------------------------------------------------
    // Ringtone — plays for up to 30 s or until the user acts
    // -------------------------------------------------------------------------

    private fun startRingtone() {
        stopRingtone()  // don't stack multiple plays
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.also { rt ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                rt.play()
            }
            // Auto-stop after 30 seconds regardless of user action
            ringtoneHandler.postDelayed({ stopRingtone() }, 30_000)
        } catch (_: Exception) {}
    }

    private fun stopRingtone() {
        ringtoneHandler.removeCallbacksAndMessages(null)
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        AppForegroundTracker.isInForeground = true
    }

    override fun onPause() {
        super.onPause()
        AppForegroundTracker.isInForeground = false
        stopRingtone()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
        countDownTimer?.cancel()
        try { unregisterReceiver(cancelReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(switchToListReceiver) } catch (_: Exception) {}
        // Queue items are intentionally left intact so the Active Calls card on the
        // home screen can pick them up. MISSED is only recorded by the 60-second
        // CountDownTimer.onFinish(), never by a lifecycle event.
    }
}
