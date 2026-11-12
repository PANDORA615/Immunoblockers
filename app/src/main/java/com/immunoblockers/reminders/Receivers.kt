package com.immunoblockers.reminders

import android.content.*
import androidx.work.*
import com.immunoblockers.ImmunoblockersApp
import com.immunoblockers.data.Occurrence
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as ImmunoblockersApp
        app.appScope.launch {
            try {
                val id = intent.getStringExtra("doseId")
                when (intent.action) {
                    ReminderScheduler.TAKE -> if (id != null) { app.repository.mark(id, Occurrence.TAKEN); app.reminders.cancel(id) }
                    ReminderScheduler.SNOOZE -> if (id != null) { app.repository.snooze(id, 10); app.reminders.cancel(id) }
                    else -> app.reminders.deliver()
                }
                app.reminders.reconcileAndSchedule()
            } finally { pending.finish() }
        }
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueueUniqueWork("restore", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReconcileWorker>().build())
    }
}
class ReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as ImmunoblockersApp
        app.reminders.reconcileAndSchedule()
        app.reminders.summary()
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
