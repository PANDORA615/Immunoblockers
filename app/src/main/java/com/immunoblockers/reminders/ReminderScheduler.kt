package com.immunoblockers.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.immunoblockers.*
import com.immunoblockers.data.*
import com.immunoblockers.widgets.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class ReminderScheduler(private val context: Context, private val repo: Repository, private val settings: SettingsRepository) {
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val manager = NotificationManagerCompat.from(context)
    private val mutex = Mutex()
    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminder_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
    }
    fun exactAvailable() = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    fun notificationsAvailable(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    private fun tick() = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(id: String) { manager.cancel(id, 0) }
    suspend fun reconcileAndSchedule() = mutex.withLock {
        repo.reconcile()
        scheduleLocked()
        WidgetUpdater.updateAll(context, repo.dao.occurrences(), settings.flow.first(), repo.dao.medications().filter { !it.paused && !it.archived }.map { it.id }.toSet())
    }
    private suspend fun scheduleLocked() {
        val all = repo.dao.occurrences()
        val active = repo.dao.medications().filter { !it.paused && !it.archived }.map { it.id }.toSet()
        val live = all.filter { it.state == Occurrence.PENDING && it.medicationId in active }.map { it.id }.toSet()
        context.getSystemService(NotificationManager::class.java).activeNotifications.forEach { if (it.tag != null && it.tag !in live && it.tag != "test" && it.tag != "summary") manager.cancel(it.tag, it.id) }
        val pref = settings.flow.first()
        if (!pref.reminders) { alarm.cancel(tick()); manager.cancelAll(); return }
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val candidates = all.filter { it.medicationId in active && it.state == Occurrence.PENDING && it.notifiedAt == null && (it.date >= today || it.snoozedUntil != null) }
        // One durable next alarm avoids platform limits for long courses / many medicines.
        val next = candidates.minOfOrNull { maxOf(it.dueAt, now + 1000) }
        alarm.cancel(tick())
        if (next != null) {
            try {
                if (exactAvailable()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, tick())
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, tick())
            } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, tick()) }
        }
    }
    suspend fun deliver() = mutex.withLock {
        repo.reconcile()
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val pref = settings.flow.first()
        if (pref.reminders) {
            repo.deliverDue(now) { row -> if (notificationsAvailable()) notifyDose(row, pref) }
        }
        scheduleLocked()
    }
    suspend fun test() {
        if (!notificationsAvailable()) return
        val notification = base(context.getString(R.string.test_notification_title), context.getString(R.string.test_notification_body), settings.flow.first()).build()
        try { manager.notify("test", 0, notification) } catch (_: SecurityException) { }
    }
    suspend fun summary() {
        val rows = repo.dao.occurrences().count { it.date < LocalDate.now().toString() && it.state == Occurrence.PENDING }
        if (rows > 0 && notificationsAvailable() && settings.flow.first().reminders) {
            try { manager.notify("summary", 0, base(context.getString(R.string.unmarked_summary), context.getString(R.string.unmarked_count, rows), settings.flow.first()).setOnlyAlertOnce(true).build()) } catch (_: SecurityException) { }
        }
    }
    private fun notifyDose(row: Occurrence, pref: ThemeSettings) {
        val title = if (pref.privateNotifications) context.getString(R.string.private_title) else row.name
        val body = if (pref.privateNotifications) context.getString(R.string.private_body) else context.getString(R.string.notification_dose, row.dose, row.unit, row.time)
        val open = Intent(context, MainActivity::class.java).setData(Uri.parse("immunoblockers://dose/${Uri.encode(row.id)}")).putExtra("doseId", row.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = base(title, body, pref).setContentIntent(content)
            .addAction(0, context.getString(R.string.taken), action(row.id, TAKE))
            .addAction(0, context.getString(R.string.snooze_notification), action(row.id, SNOOZE))
            .build()
        try { manager.notify(row.id, 0, notification) } catch (_: SecurityException) { }
    }
    private fun base(title: String, text: String, pref: ThemeSettings) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true).setVisibility(if (pref.privateNotifications) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
        .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    private fun action(id: String, type: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(type).setData(Uri.parse("immunoblockers://action/$type/${Uri.encode(id)}")).putExtra("doseId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    companion object { const val CHANNEL = "medication_reminders"; const val TICK = "tick"; const val TAKE = "take"; const val SNOOZE = "snooze" }
}
