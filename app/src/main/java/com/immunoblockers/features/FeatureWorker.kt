package com.immunoblockers.features

import android.content.*
import android.app.*
import android.net.ConnectivityManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.immunoblockers.*
import com.immunoblockers.R
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class FeatureWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as ImmunoblockersApp
        val prefs=app.getSharedPreferences("feature_checks",Context.MODE_PRIVATE)
        val settings=app.settings.flow.first()
        val rows=app.repository.dao.occurrences();val meds=app.repository.dao.medications()
        fun notify(id:Int,title:Int,text:String,screen:String) {
            if(!settings.reminders||!app.reminders.notificationsAvailable())return
            val manager=app.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("feature_updates",app.getString(R.string.feature_channel),NotificationManager.IMPORTANCE_DEFAULT))
            val intent=Intent(app,MainActivity::class.java).setAction("feature.$screen").putExtra("screen","tools/$screen")
            val pending=PendingIntent.getActivity(app,id,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            try{NotificationManagerCompat.from(app).notify(id,NotificationCompat.Builder(app,"feature_updates").setSmallIcon(R.drawable.ic_notification).setContentTitle(app.getString(title)).setContentText(text).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).build())}catch(_:SecurityException){}
        }
        val low=LocalBook(app).stocks(rows).filter {s->s.remaining<=s.threshold&&meds.any {it.id==s.id&&!it.archived&&!it.paused&&it.unit==s.unit}}
        val day=LocalDate.now().toString()
        if(low.isNotEmpty()&&prefs.getString("low_day",null)!=day&&settings.reminders&&app.reminders.notificationsAvailable()) {
            notify(501,R.string.stock_notification,app.getString(R.string.stock_notification_body,low.size),"stock")
            prefs.edit().putString("low_day",day).apply()
        }
        @Suppress("DEPRECATION")
        val connected=app.getSystemService(ConnectivityManager::class.java).activeNetworkInfo?.isConnected==true
        if(connected) {
            if(System.currentTimeMillis()-prefs.getLong("update_check",0)>86400000L) {
                prefs.edit().putLong("update_check",System.currentTimeMillis()).apply()
                runCatching {Updates.check()?.let {r->if(prefs.getString("update_notified",null)!=r.version){notify(503,R.string.update_notification,r.version,"update");prefs.edit().putString("update_notified",r.version).apply()}}}
            }
        }
        return Result.success()
    }
}
