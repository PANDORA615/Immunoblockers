package com.immunoblockers

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID

class DesignAndJournalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        var captured:Bitmap?=null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            captured=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        }
        val bitmap=captured?:return
        val folder=File(app.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun fieldDesignSurvivesRecreation() {
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasText("С обводкой"))
        compose.onNodeWithText("С обводкой").performClick()
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        runBlocking {withTimeout(15000){while(app.settings.flow.first().fieldStyle!="outlined")delay(100)}}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasText("С обводкой"))
        compose.onNodeWithText("С обводкой").assertIsSelected()
        screenshot("appearance")
    }
    @Test fun journalShowsHistoricalDoseAndActualMark() {
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        val now=System.currentTimeMillis()
        val med=Medication(UUID.randomUUID().toString(),"Журнал проверка","1.25","мг",start=LocalDate.now().toString(),times="23:59",createdAt=now,updatedAt=now)
        runBlocking {
            app.repository.saveMedication(med)
            val row=app.repository.dao.occurrences().first {it.medicationId==med.id&&it.date==LocalDate.now().toString()}
            app.repository.mark(row.id,Occurrence.TAKEN)
        }
        compose.onNodeWithText("История").performClick()
        compose.onNodeWithTag("journalList").performScrollToNode(hasText("График приёмов"))
        screenshot("chart")
        compose.onNodeWithTag("journalList").performScrollToNode(hasText("1,25 мг"))
        compose.onNodeWithText("1,25 мг").assertExists()
        compose.onAllNodesWithText("Журнал проверка").onFirst().assertExists()
        screenshot("journal")
        runBlocking {app.repository.saveMedication(med.copy(archived=true,version=2));app.reminders.reconcileAndSchedule()}
    }
    @Test fun atomicAppearanceChangesKeepUnrelatedFields()=runBlocking {
        val settings=ApplicationProvider.getApplicationContext<ImmunoblockersApp>().settings
        val old=settings.flow.first()
        try {
            coroutineScope {
                listOf<suspend ()->Unit>(
                    {settings.update {it.copy(corners="square")}},
                    {settings.update {it.copy(fieldStyle="outlined")}},
                    {settings.update {it.copy(privateWidgets=true)}},
                    {settings.update {it.copy(boldText=false)}}
                ).map {async(Dispatchers.IO){it()}}.awaitAll()
            }
            val saved=settings.flow.first()
            assertEquals("square",saved.corners);assertEquals("outlined",saved.fieldStyle)
            assertTrue(saved.privateWidgets);assertFalse(saved.boldText)
        } finally {settings.save(old)}
    }
}
