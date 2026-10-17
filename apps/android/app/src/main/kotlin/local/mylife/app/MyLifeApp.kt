package local.mylife.app

import android.app.Application
import local.mylife.app.feature.auth.SessionLifecycleManager

class MyLifeApp : Application() {
    lateinit var container: AppContainer
        private set

    lateinit var sessionLifecycleManager: SessionLifecycleManager
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        sessionLifecycleManager = SessionLifecycleManager(container.sessionManager)
        registerActivityLifecycleCallbacks(sessionLifecycleManager)
        local.mylife.app.feature.reminders.ReminderNotificationHelper.ensureChannel(this)
    }
}
