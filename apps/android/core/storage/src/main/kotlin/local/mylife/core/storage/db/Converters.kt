package local.mylife.core.storage.db

import androidx.room.TypeConverter
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.PlaceVisitStatus
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskStatus

class Converters {
    @TypeConverter
    fun fromCategoryKind(kind: CategoryKind): String = kind.name

    @TypeConverter
    fun toCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromTaskPriority(priority: TaskPriority): String = priority.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority = TaskPriority.valueOf(value)

    @TypeConverter
    fun fromPlaceVisitStatus(status: PlaceVisitStatus): String = status.name

    @TypeConverter
    fun toPlaceVisitStatus(value: String): PlaceVisitStatus = PlaceVisitStatus.valueOf(value)
}
