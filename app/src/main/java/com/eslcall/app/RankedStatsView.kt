package com.eslcall.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** Compact ranked statistic rows for aisle and product call volume. */
class RankedStatsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Item(val label: String, val value: Float)

    var items: List<Item> = emptyList()
        set(value) {
            field = value.take(5)
            contentDescription = field.mapIndexed { index, item ->
                "Rank ${index + 1}, ${item.label}, ${item.value.roundToInt()} calls"
            }.joinToString(". ")
            requestLayout()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.soft_surface)
    }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rankTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 14f * density
        isFakeBoldText = true
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        textSize = 14f * density
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val colors = intArrayOf(
        0xFF2F006D.toInt(),
        0xFF001973.toInt(),
        0xFF5E3588.toInt(),
        0xFF7A5A9C.toInt(),
        0xFF9B83B2.toInt(),
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = items.size.coerceAtLeast(1)
        val desiredHeight = (rows * 64f * density).roundToInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val rowHeight = (height - paddingTop - paddingBottom).toFloat() / items.size
        val radius = 14f * density

        items.forEachIndexed { index, item ->
            val top = paddingTop + index * rowHeight + 4f * density
            val bottom = paddingTop + (index + 1) * rowHeight - 4f * density
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, 14f * density, 14f * density, rowPaint)

            val centerY = (top + bottom) / 2f
            val rankCenterX = left + 24f * density
            rankPaint.color = colors[index.coerceAtMost(colors.lastIndex)]
            canvas.drawCircle(rankCenterX, centerY, radius, rankPaint)
            val rankBaseline = centerY -
                (rankTextPaint.fontMetrics.ascent + rankTextPaint.fontMetrics.descent) / 2f
            canvas.drawText((index + 1).toString(), rankCenterX, rankBaseline, rankTextPaint)

            val baseline = centerY -
                (labelPaint.fontMetrics.ascent + labelPaint.fontMetrics.descent) / 2f
            val count = item.value.roundToInt()
            val countText = "$count call${if (count == 1) "" else "s"}"
            canvas.drawText(countText, right - 14f * density, baseline, countPaint)
            val textLeft = left + 48f * density
            val availableWidth = right - 92f * density - textLeft
            canvas.drawText(fitLabel(item.label, availableWidth), textLeft, baseline, labelPaint)
        }
    }

    private fun fitLabel(label: String, maxWidth: Float): String {
        if (labelPaint.measureText(label) <= maxWidth) return label
        val ellipsis = "…"
        var end = label.length
        while (end > 1 && labelPaint.measureText(label.substring(0, end) + ellipsis) > maxWidth) {
            end -= 1
        }
        return label.substring(0, end).trimEnd() + ellipsis
    }
}
