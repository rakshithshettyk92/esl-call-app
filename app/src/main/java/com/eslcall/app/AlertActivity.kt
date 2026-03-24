package com.eslcall.app

import android.app.NotificationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        const val EXTRA_MESSAGE      = "extra_message"
        const val EXTRA_COMPANY_CODE = "extra_company_code"
        const val EXTRA_LABEL_CODE   = "extra_label_code"
        private const val AUTO_DISMISS_MS = 60_000L
    }

    private val autoDismissHandler  = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { finish() }

    private lateinit var btnOnMyWay: Button
    private lateinit var btnDismiss: Button

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

        btnOnMyWay = findViewById(R.id.btnOnMyWay)
        btnDismiss = findViewById(R.id.btnDismiss)

        applyIntent(intent)

        // Both close button (top-right ✕) and Dismiss button close without any action
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        btnDismiss.setOnClickListener { finish() }

        autoDismissHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.let { applyIntent(it) }
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        autoDismissHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
    }

    private fun applyIntent(intent: android.content.Intent) {
        val message     = intent.getStringExtra(EXTRA_MESSAGE)      ?: "Employee Call"
        val companyCode = intent.getStringExtra(EXTRA_COMPANY_CODE) ?: ""
        val labelCode   = intent.getStringExtra(EXTRA_LABEL_CODE)   ?: ""

        findViewById<TextView>(R.id.tvAlertMessage).text = message

        // Reset button state in case activity is reused
        btnOnMyWay.isEnabled = true
        btnOnMyWay.text      = "On My Way"

        btnOnMyWay.setOnClickListener {
            if (companyCode.isNotBlank() && labelCode.isNotBlank()) {
                triggerAcknowledge(companyCode, labelCode)
            } else {
                // No ESL data — just close
                finish()
            }
        }
    }

    private fun triggerAcknowledge(companyCode: String, labelCode: String) {
        btnOnMyWay.isEnabled = false
        btnOnMyWay.text      = "Sending..."
        btnDismiss.isEnabled = false

        // Dismiss the tray notification
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(MyFirebaseMessagingService.ALERT_NOTIFICATION_ID)

        Thread {
            val success = try {
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
                    readTimeout = 10_000
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                conn.inputStream.bufferedReader().readText()
                true
            } catch (e: Exception) {
                false
            }

            runOnUiThread {
                if (success) {
                    btnOnMyWay.text = "On My Way ✓"
                    // Close alert after brief confirmation
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1_500)
                } else {
                    btnOnMyWay.isEnabled = true
                    btnOnMyWay.text      = "On My Way"
                    btnDismiss.isEnabled = true
                    Toast.makeText(this, "Could not reach server — try again", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
    }
}
