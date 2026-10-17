package local.mylife

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        // Проверка отображения дашборда и утверждённого дизайна «Фокус»
        composeTestRule.onNodeWithText("Главная").assertExists()
        composeTestRule.onNodeWithText("БЛИЖАЙШЕЕ ДЕЛО").assertExists()
        composeTestRule.onNodeWithText("Все дела завершены").assertExists()

        // Открытие выдвижного меню слева по кнопке ☰
        composeTestRule.onNodeWithText("☰").performClick()

        // Проверка категорий в меню
        composeTestRule.onNodeWithText("Моя жизнь").assertExists()
        composeTestRule.onNodeWithText("Покупки").assertExists()
        composeTestRule.onNodeWithText("Сейф").assertExists()
    }
}
