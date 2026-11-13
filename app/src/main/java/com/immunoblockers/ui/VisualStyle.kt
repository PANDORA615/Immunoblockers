package com.immunoblockers.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.immunoblockers.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.testTag

internal data class BackdropSource(val image:ImageBitmap,val fallback:ImageBitmap)
private val LocalBackdropTint=staticCompositionLocalOf {Color.Transparent}
private val LocalRootSize=staticCompositionLocalOf {IntSize.Zero}
private val LocalBackdrop=staticCompositionLocalOf<BackdropSource?> {null}

/** Every surface samples the same root image. Only the background layer is blurred. */
@Composable private fun GlassLayer(modifier:Modifier=Modifier) {
    val source=LocalBackdrop.current ?: return
    val root=LocalRootSize.current
    val tint=LocalBackdropTint.current
    var position by remember {mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)}
    val system=android.os.Build.VERSION.SDK_INT>=31
    Canvas(modifier.onGloballyPositioned {position=it.positionInRoot()}.then(if(system)Modifier.blur(20.dp,BlurredEdgeTreatment.Unbounded) else Modifier)) {
        val image=if(system)source.image else source.fallback
        if(root.width>0&&root.height>0)drawImage(image,srcSize=IntSize(image.width,image.height),dstOffset=IntOffset(-position.x.toInt(),-position.y.toInt()),dstSize=root)
        drawRect(tint)
    }
}

@Composable fun AppCard(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,colors:CardColors=CardDefaults.cardColors(),content:@Composable ColumnScope.()->Unit) {
    if(LocalAppearance.current.interfaceStyle!="glass") {
        if(onClick==null)Card(modifier=modifier,colors=colors,content=content)
        else Card(onClick=onClick,modifier=modifier,colors=colors,content=content)
        return
    }
    val shape=MaterialTheme.shapes.large
    Box(modifier.shadow(3.dp,shape).clip(shape).then(if(onClick!=null)Modifier.clickable(onClick=onClick)else Modifier)
        .border(1.dp,Brush.linearGradient(listOf(Color.White.copy(alpha=.8f),Color.White.copy(alpha=.12f),Color.White.copy(alpha=.45f))),shape)) {
        GlassLayer(Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha=.13f),Color.White.copy(alpha=.025f),Color.White.copy(alpha=.08f)))))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {Column(content=content)}
    }
}

