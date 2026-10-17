package local.mylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        val setupTitleNodes = composeTestRule.onAllNodesWithText("Придумайте PIN-код").fetchSemanticsNodes()
        if (setupTitleNodes.isNotEmpty()) {
            // Ввод нового PIN (123456)
            for (ch in "123456") {
                composeTestRule.onNodeWithText(ch.toString()).performClick()
            }
            composeTestRule.waitForIdle()
            // Подтверждение PIN (123456)
            for (ch in "123456") {
                composeTestRule.onNodeWithText(ch.toString()).performClick()
            }
            composeTestRule.waitForIdle()
        } else {
            val loginTitleNodes = composeTestRule.onAllNodesWithText("Вход в приложение").fetchSemanticsNodes()
            if (loginTitleNodes.isNotEmpty()) {
                for (ch in "123456") {
                    composeTestRule.onNodeWithText(ch.toString()).performClick()
                }
                composeTestRule.waitForIdle()
            }
        }

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
    }
}
