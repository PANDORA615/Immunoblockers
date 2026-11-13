package com.immunoblockers.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap

/** Copies a centre-cropped photo into the same coordinate space used by every glass surface. */
internal fun photoBackdrop(path:String,width:Int,height:Int):BackdropSource? {
    val original=BitmapFactory.decodeFile(path)?:return null
    val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    val scale=maxOf(width.toFloat()/original.width,height.toFloat()/original.height)
    val drawnWidth=original.width*scale;val drawnHeight=original.height*scale
    Canvas(image).drawBitmap(original,null,RectF((width-drawnWidth)/2,(height-drawnHeight)/2,(width+drawnWidth)/2,(height+drawnHeight)/2),Paint(3))
    original.recycle()
    val smallWidth=90;val smallHeight=(height*smallWidth/width).coerceAtLeast(1)
    val small=Bitmap.createScaledBitmap(image,smallWidth,smallHeight,true)
    val pixels=IntArray(smallWidth*smallHeight)
    small.getPixels(pixels,0,smallWidth,0,0,smallWidth,smallHeight)
    // Separable software blur works on Android 8–11; foreground content never enters this bitmap.
    repeat(2) {
        for(horizontal in listOf(true,false)) {
            val input=pixels.copyOf()
            for(y in 0 until smallHeight)for(x in 0 until smallWidth) {
                var red=0;var green=0;var blue=0
                for(delta in -6..6) {
                    val xx=if(horizontal)(x+delta).coerceIn(0,smallWidth-1) else x
                    val yy=if(horizontal)y else (y+delta).coerceIn(0,smallHeight-1)
                    val color=input[yy*smallWidth+xx]
                    red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color)
                }
                pixels[y*smallWidth+x]=Color.rgb(red/13,green/13,blue/13)
            }
        }
    }
    small.setPixels(pixels,0,smallWidth,0,0,smallWidth,smallHeight)
    val blurred=Bitmap.createScaledBitmap(small,width,height,true)
    small.recycle()
    return BackdropSource(image.asImageBitmap(),blurred.asImageBitmap())
}
