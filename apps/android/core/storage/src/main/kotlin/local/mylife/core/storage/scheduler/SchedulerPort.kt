package local.mylife.core.storage.scheduler

interface SchedulerPort {
    fun schedule(reminderId: String, triggerAtEpochMs: Long)
    fun cancel(reminderId: String)
    fun canScheduleExact(): Boolean = true
}
