package local.mylife

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import local.mylife.app.feature.reminders.RemindersScreen
import local.mylife.app.feature.reminders.RemindersViewModel
import local.mylife.core.designsystem.theme.MyLifeTheme
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.ReminderRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Only synthetic in-memory data; does not open application databases. */
class RemindersScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyScreenOpensCancelsAndSavesReminder() {
        val items = mutableListOf<ReminderItem>()
        val repository = object : ReminderRepository {
            override suspend fun getAllReminders() = items.toList()
            override suspend fun getActiveReminders() = items.filter { it.isEnabled }
            override suspend fun getReminderById(id: String) = items.find { it.id == id }
            override suspend fun getRemindersByTaskId(taskId: String) = items.filter { it.taskId == taskId }
            override suspend fun saveReminder(reminder: ReminderItem) { items.add(reminder) }
            override suspend fun toggleReminder(id: String, isEnabled: Boolean) {}
            override suspend fun deleteReminder(id: String) {}
            override suspend fun cancelRemindersForTask(taskId: String) {}
        }
        lateinit var vm: RemindersViewModel
        compose.runOnIdle { vm = RemindersViewModel(repository) }
        compose.setContent { MyLifeTheme { RemindersScreen(vm) } }
        compose.onNodeWithText("+ Запланировать напоминание").performClick()
        compose.onNodeWithText("Новое напоминание").assertIsDisplayed()
        compose.runOnIdle { assertTrue(items.isEmpty()) }
        compose.onNodeWithTag("save_reminder_button").performClick()
        compose.onNodeWithText("Название напоминания не может быть пустым").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertTrue(items.isEmpty()) }
        compose.onNodeWithText("+ Запланировать напоминание").performClick()
        compose.onNodeWithTag("reminder_title_field").performTextInput("Синтетическое напоминание")
        compose.onNodeWithTag("save_reminder_button").performClick()
        compose.runOnIdle {
            assertEquals(1, items.size)
            assertEquals("Синтетическое напоминание", items.single().title)
            assertTrue(items.single().fireEpochMs > System.currentTimeMillis())
        }
        compose.onNodeWithText("Синтетическое напоминание").assertIsDisplayed()
    }
}
