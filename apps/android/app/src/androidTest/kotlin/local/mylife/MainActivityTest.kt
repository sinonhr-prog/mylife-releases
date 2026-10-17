package local.mylife

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testDashboardAndDrawerDisplayed() {
        // Проверяем, отображается ли экран создания или ввода PIN
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Придумайте PIN-код").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Вход в приложение").fetchSemanticsNodes().isNotEmpty()
        }
        val setupTitleNodes = composeTestRule.onAllNodesWithText("Придумайте PIN-код").fetchSemanticsNodes()
        if (setupTitleNodes.isNotEmpty()) {
            // Ввод нового PIN (123456)
            for (ch in "583926") {
                composeTestRule.onNodeWithText(ch.toString()).performClick()
            }
            composeTestRule.waitUntil(15000) { composeTestRule.onAllNodesWithText("Повторите PIN-код").fetchSemanticsNodes().isNotEmpty() }
            // Подтверждение PIN (123456)
            for (ch in "583926") {
                composeTestRule.onNodeWithText(ch.toString()).performClick()
            }
            composeTestRule.waitForIdle()
        } else {
            val loginTitleNodes = composeTestRule.onAllNodesWithText("Вход в приложение").fetchSemanticsNodes()
            if (loginTitleNodes.isNotEmpty()) {
                for (ch in "583926") {
                    composeTestRule.onNodeWithText(ch.toString()).performClick()
                }
                composeTestRule.waitForIdle()
            }
        }

        composeTestRule.waitUntil(15000) { composeTestRule.onAllNodesWithText("Сначала главное").fetchSemanticsNodes().isNotEmpty() }

        // Проверка отображения дашборда и утверждённого дизайна «Фокус»
        composeTestRule.onNodeWithText("Сначала главное").assertExists()
        composeTestRule.onNodeWithText("Остальное успеется.").assertExists()

        // Открытие выдвижного меню слева по кнопке ☰
        composeTestRule.onNodeWithText("☰").performClick()
        composeTestRule.waitForIdle()

        // Проверка категорий в меню
        composeTestRule.onNode(hasText("Покупки") and hasText("🛍")).assertExists()
        composeTestRule.onNodeWithText("Пароли (Сейф)").assertExists()
        composeTestRule.onNodeWithText("Задачи").assertExists()
        composeTestRule.onNodeWithText("Настройки").assertExists()

        // Переход в раздел «Покупки» через элемент меню
        composeTestRule.onNode(hasText("Покупки") and hasText("🛍")).performClick()
        composeTestRule.waitForIdle()

        // Проверка элементов экрана «Покупки»
        composeTestRule.onNodeWithText("Поиск покупок...").assertExists()
        composeTestRule.onNodeWithText("Все").assertExists()

        // Проверка системной кнопки «Назад» (Back) — возврат на Главную
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Снова отображается дашборд «Сначала главное»
        composeTestRule.onNodeWithText("Сначала главное").assertExists()

        // The footer and the vault body must share a ViewModel.
        composeTestRule.onNodeWithText("☰").performClick()
        composeTestRule.onNodeWithText("Пароли (Сейф)").performScrollTo().performClick()
        composeTestRule.waitUntil(15000) { composeTestRule.onAllNodesWithText("Вход в Сейф").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Вход в Сейф").assertExists()
        for (digit in "583926") composeTestRule.onNodeWithText(digit.toString()).performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Ваши пароли").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Добавить пароль").performClick()
        composeTestRule.onNodeWithText("Новый пароль").assertIsDisplayed()
        composeTestRule.onNodeWithText("Отмена").performClick()
        composeTestRule.onNodeWithText("Закрыть сейф").performClick()
        composeTestRule.onNodeWithText("Сначала главное").assertExists()

        // Backgrounding must hide private screens and require authentication again.
        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        composeTestRule.onNodeWithText("Вход в приложение").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сначала главное").assertDoesNotExist()
    }
}
