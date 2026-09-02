package com.eslcall.app

import android.os.Bundle
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class DeviceSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            applyStatusBarInset()
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        val alert = findViewById<TextInputEditText>(R.id.etAlertTimeout)
        val poll = findViewById<TextInputEditText>(R.id.etAuthPollInterval)
        val keepOn = findViewById<MaterialSwitch>(R.id.switchKeepScreenOn)
        val sessionTimeout = findViewById<MaterialAutoCompleteTextView>(R.id.dropdownSessionTimeout)
        val timeoutLabels = DeviceSettings.sessionTimeoutOptions
            .map(DeviceSettings::sessionTimeoutLabel)
        sessionTimeout.setAdapter(ArrayAdapter(this,
            android.R.layout.simple_list_item_1, timeoutLabels))
        sessionTimeout.setText(
            DeviceSettings.sessionTimeoutLabel(DeviceSettings.sessionTimeoutHours(this)), false)
        alert.setText((DeviceSettings.alertTimeoutMs(this) / 1_000).toString())
        poll.setText((DeviceSettings.authPollIntervalMs(this) / 1_000).toString())
        keepOn.isChecked = DeviceSettings.keepReadyScreenOn(this)

        findViewById<Button>(R.id.btnRestoreRecommended).setOnClickListener {
            alert.setText("60")
            poll.setText("300")
            keepOn.isChecked = true
            sessionTimeout.setText(DeviceSettings.sessionTimeoutLabel(0), false)
            alert.error = null
            poll.error = null
            sessionTimeout.error = null
            Toast.makeText(this, "Recommended choices loaded. Tap Save choices to apply them.",
                Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSaveDeviceSettings).setOnClickListener {
            val alertSeconds = alert.text?.toString()?.toIntOrNull()
            val pollSeconds = poll.text?.toString()?.toIntOrNull()
            val selectedTimeoutHours = timeoutLabels.indexOf(sessionTimeout.text.toString())
                .takeIf { it >= 0 }
                ?.let { DeviceSettings.sessionTimeoutOptions[it] }
            when {
                alertSeconds == null || alertSeconds !in 15..600 ->
                    alert.error = "Enter a time from 15 to 600 seconds"
                pollSeconds == null || pollSeconds !in 30..3600 ->
                    poll.error = "Enter a time from 30 to 3600 seconds"
                selectedTimeoutHours == null ->
                    sessionTimeout.error = "Choose when this device should sign out"
                else -> {
                    DeviceSettings.save(this, alertSeconds, pollSeconds, keepOn.isChecked,
                        selectedTimeoutHours)
                    Session.restartExpiryClock(this)
                    Toast.makeText(this, "Your choices were saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
