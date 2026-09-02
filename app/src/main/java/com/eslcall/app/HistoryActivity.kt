package com.eslcall.app

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

class HistoryActivity : AppCompatActivity() {

    private enum class Filter { ALL, ATTENDED, MISSED, DISMISSED, HANDLED_ELSEWHERE }

    private lateinit var allItems: List<AlertHistoryItem>
    private lateinit var adapter: HistoryAdapter
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var recycler: RecyclerView
    private lateinit var tvSummary: TextView
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<View>(R.id.historyHeader).applyStatusBarInset()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        allItems = AlertHistoryStore.load(this)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        recycler = findViewById(R.id.recyclerHistory)
        tvSummary = findViewById(R.id.tvHistorySummary)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        adapter = HistoryAdapter(allItems)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        mapOf(
            R.id.chipAll to Filter.ALL,
            R.id.chipAttended to Filter.ATTENDED,
            R.id.chipMissed to Filter.MISSED,
            R.id.chipDismissed to Filter.DISMISSED,
            R.id.chipHandledElsewhere to Filter.HANDLED_ELSEWHERE,
        ).forEach { (id, filter) ->
            findViewById<Chip>(id).setOnClickListener { showFilter(filter) }
        }
        showFilter(Filter.ALL)
    }

    private fun showFilter(filter: Filter) {
        val filtered = when (filter) {
            Filter.ALL -> allItems
            Filter.ATTENDED -> allItems.filter { it.status == AlertStatus.ACKNOWLEDGED }
            Filter.MISSED -> allItems.filter { it.status == AlertStatus.MISSED }
            Filter.DISMISSED -> allItems.filter { it.status == AlertStatus.DISMISSED }
            Filter.HANDLED_ELSEWHERE -> allItems.filter { it.status == AlertStatus.HANDLED_BY_OTHER }
        }
        adapter.submitItems(filtered)
        tvSummary.text = when (filter) {
            Filter.ALL -> "${allItems.size} call${if (allItems.size == 1) "" else "s"} on this device"
            else -> "${filtered.size} of ${allItems.size} calls"
        }
        tvEmptyTitle.text = when (filter) {
            Filter.ALL -> "No call history yet"
            Filter.ATTENDED -> "No calls attended by you"
            Filter.MISSED -> "No missed calls"
            Filter.DISMISSED -> "No calls marked as not taken"
            Filter.HANDLED_ELSEWHERE -> "No calls attended elsewhere"
        }
        tvEmptyMessage.text = if (filter == Filter.ALL) {
            "New calls and what happened to them will appear here."
        } else {
            "Choose another option above to see different calls."
        }
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }
}
