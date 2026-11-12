package com.immunoblockers.data

import androidx.room.*
import java.time.*

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey val id: String,
    val name: String, val dose: String, val unit: String, val note: String = "",
    val start: String, val end: String? = null, val days: String = "1,2,3,4,5,6,7",
    val times: String, val paused: Boolean = false, val archived: Boolean = false,
    val version: Int = 1, val createdAt: Long, val updatedAt: Long
)
@Entity(tableName = "occurrences", indices = [Index(value = ["medicationId", "date", "time"], unique = true)])
data class Occurrence(
    @PrimaryKey val id: String,
    val medicationId: String, val scheduleVersion: Int,
    val date: String, val time: String, val scheduledAt: Long,
    val name: String, val dose: String, val unit: String,
    val state: String = PENDING, val markedAt: Long? = null,
    val snoozedUntil: Long? = null, val notifiedAt: Long? = null
) {
    companion object { const val PENDING = "pending"; const val TAKEN = "taken"; const val SKIPPED = "skipped" }
    val dueAt: Long get() = snoozedUntil ?: scheduledAt
}

data class DoseChange(val before: Occurrence, val after: Occurrence)

object ScheduleEngine {
    fun instant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
        val local = date.atTime(time)
        val offsets = zone.rules.getValidOffsets(local)
        return when {
            offsets.isNotEmpty() -> local.toInstant(offsets.first())
            else -> zone.rules.getTransition(local).dateTimeAfter.atZone(zone).toInstant()
        }
    }
    fun generate(med: Medication, from: LocalDate, through: LocalDate, zone: ZoneId): List<Occurrence> {
        if (med.paused || med.archived) return emptyList()
        val start = maxOf(from, LocalDate.parse(med.start))
        val end = minOf(through, med.end?.let(LocalDate::parse) ?: through)
        if (start > end) return emptyList()
        val days = med.days.split(',').mapNotNull(String::toIntOrNull).toSet()
        val times = med.times.split(',').map(LocalTime::parse).distinct().sorted()
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }
            .filter { it.dayOfWeek.value in days }.flatMap { date ->
                times.map { time ->
                    Occurrence("${med.id}|$date|$time", med.id, med.version, date.toString(), time.toString(),
                        instant(date, time, zone).toEpochMilli(), med.name, med.dose, med.unit)
                }
            }.toList()
    }
    fun snoozeTime(now: Long, minutes: Int): Long {
        require(minutes in setOf(5, 10, 15, 30))
        return now + minutes * 60_000L
    }
    fun canMark(occurrence: Occurrence) = occurrence.state == Occurrence.PENDING
}

@Dao
interface AppDao {
    @Query("SELECT * FROM medications ORDER BY archived, name COLLATE NOCASE") fun observeMedications(): kotlinx.coroutines.flow.Flow<List<Medication>>
    @Query("SELECT * FROM occurrences ORDER BY date DESC, time ASC") fun observeOccurrences(): kotlinx.coroutines.flow.Flow<List<Occurrence>>
    @Query("SELECT * FROM medications") suspend fun medications(): List<Medication>
    @Query("SELECT * FROM occurrences") suspend fun occurrences(): List<Occurrence>
    @Query("SELECT * FROM occurrences WHERE id = :id") suspend fun occurrence(id: String): Occurrence?
    @Upsert suspend fun saveMedication(med: Medication)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOccurrences(rows: List<Occurrence>)
    @Update suspend fun updateOccurrence(row: Occurrence)
    @Query("DELETE FROM occurrences WHERE medicationId = :id AND state = 'pending' AND scheduledAt > :now") suspend fun deleteFuture(id: String, now: Long)
    @Query("UPDATE occurrences SET state = :state, markedAt = :now, snoozedUntil = NULL WHERE id = :id AND state = 'pending'") suspend fun mark(id: String, state: String, now: Long): Int
}
@Database(entities = [Medication::class, Occurrence::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
