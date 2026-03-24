package com.eslcall.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class ActiveCallsAdapter(
    private var items: List<PendingAlert>,
    private val onAcknowledge: (PendingAlert) -> Unit,
    private val onDismiss:     (PendingAlert) -> Unit
) : RecyclerView.Adapter<ActiveCallsAdapter.ViewHolder>() {

    private val inProgress = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage:  TextView = view.findViewById(R.id.tvCallMessage)
        val tvDetail:   TextView = view.findViewById(R.id.tvCallDetail)
        val tvTime:     TextView = view.findViewById(R.id.tvCallTime)
        val btnOnMyWay: Button   = view.findViewById(R.id.btnCallOnMyWay)
        val btnDismiss: Button   = view.findViewById(R.id.btnCallDismiss)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_active_call, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert   = items[position]
        val loading = alert.labelCode in inProgress

        holder.tvMessage.text = alert.message
        holder.tvDetail.text  = if (alert.labelCode.isNotBlank())
            "Label: ${alert.labelCode}" else alert.companyCode
        holder.tvTime.text    = timeAgo(alert.receivedAt)

        holder.btnOnMyWay.isEnabled = !loading
        holder.btnDismiss.isEnabled = !loading
        holder.btnOnMyWay.text      = if (loading) "Sending…" else "On My Way"

        holder.btnOnMyWay.setOnClickListener {
            if (!loading) {
                inProgress.add(alert.labelCode)
                notifyItemChanged(position)
                onAcknowledge(alert)
            }
        }
        holder.btnDismiss.setOnClickListener {
            if (!loading) onDismiss(alert)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<PendingAlert>) {
        items = newItems
        inProgress.clear()
        notifyDataSetChanged()
    }

    fun setItemIdle(labelCode: String) {
        inProgress.remove(labelCode)
        val idx = items.indexOfFirst { it.labelCode == labelCode }
        if (idx >= 0) notifyItemChanged(idx)
    }

    private fun timeAgo(receivedAt: Long): String {
        val diff = System.currentTimeMillis() - receivedAt
        return when {
            diff < 60_000        -> "Just now"
            diff < 3_600_000     -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            else                 -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        }
    }
}
