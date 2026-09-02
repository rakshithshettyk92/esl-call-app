package com.eslcall.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private var items: List<AlertHistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewIconBg: View = view.findViewById(R.id.viewIconBg)
        val tvIcon: TextView = view.findViewById(R.id.tvStatusIcon)
        val tvMessage: TextView = view.findViewById(R.id.tvItemMessage)
        val tvStatus: TextView = view.findViewById(R.id.tvItemStatus)
        val tvContext: TextView = view.findViewById(R.id.tvItemContext)
        val tvDay: TextView = view.findViewById(R.id.tvItemDay)
        val tvTime: TextView = view.findViewById(R.id.tvItemTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvMessage.text = item.message
        holder.tvDay.text = item.relativeDay()
        holder.tvTime.text = item.formattedTimeOnly()
        holder.tvContext.text = listOfNotNull(
            item.companyCode.takeIf { it.isNotBlank() }?.let { "Company $it" },
            item.labelCode.takeIf { it.isNotBlank() }?.let { "Button $it" },
        ).joinToString("  •  ")
        holder.tvContext.visibility = if (holder.tvContext.text.isBlank()) View.GONE else View.VISIBLE

        when (item.status) {
            AlertStatus.ACKNOWLEDGED -> bindStatus(
                holder,
                "\u2713",
                item.handledBy?.let { "Attended by you - $it" } ?: "Attended by you",
                R.color.green,
                R.drawable.shape_status_attended,
            )
            AlertStatus.HANDLED_BY_OTHER -> bindStatus(
                holder,
                "\u2192",
                item.handledBy?.let { "Attended by $it" } ?: "Attended by another associate",
                R.color.handled_other_text,
                R.drawable.shape_status_other,
            )
            AlertStatus.DISMISSED -> bindStatus(
                holder, "\u00D7", "Not taken on this device", R.color.dismissed_text, R.drawable.shape_status_dismissed)
            AlertStatus.MISSED -> bindStatus(
                holder, "!", "Missed because no one responded in time", R.color.missed_text, R.drawable.shape_status_missed)
        }
    }

    private fun bindStatus(holder: ViewHolder, icon: String, label: String, color: Int, background: Int) {
        val parsedColor = ContextCompat.getColor(holder.itemView.context, color)
        holder.tvIcon.text = icon
        holder.tvIcon.setTextColor(parsedColor)
        holder.viewIconBg.setBackgroundResource(background)
        holder.viewIconBg.alpha = 1f
        holder.tvStatus.text = label
        holder.tvStatus.setTextColor(parsedColor)
    }

    override fun getItemCount() = items.size

    fun submitItems(updated: List<AlertHistoryItem>) {
        items = updated
        notifyDataSetChanged()
    }
}
