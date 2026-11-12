package com.immunoblockers.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class Repository(val db: AppDatabase) {
    val dao = db.dao()
    val medications = dao.observeMedications()
    val occurrences = dao.observeOccurrences()
    private val mutex = Mutex()
    suspend fun saveMedication(med: Medication, now: Long = System.currentTimeMillis()) = mutex.withLock {
        db.withTransaction {
            dao.deleteFuture(med.id, now)
            dao.saveMedication(med)
            reconcileLocked(now)
        }
    }
    suspend fun reconcile(now: Long = System.currentTimeMillis()) = mutex.withLock { db.withTransaction { reconcileLocked(now) } }
    private suspend fun reconcileLocked(now: Long) {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (med in dao.medications()) {
            dao.insertOccurrences(ScheduleEngine.generate(med, maxOf(today.minusDays(30), Instant.ofEpochMilli(med.createdAt).atZone(zone).toLocalDate()), today.plusDays(30), zone).filter { med.version == 1 || it.scheduledAt >= med.updatedAt })
        }
        for (row in dao.occurrences()) {
            if (row.state == Occurrence.PENDING) {
                val time = ScheduleEngine.instant(LocalDate.parse(row.date), LocalTime.parse(row.time), zone).toEpochMilli()
                if (time != row.scheduledAt) dao.updateOccurrence(row.copy(scheduledAt = time))
            }
        }
    }
    suspend fun deliverDue(now: Long, deliver: (Occurrence) -> Unit) = mutex.withLock {
        db.withTransaction {
            val active = dao.medications().filter { !it.paused && !it.archived }.map { it.id }.toSet()
            val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            for (row in dao.occurrences()) {
                if (row.medicationId in active && row.state == Occurrence.PENDING && row.notifiedAt == null && row.dueAt <= now && (row.date == today || row.snoozedUntil != null)) {
                    deliver(row)
                    dao.updateOccurrence(row.copy(notifiedAt = now))
                }
            }
        }
    }
    suspend fun mark(id: String, state: String): DoseChange? = mutex.withLock {
        db.withTransaction {
            val before = dao.occurrence(id) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            if (dao.mark(id, state, now) == 0) null else DoseChange(before, before.copy(state = state, markedAt = now, snoozedUntil = null))
        }
    }
    suspend fun correct(id: String, state: String): DoseChange? = mutex.withLock {
        db.withTransaction {
            val before = dao.occurrence(id) ?: return@withTransaction null
            val after = before.copy(state = state, markedAt = if (state == Occurrence.PENDING) null else System.currentTimeMillis(), snoozedUntil = null, notifiedAt = if (state == Occurrence.PENDING) null else before.notifiedAt)
            dao.updateOccurrence(after)
            DoseChange(before, after)
        }
    }
    suspend fun undo(change: DoseChange) = mutex.withLock {
        db.withTransaction {
            val current = dao.occurrence(change.before.id)
            // An older Undo must never overwrite a later correction or snooze.
            if (current?.copy(notifiedAt = change.after.notifiedAt) == change.after) dao.updateOccurrence(change.before.copy(notifiedAt = null))
        }
    }
    suspend fun snooze(id: String, minutes: Int) = mutex.withLock {
        db.withTransaction {
            dao.occurrence(id)?.takeIf { it.state == Occurrence.PENDING }?.let {
                dao.updateOccurrence(it.copy(snoozedUntil = ScheduleEngine.snoozeTime(System.currentTimeMillis(), minutes), notifiedAt = null))
            }
        }
    }
}
