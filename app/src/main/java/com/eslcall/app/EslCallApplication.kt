package com.eslcall.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Tracks foreground state across every activity, including admin screens. */
class EslCallApplication : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        lateinit var instance: EslCallApplication
            private set
    }

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
        AppForegroundTracker.isInForeground = startedActivities > 0
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        AppForegroundTracker.isInForeground = startedActivities > 0
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
