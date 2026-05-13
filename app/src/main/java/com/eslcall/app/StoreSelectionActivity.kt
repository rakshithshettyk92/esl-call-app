package com.eslcall.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Two-step flow: enter company code → load stores via the relay → pick one.
 * Saving the store subscribes the device to the per-store FCM topic so alerts
 * start arriving immediately.
 */
class StoreSelectionActivity : AppCompatActivity() {

    private lateinit var etCompany:        TextInputEditText
    private lateinit var btnLoadStores:    Button
    private lateinit var progress:         CircularProgressIndicator
    private lateinit var storeInputLayout: TextInputLayout
    private lateinit var storeInput:       MaterialAutoCompleteTextView
    private lateinit var tvStoreCount:     TextView
    private lateinit var tvError:          TextView
    private lateinit var btnContinue:      Button
    private lateinit var toolbar:          MaterialToolbar

    private var stores:   List<Store> = emptyList()
    private var selected: Store?      = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_selection)

        toolbar          = findViewById(R.id.toolbar)
        etCompany        = findViewById(R.id.etCompany)
        btnLoadStores    = findViewById(R.id.btnLoadStores)
        progress         = findViewById(R.id.progress)
        storeInputLayout = findViewById(R.id.storeInputLayout)
        storeInput       = findViewById(R.id.storeInput)
        tvStoreCount     = findViewById(R.id.tvStoreCount)
        tvError          = findViewById(R.id.tvError)
        btnContinue      = findViewById(R.id.btnContinue)

        // Pre-fill if we already had a company saved (e.g. user is switching stores).
        Session.companyCode(this)?.let { etCompany.setText(it) }

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnLoadStores.setOnClickListener { loadStores() }
        btnContinue.setOnClickListener   { onContinue() }

        // Resolve selection by name OR code as the user types/picks.
        storeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim().orEmpty()
                selected = stores.firstOrNull {
                    it.display().equals(input, ignoreCase = true) ||
                    it.code.equals(input, ignoreCase = true) ||
                    it.name.equals(input, ignoreCase = true)
                }
                btnContinue.isEnabled = selected != null
                if (selected != null) tvError.visibility = View.GONE
            }
        })
    }

    private fun loadStores() {
        val company = etCompany.text?.toString()?.trim().orEmpty().uppercase()
        if (company.isEmpty()) {
            showError("Enter a company code")
            return
        }

        progress.visibility       = View.VISIBLE
        btnLoadStores.isEnabled   = false
        btnContinue.isEnabled     = false
        storeInputLayout.isEnabled = false
        tvError.visibility        = View.GONE

        Thread {
            try {
                val list = RelayApi.fetchStores(company)
                runOnUiThread {
                    stores = list
                    val adapter = ArrayAdapter(this,
                        android.R.layout.simple_list_item_1,
                        list.map { it.display() })
                    storeInput.setAdapter(adapter)
                    storeInputLayout.isEnabled = list.isNotEmpty()
                    tvStoreCount.text       = "${list.size} store${if (list.size == 1) "" else "s"}"
                    tvStoreCount.visibility = View.VISIBLE
                    progress.visibility     = View.GONE
                    btnLoadStores.isEnabled = true
                    if (list.isEmpty()) showError("No stores found for $company")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility     = View.GONE
                    btnLoadStores.isEnabled = true
                    showError(e.message ?: "Failed to load stores")
                }
            }
        }.start()
    }

    private fun onContinue() {
        val store   = selected ?: return
        val company = etCompany.text?.toString()?.trim().orEmpty().uppercase()
        Session.setStore(this, company, store.code, store.name)
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }
}
