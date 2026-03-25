package com.eslcall.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlertHistoryStore {

    private const val PREFS_NAME  = "alert_history"
    private const val KEY_HISTORY = "history_json"
    private const val MAX_ITEMS   = 100

    fun save(context: Context, item: AlertHistoryItem) {
        val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_HISTORY, "[]")
        val array    = try { JSONArray(existing) } catch (_: Exception) { JSONArray() }

        val obj = JSONObject().apply {
            put("message",     item.message)
            put("companyCode", item.companyCode)
            put("labelCode",   item.labelCode)
            put("timestamp",   item.timestamp)
            put("status",      item.status.name)
        }

        // Insert newest first, cap at MAX_ITEMS
        val newArray = JSONArray()
        newArray.put(obj)
        for (i in 0 until minOf(array.length(), MAX_ITEMS - 1)) {
            newArray.put(array.getJSONObject(i))
        }

        prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply()
    }

    /**
     * Remove history entries for labelCode that are NOT yet acknowledged.
     * Called when a cancel FCM arrives — if this device already saved an ACKNOWLEDGED
     * entry (i.e. it was the one that tapped On My Way), keep it in history.
     */
    fun removeByLabelCode(context: Context, labelCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json  = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { return }
        val kept  = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val sameLabel = obj.optString("labelCode") == labelCode
            val isAcknowledged = obj.optString("status") == AlertStatus.ACKNOWLEDGED.name
            if (!sameLabel || isAcknowledged) kept.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, kept.toString()).apply()
    }

    fun load(context: Context): List<AlertHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json  = prefs.getString(KEY_HISTORY, "[]")
        val array = try { JSONArray(json) } catch (_: Exception) { return emptyList() }

        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            AlertHistoryItem(
                message     = obj.optString("message",     ""),
                companyCode = obj.optString("companyCode", ""),
                labelCode   = obj.optString("labelCode",   ""),
                timestamp   = obj.optLong("timestamp",     0L),
                status      = runCatching {
                    AlertStatus.valueOf(obj.optString("status", "ACKNOWLEDGED"))
                }.getOrDefault(AlertStatus.ACKNOWLEDGED)
            )
        }
    }
}
