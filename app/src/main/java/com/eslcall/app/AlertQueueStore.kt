package com.eslcall.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PendingAlert(
    val id:             String,
    val message:        String,
    val companyCode:    String,
    val labelCode:      String,
    val receivedAt:     Long,
    val notificationId: Int
)

object AlertQueueStore {

    private const val PREFS = "alert_queue"
    private const val KEY   = "queue_json"

    /** Add to end of queue. Skips silently if same labelCode is already queued. */
    fun enqueue(context: Context, alert: PendingAlert) {
        val list = loadAll(context)
        if (list.any { it.labelCode == alert.labelCode }) return
        saveAll(context, list + alert)
    }

    fun peek(context: Context): PendingAlert? = loadAll(context).firstOrNull()

    fun dequeue(context: Context): PendingAlert? {
        val list = loadAll(context)
        if (list.isEmpty()) return null
        saveAll(context, list.drop(1))
        return list.first()
    }

    fun size(context: Context): Int = loadAll(context).size

    fun removeByLabelCode(context: Context, labelCode: String) {
        saveAll(context, loadAll(context).filter { it.labelCode != labelCode })
    }

    fun loadAll(context: Context): List<PendingAlert> {
        val json  = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            try {
                val o = array.getJSONObject(i)
                PendingAlert(
                    id             = o.getString("id"),
                    message        = o.getString("message"),
                    companyCode    = o.getString("companyCode"),
                    labelCode      = o.getString("labelCode"),
                    receivedAt     = o.getLong("receivedAt"),
                    notificationId = o.getInt("notificationId")
                )
            } catch (_: Exception) { null }
        }
    }

    fun clear(context: Context) = saveAll(context, emptyList())

    private fun saveAll(context: Context, list: List<PendingAlert>) {
        val array = JSONArray()
        list.forEach { a ->
            array.put(JSONObject().apply {
                put("id",             a.id)
                put("message",        a.message)
                put("companyCode",    a.companyCode)
                put("labelCode",      a.labelCode)
                put("receivedAt",     a.receivedAt)
                put("notificationId", a.notificationId)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
