package com.immunoblockers

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db:AppDatabase
    private lateinit var repo:Repository
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build();repo=Repository(db)}
    @After fun close(){db.close()}
    private fun med()=Medication("test","Тест","1","таблетка",start=LocalDate.now().toString(),times="23:59",createdAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis())
    @Test fun conflictingMarksAreIdempotentAndHistorySurvivesArchive()=runBlocking {
        val med=med();repo.saveMedication(med)
        val row=repo.dao.occurrences().first()
        val results=coroutineScope {(1..10).map{async(Dispatchers.IO){repo.mark(row.id,Occurrence.TAKEN)}}.awaitAll()}
        assertEquals(1,results.count{it!=null})
        val marked=repo.dao.occurrence(row.id)!!
        repo.saveMedication(med.copy(archived=true,version=2))
        assertEquals(marked,repo.dao.occurrence(row.id))
    }
    @Test fun editPreservesCompletedSnapshotAndSnoozeKeepsIdentity()=runBlocking {
        val med=med();repo.saveMedication(med)
        val row=repo.dao.occurrences().first()
        repo.snooze(row.id,10)
        assertEquals(row.scheduledAt,repo.dao.occurrence(row.id)!!.scheduledAt)
        assertNotNull(repo.dao.occurrence(row.id)!!.snoozedUntil)
        repo.mark(row.id,Occurrence.TAKEN)
        repo.saveMedication(med.copy(name="Новое",times="22:00",version=2))
        assertEquals("Тест",repo.dao.occurrence(row.id)!!.name)
        repo.correct(row.id,Occurrence.SKIPPED)
        assertEquals(Occurrence.SKIPPED,repo.dao.occurrence(row.id)!!.state)
    }
    @Test fun staleUndoCannotOverwriteLaterCorrection()=runBlocking {
        repo.saveMedication(med())
        val row=repo.dao.occurrences().first()
        val first=repo.mark(row.id,Occurrence.TAKEN)!!
        repo.correct(row.id,Occurrence.SKIPPED)
        repo.undo(first)
        assertEquals(Occurrence.SKIPPED,repo.dao.occurrence(row.id)!!.state)
        val reset=repo.correct(row.id,Occurrence.PENDING)!!
        repo.undo(reset)
        assertEquals(Occurrence.SKIPPED,repo.dao.occurrence(row.id)!!.state)
    }

}
