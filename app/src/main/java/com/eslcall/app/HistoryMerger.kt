package com.eslcall.app

import kotlin.math.abs

object HistoryMerger {
    private const val SAME_CALL_WINDOW_MS = 5 * 60 * 1000L

    fun merge(storeItems: List<AlertHistoryItem>, deviceItems: List<AlertHistoryItem>): List<AlertHistoryItem> {
        val storeIds = storeItems.mapNotNull { it.callId }.toSet()
        val uniqueDeviceItems = deviceItems.filter { local ->
            local.callId?.let { it !in storeIds } ?: storeItems.none { remote ->
                local.companyCode == remote.companyCode &&
                    local.labelCode == remote.labelCode &&
                    abs(local.timestamp - remote.timestamp) <= SAME_CALL_WINDOW_MS
            }
        }
        return (storeItems + uniqueDeviceItems)
            .sortedByDescending { it.timestamp }
            .take(100)
    }
}
