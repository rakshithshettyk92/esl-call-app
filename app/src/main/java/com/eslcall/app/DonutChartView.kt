package com.eslcall.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Minimal donut chart. Set [segments] and the view redraws.
 * Empty / all-zero segments render a light grey ring so the user can still see
 * the chart shape with no data.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Segment(val value: Float, val color: Int)

    var segments: List<Segment> = emptyList()
        set(value) { field = value; invalidate() }

    var centerText: String? = null
        set(value) { field = value; invalidate() }

    var centerCaption: String? = null
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF1A1A2E.toInt()
        isFakeBoldText = true
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF6B7A9A.toInt()
    }
    private val rect = RectF()
    private val emptyColor = 0xFFE0E0E0.toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        val stroke = side * 0.18f
        paint.strokeWidth = stroke

        val cx = width / 2f
        val cy = height / 2f
        val radius = side / 2f - stroke / 2f - 4f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) {
            paint.color = emptyColor
            canvas.drawArc(rect, 0f, 360f, false, paint)
            return
        }

        var start = -90f
        segments.forEach { seg ->
            if (seg.value <= 0f) return@forEach
            val sweep = (seg.value / total) * 360f
            paint.color = seg.color
            canvas.drawArc(rect, start, sweep, false, paint)
            start += sweep
        }

        drawCenterLabels(canvas, side)
    }

    private fun drawCenterLabels(canvas: Canvas, side: Float) {
        val text = centerText ?: return
        val cx = width / 2f
        val cy = height / 2f
        textPaint.textSize = side * 0.20f
        captionPaint.textSize = side * 0.08f

        val hasCaption = !centerCaption.isNullOrEmpty()
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val baseline = if (hasCaption) cy - (fm.ascent + fm.descent) / 2 - textHeight * 0.15f
                       else cy - (fm.ascent + fm.descent) / 2
        canvas.drawText(text, cx, baseline, textPaint)

        if (hasCaption) {
            val capFm = captionPaint.fontMetrics
            canvas.drawText(
                centerCaption!!,
                cx,
                baseline + textHeight * 0.55f - capFm.ascent * 0.6f,
                captionPaint,
            )
        }
    }
}
