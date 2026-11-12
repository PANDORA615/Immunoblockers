package com.immunoblockers

import com.immunoblockers.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleEngineTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun med() = Medication("a","Лекарство","1.5","мл",start="2026-01-01",times="09:00,21:00",createdAt=0,updatedAt=0)
    @Test fun dailyTwoTimesAndStableIdentity() {
        val one=ScheduleEngine.generate(med(),LocalDate.of(2026,1,1),LocalDate.of(2026,1,2),zone)
        assertEquals(4,one.size)
        assertEquals(4,one.map {it.id}.toSet().size)
        assertEquals(one,ScheduleEngine.generate(med(),LocalDate.of(2026,1,1),LocalDate.of(2026,1,2),zone))
    }
    @Test fun selectedWeekdaysAndCourseBoundaries() {
        val rows=ScheduleEngine.generate(med().copy(days="1,3",start="2026-01-05",end="2026-01-07"),LocalDate.of(2026,1,1),LocalDate.of(2026,2,1),zone)
        assertEquals(listOf("2026-01-05","2026-01-07"),rows.map{it.date}.distinct())
    }
    @Test fun pauseAndArchiveProduceNothing() {
        for(m in listOf(med().copy(paused=true),med().copy(archived=true)))assertTrue(ScheduleEngine.generate(m,LocalDate.of(2026,1,1),LocalDate.of(2026,1,10),zone).isEmpty())
    }
    @Test fun editKeepsIdentityAndUpdatesSnapshot() {
        val d=LocalDate.of(2026,1,1)
        val old=ScheduleEngine.generate(med(),d,d,zone)
        val edited=ScheduleEngine.generate(med().copy(version=2,name="Новое имя"),d,d,zone)
        assertEquals(old.map{it.id},edited.map{it.id})
        assertEquals("Новое имя",edited.first().name)
        assertEquals(2,edited.first().scheduleVersion)
    }
    @Test fun springGapUsesFirstValidMoment() {
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"),ScheduleEngine.instant(LocalDate.of(2026,3,29),LocalTime.of(2,30),zone))
    }
    @Test fun autumnOverlapUsesFirstOffsetAndOneOccurrence() {
        val date=LocalDate.of(2026,10,25)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"),ScheduleEngine.instant(date,LocalTime.of(2,30),zone))
        assertEquals(1,ScheduleEngine.generate(med().copy(times="02:30"),date,date,zone).size)
    }
    @Test fun sameWallTimeDifferentZoneSameIdentity() {
        val d=LocalDate.of(2026,1,1)
        val a=ScheduleEngine.generate(med(),d,d,zone)
        val b=ScheduleEngine.generate(med(),d,d,ZoneId.of("Asia/Tokyo"))
        assertEquals(a.map{it.id},b.map{it.id})
        assertNotEquals(a.first().scheduledAt,b.first().scheduledAt)
    }
    @Test fun snoozeCrossesMidnightWithoutChangingNextDose() {
        val now=Instant.parse("2026-01-01T23:55:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-01-02T00:05:00Z").toEpochMilli(),ScheduleEngine.snoozeTime(now,10))
        val d=LocalDate.of(2026,1,1)
        val row=ScheduleEngine.generate(med(),d,d,zone).first()
        assertEquals(row.scheduledAt,row.copy(snoozedUntil=ScheduleEngine.snoozeTime(now,10)).scheduledAt)
    }
    @Test fun duplicateTimesDoNotDuplicateOccurrences() {
        val d=LocalDate.of(2026,1,1)
        assertEquals(1,ScheduleEngine.generate(med().copy(times="09:00,09:00"),d,d,zone).size)
    }
    @Test fun alreadyMarkedCannotBeMarkedAgain() {
        val d=LocalDate.of(2026,1,1)
        val row=ScheduleEngine.generate(med(),d,d,zone).first()
        assertTrue(ScheduleEngine.canMark(row));assertFalse(ScheduleEngine.canMark(row.copy(state=Occurrence.TAKEN)))
    }
    @Test fun emptyRangeIsEmpty() { assertTrue(ScheduleEngine.generate(med().copy(end="2025-01-01"),LocalDate.of(2026,1,1),LocalDate.of(2026,1,2),zone).isEmpty()) }
}
