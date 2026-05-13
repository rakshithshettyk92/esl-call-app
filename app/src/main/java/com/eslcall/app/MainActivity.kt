package com.eslcall.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Username + company/store live in Session (see Session.kt). FCM topic
    // subscription is handled there too — the relay routes per-store now.

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this,
                "Notifications disabled — alerts won't appear", Toast.LENGTH_LONG).show()
        }

    // Views
    private lateinit var layoutLogin:       LinearLayout
    private lateinit var layoutReady:       LinearLayout
    private lateinit var layoutLastAlert:   LinearLayout
    private lateinit var etUsername:        EditText
    private lateinit var etPassword:        EditText
    private lateinit var btnLogin:          Button
    private lateinit var progressLogin:     CircularProgressIndicator
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var tvLoginError:      TextView
    private lateinit var tvReadyUser:       TextView
    private lateinit var tvUserAvatar:      TextView
    private lateinit var tvStatus:          TextView
    private lateinit var tvLastAlertMessage:TextView
    private lateinit var tvLastAlertTime:   TextView
    private lateinit var viewPulseRing:     View
    private lateinit var btnHistory:            Button
    private lateinit var btnTestAlert:          Button
    private lateinit var btnLogout:             Button
    private lateinit var btnAdmin:              Button
    private lateinit var btnSwitchStore:        Button
    private lateinit var tvCurrentStore:        TextView
    private lateinit var layoutActiveCalls:    LinearLayout
    private lateinit var tvActiveCallsCount:   TextView
    private lateinit var tvActiveCountStatus:  TextView
    private lateinit var viewActiveCallsPulse: View
    private lateinit var btnRespondNow:        Button

    private var pulseAnimator:       AnimatorSet? = null
    private var activeCallsAnimator: AnimatorSet? = null

    private val activeListReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshActiveCalls()
            refreshLastAlert()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        askNotificationPermission()

        btnLogin.setOnClickListener  { attemptLogin() }
        btnLogout.setOnClickListener { attemptLogout() }
        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        btnRespondNow.setOnClickListener {
            startActivity(Intent(this, ActiveCallsActivity::class.java))
        }
        btnAdmin.setOnClickListener {
            startActivity(Intent(this, FieldMappingActivity::class.java))
        }
        btnSwitchStore.setOnClickListener {
            Session.clearStore(this)
            startActivity(Intent(this, StoreSelectionActivity::class.java))
        }
        btnTestAlert.setOnClickListener {
            // AlertActivity is queue-driven; the bare EXTRA_MESSAGE intent
            // would just be ignored. Enqueue a fake alert and let the normal
            // pipeline render it. Blank company/label means "On My Way" will
            // dismiss locally without calling the relay.
            AlertQueueStore.enqueue(this, PendingAlert(
                id             = java.util.UUID.randomUUID().toString(),
                message        = "Test — Shelf A3, Aisle 2",
                companyCode    = "",
                labelCode      = "",
                receivedAt     = System.currentTimeMillis(),
                notificationId = MyFirebaseMessagingService.ALERT_NOTIFICATION_ID,
            ))
            startActivity(Intent(this, AlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }

        // Password visibility toggle
        var passwordVisible = false
        btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            val pos = etPassword.selectionEnd
            if (passwordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_off)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye)
            }
            etPassword.setSelection(pos)
        }

        checkAuthStatus()
    }

    override fun onResume() {
        super.onResume()
        AppForegroundTracker.isInForeground = true
        ContextCompat.registerReceiver(
            this, activeListReceiver,
            IntentFilter(MyFirebaseMessagingService.ACTION_ACTIVE_LIST_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Apply UI state from Session. Done in onResume (not just onCreate) so
        // returning from the store picker swaps the login layout for the ready
        // screen even when we never finished MainActivity.
        val user = Session.username(this)
        when {
            user != null && Session.hasStoreSelected(this) -> {
                Session.resubscribeCurrentTopic(this)
                showReadyState(user)
            }
            user != null -> {
                // Logged in but no store picked yet — force the picker.
                startActivity(Intent(this, StoreSelectionActivity::class.java))
            }
            else -> {
                // Signed out (could be a fresh start or returning from store picker
                // after backing out). Make sure the login layout is the visible one.
                showLoginState()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        AppForegroundTracker.isInForeground = false
        try { unregisterReceiver(activeListReceiver) } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // Auth — Login
    // -------------------------------------------------------------------------

    private fun attemptLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            showLoginError("Enter username and password")
            return
        }

        btnLogin.isEnabled = false
        btnLogin.text      = ""
        progressLogin.visibility = View.VISIBLE

        Thread {
            try {
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }.toString()

                val result  = postToRelay("/auth/login", body)
                val success = result.optString("status") == "ok"

                runOnUiThread {
                    btnLogin.isEnabled       = true
                    btnLogin.text            = "Sign In"
                    progressLogin.visibility = View.GONE
                    if (success) {
                        tvLoginError.visibility = View.GONE
                        Session.setUsername(this, username)
                        routePostLogin(username)
                    } else {
                        showLoginError("Invalid credentials")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnLogin.isEnabled       = true
                    btnLogin.text            = "Sign In"
                    progressLogin.visibility = View.GONE
                    showLoginError("Could not connect to server")
                }
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // Auth — Logout
    // -------------------------------------------------------------------------

    private fun attemptLogout() {
        btnLogout.isEnabled = false

        Thread {
            try { postToRelay("/auth/logout", "{}") } catch (_: Exception) {}

            runOnUiThread {
                btnLogout.isEnabled = true
                Session.clear(this)
                showLoginState()
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // Auth — Status check on app open
    // -------------------------------------------------------------------------

    private fun checkAuthStatus() {
        Thread {
            try {
                val result   = getFromRelay("/auth/status")
                val loggedIn = result.optBoolean("loggedIn", false)
                val username = result.optString("username", "")

                runOnUiThread {
                    if (loggedIn && username.isNotBlank()) {
                        Session.setUsername(this, username)
                        routePostLogin(username)
                    } else {
                        Session.clear(this)
                        showLoginState()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val savedUser = Session.username(this)
                    if (savedUser != null) routePostLogin(savedUser)
                    else showLoginState()
                }
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // UI state
    // -------------------------------------------------------------------------

    private fun showLoginState() {
        layoutLogin.visibility  = View.VISIBLE
        layoutReady.visibility  = View.GONE
        etUsername.text.clear()
        etPassword.text.clear()
        tvLoginError.visibility = View.GONE
        stopPulse()
        stopActiveCallsPulse()
    }

    /**
     * Routes after successful auth. If the user has not picked a store yet, send
     * them to the store picker — alerts only flow once a per-store FCM topic is
     * subscribed.
     */
    private fun routePostLogin(username: String) {
        if (!Session.hasStoreSelected(this)) {
            startActivity(Intent(this, StoreSelectionActivity::class.java))
            return
        }
        Session.resubscribeCurrentTopic(this)
        showReadyState(username)
    }

    private fun showReadyState(username: String) {
        layoutLogin.visibility = View.GONE
        layoutReady.visibility = View.VISIBLE
        tvReadyUser.text       = username
        tvUserAvatar.text      = username.first().uppercaseChar().toString()
        tvStatus.text          = "Ready — Listening for calls"
        val store = Session.storeName(this) ?: Session.storeCode(this).orEmpty()
        val co    = Session.companyCode(this).orEmpty()
        tvCurrentStore.text    = if (store.isNotEmpty()) "$co • $store" else co
        startPulse()
        refreshLastAlert()
        refreshActiveCalls()
    }

    private fun showLoginError(message: String) {
        tvLoginError.text       = message
        tvLoginError.visibility = View.VISIBLE
    }

    // -------------------------------------------------------------------------
    // Last alert display
    // -------------------------------------------------------------------------

    private fun refreshLastAlert() {
        val history = AlertHistoryStore.load(this)
        if (history.isNotEmpty()) {
            val latest = history.first()
            tvLastAlertMessage.text  = latest.message
            tvLastAlertTime.text     = "${latest.relativeDay()}, ${latest.formattedTimeOnly()}"
            layoutLastAlert.visibility = View.VISIBLE
        }
    }

    // -------------------------------------------------------------------------
    // Active calls card
    // -------------------------------------------------------------------------

    private fun refreshActiveCalls() {
        // Purge alerts whose timeout window has already passed
        val now = System.currentTimeMillis()
        AlertQueueStore.loadAll(this)
            .filter { !AcknowledgedStore.isAcknowledged(this, it.labelCode) }
            .filter { (now - it.receivedAt) >= Constants.ALERT_TIMEOUT_MS }
            .forEach { alert ->
                AlertHistoryStore.save(this, AlertHistoryItem(
                    message     = alert.message,
                    companyCode = alert.companyCode,
                    labelCode   = alert.labelCode,
                    timestamp   = now,
                    status      = AlertStatus.MISSED
                ))
                AlertQueueStore.removeByLabelCode(this, alert.labelCode)
                (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .cancel(alert.notificationId)
            }

        val count = AlertQueueStore.loadAll(this)
            .filter { !AcknowledgedStore.isAcknowledged(this, it.labelCode) }
            .size

        // Inline status count (always visible in the status card)
        if (::tvActiveCountStatus.isInitialized) {
            if (count > 0) {
                tvActiveCountStatus.text      = "● $count active call${if (count > 1) "s" else ""}"
                tvActiveCountStatus.setTextColor(0xFFC62828.toInt())
            } else {
                tvActiveCountStatus.text      = "No active calls"
                tvActiveCountStatus.setTextColor(
                    resources.getColor(R.color.text_secondary, theme))
            }
        }

        // Active calls card + red pulse
        if (count > 0) {
            tvActiveCallsCount.text      = "$count Active Call${if (count > 1) "s" else ""}"
            layoutActiveCalls.visibility = View.VISIBLE
            startActiveCallsPulse()
        } else {
            layoutActiveCalls.visibility = View.GONE
            stopActiveCallsPulse()
            // All alerts handled — clear any lingering grouped notification from shade
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .cancel(MyFirebaseMessagingService.GROUPED_NOTIFICATION_ID)
        }
    }

    private fun startActiveCallsPulse() {
        if (activeCallsAnimator?.isRunning == true) return
        val scaleX = ObjectAnimator.ofFloat(viewActiveCallsPulse, "scaleX", 1f, 2.2f)
        val scaleY = ObjectAnimator.ofFloat(viewActiveCallsPulse, "scaleY", 1f, 2.2f)
        val alpha  = ObjectAnimator.ofFloat(viewActiveCallsPulse, "alpha", 0.7f, 0f)
        listOf(scaleX, scaleY, alpha).forEach {
            it.repeatCount  = ObjectAnimator.INFINITE
            it.duration     = 900
            it.interpolator = AccelerateDecelerateInterpolator()
        }
        activeCallsAnimator = AnimatorSet().apply { playTogether(scaleX, scaleY, alpha) }
        activeCallsAnimator?.start()
    }

    private fun stopActiveCallsPulse() {
        activeCallsAnimator?.cancel()
        activeCallsAnimator = null
        if (::viewActiveCallsPulse.isInitialized) {
            viewActiveCallsPulse.scaleX = 1f
            viewActiveCallsPulse.scaleY = 1f
            viewActiveCallsPulse.alpha  = 0.7f
        }
    }

    // -------------------------------------------------------------------------
    // Pulse animation
    // -------------------------------------------------------------------------

    private fun startPulse() {
        stopPulse()
        val scaleX = ObjectAnimator.ofFloat(viewPulseRing, "scaleX", 1f, 2f)
        val scaleY = ObjectAnimator.ofFloat(viewPulseRing, "scaleY", 1f, 2f)
        val alpha  = ObjectAnimator.ofFloat(viewPulseRing, "alpha", 0.6f, 0f)

        listOf(scaleX, scaleY, alpha).forEach {
            it.repeatCount  = ObjectAnimator.INFINITE
            it.duration     = 1_200
            it.interpolator = AccelerateDecelerateInterpolator()
        }

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
        pulseAnimator?.start()
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        viewPulseRing.scaleX = 1f
        viewPulseRing.scaleY = 1f
        viewPulseRing.alpha  = 0.6f
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPulse()
        stopActiveCallsPulse()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun bindViews() {
        layoutLogin        = findViewById(R.id.layoutLogin)
        layoutReady        = findViewById(R.id.layoutReady)
        layoutLastAlert    = findViewById(R.id.layoutLastAlert)
        etUsername         = findViewById(R.id.etUsername)
        etPassword         = findViewById(R.id.etPassword)
        btnLogin           = findViewById(R.id.btnLogin)
        progressLogin      = findViewById(R.id.progressLogin)
        btnTogglePassword  = findViewById(R.id.btnTogglePassword)
        tvLoginError       = findViewById(R.id.tvLoginError)
        tvReadyUser        = findViewById(R.id.tvReadyUser)
        tvUserAvatar       = findViewById(R.id.tvUserAvatar)
        tvStatus           = findViewById(R.id.tvStatus)
        tvLastAlertMessage = findViewById(R.id.tvLastAlertMessage)
        tvLastAlertTime    = findViewById(R.id.tvLastAlertTime)
        viewPulseRing      = findViewById(R.id.viewPulseRing)
        btnHistory            = findViewById(R.id.btnHistory)
        btnTestAlert          = findViewById(R.id.btnTestAlert)
        btnLogout             = findViewById(R.id.btnLogout)
        layoutActiveCalls    = findViewById(R.id.layoutActiveCalls)
        tvActiveCallsCount   = findViewById(R.id.tvActiveCallsCount)
        tvActiveCountStatus  = findViewById(R.id.tvActiveCountStatus)
        viewActiveCallsPulse = findViewById(R.id.viewActiveCallsPulse)
        btnRespondNow        = findViewById(R.id.btnRespondNow)
        btnAdmin             = findViewById(R.id.btnAdmin)
        btnSwitchStore       = findViewById(R.id.btnSwitchStore)
        tvCurrentStore       = findViewById(R.id.tvCurrentStore)
    }

    private fun postToRelay(path: String, body: String): JSONObject {
        val conn = (URL("${Constants.RELAY_URL}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty(Constants.AUTH_HEADER, Constants.AUTH_KEY)
            doOutput = true
            connectTimeout = 10_000
            readTimeout    = 10_000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
        val response = conn.inputStream.bufferedReader().readText()
        return JSONObject(response)
    }

    private fun getFromRelay(path: String): JSONObject {
        val conn = (URL("${Constants.RELAY_URL}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty(Constants.AUTH_HEADER, Constants.AUTH_KEY)
            connectTimeout = 10_000
            readTimeout    = 10_000
        }
        val response = conn.inputStream.bufferedReader().readText()
        return JSONObject(response)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
