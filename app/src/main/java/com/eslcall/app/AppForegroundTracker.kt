package com.eslcall.app

/**
 * Simple flag set by any visible Activity so the FCM service can decide
 * whether to show a heads-up notification banner.
 */
object AppForegroundTracker {
    @Volatile var isInForeground: Boolean = false
}
