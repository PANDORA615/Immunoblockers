package com.immunoblockers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class WellbeingTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private lateinit var db:AppDatabase
 private var prefs=ThemeSettings(askWellbeing=true)
 private lateinit var repo:Repository
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();repo=Repository(db){prefs}}
 @After fun close(){db.close()}
 @Test fun textOnlyMoodOnlyEditingDeletingAndEmptyRejection()=runBlocking {
  val entry=WellbeingEntry("text",1234,null,"Только текст")
  repo.saveWellbeing(entry)
  assertEquals(listOf(entry),repo.wellbeing.first())
  repo.saveWellbeing(entry.copy(text="Изменено"))
  repo.saveWellbeing(WellbeingEntry("mood",2345,"Хорошо"))
  assertEquals(2,repo.wellbeing.first().size)
  var rejected=false
  try{repo.saveWellbeing(WellbeingEntry("empty",1,null,"  "))}catch(_:IllegalArgumentException){rejected=true}
  assertTrue(rejected)
  repo.deleteWellbeing("mood")
  assertEquals("Изменено",repo.wellbeing.first().single().text)
 }
 private suspend fun row(id:String):Occurrence {
  val row=Occurrence(id,id,1,"2026-11-12","09:00",1,"Лекарство","1","мг")
  repo.dao.insertOccurrences(listOf(row));return row
 }
 @Test fun doubleTapGroupingIntervalsAndHistoryCorrection()=runBlocking {
  for(hours in listOf(1,2,4,6)) {
   prefs=prefs.copy(surveyHours=hours)
   val firstAt=hours*100_000_000L
   val first=row("first-$hours")
   val results=coroutineScope {(1..8).map {async {repo.mark(first.id,Occurrence.TAKEN,firstAt)}}.awaitAll()}
   assertEquals(1,results.count {it!=null})
   repo.mark(row("inside-$hours").id,Occurrence.TAKEN,firstAt+30*60_000)
   repo.mark(row("outside-$hours").id,Occurrence.TAKEN,firstAt+30*60_000+1)
   val group=db.wellbeing().groups().first {it.firstAt==firstAt}
   assertEquals(hours*3_600_000L,group.dueAt-group.firstAt)
   assertEquals(2,db.wellbeing().members(group.id).size)
  }
  val count=db.wellbeing().groups().size
  repo.correct(row("historical").id,Occurrence.TAKEN)
  assertEquals(count,db.wellbeing().groups().size)
  prefs=prefs.copy(askWellbeing=false)
  repo.mark(row("disabled").id,Occurrence.TAKEN)
  assertEquals(count,db.wellbeing().groups().size)
 }
 @Test fun answerLinksOnlyStillTakenMembers()=runBlocking {
  repo.mark(row("a").id,Occurrence.TAKEN,1000)
  repo.mark(row("b").id,Occurrence.TAKEN,2000)
  repo.correct("b",Occurrence.PENDING)
  val group=db.wellbeing().groups().single()
  repo.saveWellbeing(WellbeingEntry("answer",3000,text="Заметка",groupId=group.id))
  assertEquals("answered",db.wellbeing().group(group.id)!!.status)
  db.openHelper.readableDatabase.query("SELECT occurrenceId FROM wellbeing_links").use {assertTrue(it.moveToFirst());assertEquals("a",it.getString(0));assertFalse(it.moveToNext())}
 }
 @Test fun versionOneMigrationAndLegacyImportAreDurableAndIdempotent()=runBlocking {
  val name="migration-${UUID.randomUUID()}.db"
  val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
  old.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `unit` TEXT NOT NULL, `note` TEXT NOT NULL, `start` TEXT NOT NULL, `end` TEXT, `days` TEXT NOT NULL, `times` TEXT NOT NULL, `paused` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `version` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
old.execSQL("CREATE TABLE IF NOT EXISTS `occurrences` (`id` TEXT NOT NULL, `medicationId` TEXT NOT NULL, `scheduleVersion` INTEGER NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `unit` TEXT NOT NULL, `state` TEXT NOT NULL, `markedAt` INTEGER, `snoozedUntil` INTEGER, `notifiedAt` INTEGER, PRIMARY KEY(`id`))")
old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_occurrences_medicationId_date_time` ON `occurrences` (`medicationId`, `date`, `time`)")
  old.version=1;old.close()
  val prefs=context.getSharedPreferences("personal_book",Context.MODE_PRIVATE)
  val before=HashMap(prefs.all)
  prefs.edit().putString("mood","Так себе").putString("note","Старая заметка").putLong("mood_at",123456).remove("journal_imported_v2").commit()
  try {
   var migrated=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
   Repository(migrated).importLegacy(context)
   Repository(migrated).importLegacy(context)
   val entry=migrated.wellbeing().observe().first().single()
   assertEquals(123456L,entry.recordedAt);assertEquals("Старая заметка",entry.text);assertEquals("Так себе",entry.mood)
   migrated.close()
   migrated=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
   assertEquals(entry,migrated.wellbeing().observe().first().single());migrated.close()
  }finally {
   context.deleteDatabase(name)
   val restore=prefs.edit().clear()
   before.forEach {(key,value)->when(value){is String->restore.putString(key,value);is Long->restore.putLong(key,value);is Boolean->restore.putBoolean(key,value)}}
   restore.commit()
  }
 }
}
