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
        assertEquals(3,providers.size)
        val row=Occurrence("widget-render","render",1,LocalDate.now().toString(),"23:59",LocalDate.now().atTime(23,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),"Виджет препарат","1.25","мг")
        for(kind in WidgetKind.entries)for(private in listOf(false,true)) {
            val rows=if(kind==WidgetKind.JOURNAL)listOf(row.copy(state=Occurrence.TAKEN,markedAt=System.currentTimeMillis())) else listOf(row)
            var result=""
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view=WidgetUpdater.render(app,kind,rows,ThemeSettings(privateWidgets=private)).apply(app,FrameLayout(app))
                result=text(view)
                if(!private) {
                    val scale=app.resources.displayMetrics.density
                    val width=(320*scale).toInt();val height=(250*scale).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                    view.layout(0,0,width,height)
                    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val folder=File(app.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
                    File(folder,"widget-${kind.name}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                }
            }
            if(private){assertFalse(result.contains(row.name));assertFalse(result.contains("1,25"))}
            else {assertTrue(result.contains(row.name));assertTrue(result.contains("1,25"))}
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
            manager.updateAppWidget(widgetId,WidgetUpdater.render(app,WidgetKind.NEXT,listOf(row),ThemeSettings()))
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
}
