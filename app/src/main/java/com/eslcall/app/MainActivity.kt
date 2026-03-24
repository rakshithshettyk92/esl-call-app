package com.eslcall.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
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
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val FCM_TOPIC     = "employee-calls"
        private const val PREFS_NAME    = "esl_prefs"
        private const val PREF_USERNAME = "logged_in_user"
    }

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
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var tvLoginError:      TextView
    private lateinit var tvReadyUser:       TextView
    private lateinit var tvStatus:          TextView
    private lateinit var tvLastAlertMessage:TextView
    private lateinit var tvLastAlertTime:   TextView
    private lateinit var viewPulseRing:     View
    private lateinit var btnHistory:        Button
    private lateinit var btnTestAlert:      Button
    private lateinit var btnLogout:         Button

    private var pulseAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        askNotificationPermission()
        subscribeToFcmTopic()

        btnLogin.setOnClickListener { attemptLogin() }
        btnLogout.setOnClickListener { attemptLogout() }
        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        btnTestAlert.setOnClickListener {
            startActivity(Intent(this, AlertActivity::class.java).apply {
                putExtra(AlertActivity.EXTRA_MESSAGE, "Test — Shelf A3, Aisle 2")
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
        // Refresh last alert when returning from history or alert screen
        if (layoutReady.visibility == View.VISIBLE) {
            refreshLastAlert()
        }
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
        btnLogin.text = "Signing in..."

        Thread {
            try {
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }.toString()

                val result  = postToRelay("/auth/login", body)
                val success = result.optString("status") == "ok"

                runOnUiThread {
                    btnLogin.isEnabled = true
                    btnLogin.text      = "Sign In"
                    if (success) {
                        tvLoginError.visibility = View.GONE
                        saveUsername(username)
                        showReadyState(username)
                    } else {
                        showLoginError("Invalid credentials")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnLogin.isEnabled = true
                    btnLogin.text      = "Sign In"
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
                clearUsername()
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
                        saveUsername(username)
                        showReadyState(username)
                    } else {
                        clearUsername()
                        showLoginState()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val savedUser = getSavedUsername()
                    if (savedUser != null) showReadyState(savedUser)
                    else showLoginState()
                }
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // UI state
    // -------------------------------------------------------------------------

    private fun showLoginState() {
        layoutLogin.visibility = View.VISIBLE
        layoutReady.visibility = View.GONE
        etUsername.text.clear()
        etPassword.text.clear()
        tvLoginError.visibility = View.GONE
        stopPulse()
    }

    private fun showReadyState(username: String) {
        layoutLogin.visibility = View.GONE
        layoutReady.visibility = View.VISIBLE
        tvReadyUser.text       = "Signed in as $username"
        tvStatus.text          = "Ready — Listening for calls"
        startPulse()
        refreshLastAlert()
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
    // Pulse animation
    // -------------------------------------------------------------------------

    private fun startPulse() {
        stopPulse()
        val scaleX = ObjectAnimator.ofFloat(viewPulseRing, "scaleX", 1f, 2f)
        val scaleY = ObjectAnimator.ofFloat(viewPulseRing, "scaleY", 1f, 2f)
        val alpha  = ObjectAnimator.ofFloat(viewPulseRing, "alpha", 0.6f, 0f)

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration       = 1_200
            interpolator   = AccelerateDecelerateInterpolator()
            repeatCount    // set on individual animators below
        }

        listOf(scaleX, scaleY, alpha).forEach {
            it.repeatCount = ObjectAnimator.INFINITE
            it.duration    = 1_200
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
        btnTogglePassword  = findViewById(R.id.btnTogglePassword)
        tvLoginError       = findViewById(R.id.tvLoginError)
        tvReadyUser        = findViewById(R.id.tvReadyUser)
        tvStatus           = findViewById(R.id.tvStatus)
        tvLastAlertMessage = findViewById(R.id.tvLastAlertMessage)
        tvLastAlertTime    = findViewById(R.id.tvLastAlertTime)
        viewPulseRing      = findViewById(R.id.viewPulseRing)
        btnHistory         = findViewById(R.id.btnHistory)
        btnTestAlert       = findViewById(R.id.btnTestAlert)
        btnLogout          = findViewById(R.id.btnLogout)
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

    private fun saveUsername(username: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(PREF_USERNAME, username).apply()
    }

    private fun clearUsername() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(PREF_USERNAME).apply()
    }

    private fun getSavedUsername(): String? =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_USERNAME, null)

    private fun subscribeToFcmTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC)
            .addOnFailureListener {
                Toast.makeText(this, "FCM subscription failed", Toast.LENGTH_SHORT).show()
            }
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
