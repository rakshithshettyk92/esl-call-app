package com.eslcall.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

/**
 * Admin screen for the (company, store) field mapping. Dropdowns are populated
 * from /admin/articles/upload/format and the saved mapping comes from
 * /admin/field-mapping. Save POSTs back to the relay.
 */
class FieldMappingActivity : AppCompatActivity() {

    private lateinit var toolbar:              MaterialToolbar
    private lateinit var tvScope:              TextView
    private lateinit var progress:             CircularProgressIndicator
    private lateinit var articleIdInput:       MaterialAutoCompleteTextView
    private lateinit var articleNameInput:     MaterialAutoCompleteTextView
    private lateinit var helpEnabledFieldInput:MaterialAutoCompleteTextView
    private lateinit var helpEnabledValueInput:TextInputEditText
    private lateinit var aisleInput:           MaterialAutoCompleteTextView
    private lateinit var revertDelayInput:     TextInputEditText
    private lateinit var tvError:              TextView
    private lateinit var btnSave:              Button

    private var columns: List<String> = emptyList()

    private val company by lazy { Session.companyCode(this).orEmpty() }
    private val store   by lazy { Session.storeCode(this).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_field_mapping)

        toolbar               = findViewById(R.id.toolbar)
        tvScope               = findViewById(R.id.tvScope)
        progress              = findViewById(R.id.progress)
        articleIdInput        = findViewById(R.id.articleIdInput)
        articleNameInput      = findViewById(R.id.articleNameInput)
        helpEnabledFieldInput = findViewById(R.id.helpEnabledFieldInput)
        helpEnabledValueInput = findViewById(R.id.helpEnabledValueInput)
        aisleInput            = findViewById(R.id.aisleInput)
        revertDelayInput      = findViewById(R.id.revertDelayInput)
        tvError               = findViewById(R.id.tvError)
        btnSave               = findViewById(R.id.btnSave)

        if (company.isEmpty() || store.isEmpty()) {
            Toast.makeText(this, "Select a store first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvScope.text = "Scope: $company / ${Session.storeName(this) ?: store}"

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnSave.setOnClickListener { save() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshSaveEnabled() }
        }
        listOf(articleIdInput, articleNameInput, helpEnabledFieldInput, aisleInput).forEach {
            it.addTextChangedListener(watcher)
        }
        helpEnabledValueInput.addTextChangedListener(watcher)
        revertDelayInput.addTextChangedListener(watcher)

        loadConfig()
    }

    private fun loadConfig() {
        progress.visibility = View.VISIBLE
        btnSave.isEnabled   = false

        Thread {
            try {
                val cols    = RelayApi.fetchArticleColumns(company)
                val mapping = RelayApi.fetchFieldMapping(company, store)
                runOnUiThread {
                    columns = cols
                    attachAdapters()
                    bind(mapping)
                    progress.visibility = View.GONE
                    refreshSaveEnabled()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    showError(e.message ?: "Failed to load configuration")
                    // Allow editing with defaults even if the fetch failed.
                    bind(CallFieldMapping.DEFAULT)
                    refreshSaveEnabled()
                }
            }
        }.start()
    }

    private fun attachAdapters() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, columns)
        listOf(articleIdInput, articleNameInput, helpEnabledFieldInput, aisleInput).forEach {
            it.setAdapter(adapter)
        }
    }

    private fun bind(m: CallFieldMapping) {
        articleIdInput.setText(m.articleIdField, false)
        articleNameInput.setText(m.articleNameField, false)
        helpEnabledFieldInput.setText(m.helpEnabledField, false)
        helpEnabledValueInput.setText(m.helpEnabledValue)
        aisleInput.setText(m.aisleField.orEmpty(), false)
        revertDelayInput.setText(m.revertDelaySeconds.toString())
    }

    private fun current(): CallFieldMapping = CallFieldMapping(
        articleIdField     = articleIdInput.text.toString().trim(),
        articleNameField   = articleNameInput.text.toString().trim(),
        helpEnabledField   = helpEnabledFieldInput.text.toString().trim(),
        helpEnabledValue   = helpEnabledValueInput.text.toString().trim(),
        aisleField         = aisleInput.text.toString().trim().takeIf { it.isNotBlank() },
        revertDelaySeconds = revertDelayInput.text.toString().trim().toIntOrNull() ?: 60,
        allColumns         = columns,
    )

    private fun save() {
        val mapping = current()
        if (!mapping.isComplete()) {
            showError("Fill in the required fields")
            return
        }
        btnSave.isEnabled   = false
        progress.visibility = View.VISIBLE
        tvError.visibility  = View.GONE

        Thread {
            try {
                RelayApi.saveFieldMapping(company, store, mapping)
                runOnUiThread {
                    progress.visibility = View.GONE
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    btnSave.isEnabled   = true
                    showError(e.message ?: "Save failed")
                }
            }
        }.start()
    }

    private fun refreshSaveEnabled() {
        btnSave.isEnabled = current().isComplete()
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }
}
