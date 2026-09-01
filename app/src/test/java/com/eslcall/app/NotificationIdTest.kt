package com.eslcall.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationIdTest {
    @Test fun blankLabelUsesFallbackId() {
        assertEquals(MyFirebaseMessagingService.ALERT_NOTIFICATION_ID, notificationIdFor(""))
    }

    @Test fun labelIdsAreStableAndDistinct() {
        assertEquals(notificationIdFor("label-123"), notificationIdFor("label-123"))
        assertNotEquals(notificationIdFor("label-123"), notificationIdFor("label-456"))
    }
}
