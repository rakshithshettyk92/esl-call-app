package com.eslcall.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Full-screen alert shown when an ESL button press is received.
 * - "On My Way" triggers ESL actions (page flip + LED) via the relay
 * - "Dismiss" closes the popup without triggering any actions
 */
class AlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MESSAGE         = "extra_message"
        const val EXTRA_COMPANY_CODE    = "extra_company_code"
        const val EXTRA_LABEL_CODE      = "extra_label_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val AUTO_DISMISS_MS = 60_000L
    }

    private var countDownTimer:      CountDownTimer? = null
    private var currentLabelCode:    String = ""
    private var currentNotificationId: Int  = MyFirebaseMessagingService.ALERT_NOTIFICATION_ID

    // Receives the cancel broadcast — only acts if it matches our label
    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cancelLabel = intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_CANCEL_LABEL_CODE) ?: ""
            if (cancelLabel == currentLabelCode) {
                countDownTimer?.cancel()
                showAlreadyAcknowledged()
            }
        }
    }

    private lateinit var btnOnMyWay:        Button
    private lateinit var btnDismiss:        Button
    private lateinit var progressCountdown: CircularProgressIndicator
    private lateinit var tvCountdown:       TextView
    private lateinit var tvAutoDismiss:     TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alert)

        btnOnMyWay        = findViewById(R.id.btnOnMyWay)
        btnDismiss        = findViewById(R.id.btnDismiss)
        progressCountdown = findViewById(R.id.progressCountdown)
        tvCountdown       = findViewById(R.id.tvCountdown)
        tvAutoDismiss     = findViewById(R.id.tvAutoDismiss)

        applyIntent(intent)

        // Both close button (top-right ✕) and Dismiss button close without any action
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        btnDismiss.setOnClickListener { finish() }

        // Listen for cancel broadcasts from other devices acknowledging the same alert
        ContextCompat.registerReceiver(
            this,
            cancelReceiver,
            IntentFilter(MyFirebaseMessagingService.ACTION_CANCEL_ALERT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        startCountdown()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.let { applyIntent(it) }
        restartCountdown()
    }

    private fun applyIntent(intent: android.content.Intent) {
        val message     = intent.getStringExtra(EXTRA_MESSAGE)         ?: "Employee Call"
        val companyCode = intent.getStringExtra(EXTRA_COMPANY_CODE)    ?: ""
        val labelCode   = intent.getStringExtra(EXTRA_LABEL_CODE)      ?: ""
        currentLabelCode     = labelCode
        currentNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID,
            MyFirebaseMessagingService.ALERT_NOTIFICATION_ID)

        // If already acknowledged (e.g. user tapped banner "On My Way" before opening)
        if (AcknowledgedStore.isAcknowledged(this, labelCode)) {
            showAlreadyAcknowledged()
            return
        }

        findViewById<TextView>(R.id.tvAlertMessage).text = message

        // Reset button state in case activity is reused
        btnOnMyWay.isEnabled = true
        btnOnMyWay.text      = "On My Way"

        btnOnMyWay.setOnClickListener {
            if (companyCode.isNotBlank() && labelCode.isNotBlank()) {
                triggerAcknowledge(companyCode, labelCode, message)
            } else {
                finish()
            }
        }
    }

    private fun startCountdown() {
        val totalMs = AUTO_DISMISS_MS
        countDownTimer = object : CountDownTimer(totalMs, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1_000).toInt()
                val progress    = (millisUntilFinished * 100 / totalMs).toInt()
                tvCountdown.text       = secondsLeft.toString()
                tvAutoDismiss.text     = "Auto-closing in ${secondsLeft}s"
                progressCountdown.progress = progress
            }
            override fun onFinish() {
                finish()
            }
        }.start()
    }

    private fun restartCountdown() {
        countDownTimer?.cancel()
        startCountdown()
    }

    private fun triggerAcknowledge(companyCode: String, labelCode: String, message: String) {
        btnOnMyWay.isEnabled = false
        btnOnMyWay.text      = "Sending..."
        btnDismiss.isEnabled = false
        countDownTimer?.cancel()

        // Dismiss the tray notification for this specific label
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(currentNotificationId)

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
                val responseBody = if (responseCode < 400)
                    conn.inputStream.bufferedReader().readText()
                else
                    conn.errorStream.bufferedReader().readText()

                runOnUiThread {
                    when (responseCode) {
                        200 -> {
                            AcknowledgedStore.markAcknowledged(this, labelCode)
                            AlertHistoryStore.save(
                                this,
                                AlertHistoryItem(
                                    message     = message,
                                    companyCode = companyCode,
                                    labelCode   = labelCode,
                                    timestamp   = System.currentTimeMillis()
                                )
                            )
                            btnOnMyWay.text = "On My Way ✓"
                            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1_500)
                        }
                        409 -> {
                            // Already acknowledged by another device
                            showAlreadyAcknowledged()
                        }
                        else -> {
                            btnOnMyWay.isEnabled = true
                            btnOnMyWay.text      = "On My Way"
                            btnDismiss.isEnabled = true
                            startCountdown()
                            Toast.makeText(this, "Could not reach server — try again", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnOnMyWay.isEnabled = true
                    btnOnMyWay.text      = "On My Way"
                    btnDismiss.isEnabled = true
                    startCountdown()
                    Toast.makeText(this, "Could not reach server — try again", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showAlreadyAcknowledged() {
        countDownTimer?.cancel()
        btnOnMyWay.isEnabled = false
        btnOnMyWay.text      = "Already Acknowledged"
        btnDismiss.isEnabled = true
        tvAutoDismiss.text   = "Acknowledged by another device"
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2_500)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        unregisterReceiver(cancelReceiver)
    }
}
