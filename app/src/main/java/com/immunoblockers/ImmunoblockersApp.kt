package com.immunoblockers

import android.app.Application
import androidx.room.Room
import androidx.work.*
import com.immunoblockers.data.*
import com.immunoblockers.reminders.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class ImmunoblockersApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "immunoblockers.db").build() }
    val repository by lazy { Repository(database) }
    val settings by lazy { SettingsRepository(this) }
    val reminders by lazy { ReminderScheduler(this, repository, settings) }
    override fun onCreate() {
        super.onCreate()
        reminders.createChannel()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("reconcile", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReconcileWorker>(12, TimeUnit.HOURS).build())
        appScope.launch { reminders.reconcileAndSchedule() }
    }
}
