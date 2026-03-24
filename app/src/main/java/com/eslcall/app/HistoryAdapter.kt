package com.eslcall.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private val items: List<AlertHistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewIconBg:  View     = view.findViewById(R.id.viewIconBg)
        val tvIcon:      TextView = view.findViewById(R.id.tvStatusIcon)
        val tvMessage:   TextView = view.findViewById(R.id.tvItemMessage)
        val tvStatus:    TextView = view.findViewById(R.id.tvItemStatus)
        val tvDay:       TextView = view.findViewById(R.id.tvItemDay)
        val tvTime:      TextView = view.findViewById(R.id.tvItemTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvMessage.text = item.message
        holder.tvDay.text     = item.relativeDay()
        holder.tvTime.text    = item.formattedTimeOnly()

        when (item.status) {
            AlertStatus.ACKNOWLEDGED -> {
                holder.tvIcon.text       = "✓"
                holder.tvIcon.setTextColor(Color.parseColor("#00897B"))
                holder.viewIconBg.setBackgroundResource(R.drawable.shape_circle_green)
                holder.viewIconBg.alpha  = 0.15f
                holder.tvStatus.text     = "On My Way"
                holder.tvStatus.setTextColor(Color.parseColor("#00897B"))
            }
            AlertStatus.DISMISSED -> {
                holder.tvIcon.text       = "✕"
                holder.tvIcon.setTextColor(Color.parseColor("#757575"))
                holder.viewIconBg.setBackgroundResource(R.drawable.shape_circle_green)
                holder.viewIconBg.alpha  = 0.08f
                holder.tvStatus.text     = "Dismissed"
                holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
            }
            AlertStatus.MISSED -> {
                holder.tvIcon.text       = "⏱"
                holder.tvIcon.setTextColor(Color.parseColor("#E65100"))
                holder.viewIconBg.setBackgroundResource(R.drawable.shape_circle_green)
                holder.viewIconBg.alpha  = 0.10f
                holder.tvStatus.text     = "Missed / Timed out"
                holder.tvStatus.setTextColor(Color.parseColor("#E65100"))
            }
        }
    }

    override fun getItemCount() = items.size
}
