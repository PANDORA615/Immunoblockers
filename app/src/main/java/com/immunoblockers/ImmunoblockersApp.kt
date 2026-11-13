package com.immunoblockers

import android.app.Application
import androidx.room.Room
import androidx.work.*
import com.immunoblockers.data.*
import com.immunoblockers.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ImmunoblockersApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "immunoblockers.db").addMigrations(MIGRATION_1_2).build() }
    val repository by lazy { Repository(database) { settings.flow.first() } }
    val settings by lazy { SettingsRepository(this) }
    val reminders by lazy { ReminderScheduler(this, repository, settings) }
    override fun onCreate() {
        super.onCreate()
        // Retire the previous exchange locally before any old periodic worker can run.
        getSharedPreferences("pair", MODE_PRIVATE).edit().clear().putBoolean("stopped",true).commit()
        getSystemService(android.app.NotificationManager::class.java).cancel(502)
        reminders.createChannel()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("feature_checks",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.immunoblockers.features.FeatureWorker>(15,TimeUnit.MINUTES).build())
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("reconcile", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReconcileWorker>(12, TimeUnit.HOURS).build())
        appScope.launch {
            repository.importLegacy(this@ImmunoblockersApp)
            reminders.reconcileAndSchedule()
        }
    }
}
