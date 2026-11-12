package com.immunoblockers

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.immunoblockers.data.*
import com.immunoblockers.features.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class SurveyDeliveryTest {
 private val app get()=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
 private suspend fun worker(id:String)=TestListenableWorkerBuilder<WellbeingSurveyWorker>(app).setInputData(workDataOf("group" to id)).build().doWork()
 @Test fun duplicateDeliveryAnswerAndStaleGroups()=runBlocking {
  val original=app.settings.flow.first()
  val id=UUID.randomUUID().toString();val now=System.currentTimeMillis();val dao=app.database.wellbeing()
  try {
   app.settings.update {it.copy(askWellbeing=true)}
   app.repository.dao.insertOccurrences(listOf(Occurrence(id,id,1,"2026-11-12","09:00",now,"Связанный препарат","1","мг",Occurrence.TAKEN,now)))
   dao.saveGroup(SurveyGroup(id,now-7_200_000,now-1000));dao.member(SurveyMember(id,id))
   coroutineScope {listOf(async {worker(id)},async {worker(id)}).awaitAll()}
   assertEquals("delivered",dao.group(id)!!.status)
   assertEquals(1,app.getSystemService(NotificationManager::class.java).activeNotifications.count {it.tag=="wellbeing-survey-$id"})
   app.repository.saveWellbeing(WellbeingEntry(id,now,text="Ответ",groupId=id))
   WellbeingSurveys.reconcile(app)
   worker(id)
   assertEquals("answered",dao.group(id)!!.status)
   assertFalse(app.getSystemService(NotificationManager::class.java).activeNotifications.any {it.tag=="wellbeing-survey-$id"})
   dao.saveGroup(SurveyGroup(id,now-100_000_000,now-86_400_001))
   worker(id)
   assertEquals("pending",dao.group(id)!!.status)
   WellbeingSurveys.reconcile(app)
   assertEquals("cancelled",dao.group(id)!!.status)
  }finally {app.repository.deleteWellbeing(id);dao.deleteMembers(id);dao.deleteGroup(id);app.settings.save(original);WellbeingSurveys.reconcile(app)}
 }
 @Test fun cancelledIntakesDisableAndRestoreDoNotRecreateOldSurveys()=runBlocking {
  val original=app.settings.flow.first();val dao=app.database.wellbeing();val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
  val manager=WorkManager.getInstance(app)
  try {
   app.settings.update {it.copy(askWellbeing=true)}
   app.repository.dao.insertOccurrences(listOf(Occurrence(id,id,1,"2026-11-12","09:00",now,"Препарат","1","мг",Occurrence.TAKEN,now)))
   dao.saveGroup(SurveyGroup(id,now,now+7_200_000));dao.member(SurveyMember(id,id))
   WellbeingSurveys.reconcile(app);WellbeingSurveys.reconcile(app)
   var jobs=manager.getWorkInfosForUniqueWork("wellbeing-survey-$id").get(15,TimeUnit.SECONDS)
   assertEquals(1,jobs.count {!it.state.isFinished})
   val workId=jobs.single { !it.state.isFinished }.id
   WellbeingSurveys.reconcile(app)
   jobs=manager.getWorkInfosForUniqueWork("wellbeing-survey-$id").get(15,TimeUnit.SECONDS)
   assertEquals(workId,jobs.single {!it.state.isFinished}.id)
   app.repository.correct(id,Occurrence.PENDING);WellbeingSurveys.reconcile(app)
   assertEquals("cancelled",dao.group(id)!!.status)
   app.repository.correct(id,Occurrence.TAKEN)
   dao.saveGroup(SurveyGroup(id,now,now+7_200_000))
   app.settings.update {it.copy(askWellbeing=false)};WellbeingSurveys.reconcile(app)
   assertEquals("cancelled",dao.group(id)!!.status)
   app.settings.update {it.copy(askWellbeing=true)};WellbeingSurveys.reconcile(app)
   assertEquals("cancelled",dao.group(id)!!.status)
  }finally {dao.deleteMembers(id);dao.deleteGroup(id);app.settings.save(original);WellbeingSurveys.reconcile(app)}
 }
}
