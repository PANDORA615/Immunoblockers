package com.immunoblockers.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class Repository(val db: AppDatabase, private val preferences:suspend ()->ThemeSettings = {ThemeSettings()}) {
    val wellbeing = db.wellbeing().observe()
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
    suspend fun mark(id: String, state: String, now:Long=System.currentTimeMillis()): DoseChange? = mutex.withLock {
        db.withTransaction {
            val before = dao.occurrence(id) ?: return@withTransaction null
            if (dao.mark(id, state, now) == 0) null else {
                val settings=preferences()
                if(state==Occurrence.TAKEN && settings.askWellbeing) {
                    val journal=db.wellbeing()
                    val group=journal.groups().filter {it.status=="pending" && now>=it.firstAt && now-it.firstAt<=30*60_000L}.maxByOrNull {it.firstAt}
                        ?: SurveyGroup(java.util.UUID.randomUUID().toString(),now,now+settings.surveyHours.coerceIn(1,6)*3_600_000L).also {journal.saveGroup(it)}
                    journal.member(SurveyMember(group.id,id))
                }
                DoseChange(before, before.copy(state = state, markedAt = now, snoozedUntil = null))
            }
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
    suspend fun importLegacy(context:android.content.Context) = mutex.withLock {
        val prefs=context.getSharedPreferences("personal_book",android.content.Context.MODE_PRIVATE)
        if(prefs.getBoolean("journal_imported_v2",false))return@withLock
        db.withTransaction {
            val mood=prefs.getString("mood","").orEmpty().trim()
            val text=prefs.getString("note","").orEmpty()
            if(mood.isNotEmpty()||text.isNotBlank()) {
                // A stable ID makes retries safe even if the process dies before the preference commit.
                if(db.wellbeing().entry("legacy-personal-book")==null)
                    db.wellbeing().save(WellbeingEntry("legacy-personal-book",prefs.getLong("mood_at",0),mood.ifEmpty {null},text))
            }
        }
        check(prefs.edit().putBoolean("journal_imported_v2",true).commit())
    }
    suspend fun saveWellbeing(entry:WellbeingEntry) = mutex.withLock {
        require(!entry.mood.isNullOrBlank()||entry.text.isNotBlank())
        db.withTransaction {
            val journal=db.wellbeing()
            journal.save(entry.copy(mood=entry.mood?.takeIf {it.isNotBlank()}))
            entry.groupId?.let {id->
                val members=journal.members(id).filter {dao.occurrence(it)?.state==Occurrence.TAKEN}
                journal.link(members.map {WellbeingLink(entry.id,it)})
                journal.group(id)?.let {journal.saveGroup(it.copy(status="answered"))}
            }
        }
    }
    suspend fun deleteWellbeing(id:String)=db.withTransaction { db.wellbeing().deleteLinks(id);db.wellbeing().delete(id) }
}
