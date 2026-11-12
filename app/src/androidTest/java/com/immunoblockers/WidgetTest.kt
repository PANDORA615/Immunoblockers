package com.immunoblockers

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.view.*
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import android.widget.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.immunoblockers.data.*
import com.immunoblockers.widgets.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class WidgetTest {
    private val app get()=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
    private fun text(view: View):String = when(view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n"){text(view.getChildAt(it))}
        else -> ""
    }
    @Test fun allWidgetLayoutsRenderAndPrivacyHidesDrugAndDose() {
        val providers=AppWidgetManager.getInstance(app).installedProviders.filter {it.provider.packageName==app.packageName}
        assertEquals(7,providers.size)
        val row=Occurrence("widget-render","render",1,LocalDate.now().toString(),"23:59",LocalDate.now().atTime(23,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),"Виджет препарат","1.25","мг")
        for(kind in WidgetKind.entries)for(private in listOf(false,true))for(widgetWidth in listOf(160,320)) {
            val rows=if(kind==WidgetKind.JOURNAL)listOf(row.copy(state=Occurrence.TAKEN,markedAt=System.currentTimeMillis())) else listOf(row)
            var result=""
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view=WidgetUpdater.render(app,kind,rows,ThemeSettings(privateWidgets=private),height=160,width=widgetWidth,latest=WellbeingEntry("private",1,"Хорошо","Личная запись")).apply(app,FrameLayout(app))
                result=text(view)
                if(!private) {
                    val scale=app.resources.displayMetrics.density
                    val width=(widgetWidth*scale).toInt();val height=(160*scale).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                    view.layout(0,0,width,height)
                    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val folder=File(app.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
                    File(folder,"widget-${kind.name}-${widgetWidth}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                }
            }
            if(private){assertFalse(result.contains(row.name));assertFalse(result.contains("1,25"));assertFalse(result.contains("Личная запись"))}
            else if(kind in listOf(WidgetKind.NEXT,WidgetKind.TODAY,WidgetKind.JOURNAL)){assertTrue(result.contains(row.name));assertTrue(result.contains("1,25"))}
        }
    }
    @Test fun pausedMedicineCannotBeOfferedAsTheNextWidgetDose() {
        val row=Occurrence("inactive","paused",1,LocalDate.now().toString(),"23:59",0,"На паузе препарат","2","мг")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view=WidgetUpdater.render(app,WidgetKind.NEXT,listOf(row),ThemeSettings(),activeMedicationIds=emptySet()).apply(app,FrameLayout(app))
            assertFalse(text(view).contains(row.name))
            assertEquals(View.GONE,view.findViewById<View>(R.id.widget_actions).visibility)
        }
    }
    @Test fun widgetTakeActionWritesOneDoseAndPreservesQuantity()=runBlocking {
        val now=System.currentTimeMillis()
        val med=Medication(UUID.randomUUID().toString(),"Виджет действие","0.5","таблетка",start=LocalDate.now().toString(),times="23:59",createdAt=now,updatedAt=now)
        app.repository.saveMedication(med)
        val row=app.repository.dao.occurrences().first {it.medicationId==med.id&&it.date==LocalDate.now().toString()}
        val manager=AppWidgetManager.getInstance(app)
        val host=AppWidgetHost(app,202)
        val widgetId=host.allocateAppWidgetId()
        try {
            assertTrue("Test host needs: adb shell appwidget grantbind --package com.immunoblockers --user 0",manager.bindAppWidgetIdIfAllowed(widgetId,ComponentName(app,NextDoseWidget::class.java)))
            manager.updateAppWidget(widgetId,WidgetUpdater.render(app,WidgetKind.NEXT,listOf(row),ThemeSettings(confirmWidgetActions=false)))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                host.startListening()
                val view=host.createView(app,widgetId,manager.getAppWidgetInfo(widgetId))
                assertTrue(text(view).contains(row.name))
                view.findViewById<TextView>(R.id.widget_take).performClick()
                view.findViewById<TextView>(R.id.widget_take).performClick()
            }
            withTimeout(15000){while(app.repository.dao.occurrence(row.id)?.state!=Occurrence.TAKEN)delay(100)}
            val saved=app.repository.dao.occurrence(row.id)!!
            assertEquals("0.5",saved.dose)
            assertNotNull(saved.markedAt)
            assertEquals(1,app.repository.dao.occurrences().count {it.id==row.id})
        } finally {
            app.repository.saveMedication(med.copy(archived=true,version=2));app.reminders.reconcileAndSchedule()
            host.stopListening();host.deleteAppWidgetId(widgetId)
        }
    }
    @Test fun realHostsRenderAllProvidersAtBothSizes() {
        val manager=AppWidgetManager.getInstance(app)
        val host=AppWidgetHost(app,204)
        val row=Occurrence("host-render","render",1,LocalDate.now().toString(),"23:59",System.currentTimeMillis()+60_000,"Виджет препарат","1.25","мг")
        for(kind in WidgetKind.entries)for((width,height) in listOf(160 to 110,160 to 160,320 to 160))for(theme in listOf("light","dark")) {
            val id=host.allocateAppWidgetId()
            try {
                val options=android.os.Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height)
                }
                assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(app,kind.provider),options))
                val displayed=if(kind==WidgetKind.JOURNAL||kind==WidgetKind.PROGRESS||kind==WidgetKind.WEEK)listOf(row.copy(state=Occurrence.TAKEN,markedAt=System.currentTimeMillis()),row.copy(id="second",name="Следующий приём")) else listOf(row,row.copy(id="second",name="Следующий приём"))
                manager.updateAppWidget(id,WidgetUpdater.render(app,kind,displayed,ThemeSettings(widgetTheme=theme),height=height,width=width,latest=WellbeingEntry("last",1,"Хорошо","Заметка")))
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    host.startListening()
                    val view=host.createView(app,id,manager.getAppWidgetInfo(id))
                    val scale=app.resources.displayMetrics.density
                    val w=(width*scale).toInt();val h=(height*scale).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
                    view.layout(0,0,w,h)
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val folder=File(app.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
                    File(folder,"host-${kind.name}-$width-$height-$theme.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                    if(kind==WidgetKind.NEXT||kind==WidgetKind.WELLBEING) {
                        val button=view.findViewById<TextView>(R.id.widget_take)
                        assertNotNull(button)
                        assertTrue(button.height>=48*scale-1)
                        val bounds=android.graphics.Rect()
                        button.getDrawingRect(bounds);view.offsetDescendantRectToMyCoords(button,bounds)
                        assertTrue("${kind.name} button clipped at $width x $height: $bounds",bounds.bottom<=view.height)
                    }
                }
            }finally {host.stopListening();host.deleteAppWidgetId(id)}
        }
    }
}
