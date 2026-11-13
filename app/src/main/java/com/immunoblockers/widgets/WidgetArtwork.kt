package com.immunoblockers.widgets

import android.graphics.*
import com.immunoblockers.data.ThemeSettings

/** RemoteViews cannot sample live wallpaper. This image is always local and preblurred. */
object WidgetArtwork {
 private var cachedKey=""
 private var cached:Bitmap?=null
 @Synchronized fun background(settings:ThemeSettings,base:Int,accent:Int):Bitmap {
  val key="${settings.widgetImage}:${settings.widgetOpacity}:$base:$accent"
  if(key==cachedKey)cached?.let {return it}
  val result=Bitmap.createBitmap(480,300,Bitmap.Config.ARGB_8888)
  val canvas=Canvas(result);val paint=Paint(3)
  val bounds=RectF(2f,2f,478f,298f)
  canvas.save();canvas.clipPath(Path().apply {addRoundRect(bounds,36f,36f,Path.Direction.CW)})
  if(settings.widgetImage.isNotBlank()) {
   val decoded=BitmapFactory.decodeFile(settings.widgetImage)
   decoded?.let {
    // Downsample, then box-blur twice before enlargement; text is drawn by RemoteViews afterwards.
    val small=Bitmap.createScaledBitmap(it,80,50,true);val pixels=IntArray(4000);small.getPixels(pixels,0,80,0,0,80,50)
    repeat(2) {
     val input=pixels.copyOf()
     for(y in 0 until 50)for(x in 0 until 80) {
      var red=0;var green=0;var blue=0;var count=0
      for(dy in -3..3)for(dx in -3..3) {
       val color=input[(y+dy).coerceIn(0,49)*80+(x+dx).coerceIn(0,79)]
       red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);count++
      }
      pixels[y*80+x]=Color.rgb(red/count,green/count,blue/count)
     }
    }
    small.setPixels(pixels,0,80,0,0,80,50);paint.alpha=settings.widgetOpacity.coerceIn(40,255)
    canvas.drawBitmap(small,null,bounds,paint);paint.alpha=255
    if(small!==it)it.recycle();small.recycle()
   }
  }
  paint.color=base;paint.alpha=settings.widgetOpacity.coerceIn(40,255);canvas.drawRoundRect(bounds,36f,36f,paint)
  paint.alpha=255;paint.shader=LinearGradient(0f,0f,480f,300f,intArrayOf(0x40FFFFFF,0x04FFFFFF,0x1AFFFFFF),null,Shader.TileMode.CLAMP)
  canvas.drawRoundRect(bounds,36f,36f,paint);canvas.restore()
  paint.shader=LinearGradient(0f,0f,480f,300f,intArrayOf(0xCCFFFFFF.toInt(),0x20FFFFFF,0x70FFFFFF),null,Shader.TileMode.CLAMP)
  paint.style=Paint.Style.STROKE;paint.strokeWidth=2.5f;canvas.drawRoundRect(bounds,36f,36f,paint)
  cachedKey=key;cached=result;return result
 }
 fun ring(taken:Int,total:Int,primary:Int,track:Int):Bitmap {
  val bitmap=Bitmap.createBitmap(280,90,Bitmap.Config.ARGB_8888)
  val canvas=Canvas(bitmap);val paint=Paint(3).apply {style=Paint.Style.STROKE;strokeWidth=9f;strokeCap=Paint.Cap.ROUND;color=track}
  val rect=RectF(105f,10f,175f,80f);canvas.drawOval(rect,paint);paint.color=primary
  canvas.drawArc(rect,-90f,if(total==0)0f else 360f*taken/total,false,paint)
  return bitmap
 }
}