@Composable fun GlassBackdrop(enabled:Boolean,content:@Composable ()->Unit) {
    val c=MaterialTheme.colorScheme
    var rootSize by remember {mutableStateOf(IntSize.Zero)}
    val path=LocalAppearance.current.wallpaperPath
    val imageHeight=if(rootSize.width==0)800 else (360L*rootSize.height/rootSize.width).toInt().coerceIn(240,1440)
    val photo by produceState<BackdropSource?>(null,path,imageHeight) {
        value=withContext(Dispatchers.IO) {if(path.isBlank())null else runCatching {photoBackdrop(path,360,imageHeight)}.getOrNull()}
    }
    val gradient=remember(c.primary,c.surface) {
        fun bitmap(blur:Boolean):ImageBitmap {
            val bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap);val paint=android.graphics.Paint(3)
            paint.shader=android.graphics.LinearGradient(0f,0f,360f,800f,intArrayOf(c.surface.copy(alpha=1f).toArgb(),c.secondaryContainer.copy(alpha=1f).toArgb(),c.surface.copy(alpha=1f).toArgb()),null,android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,360f,800f,paint)
            if(blur)paint.maskFilter=android.graphics.BlurMaskFilter(42f,android.graphics.BlurMaskFilter.Blur.NORMAL)
            paint.shader=android.graphics.RadialGradient(320f,220f,300f,intArrayOf(c.tertiary.copy(alpha=.25f).toArgb(),android.graphics.Color.TRANSPARENT),null,android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(320f,220f,300f,paint)
            paint.shader=android.graphics.RadialGradient(20f,610f,330f,intArrayOf(c.primary.copy(alpha=.24f).toArgb(),android.graphics.Color.TRANSPARENT),null,android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(20f,610f,330f,paint)
            return bitmap.asImageBitmap()
        }
        BackdropSource(bitmap(false),bitmap(true))
    }
    val source=photo?:gradient
    val tint=if(photo==null)Color.Transparent else c.surface.copy(alpha=if(c.surface.luminance()<.5f).72f else .68f)
    CompositionLocalProvider(LocalBackdrop provides source,LocalRootSize provides rootSize,LocalBackdropTint provides tint) {
        Box(Modifier.fillMaxSize().background(c.surface.copy(alpha=1f)).onSizeChanged {rootSize=it}) {
            if(enabled||photo!=null) {
                androidx.compose.foundation.Image(source.image,null,Modifier.matchParentSize().then(if(photo!=null)Modifier.testTag("appWallpaper")else Modifier),contentScale=androidx.compose.ui.layout.ContentScale.FillBounds)
                Box(Modifier.matchParentSize().background(tint))
            }
            content()
        }
    }
}

/** Keep scrolling cards inside their viewport and soften the boundary beneath the fixed header. */
fun Modifier.scrollViewport(canScrollBack:Boolean,canScrollForward:Boolean):Modifier = this
    .graphicsLayer {compositingStrategy=CompositingStrategy.Offscreen;clip=true}
    .drawWithContent {
        drawContent()
        val edge=18.dp.toPx().coerceAtMost(size.height/3)
        if(canScrollBack)drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black),0f,edge),size=androidx.compose.ui.geometry.Size(size.width,edge),blendMode=BlendMode.DstIn)
        if(canScrollForward)drawRect(Brush.verticalGradient(listOf(Color.Black,Color.Transparent),size.height-edge,size.height),topLeft=androidx.compose.ui.geometry.Offset(0f,size.height-edge),size=androidx.compose.ui.geometry.Size(size.width,edge),blendMode=BlendMode.DstIn)
    }


@Composable fun LiquidNavigation(route:String,navigate:(String)->Unit) {
    val tabs=listOf(Triple("today",R.string.today,Icons.Outlined.Today),Triple("medications",R.string.medications,Icons.Outlined.Medication),Triple("history",R.string.history,Icons.Outlined.History),Triple("helpers",R.string.helpers_tab,Icons.Outlined.Widgets),Triple("settings",R.string.settings,Icons.Outlined.Tune))
    val index=tabs.indexOfFirst {it.first==route}.coerceAtLeast(0)
    val reduced=LocalReducedMotion.current
    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp).height(76.dp).clip(RoundedCornerShape(38.dp))) {
        val cell=maxWidth/5
        val x by animateDpAsState(cell*index,if(reduced)tween(0) else spring(dampingRatio=.85f,stiffness=550f),label="liquid-tab")
        val c=MaterialTheme.colorScheme
        GlassLayer(Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(c.surface.copy(alpha=.16f)).border(1.5.dp,Brush.verticalGradient(listOf(Color.White.copy(alpha=.75f),Color.White.copy(alpha=.08f),Color.White.copy(alpha=.4f))),RoundedCornerShape(38.dp)))
        Box(Modifier.offset(x=x+4.dp,y=5.dp).width(cell-8.dp).height(66.dp).clip(RoundedCornerShape(33.dp))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha=.32f),c.primary.copy(alpha=.13f),Color.White.copy(alpha=.1f))))
            .border(1.dp,Color.White.copy(alpha=.5f),RoundedCornerShape(33.dp)))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed {i,(path,title,icon)->
                val scale by animateFloatAsState(if(i==index)1.1f else 1f,tween(if(reduced)0 else 220),label="icon-scale")
                Column(Modifier.weight(1f).fillMaxHeight().selectableTab(i==index){navigate(path)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(icon,null,Modifier.size(24.dp).scale(scale),tint=if(i==index)c.primary else c.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(title),style=MaterialTheme.typography.labelSmall,maxLines=1,color=if(i==index)c.primary else c.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable private fun Modifier.selectableTab(selected:Boolean,click:()->Unit)=this.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,onClick=click).semantics {this.selected=selected;role=Role.Tab}
