package com.eslcall.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONObject

/**
 * Per-(company, store) analytics dashboard. All data comes from the relay's
 * /admin/analytics endpoint; the screen just translates JSON into chart inputs.
 */
class AnalyticsActivity : AppCompatActivity() {

    private lateinit var rangeChips:   ChipGroup
    private lateinit var progress:     CircularProgressIndicator
    private lateinit var tvError:      TextView
    private lateinit var tvScope:      TextView

    private lateinit var tvKpiTotal:     TextView
    private lateinit var tvKpiResponse:  TextView
    private lateinit var tvKpiAck:       TextView
    private lateinit var tvKpiMissed:    TextView
    private lateinit var tvKpiDismissed: TextView

    private lateinit var donutStatus:   DonutChartView
    private lateinit var chartAisles:   VerticalBarChartView
    private lateinit var chartArticles: VerticalBarChartView
    private lateinit var chartHour:     VerticalBarChartView
    private lateinit var tvNoAisles:    TextView
    private lateinit var tvNoArticles:  TextView

    private val company by lazy { Session.companyCode(this).orEmpty() }
    private val store   by lazy { Session.storeCode(this).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rangeChips     = findViewById(R.id.rangeChips)
        progress       = findViewById(R.id.progress)
        tvError        = findViewById(R.id.tvError)
        tvScope        = findViewById(R.id.tvScope)
        tvKpiTotal     = findViewById(R.id.tvKpiTotal)
        tvKpiResponse  = findViewById(R.id.tvKpiResponse)
        tvKpiAck       = findViewById(R.id.tvKpiAck)
        tvKpiMissed    = findViewById(R.id.tvKpiMissed)
        tvKpiDismissed = findViewById(R.id.tvKpiDismissed)
        donutStatus    = findViewById(R.id.donutStatus)
        chartAisles    = findViewById(R.id.chartAisles)
        chartArticles  = findViewById(R.id.chartArticles)
        chartHour      = findViewById(R.id.chartHour)
        tvNoAisles     = findViewById(R.id.tvNoAisles)
        tvNoArticles   = findViewById(R.id.tvNoArticles)

        tvScope.text = "$company • ${Session.storeName(this) ?: store}"

        rangeChips.setOnCheckedStateChangeListener { _, _ -> reload() }
        reload()
    }

    private fun currentRange(): String = when (rangeChips.checkedChipId) {
        R.id.chipToday -> "today"
        R.id.chip30d   -> "30d"
        else           -> "7d"
    }

    private fun reload() {
        if (company.isEmpty() || store.isEmpty()) {
            showError("Pick a store first.")
            return
        }
        progress.visibility = View.VISIBLE
        tvError.visibility  = View.GONE
        val range = currentRange()

        Thread {
            try {
                val json = RelayApi.fetchAnalytics(company, store, range)
                runOnUiThread {
                    progress.visibility = View.GONE
                    render(json)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    showError(e.message ?: "Failed to load analytics")
                }
            }
        }.start()
    }

    private fun render(json: JSONObject) {
        val totals = json.optJSONObject("totals") ?: JSONObject()
        val total      = totals.optInt("delivered",    0)
        val ack        = totals.optInt("acknowledged", 0)
        val missed     = totals.optInt("missed",       0)
        val dismissed  = totals.optInt("dismissed",    0)

        tvKpiTotal.text     = total.toString()
        tvKpiAck.text       = ack.toString()
        tvKpiMissed.text    = missed.toString()
        tvKpiDismissed.text = dismissed.toString()

        val response = json.optJSONObject("responseMs")
        val avgMs    = response?.optInt("avg") ?: 0
        tvKpiResponse.text = if (avgMs > 0) formatDuration(avgMs.toLong()) else "—"

        // Donut: outcome mix
        donutStatus.segments = listOf(
            DonutChartView.Segment(ack.toFloat(),       0xFF00897B.toInt()),
            DonutChartView.Segment(missed.toFloat(),    0xFFE65100.toInt()),
            DonutChartView.Segment(dismissed.toFloat(), 0xFF757575.toInt()),
        )
        val pct = if (total > 0) (ack * 100 / total) else 0
        donutStatus.centerText    = "$pct%"
        donutStatus.centerCaption = "Acknowledged"

        // Top aisles
        val aisles = parseTopList(json.optJSONArray("topAisles"))
        chartAisles.bars = aisles.map { VerticalBarChartView.Bar(it.first, it.second.toFloat()) }
        tvNoAisles.visibility   = if (aisles.isEmpty()) View.VISIBLE else View.GONE
        chartAisles.visibility  = if (aisles.isEmpty()) View.GONE    else View.VISIBLE

        // Top articles
        val articles = parseTopList(json.optJSONArray("topArticles"))
        chartArticles.bars = articles.map { VerticalBarChartView.Bar(truncate(it.first, 12), it.second.toFloat()) }
        tvNoArticles.visibility  = if (articles.isEmpty()) View.VISIBLE else View.GONE
        chartArticles.visibility = if (articles.isEmpty()) View.GONE    else View.VISIBLE

        // Hour-of-day: 24 buckets
        val perHour = json.optJSONArray("perHour")
        val hourBars = (0..23).map { h ->
            val v = perHour?.optInt(h) ?: 0
            // Show every-6h label so axis isn't crowded
            val label = if (h % 6 == 0) "${h}h" else ""
            VerticalBarChartView.Bar(label, v.toFloat())
        }
        chartHour.bars = hourBars
    }

    private fun parseTopList(arr: org.json.JSONArray?): List<Pair<String, Int>> {
        if (arr == null) return emptyList()
        val out = ArrayList<Pair<String, Int>>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("key").takeIf { it.isNotEmpty() } ?: continue
            out.add(key to o.optInt("count", 0))
        }
        return out
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max - 1) + "…"

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        return when {
            totalSec < 60  -> "${totalSec}s"
            totalSec < 3600 -> "${totalSec / 60}m ${totalSec % 60}s"
            else            -> "${totalSec / 3600}h ${(totalSec % 3600) / 60}m"
        }
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }
}
