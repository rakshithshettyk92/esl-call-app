package com.eslcall.app

import java.text.SimpleDateFormat
import java.util.*

enum class AlertStatus { ACKNOWLEDGED, DISMISSED, MISSED }

data class AlertHistoryItem(
    val message:     String,
    val companyCode: String,
    val labelCode:   String,
    val timestamp:   Long,
    val status:      AlertStatus = AlertStatus.ACKNOWLEDGED
) {
    fun formattedTimeOnly(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun relativeDay(): String {
        val now     = Calendar.getInstance()
        val itemCal = Calendar.getInstance().also { it.timeInMillis = timestamp }
        return when {
            isSameDay(now, itemCal)    -> "Today"
            isYesterday(now, itemCal)  -> "Yesterday"
            else -> SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, item: Calendar): Boolean {
        val yesterday = Calendar.getInstance().also {
            it.timeInMillis = now.timeInMillis
            it.add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, item)
    }
}
