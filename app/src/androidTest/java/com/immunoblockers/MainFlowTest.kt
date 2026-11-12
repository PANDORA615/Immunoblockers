package com.immunoblockers

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MainFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun createSeeTakeAndPersistTheme() {
        compose.onAllNodesWithText("Добавить лекарство").onFirst().performClick()
        compose.onNodeWithText("Название лекарства").performTextInput("UI тест")
        compose.onNodeWithText("Разовая дозировка").performTextInput("1")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("UI тест").assertExists()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("UI тест").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("Принять").onFirst().performScrollTo().performClick()
        compose.onAllNodesWithText("Принято").onFirst().assertExists()
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Тёмная").performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Тёмная").assertIsSelected()
    }
}
