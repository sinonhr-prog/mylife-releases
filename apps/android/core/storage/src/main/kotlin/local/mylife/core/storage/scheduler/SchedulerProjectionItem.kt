package local.mylife.core.storage.scheduler

import kotlinx.serialization.Serializable

@Serializable
data class SchedulerProjectionItem(
    val reminderId: String,
    val fireEpochMs: Long,
    val repeatRule: String? = null,
    val isEnabled: Boolean = true,
)
