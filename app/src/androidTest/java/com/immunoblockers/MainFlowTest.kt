package com.immunoblockers

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.data.Occurrence
import kotlinx.coroutines.*
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
        compose.waitUntil(30000){compose.onAllNodesWithTag("todayList").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("todayList").performScrollToNode(hasText("UI тест"))
        compose.onNodeWithText("UI тест").assertExists()
        compose.onNodeWithTag("todayList").performTouchInput { swipeUp() }
        compose.onAllNodesWithText("Принять").onFirst().performScrollTo().performClick()
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        runBlocking {withTimeout(30000){while(app.repository.dao.occurrences().none {it.name=="UI тест"&&it.state==Occurrence.TAKEN})delay(100)}}
        compose.onNodeWithTag("todayList").performScrollToNode(hasText("Принято"))
        compose.onAllNodesWithText("Принято").onFirst().assertExists()
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Тёмная").performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Тёмная").assertIsSelected()
    }
}
