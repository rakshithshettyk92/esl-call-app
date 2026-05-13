package com.eslcall.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Minimal vertical bar chart. Bars are scaled to the largest value. Value is
 * drawn above each non-zero bar; label is drawn below every bar.
 */
class VerticalBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Bar(val label: String, val value: Float)

    var bars: List<Bar> = emptyList()
        set(value) { field = value; invalidate() }

    var barColor: Int = 0xFF2F006D.toInt()
        set(value) { field = value; invalidate() }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val density = resources.displayMetrics.density
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val max = bars.maxOf { it.value }.coerceAtLeast(1f)
        val labelTextSize = 10f * density
        val valueTextSize = 11f * density
        val labelArea = labelTextSize + 6f * density
        val valueArea = valueTextSize + 4f * density
        val barAreaHeight = (height - labelArea - valueArea).coerceAtLeast(0f)
        val cellWidth = width.toFloat() / bars.size
        val barWidth = (cellWidth * 0.65f).coerceAtMost(28f * density)
        val corner = 4f * density

        val labelSkip = when {
            bars.size > 20 -> 5
            bars.size > 12 -> 2
            else           -> 1
        }
        val showValues = bars.size <= 20

        bars.forEachIndexed { i, bar ->
            val cx = (i + 0.5f) * cellWidth
            val barH = (bar.value / max) * barAreaHeight
            val left = cx - barWidth / 2f
            val right = cx + barWidth / 2f
            val top = valueArea + (barAreaHeight - barH)
            val bottom = valueArea + barAreaHeight
            rect.set(left, top, right, bottom)
            barPaint.color = barColor
            canvas.drawRoundRect(rect, corner, corner, barPaint)

            if (showValues && bar.value > 0) {
                textPaint.color = 0xFF1A1A2E.toInt()
                textPaint.textSize = valueTextSize
                canvas.drawText(bar.value.toInt().toString(), cx, top - 2f * density, textPaint)
            }
            if (i % labelSkip == 0) {
                textPaint.color = 0xFF6B7A9A.toInt()
                textPaint.textSize = labelTextSize
                canvas.drawText(bar.label, cx, height - 4f * density, textPaint)
            }
        }
    }
}
