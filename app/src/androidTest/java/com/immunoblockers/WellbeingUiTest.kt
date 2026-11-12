package com.immunoblockers

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class WellbeingUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
 @Test fun textOnlyEntryPersistsAfterRecreationAndCanBeEditedAndDeleted() {
  val text="Текст без состояния ${System.currentTimeMillis()}"
  compose.onNodeWithText("Помощники").performClick()
  compose.onNodeWithText("Мы вдвоём").assertDoesNotExist()
  compose.onNodeWithText("Самочувствие").performClick()
  compose.onNodeWithText("Личная заметка о самочувствии").performTextInput(text)
  compose.onNodeWithText("Сохранить самочувствие").performClick()
  compose.waitUntil(15000){runBlocking {app.repository.wellbeing.first().any {it.text==text&&it.mood==null}}}
  compose.onNodeWithText("Самочувствие сохранено").assertExists()
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText(text).assertExists()
  compose.onAllNodesWithText("Изменить").onFirst().performClick()
  compose.onNodeWithText("Личная заметка о самочувствии").performTextReplacement("$text изменено")
  compose.onNodeWithText("Сохранить изменения").performClick()
  compose.waitUntil(15000){runBlocking {app.repository.wellbeing.first().any {it.text=="$text изменено"}}}
  compose.onAllNodesWithText("Удалить").onFirst().performClick()
  compose.waitUntil(15000){runBlocking {app.repository.wellbeing.first().none {it.text.startsWith(text)}}}
 }
 @Test fun profileAndFiveTabsSurviveStyleChange() {
  val original=runBlocking {app.settings.flow.first()}
  try {
   compose.onNodeWithText(original.nickname.ifBlank {"Мой профиль"}).performClick()
   compose.onNodeWithText("Ник · до 32 символов").performTextReplacement("Локальный профиль")
   compose.onNodeWithText("Сохранить ник").performClick()
   compose.waitUntil(15000){runBlocking {app.settings.flow.first().nickname=="Локальный профиль"}}
   compose.activityRule.scenario.recreate()
   compose.onNodeWithText("Локальный профиль").assertExists()
   compose.onNodeWithText("Назад").performClick()
   for(tab in listOf("Лекарства","История","Помощники","Настройки","Сегодня"))compose.onNodeWithText(tab).performClick()
   compose.onNodeWithText("Настройки").performClick()
   compose.onNodeWithTag("settingsList").performScrollToNode(hasText("Material 3"))
   compose.onNodeWithText("Material 3").performClick()
   compose.onNodeWithText("Material 3").assertIsSelected()
   compose.onNodeWithText("Liquid Glass + Glassmorphism").performClick()
   compose.onNodeWithText("Liquid Glass + Glassmorphism").assertIsSelected()
  }finally {runBlocking {app.settings.save(original)}}
 }
}
