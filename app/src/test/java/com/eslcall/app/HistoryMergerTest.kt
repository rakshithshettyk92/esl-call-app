package com.eslcall.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMergerTest {
    @Test
    fun `store outcome replaces matching device outcome`() {
        val local = AlertHistoryItem("Help", "SIF", "L1", 1_000L, AlertStatus.MISSED)
        val remote = AlertHistoryItem(
            "Help", "SIF", "L1", 2_000L, AlertStatus.HANDLED_BY_OTHER,
            handledBy = "associate.two", callId = "call-1",
        )

        val merged = HistoryMerger.merge(listOf(remote), listOf(local))

        assertEquals(listOf(remote), merged)
    }

    @Test
    fun `keeps unrelated local preview or older call`() {
        val remote = AlertHistoryItem("Help", "SIF", "L1", 600_000L, AlertStatus.MISSED)
        val local = AlertHistoryItem("Preview", "", "", 700_000L, AlertStatus.DISMISSED)

        val merged = HistoryMerger.merge(listOf(remote), listOf(local))

        assertEquals(2, merged.size)
        assertEquals(local, merged.first())
    }
}
