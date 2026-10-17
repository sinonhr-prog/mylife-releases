package local.mylife.app.feature.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import local.mylife.core.security.SessionManager

class SessionLifecycleManager(
    private val sessionManager: SessionManager,
) : Application.ActivityLifecycleCallbacks {

    private var startedActivityCount = 0

    fun enableWindowSecurity(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    fun disableWindowSecurity(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount <= 0) {
            startedActivityCount = 0
            // App went to background: automatically lock all sessions and wipe memory keys
            sessionManager.lockAll()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
