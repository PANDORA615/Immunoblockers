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
import com.immunoblockers.features.LocalBook
import com.immunoblockers.data.IntakeAnalytics
import com.immunoblockers.data.decimalLabel
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
class WeekWidget : IntakeWidgetProvider()
class StockWidget : IntakeWidgetProvider()
class ProgressWidget : IntakeWidgetProvider()
class WellbeingWidget : IntakeWidgetProvider()

enum class WidgetKind(val provider: Class<out AppWidgetProvider>, val title: Int, val description: Int) {
    NEXT(NextDoseWidget::class.java,R.string.widget_next_name,R.string.widget_next_description),
    TODAY(TodayWidget::class.java,R.string.widget_today_name,R.string.widget_today_description),
    JOURNAL(JournalWidget::class.java,R.string.widget_journal_name,R.string.widget_journal_description),
    WEEK(WeekWidget::class.java,R.string.widget_week_name,R.string.widget_week_description),
    STOCK(StockWidget::class.java,R.string.widget_stock_name,R.string.widget_stock_description),
    PROGRESS(ProgressWidget::class.java,R.string.widget_progress_name,R.string.widget_progress_description),
    WELLBEING(WellbeingWidget::class.java,R.string.widget_wellbeing_name,R.string.widget_wellbeing_description)
}

