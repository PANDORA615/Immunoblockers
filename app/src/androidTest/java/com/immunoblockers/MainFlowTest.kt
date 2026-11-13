package com.immunoblockers

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.data.Occurrence
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test

class MainFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun createSeeTakeAndPersistTheme() {
        compose.onNodeWithTag("addMedication").performClick()
        compose.onNodeWithText("Название лекарства").performTextInput("UI тест")
        compose.onNodeWithText("Разовая дозировка").performTextInput("1")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("UI тест").assertExists()
        compose.onNodeWithText("Сохранить").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){assertTrue(it())}
        try {compose.waitUntil(30000){compose.onAllNodesWithTag("todayList").fetchSemanticsNodes().isNotEmpty()}}
        catch(failure:Exception){throw AssertionError(compose.onRoot().printToString(),failure)}
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
        compose.waitUntil(15000){compose.onAllNodes(hasText("Тёмная") and isSelected()).fetchSemanticsNodes().size==1}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000){compose.onAllNodes(hasText("Тёмная") and isSelected()).fetchSemanticsNodes().size==1}
        compose.onNodeWithText("Тёмная").assertIsSelected()
    }
}
