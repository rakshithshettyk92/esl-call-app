package com.eslcall.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

/**
 * Admin landing — two cards routing to the per-screen admin tools.
 * Lives in front of FieldMappingActivity and AnalyticsActivity so the user
 * always has one consistent "where do admin things live" answer.
 */
class AdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val co    = Session.companyCode(this).orEmpty()
        val store = Session.storeName(this) ?: Session.storeCode(this).orEmpty()
        findViewById<TextView>(R.id.tvScope).text =
            if (co.isNotEmpty() && store.isNotEmpty()) "Scope: $co • $store" else ""

        findViewById<MaterialCardView>(R.id.cardFieldMapping).setOnClickListener {
            startActivity(Intent(this, FieldMappingActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardAnalytics).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardDeviceSettings).setOnClickListener {
            startActivity(Intent(this, DeviceSettingsActivity::class.java))
        }
    }
}
