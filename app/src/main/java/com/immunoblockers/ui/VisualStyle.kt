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
import com.immunoblockers.R

@Composable fun AppCard(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,colors:CardColors=CardDefaults.cardColors(),content:@Composable ColumnScope.()->Unit) {
    val glass=LocalAppearance.current.interfaceStyle=="glass"
    val border=if(glass)BorderStroke(1.dp,Brush.linearGradient(listOf(Color.White.copy(alpha=.65f),MaterialTheme.colorScheme.primary.copy(alpha=.12f),Color.White.copy(alpha=.2f))))else null
    val elevation=CardDefaults.cardElevation(defaultElevation=if(glass)4.dp else 0.dp)
    if(onClick==null)Card(modifier=modifier,colors=colors,border=border,elevation=elevation,content=content)
    else Card(onClick=onClick,modifier=modifier,colors=colors,border=border,elevation=elevation,content=content)
}

@Composable fun GlassBackdrop(content:@Composable ()->Unit) {
    val c=MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.surface,c.secondaryContainer.copy(alpha=1f),c.surface)))) {
        Canvas(Modifier.fillMaxSize().blur(24.dp)) {
            drawCircle(Brush.radialGradient(listOf(c.tertiary.copy(alpha=.13f),Color.Transparent),center=androidx.compose.ui.geometry.Offset(size.width*.9f,size.height*.25f),radius=size.width*.9f),radius=size.width*.9f,center=androidx.compose.ui.geometry.Offset(size.width*.9f,size.height*.25f))
            drawCircle(Brush.radialGradient(listOf(c.primary.copy(alpha=.12f),Color.Transparent),center=androidx.compose.ui.geometry.Offset(0f,size.height*.7f),radius=size.width),radius=size.width,center=androidx.compose.ui.geometry.Offset(0f,size.height*.7f))
        }
        content()
    }
}

@Composable fun LiquidNavigation(route:String,navigate:(String)->Unit) {
    val tabs=listOf(Triple("today",R.string.today,Icons.Outlined.Today),Triple("medications",R.string.medications,Icons.Outlined.Medication),Triple("history",R.string.history,Icons.Outlined.History),Triple("settings",R.string.settings,Icons.Outlined.Tune))
    val index=tabs.indexOfFirst {it.first==route}.coerceAtLeast(0)
    val reduced=LocalReducedMotion.current
    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp).height(96.dp)) {
        val cell=maxWidth/4
        val x by animateDpAsState(cell*index,if(reduced)tween(0) else spring(dampingRatio=.82f,stiffness=550f),label="liquid-tab")
        val c=MaterialTheme.colorScheme
        Box(Modifier.fillMaxWidth().height(76.dp).align(Alignment.BottomCenter).shadow(10.dp,RoundedCornerShape(32.dp)).background(c.surface.copy(alpha=.88f),RoundedCornerShape(32.dp)).border(1.dp,Color.White.copy(alpha=.4f),RoundedCornerShape(32.dp)))
        Box(Modifier.offset(x=x+(cell-56.dp)/2,y=if(reduced)12.dp else 0.dp).size(56.dp).shadow(6.dp,CircleShape).background(Brush.linearGradient(listOf(c.primaryContainer.copy(alpha=1f),c.secondaryContainer.copy(alpha=1f))),CircleShape).border(1.dp,Color.White.copy(alpha=.65f),CircleShape))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed {i,(path,title,icon)->
                val y by animateDpAsState(if(i==index&&!reduced)(-10).dp else 8.dp,tween(if(reduced)100 else 280),label="lift")
                val scale by animateFloatAsState(if(i==index)1.12f else 1f,tween(220),label="icon-scale")
                Column(Modifier.weight(1f).fillMaxHeight().selectableTab(i==index){navigate(path)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(icon,null,Modifier.offset(y=y).size(26.dp).scale(if(reduced)1f else scale),tint=if(i==index)c.onPrimaryContainer else c.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(title),style=MaterialTheme.typography.labelMedium,color=if(i==index)c.primary else c.onSurfaceVariant)
                }
            }
        }
    }
}
private fun Modifier.selectableTab(selected:Boolean,click:()->Unit)=this.clickable(onClick=click).semantics {this.selected=selected;role=Role.Tab}
