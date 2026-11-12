package com.immunoblockers

import androidx.compose.ui.graphics.Color
import com.immunoblockers.ui.seedScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PaletteContrastTest {
    private fun luminance(c:Color):Double {
        fun linear(v:Float):Double = if(v<=0.04045) v/12.92 else ((v+0.055)/1.055).pow(2.4)
        return 0.2126*linear(c.red)+0.7152*linear(c.green)+0.0722*linear(c.blue)
    }
    private fun ratio(a:Color,b:Color):Double {val l=luminance(a);val r=luminance(b);return (maxOf(l,r)+0.05)/(minOf(l,r)+0.05)}
    @Test fun arbitrarySeedsKeepTextContrastInBothThemes() {
        for(seed in listOf(0xFF000000,0xFFFFFFFF,0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFF146B55,0xFFFF00FF))for(dark in listOf(false,true)){
            val c=seedScheme(seed,dark)
            for((a,b)in listOf(c.primary to c.onPrimary,c.primaryContainer to c.onPrimaryContainer,c.secondary to c.onSecondary,c.secondaryContainer to c.onSecondaryContainer,c.tertiary to c.onTertiary,c.tertiaryContainer to c.onTertiaryContainer,c.surface to c.onSurface,c.surfaceVariant to c.onSurfaceVariant))assertTrue("seed=$seed dark=$dark contrast=${ratio(a,b)}",ratio(a,b)>=4.5)
        }
    }
}
