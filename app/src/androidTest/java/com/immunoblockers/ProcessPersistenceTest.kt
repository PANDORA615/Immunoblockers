package com.immunoblockers

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.TimeUnit

/** CI invokes each phase separately, with am force-stop between them. */
class ProcessPersistenceTest {
 @Test fun journalProfileAndPendingSurveySurviveProcessRestart()=runBlocking {
  val phase=InstrumentationRegistry.getArguments().getString("persistence_phase")
  Assume.assumeTrue("This scenario requires the two-process CI harness",phase!=null)
  val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
  val backup=app.getSharedPreferences("process_test",Context.MODE_PRIVATE)
  val id="process-persistence"
  val journal=app.database.wellbeing()
  if(phase=="prepare") {
   val settings=app.settings.flow.first()
   backup.edit().putString("nickname",settings.nickname).putString("avatar",settings.avatarPath)
     .putString("wallpaper",settings.wallpaperPath).putBoolean("ask",settings.askWellbeing).putInt("hours",settings.surveyHours).putInt("pid",android.os.Process.myPid()).commit()
   val image=File(app.filesDir,"images/process-test.jpg").apply {parentFile!!.mkdirs()}
   val bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888)
   bitmap.eraseColor(android.graphics.Color.GREEN)
   image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
   app.settings.update {it.copy(nickname="Профиль после перезапуска",avatarPath=image.absolutePath,wallpaperPath=image.absolutePath,askWellbeing=true,surveyHours=2)}
   app.repository.saveWellbeing(WellbeingEntry(id,123456,text="Текст после перезапуска"))
   val now=System.currentTimeMillis()
   app.repository.dao.insertOccurrences(listOf(Occurrence(id,id,1,"2026-11-12","09:00",now,"Тест процесса","1","мг")))
   app.repository.mark(id,Occurrence.TAKEN)
   app.reminders.reconcileAndSchedule()
   val group=journal.membership(id)!!
   val jobs=WorkManager.getInstance(app).getWorkInfosForUniqueWork("wellbeing-survey-$group").get(15,TimeUnit.SECONDS)
   backup.edit().putString("group",group).putString("work",jobs.single {!it.state.isFinished}.id.toString()).commit()
   // On the next application startup even a formerly configured pair must be retired.
   app.getSharedPreferences("pair",Context.MODE_PRIVATE).edit().putString("key","retired-test-key").putString("server","https://example.invalid").putBoolean("stopped",false).commit()
  }else {
   assertEquals("verify",phase)
   assertNotEquals(backup.getInt("pid",0),android.os.Process.myPid())
   try {
    val settings=app.settings.flow.first()
    assertEquals("Профиль после перезапуска",settings.nickname)
    assertTrue(File(settings.avatarPath).isFile)
    assertEquals(settings.avatarPath,settings.wallpaperPath)
    assertEquals("Текст после перезапуска",journal.entry(id)!!.text)
    assertNull(journal.entry(id)!!.mood)
    val group=backup.getString("group",null)!!
    app.reminders.reconcileAndSchedule()
    val jobs=WorkManager.getInstance(app).getWorkInfosForUniqueWork("wellbeing-survey-$group").get(15,TimeUnit.SECONDS)
    assertEquals(1,jobs.count {!it.state.isFinished})
    assertEquals(backup.getString("work",null),jobs.single {!it.state.isFinished}.id.toString())
    val pair=app.getSharedPreferences("pair",Context.MODE_PRIVATE)
    assertFalse(pair.contains("key"));assertTrue(pair.getBoolean("stopped",false))
   }finally {
    app.repository.deleteWellbeing(id)
    val group=backup.getString("group",null)
    if(group!=null){WorkManager.getInstance(app).cancelUniqueWork("wellbeing-survey-$group");journal.deleteMembers(group);journal.deleteGroup(group)}
    app.database.openHelper.writableDatabase.execSQL("DELETE FROM occurrences WHERE id=?",arrayOf(id))
    File(app.filesDir,"images/process-test.jpg").delete()
    app.settings.update {it.copy(nickname=backup.getString("nickname","").orEmpty(),avatarPath=backup.getString("avatar","").orEmpty(),wallpaperPath=backup.getString("wallpaper","").orEmpty(),askWellbeing=backup.getBoolean("ask",false),surveyHours=backup.getInt("hours",2))}
    backup.edit().clear().commit()
   }
  }
  Unit
 }
}
