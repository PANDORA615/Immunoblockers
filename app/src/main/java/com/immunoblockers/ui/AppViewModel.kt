package com.immunoblockers.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.immunoblockers.ImmunoblockersApp
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

sealed interface UiEvent { data class Marked(val before: DoseChange): UiEvent; data object Snoozed: UiEvent; data object Error: UiEvent }
class AppViewModel(application: Application): AndroidViewModel(application) {
    val app = application as ImmunoblockersApp
    val medications = app.repository.medications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val occurrences = app.repository.occurrences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettings())
    val events = Channel<UiEvent>(Channel.BUFFERED)
    private fun work(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { try { block() } catch (_: Exception) { events.send(UiEvent.Error) } } }
    fun refresh() = work { app.reminders.reconcileAndSchedule() }
    fun saveMedication(med: Medication, done: () -> Unit) = work {
        app.repository.saveMedication(med); app.reminders.reconcileAndSchedule(); withContext(Dispatchers.Main) { done() }
    }
    fun changeMedication(med: Medication) = saveMedication(med.copy(version = med.version + 1, updatedAt = System.currentTimeMillis())) {}
    fun mark(row: Occurrence, state: String, correction: Boolean = false) = work {
        val before = if(correction) app.repository.correct(row.id, state) else app.repository.mark(row.id, state)
        app.reminders.cancel(row.id); app.reminders.reconcileAndSchedule()
        if(before != null) events.send(UiEvent.Marked(before))
    }
    fun undo(row: DoseChange) = work { app.repository.undo(row); app.reminders.reconcileAndSchedule() }
    fun snooze(row: Occurrence, minutes: Int) = work { app.repository.snooze(row.id, minutes); app.reminders.cancel(row.id); app.reminders.reconcileAndSchedule(); events.send(UiEvent.Snoozed) }
    fun settings(value: ThemeSettings) = work { app.settings.save(value); app.reminders.reconcileAndSchedule() }
    fun testNotification() = work { app.reminders.test() }
}
