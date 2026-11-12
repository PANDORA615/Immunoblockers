package com.immunoblockers

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationTest {
    @Test fun nativeNotificationActionsSnoozeAndTakeWithoutAnActivity() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        val id=UUID.randomUUID().toString()
        val now=System.currentTimeMillis()
        val med=Medication(id,"Тест уведомления","1","таблетка",start=LocalDate.now().toString(),times=LocalTime.now().withSecond(0).withNano(0).toString(),createdAt=now,updatedAt=now)
        app.repository.saveMedication(med)
        val row=app.repository.dao.occurrences().first {it.medicationId==id && it.date==LocalDate.now().toString()}
        try {
            app.reminders.deliver()
            val manager=app.getSystemService(NotificationManager::class.java)
            val notification=manager.activeNotifications.first {it.tag==row.id}.notification
            assertEquals(2,notification.actions.size)
            notification.actions[1].actionIntent.send()
            withTimeout(15000){while(app.repository.dao.occurrence(row.id)?.snoozedUntil==null)delay(100)}
            val snoozed=app.repository.dao.occurrence(row.id)!!
            assertEquals(row.scheduledAt,snoozed.scheduledAt)
            assertTrue(snoozed.snoozedUntil!!>System.currentTimeMillis())
            notification.actions[0].actionIntent.send()
            notification.actions[0].actionIntent.send()
            withTimeout(15000){while(app.repository.dao.occurrence(row.id)?.state!=Occurrence.TAKEN)delay(100)}
            assertEquals(1,app.repository.dao.occurrences().count {it.id==row.id})
            withTimeout(15000){while(manager.activeNotifications.any {it.tag==row.id})delay(100)}
        } finally {app.repository.saveMedication(med.copy(archived=true,version=2));app.reminders.reconcileAndSchedule()}
    }
}
