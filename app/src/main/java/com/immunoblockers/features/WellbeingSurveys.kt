package com.immunoblockers.features

import android.app.*
import android.content.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import androidx.work.*
import com.immunoblockers.*
import com.immunoblockers.R
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object WellbeingSurveys {
    private const val TAG="wellbeing-survey"
    suspend fun reconcile(app:ImmunoblockersApp) {
        val enabled=app.settings.flow.first().askWellbeing
        val manager=WorkManager.getInstance(app)
        app.database.withTransaction {
            val dao=app.database.wellbeing()
            for(group in dao.groups()) {
                val live=dao.members(group.id).any {app.repository.dao.occurrence(it)?.state==com.immunoblockers.data.Occurrence.TAKEN}
                val valid=enabled&&live&&group.status in listOf("pending","delivered")&&System.currentTimeMillis()-group.dueAt<=86_400_000L
                val name="$TAG-${group.id}"
                if(!valid) {
                    manager.cancelUniqueWork(name)
                    NotificationManagerCompat.from(app).cancel(name,0)
                    if(group.status in listOf("pending","delivered"))dao.saveGroup(group.copy(status="cancelled"))
                } else if(group.status=="pending") manager.enqueueUniqueWork(name,ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<WellbeingSurveyWorker>().setInputData(workDataOf("group" to group.id))
                        .setInitialDelay((group.dueAt-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS).addTag(TAG).build())
            }
        }
    }
}
class WellbeingSurveyWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result {
  val app=applicationContext as ImmunoblockersApp
  val id=inputData.getString("group")?:return Result.success()
  return try {
   app.database.withTransaction {
    val dao=app.database.wellbeing();val group=dao.group(id)?:return@withTransaction
    val settings=app.settings.flow.first()
    val members=dao.members(id).mapNotNull {app.repository.dao.occurrence(it)}.filter {it.state==com.immunoblockers.data.Occurrence.TAKEN}
    if(!settings.askWellbeing||group.status!="pending"||members.isEmpty()||System.currentTimeMillis()-group.dueAt>86_400_000L)return@withTransaction
    if(System.currentTimeMillis()<group.dueAt)return@withTransaction
    val manager=app.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("wellbeing", "Самочувствие после приёма",NotificationManager.IMPORTANCE_DEFAULT))
    val intent=Intent(app,MainActivity::class.java).setData(Uri.parse("immunoblockers://wellbeing/$id")).putExtra("screen","wellbeing/$id")
    val pending=PendingIntent.getActivity(app,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification=NotificationCompat.Builder(app,"wellbeing").setSmallIcon(R.drawable.ic_notification).setContentTitle("Как вы себя чувствуете?")
      .setContentText(if(settings.privateNotifications)"Добавьте запись о самочувствии" else members.joinToString {it.name})
      .setVisibility(if(settings.privateNotifications)NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
      .setContentIntent(pending).setOnlyAlertOnce(true).setAutoCancel(true).build()
    try {NotificationManagerCompat.from(app).notify("wellbeing-survey-$id",0,notification)}catch(_:SecurityException){}
    dao.saveGroup(group.copy(status="delivered"))
   }
   Result.success()
  }catch(_:Exception){Result.retry()}
 }
}
