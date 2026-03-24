package com.eslcall.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private val items: List<AlertHistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage:   TextView = view.findViewById(R.id.tvItemMessage)
        val tvLabelCode: TextView = view.findViewById(R.id.tvItemLabelCode)
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
        holder.tvMessage.text   = item.message
        holder.tvLabelCode.text = if (item.labelCode.isNotBlank())
            "Label: ${item.labelCode}" else item.companyCode
        holder.tvDay.text       = item.relativeDay()
        holder.tvTime.text      = item.formattedTimeOnly()
    }

    override fun getItemCount() = items.size
}
