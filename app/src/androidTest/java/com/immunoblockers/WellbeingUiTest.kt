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
 private fun capture(name:String) {
  compose.waitForIdle()
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  instrumentation.waitForIdleSync();android.os.SystemClock.sleep(350)
  val bitmap=instrumentation.uiAutomation.takeScreenshot()?:return
  val folder=java.io.File(app.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
  java.io.File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  bitmap.recycle()
 }
 @Test fun textOnlyEntryPersistsAfterRecreationAndCanBeEditedAndDeleted() {
  val text="Текст без состояния ${System.currentTimeMillis()}"
  compose.onNodeWithText("Помощники").performClick()
  compose.onNodeWithText("Мы вдвоём").assertDoesNotExist()
  compose.onNodeWithText("Самочувствие").performClick()
  compose.onNodeWithText("Личная заметка о самочувствии").performTextInput(text)
  compose.onNodeWithText("Сохранить самочувствие").performScrollTo().performClick()
  compose.waitUntil(15000){runBlocking {app.repository.wellbeing.first().any {it.text==text&&it.mood==null}}}
  compose.waitUntil(15000){compose.onAllNodesWithText("Самочувствие сохранено").fetchSemanticsNodes().size==1}
  compose.onNodeWithText("Самочувствие сохранено").assertExists()
  capture("wellbeing")
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText(text).assertExists()
  compose.onAllNodesWithText("Изменить").onFirst().performScrollTo().performClick()
  compose.onNodeWithText("Личная заметка о самочувствии").performScrollTo().performTextReplacement("$text изменено")
  compose.onNodeWithText("Сохранить изменения").performScrollTo().performClick()
  compose.waitUntil(15000){runBlocking {app.repository.wellbeing.first().any {it.text=="$text изменено"}}}
  compose.onAllNodesWithText("Удалить").onFirst().performScrollTo().performClick()
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
   capture("profile")
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
