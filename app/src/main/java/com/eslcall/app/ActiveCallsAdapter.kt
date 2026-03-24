package com.eslcall.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ActiveCallsAdapter(
    private var items: List<PendingAlert>,
    private val onAcknowledge: (PendingAlert) -> Unit,
    private val onDismiss:     (PendingAlert) -> Unit
) : RecyclerView.Adapter<ActiveCallsAdapter.ViewHolder>() {

    companion object {
        private const val PAYLOAD_TICK = "tick"
    }

    private val inProgress = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage:   TextView = view.findViewById(R.id.tvCallMessage)
        val tvDetail:    TextView = view.findViewById(R.id.tvCallDetail)
        val tvCountdown: TextView = view.findViewById(R.id.tvCallTime)
        val btnOnMyWay:  Button   = view.findViewById(R.id.btnCallOnMyWay)
        val btnDismiss:  Button   = view.findViewById(R.id.btnCallDismiss)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_active_call, parent, false)
        return ViewHolder(view)
    }

    // Full bind
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert   = items[position]
        val loading = alert.labelCode in inProgress

        holder.tvMessage.text = alert.message
        holder.tvDetail.text  = if (alert.labelCode.isNotBlank())
            "Label: ${alert.labelCode}" else alert.companyCode

        bindCountdown(holder, alert)

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

    // Partial bind — only refresh the countdown text/colour
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == PAYLOAD_TICK) {
            bindCountdown(holder, items[position])
        } else {
            onBindViewHolder(holder, position)
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

    /**
     * Called every second by ActiveCallsActivity.
     * Updates countdown text on all visible items and returns alerts that have expired.
     */
    fun tick(): List<PendingAlert> {
        val expired = mutableListOf<PendingAlert>()
        val now = System.currentTimeMillis()
        items.forEachIndexed { idx, alert ->
            val remaining = Constants.ALERT_TIMEOUT_MS - (now - alert.receivedAt)
            if (remaining <= 0) expired.add(alert)
            else notifyItemChanged(idx, PAYLOAD_TICK)
        }
        return expired
    }

    private fun bindCountdown(holder: ViewHolder, alert: PendingAlert) {
        val remaining = Constants.ALERT_TIMEOUT_MS - (System.currentTimeMillis() - alert.receivedAt)
        if (remaining <= 0) {
            holder.tvCountdown.text = "Expired"
            holder.tvCountdown.setTextColor(0xFFC62828.toInt())
        } else {
            val secs = (remaining / 1_000).toInt()
            holder.tvCountdown.text = "⏱ ${secs}s"
            holder.tvCountdown.setTextColor(when {
                secs <= 10 -> 0xFFC62828.toInt()   // red
                secs <= 20 -> 0xFFE65100.toInt()   // deep orange
                else       -> 0xFF757575.toInt()   // grey
            })
        }
    }
}
