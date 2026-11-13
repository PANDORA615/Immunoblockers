package com.immunoblockers

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.immunoblockers.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WallpaperTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun copiedWallpaperRendersInBothStylesAndSurvivesRecreation() {
  val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
  val old=runBlocking {app.settings.flow.first()}
  val source=File(app.cacheDir,"wallpaper-source.png")
  val bitmap=Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888)
  val canvas=android.graphics.Canvas(bitmap)
  canvas.drawColor(android.graphics.Color.rgb(25,110,185))
  canvas.drawCircle(90f,60f,65f,android.graphics.Paint().apply {color=android.graphics.Color.rgb(245,170,60)})
  source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val path=runBlocking {copyLocalImage(app,Uri.fromFile(source))}
  try {
   source.delete()
   assertTrue(File(path).isFile)
   runBlocking {app.settings.update {it.copy(wallpaperPath=path,widgetImage=path,theme="dark",interfaceStyle="glass")}}
   compose.waitUntil(15000){compose.onAllNodesWithTag("appWallpaper").fetchSemanticsNodes().size==1}
   compose.onNodeWithTag("appWallpaper").assertExists()
   compose.activityRule.scenario.recreate()
   compose.waitUntil(15000){compose.onAllNodesWithTag("appWallpaper").fetchSemanticsNodes().size==1}
   runBlocking {app.settings.update {it.copy(interfaceStyle="material")}}
   compose.onNodeWithText("Настройки").performClick()
   compose.onNodeWithTag("settingsList").performScrollToNode(hasText("Обои приложения"))
   compose.waitUntil(15000){compose.onAllNodesWithTag("wallpaperPreview").fetchSemanticsNodes().size==1}
   compose.onNodeWithTag("wallpaperPreview").performScrollTo().assertExists()
   compose.onNodeWithTag("appWallpaper").assertExists()
   val instrumentation=InstrumentationRegistry.getInstrumentation()
   val automation=instrumentation.uiAutomation
   compose.waitForIdle();instrumentation.waitForIdleSync();android.os.SystemClock.sleep(350)
   var screenshot:Bitmap?=null
   instrumentation.runOnMainSync {screenshot=automation.takeScreenshot()}
   val folder=File(app.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
   screenshot?.let {image->File(folder,"wallpaper.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
   val vm=AppViewModel(app)
   runBlocking {storeLocalImage(vm,"",LocalImageUse.WIDGET)}
   assertTrue("Wallpaper is still using the same private file",File(path).isFile)
   runBlocking {storeLocalImage(vm,"",LocalImageUse.WALLPAPER)}
   assertFalse(File(path).exists())
   compose.activityRule.scenario.recreate()
   compose.onNodeWithTag("appWallpaper").assertDoesNotExist()
   assertEquals("",runBlocking {app.settings.flow.first().wallpaperPath})
  }finally {runBlocking {app.settings.save(old)};File(path).delete();source.delete()}
 }
}
