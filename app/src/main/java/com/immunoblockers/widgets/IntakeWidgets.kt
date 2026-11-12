package com.immunoblockers.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.immunoblockers.*
import com.immunoblockers.data.*
import com.immunoblockers.reminders.*
import com.immunoblockers.ui.seedScheme
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

/** RemoteViews keep home-screen widgets compatible with every supported Android version. */
open class IntakeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    private fun refresh(context: Context) {
        val app=context.applicationContext as ImmunoblockersApp
        val pending=goAsync()
        app.appScope.launch { try { app.reminders.reconcileAndSchedule() } finally { pending.finish() } }
    }
}
class NextDoseWidget : IntakeWidgetProvider()
class TodayWidget : IntakeWidgetProvider()
class JournalWidget : IntakeWidgetProvider()

enum class WidgetKind(val provider: Class<out AppWidgetProvider>, val title: Int, val description: Int) {
    NEXT(NextDoseWidget::class.java,R.string.widget_next_name,R.string.widget_next_description),
    TODAY(TodayWidget::class.java,R.string.widget_today_name,R.string.widget_today_description),
    JOURNAL(JournalWidget::class.java,R.string.widget_journal_name,R.string.widget_journal_description)
}

object WidgetUpdater {
    fun updateAll(context: Context, rows: List<Occurrence>, settings: ThemeSettings, activeMedicationIds: Set<String>) {
        val manager=AppWidgetManager.getInstance(context)
        WidgetKind.entries.forEach {kind ->
            manager.getAppWidgetIds(ComponentName(context,kind.provider)).forEach {id ->
                val height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,220)
                manager.updateAppWidget(id,render(context,kind,rows,settings,height,activeMedicationIds))
            }
        }
    }
    fun render(context: Context, kind: WidgetKind, rows: List<Occurrence>, settings: ThemeSettings, height: Int=220, activeMedicationIds: Set<String>?=null): RemoteViews {
        val today=LocalDate.now().toString()
        val current=rows.filter {it.date==today}.sortedBy {it.time}
        val next=rows.filter {(activeMedicationIds==null||it.medicationId in activeMedicationIds)&&it.state==Occurrence.PENDING&&(it.date>=today||it.snoozedUntil!=null)}.minByOrNull {it.dueAt}
        val dark=when(settings.theme){"dark"->true;"light"->false;else->context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES}
        val colors=if(settings.palette=="dynamic"&&Build.VERSION.SDK_INT>=31) {
            if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else seedScheme(if(settings.palette=="custom")settings.seed else 0xFF146B55,dark)
        val views=RemoteViews(context.packageName,R.layout.intake_widget)
        views.setImageViewResource(R.id.widget_background,when(settings.corners){"square"->R.drawable.widget_square;"soft"->R.drawable.widget_soft;else->R.drawable.widget_rounded})
        views.setInt(R.id.widget_background,"setColorFilter",colors.surfaceContainer.toArgb())
        views.setTextViewText(R.id.widget_header,context.getString(kind.title))
        views.setTextColor(R.id.widget_header,colors.primary.toArgb())
        views.setTextColor(R.id.widget_title,colors.onSurface.toArgb())
        views.setTextColor(R.id.widget_body,colors.onSurfaceVariant.toArgb())
        views.setTextViewText(R.id.widget_title,context.getString(R.string.app_name))
        views.setTextViewText(R.id.widget_body,context.getString(R.string.widget_day_counts,current.count {it.state==Occurrence.TAKEN},current.size,current.count {it.state==Occurrence.SKIPPED}))
        if(kind==WidgetKind.NEXT&&height<220) {
            views.setViewVisibility(R.id.widget_header,View.GONE)
            views.setInt(R.id.widget_body,"setMaxLines",1)
        }
        views.setViewVisibility(R.id.widget_actions,View.GONE)
        views.removeAllViews(R.id.widget_rows)
        val open=Intent(context,MainActivity::class.java).setAction("widget.${kind.name}")
            .putExtra("screen",if(kind==WidgetKind.JOURNAL)"history" else "today")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if(kind==WidgetKind.NEXT&&next!=null)open.putExtra("doseId",next.id)
        val launch=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_root,launch)
        when(kind) {
            WidgetKind.NEXT -> {
                views.setTextViewText(R.id.widget_title,if(next==null)context.getString(R.string.widget_no_next) else if(settings.privateWidgets)context.getString(R.string.private_title) else next.name)
                val dueTime=next?.let {Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM HH:mm"))}.orEmpty()
                val timing=if(next?.snoozedUntil!=null)context.getString(R.string.snoozed,dueTime) else dueTime
                views.setTextViewText(R.id.widget_body,if(next==null)context.getString(R.string.widget_open) else if(settings.privateWidgets)timing else context.getString(R.string.widget_next_details,next.dose.replace('.',','),next.unit,timing))
                if(next!=null) {
                    views.setViewVisibility(R.id.widget_actions,View.VISIBLE)
                    views.setTextColor(R.id.widget_take,colors.primary.toArgb())
                    views.setTextColor(R.id.widget_snooze,colors.primary.toArgb())
                    views.setOnClickPendingIntent(R.id.widget_take,action(context,next.id,ReminderScheduler.TAKE))
                    views.setOnClickPendingIntent(R.id.widget_snooze,action(context,next.id,ReminderScheduler.SNOOZE))
                }
            }
            WidgetKind.TODAY -> {
                val limit=((height-110)/48).coerceIn(1,6)
                val pending=current.filter {it.state==Occurrence.PENDING&&(activeMedicationIds==null||it.medicationId in activeMedicationIds)}.take(limit)
                if(pending.isEmpty())views.setTextViewText(R.id.widget_body,context.getString(if(current.isEmpty())R.string.no_doses else R.string.widget_no_next))
                pending.forEach {row -> addRow(context,views,if(settings.privateWidgets)"${row.time} · ${context.getString(R.string.widget_hidden)}" else context.getString(R.string.widget_pending_row,row.time,row.name,row.dose.replace('.',','),row.unit),colors.onSurface.toArgb())}
            }
            WidgetKind.JOURNAL -> {
                val limit=((height-95)/52).coerceIn(1,6)
                val marked=rows.filter {it.markedAt!=null}.sortedByDescending {it.markedAt}.take(limit)
                views.setTextViewText(R.id.widget_body,context.getString(if(marked.isEmpty())R.string.widget_no_logs else R.string.last_records))
                marked.forEach {row ->
                    val time=Instant.ofEpochMilli(row.markedAt!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM HH:mm"))
                    val text=if(settings.privateWidgets)"$time · ${context.getString(R.string.widget_hidden)}" else context.getString(R.string.widget_row,row.name,row.dose.replace('.',','),row.unit,time,context.getString(if(row.state==Occurrence.TAKEN)R.string.taken else R.string.skipped))
                    addRow(context,views,text,colors.onSurface.toArgb())
                }
            }
        }
        return views
    }
    private fun addRow(context: Context, views: RemoteViews, text: String, color: Int) {
        val row=RemoteViews(context.packageName,R.layout.intake_widget_row)
        row.setTextViewText(R.id.widget_row_text,text)
        row.setTextColor(R.id.widget_row_text,color)
        views.addView(R.id.widget_rows,row)
    }
    private fun action(context: Context, id: String, type: String) = PendingIntent.getBroadcast(context,0,
        Intent(context,ReminderReceiver::class.java).setAction(type).setData(Uri.parse("immunoblockers://widget/$type/${Uri.encode(id)}")).putExtra("doseId",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