object WidgetUpdater {
    fun updateAll(context: Context, rows: List<Occurrence>, settings: ThemeSettings, activeMedicationIds: Set<String>, latest:WellbeingEntry?=null) {
        val manager=AppWidgetManager.getInstance(context)
        WidgetKind.entries.forEach {kind ->
            manager.getAppWidgetIds(ComponentName(context,kind.provider)).forEach {id ->
                val height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,220)
                val width=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,160)
                manager.updateAppWidget(id,render(context,kind,rows,settings,height,activeMedicationIds,width,latest))
            }
        }
    }
    fun render(context: Context, kind: WidgetKind, rows: List<Occurrence>, settings: ThemeSettings, height: Int=220, activeMedicationIds: Set<String>?=null, width:Int=320, latest:WellbeingEntry?=null): RemoteViews {
        val today=LocalDate.now().toString()
        val current=rows.filter {it.date==today}.sortedBy {it.time}
        val next=rows.filter {(activeMedicationIds==null||it.medicationId in activeMedicationIds)&&it.state==Occurrence.PENDING&&(it.date>=today||it.snoozedUntil!=null)}.minByOrNull {it.dueAt}
        val compact=width<240
        val dark=when(if(settings.widgetTheme=="system")settings.theme else settings.widgetTheme){"dark"->true;"light"->false;else->context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES}
        val colors=if(settings.palette=="dynamic"&&Build.VERSION.SDK_INT>=31) {
            if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else seedScheme(if(settings.palette=="custom")settings.seed else 0xFF146B55,dark)
        val views=RemoteViews(context.packageName,R.layout.intake_widget)
        views.setImageViewResource(R.id.widget_background,when(settings.corners){"square"->R.drawable.widget_square;"soft"->R.drawable.widget_soft;else->R.drawable.widget_rounded})
        views.setImageViewBitmap(R.id.widget_background,WidgetArtwork.background(settings,colors.surfaceContainer.toArgb(),colors.primary.toArgb()))
        views.setTextViewText(R.id.widget_header,context.getString(kind.title))
        views.setTextColor(R.id.widget_header,colors.primary.toArgb())
        views.setTextColor(R.id.widget_title,colors.onSurface.toArgb())
        views.setTextColor(R.id.widget_body,colors.onSurfaceVariant.toArgb())
        views.setTextViewText(R.id.widget_title,context.getString(R.string.app_name))
        views.setTextViewText(R.id.widget_body,context.getString(R.string.widget_day_counts,current.count {it.state==Occurrence.TAKEN},current.size,current.count {it.state==Occurrence.SKIPPED}))
        if(compact||height<150) {
            views.setViewVisibility(R.id.widget_header,View.GONE)
            views.setInt(R.id.widget_body,"setMaxLines",1)
        }
        if(compact && height<150)views.setInt(R.id.widget_title,"setMaxLines",1)
        views.setInt(R.id.widget_body,"setMaxLines",if(kind==WidgetKind.WELLBEING&&!compact)2 else 1)
        if(height<128) {
            if(kind in listOf(WidgetKind.NEXT,WidgetKind.WELLBEING,WidgetKind.PROGRESS))views.setViewVisibility(R.id.widget_body,View.GONE)
            if(kind in listOf(WidgetKind.TODAY,WidgetKind.JOURNAL,WidgetKind.STOCK))views.setViewVisibility(R.id.widget_title,View.GONE)
        }
        if(!compact&&kind in listOf(WidgetKind.TODAY,WidgetKind.JOURNAL,WidgetKind.STOCK))views.setViewVisibility(R.id.widget_title,View.GONE)
        if(!compact&&kind==WidgetKind.WELLBEING)views.setViewVisibility(R.id.widget_header,View.GONE)
        views.setViewVisibility(R.id.widget_actions,View.GONE)
        views.removeAllViews(R.id.widget_rows)
        val open=Intent(context,MainActivity::class.java).setAction("widget.${kind.name}")
            .putExtra("screen",if(kind==WidgetKind.JOURNAL||kind==WidgetKind.WEEK)"history" else if(kind==WidgetKind.STOCK)"tools/stock" else if(kind==WidgetKind.WELLBEING)"tools/mood" else "today")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if(kind==WidgetKind.NEXT&&next!=null)open.putExtra("doseId",next.id)
        val launch=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_root,launch)
        when(kind) {
            WidgetKind.PROGRESS -> {
                val taken=current.count {it.state==Occurrence.TAKEN}
                views.setTextViewText(R.id.widget_title,"$taken / ${current.size}")
                views.setTextViewText(R.id.widget_body,if(compact)"Принято сегодня" else context.getString(R.string.widget_day_counts,taken,current.size,current.count {it.state==Occurrence.SKIPPED}))
                val graph=RemoteViews(context.packageName,R.layout.widget_progress_ring)
                graph.setImageViewBitmap(R.id.widget_graph,WidgetArtwork.ring(taken,current.size,colors.primary.toArgb(),colors.outlineVariant.toArgb()))
                views.addView(R.id.widget_rows,graph)
                if(!compact&&height>=150) {
                    views.setViewVisibility(R.id.widget_header,View.GONE)
                    views.setViewVisibility(R.id.widget_body,View.GONE)
                    current.filter {it.markedAt!=null}.sortedByDescending {it.markedAt}.take(2).forEach {row->
                        val label=if(settings.privateWidgets)"${row.time} · ${context.getString(R.string.widget_hidden)}" else "${if(row.state==Occurrence.TAKEN)"✓" else "–"} ${row.time} · ${row.name}"
                        addRow(context,views,label,colors.onSurface.toArgb(),1)
                    }
                }
            }
            WidgetKind.WELLBEING -> {
                views.setTextViewText(R.id.widget_title,if(settings.privateWidgets)context.getString(R.string.widget_hidden) else latest?.mood?:"Самочувствие")
                views.setTextViewText(R.id.widget_body,if(settings.privateWidgets)"Откройте приложение" else latest?.let { val date=Instant.ofEpochMilli(it.recordedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM HH:mm")); if(compact)it.text.take(80).ifBlank {date} else "${it.text.take(180)} · $date" }?:"Записей пока нет")
                if(height<128&&!settings.privateWidgets&&latest!=null)views.setTextViewText(R.id.widget_title,latest.mood?:latest.text)
                views.setViewVisibility(R.id.widget_actions,View.VISIBLE)
                views.setViewVisibility(R.id.widget_snooze,View.GONE)
                views.setTextViewText(R.id.widget_take,"Добавить")
                views.setTextColor(R.id.widget_take,colors.primary.toArgb())
                views.setOnClickPendingIntent(R.id.widget_take,launch)
            }
            WidgetKind.WEEK -> {
                val days=IntakeAnalytics.days(rows,LocalDate.now().minusDays(6),LocalDate.now(),LocalDate.now())
                views.setTextViewText(R.id.widget_title,context.getString(R.string.completion_count,days.sumOf {it.taken},days.sumOf {it.planned}))
                views.setTextViewText(R.id.widget_body,context.getString(R.string.widget_week_name))
                val bitmap=Bitmap.createBitmap(600,160,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                val peak=maxOf(1,days.maxOf {it.planned})
                days.forEachIndexed {i,day->
                    val left=20f+i*84;paint.color=colors.outlineVariant.toArgb();canvas.drawRoundRect(left,120f-day.planned*90f/peak,left+40,120f,6f,6f,paint)
                    paint.color=colors.primary.toArgb();canvas.drawRoundRect(left,120f-day.taken*90f/peak,left+40,120f,6f,6f,paint)
                    paint.color=colors.onSurface.toArgb();paint.textSize=22f;canvas.drawText(day.date.dayOfMonth.toString(),left+5,150f,paint)
                }
                val graph=RemoteViews(context.packageName,R.layout.widget_week_graph)
                graph.setImageViewBitmap(R.id.widget_graph,bitmap)
                graph.setContentDescription(R.id.widget_graph,days.joinToString(". "){"${it.date}: ${it.taken}/${it.planned}"})
                if(height>=150)views.addView(R.id.widget_rows,graph)
            }
            WidgetKind.STOCK -> {
                val stocks=LocalBook(context).stocks(rows)
                views.setTextViewText(R.id.widget_title,context.getString(R.string.tools_stock))
                views.setTextViewText(R.id.widget_body,context.getString(if(stocks.isEmpty())R.string.stock_empty else R.string.stock_hint))
                stocks.take((if(compact)1 else ((height-95)/32).coerceIn(1,5))).forEach {stock->addRow(context,views,if(settings.privateWidgets)context.getString(R.string.widget_hidden)else "${stock.name} · ${decimalLabel(stock.remaining)} ${stock.unit}",colors.onSurface.toArgb())}
            }
            WidgetKind.NEXT -> {
                views.setTextViewText(R.id.widget_title,if(next==null)context.getString(R.string.widget_no_next) else if(settings.privateWidgets)context.getString(R.string.private_title) else next.name)
                val dueTime=next?.let {Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if(compact)"HH:mm" else "d.MM HH:mm"))}.orEmpty()
                val timing=if(next?.snoozedUntil!=null){if(compact)"↻ $dueTime" else context.getString(R.string.snoozed,dueTime)} else dueTime
                views.setTextViewText(R.id.widget_body,if(next==null)context.getString(R.string.widget_open) else if(settings.privateWidgets)timing else context.getString(R.string.widget_next_details,next.dose.replace('.',','),next.unit,timing))
                if(height<128&&next!=null)views.setTextViewText(R.id.widget_title,"$timing · ${if(settings.privateWidgets)context.getString(R.string.widget_hidden) else next.name}")
                if(next!=null) {
                    if(!compact&&height>=150) {
                        views.setViewVisibility(R.id.widget_header,View.GONE)
                        views.setInt(R.id.widget_title,"setMaxLines",1)
                        val later=rows.filter {it.id!=next.id&&it.state==Occurrence.PENDING&&(activeMedicationIds==null||it.medicationId in activeMedicationIds)&&(it.date>=today||it.snoozedUntil!=null)}.minByOrNull {it.dueAt}
                        later?.let {row->
                            val time=Instant.ofEpochMilli(row.dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM HH:mm"))
                            addRow(context,views,"Далее $time · ${if(settings.privateWidgets)context.getString(R.string.widget_hidden) else row.name}",colors.onSurface.toArgb(),1)
                        }
                    }
                    views.setViewVisibility(R.id.widget_actions,View.VISIBLE)
                    views.setTextColor(R.id.widget_take,colors.primary.toArgb())
                    views.setTextColor(R.id.widget_snooze,colors.primary.toArgb())
                    val take=if(settings.confirmWidgetActions)PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).setData(Uri.parse("immunoblockers://confirm/${Uri.encode(next.id)}")).putExtra("doseId",next.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)else action(context,next.id,ReminderScheduler.TAKE)
                    views.setOnClickPendingIntent(R.id.widget_take,take)
                    views.setOnClickPendingIntent(R.id.widget_snooze,action(context,next.id,ReminderScheduler.SNOOZE))
                }
            }
            WidgetKind.TODAY -> {
                views.setTextViewText(R.id.widget_title,context.getString(R.string.completion_count,current.count {it.state==Occurrence.TAKEN},current.size))
                val limit=if(compact)1 else ((height-95)/32).coerceIn(1,6)
                val pending=current.filter {it.state==Occurrence.PENDING&&(activeMedicationIds==null||it.medicationId in activeMedicationIds)}.take(limit)
                if(pending.isEmpty())views.setTextViewText(R.id.widget_body,context.getString(if(current.isEmpty())R.string.no_doses else R.string.widget_no_next))
                pending.forEach {row -> addRow(context,views,if(settings.privateWidgets)"${row.time} · ${context.getString(R.string.widget_hidden)}" else context.getString(R.string.widget_pending_row,row.time,row.name,row.dose.replace('.',','),row.unit),colors.onSurface.toArgb())}
            }
            WidgetKind.JOURNAL -> {
                val limit=if(compact)1 else ((height-95)/32).coerceIn(1,6)
                val marked=rows.filter {it.markedAt!=null}.sortedByDescending {it.markedAt}.take(limit)
                if(compact&&marked.isNotEmpty())views.setTextViewText(R.id.widget_title,if(settings.privateWidgets)context.getString(R.string.widget_hidden) else marked.first().name)
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
    private fun addRow(context: Context, views: RemoteViews, text: String, color: Int, maxLines:Int=2) {
        val row=RemoteViews(context.packageName,R.layout.intake_widget_row)
        row.setTextViewText(R.id.widget_row_text,text)
        row.setTextColor(R.id.widget_row_text,color)
        row.setInt(R.id.widget_row_text,"setMaxLines",maxLines)
        views.addView(R.id.widget_rows,row)
    }
    private fun action(context: Context, id: String, type: String) = PendingIntent.getBroadcast(context,0,
        Intent(context,ReminderReceiver::class.java).setAction(type).setData(Uri.parse("immunoblockers://widget/$type/${Uri.encode(id)}")).putExtra("doseId",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
