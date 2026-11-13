package com.immunoblockers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.immunoblockers.data.IntakeDay
import com.immunoblockers.ui.IntakeChart
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ChartTest {
 @get:Rule val compose=createComposeRule()
 @Test fun selectedDayExplainsAllCounts() {
  val today=LocalDate.now()
  compose.setContent {MaterialTheme {IntakeChart(listOf(
   IntakeDay(today.minusDays(1),4,2,1,1,0),IntakeDay(today,3,1,0,0,2)
  ),null)}}
  compose.onNodeWithText("Принято / запланировано").assertExists()
  compose.onNodeWithText("Принято: 1 из 3").assertExists()
  compose.onNodeWithTag("chart-day-${today.minusDays(1)}").performClick()
  compose.onNodeWithText("Принято: 2 из 4").assertExists()
  compose.onNodeWithText("Пропущено: 1 · Без отметки: 1 · Ожидает: 0").assertExists()
 }
 @Test fun emptyPeriodHasExplanation() {
  compose.setContent {MaterialTheme {IntakeChart(listOf(IntakeDay(LocalDate.now(),0,0,0,0,0)),null)}}
  compose.onNodeWithTag("chartEmpty").assertExists()
  compose.onNodeWithTag("chart-day-summary").assertDoesNotExist()
 }
}
