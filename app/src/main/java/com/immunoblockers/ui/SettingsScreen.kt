@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immunoblockers.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.immunoblockers.widgets.WidgetKind
import com.immunoblockers.widgets.WidgetUpdater
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.testTag
import android.app.AlarmManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.immunoblockers.R
import com.immunoblockers.data.ThemeSettings
import java.util.Locale

fun notificationSettings(context:android.content.Context) {context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
@Composable fun PermissionStatus(vm:AppViewModel,compact:Boolean=false) {
    val context=LocalContext.current
    var tick by remember {mutableIntStateOf(0)}
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME){tick++;vm.refresh()}
    val allowed=remember(tick){vm.app.reminders.notificationsAvailable()}
    val exact=remember(tick){vm.app.reminders.exactAvailable()}
    var explain by remember {mutableStateOf(false)}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){tick++}
    if(!compact||!allowed||!exact) AppCard(colors=CardDefaults.cardColors(containerColor=if(allowed)MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if(allowed)R.string.permission_ok else R.string.permission_missing),style=MaterialTheme.typography.bodyMedium)
            if(!allowed)TextButton(onClick={if(Build.VERSION.SDK_INT>=33&&!vm.app.reminders.notificationsAvailable())explain=true else notificationSettings(context)}){Text(stringResource(R.string.permission_action))}
            Text(stringResource(if(exact)R.string.exact_ok else R.string.exact_missing),style=MaterialTheme.typography.bodySmall)
            if(!compact)TextButton(onClick={notificationSettings(context)}){Text(stringResource(R.string.open_settings))}
            if(!exact&&Build.VERSION.SDK_INT>=31)TextButton(onClick={context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}){Text(stringResource(R.string.exact_settings))}
        }
    }
    if(explain)AlertDialog(onDismissRequest={explain=false},title={Text(stringResource(R.string.permission_explain_title))},text={Text(stringResource(R.string.permission_explain_body))},confirmButton={TextButton(onClick={explain=false;if(Build.VERSION.SDK_INT>=33)request.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text(stringResource(R.string.allow))}},dismissButton={TextButton(onClick={explain=false;notificationSettings(context)}){Text(stringResource(R.string.open_settings))}})
}
@Composable fun SectionTitle(id:Int){Text(stringResource(id),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=16.dp,bottom=8.dp))}
@Composable fun SettingSwitch(title:Int,body:Int?=null,value:Boolean,change:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {Text(stringResource(title),style=MaterialTheme.typography.titleSmall);if(body!=null)Text(stringResource(body),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        val label=stringResource(title)
        Switch(value,change,modifier=Modifier.semantics {contentDescription=label})
    }
}
@Composable fun SettingsScreen(s:ThemeSettings,vm:AppViewModel) {
    val context=LocalContext.current
    var hex by rememberSaveable(s.seed){mutableStateOf("#%06X".format(s.seed and 0xFFFFFF))}
    var hue by rememberSaveable {mutableFloatStateOf(150f)}
    val imageScope=rememberCoroutineScope()
    var imageError by remember {mutableStateOf(false)}
    val widgetPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)imageScope.launch {
        try {val path=copyLocalImage(context,uri);storeLocalImage(vm,path,false);vm.refresh();imageError=false}catch(_:Exception){imageError=true}
    }}
    var wallpaperBusy by remember {mutableStateOf(false)}
    var wallpaperError by remember {mutableStateOf(false)}
    val wallpaperPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)imageScope.launch {
        wallpaperBusy=true;wallpaperError=false
        try {val path=copyLocalImage(context,uri);storeLocalImage(vm,path,LocalImageUse.WALLPAPER)}catch(_:Exception){wallpaperError=true}finally{wallpaperBusy=false}
    }}
    val valid=hex.matches(Regex("#[0-9a-fA-F]{6}"))
    LazyColumn(modifier=Modifier.testTag("settingsList"),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageHeading(stringResource(R.string.settings));SectionTitle(R.string.appearance)}
        item {Text(stringResource(R.string.theme),style=MaterialTheme.typography.titleSmall);DesignChoices(s.theme,listOf("system" to R.string.system,"light" to R.string.light,"dark" to R.string.dark)){value->vm.settings {it.copy(theme=value)}}}
        item {Text(stringResource(R.string.palette),style=MaterialTheme.typography.titleSmall);DesignChoices(s.palette,listOf("default" to R.string.default_palette,"dynamic" to R.string.dynamic_palette,"custom" to R.string.custom_palette)){value->vm.settings {it.copy(palette=value)}}}
        if(s.palette=="custom") {
            item {FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(0xFF146B55,0xFF5264AD,0xFF8B4587,0xFFB55C32,0xFF406980,0xFF665D34).forEach {seed->val label=stringResource(R.string.color_sample,"#%06X".format(seed and 0xFFFFFF));IconButton(onClick={vm.settings {it.copy(seed=seed)}},modifier=Modifier.size(52.dp).semantics{contentDescription=label}){Box(Modifier.size(32.dp).background(Color(seed),CircleShape))}}}}
            item {AppTextField(hex,{hex=it},label={Text(stringResource(R.string.hex))},isError=!valid,supportingText={if(!valid)Text(stringResource(R.string.invalid_hex))},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={val seed=0xFF000000 or hex.drop(1).toLong(16);vm.settings {it.copy(seed=seed)}},enabled=valid){Text(stringResource(R.string.apply_color))}}
            item {val label=stringResource(R.string.hue);Text(label);Slider(hue,{hue=it;hex="#%06X".format(AndroidColor.HSVToColor(floatArrayOf(it,0.6f,0.7f)) and 0xFFFFFF)},valueRange=0f..360f,modifier=Modifier.semantics{contentDescription=label})}
            item {Text(stringResource(R.string.preview),style=MaterialTheme.typography.titleMedium)}
            item {FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {listOf(false,true).forEach {dark->PalettePreview(s.seed,dark)}}}
        }
        item {Text(stringResource(R.string.interface_style),style=MaterialTheme.typography.titleLarge);DesignChoices(s.interfaceStyle,listOf("glass" to R.string.glass_style,"material" to R.string.material_style)){value->vm.settings {it.copy(interfaceStyle=value)}};Text(stringResource(R.string.glass_hint),style=MaterialTheme.typography.bodySmall)}
        item {
            Text("Обои приложения",style=MaterialTheme.typography.titleLarge)
            Text("Фон всех экранов и стеклянных карточек. Выбранное фото сохраняется на устройстве.",style=MaterialTheme.typography.bodySmall)
            if(s.wallpaperPath.isNotBlank()) {
                LocalImagePreview(s.wallpaperPath,"Предпросмотр обоев",Modifier.fillMaxWidth().height(144.dp).testTag("wallpaperPreview"))
                Text("Обои выбраны",color=MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(enabled=!wallpaperBusy,onClick={wallpaperPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text(if(wallpaperBusy)"Сохраняем обои…" else "Выбрать обои")}
            if(s.wallpaperPath.isNotBlank())TextButton(enabled=!wallpaperBusy,onClick={imageScope.launch {
                try {storeLocalImage(vm,"",LocalImageUse.WALLPAPER);wallpaperError=false}catch(_:Exception){wallpaperError=true}
            }}){Text("Удалить обои")}
            if(wallpaperError)Text("Не удалось сохранить обои. Попробуйте другое изображение.",color=MaterialTheme.colorScheme.error)
        }
        item {SettingSwitch(R.string.rounded_font,R.string.rounded_font_hint,s.roundedFont){value->vm.settings {it.copy(roundedFont=value)}}}
        item {HorizontalDivider();SectionTitle(R.string.control_design);Text(stringResource(R.string.control_design_hint),style=MaterialTheme.typography.bodyMedium)}
        item {Text(stringResource(R.string.field_design),style=MaterialTheme.typography.titleSmall);DesignChoices(s.fieldStyle,listOf("filled" to R.string.fields_filled,"outlined" to R.string.fields_outlined)){value->vm.settings {it.copy(fieldStyle=value)}}}
        item {Text(stringResource(R.string.corner_design),style=MaterialTheme.typography.titleSmall);DesignChoices(s.corners,listOf("rounded" to R.string.corners_rounded,"soft" to R.string.corners_soft,"square" to R.string.corners_square)){value->vm.settings {it.copy(corners=value)}}}
        item {SettingSwitch(R.string.bold_text,R.string.bold_text_hint,s.boldText){value->vm.settings {it.copy(boldText=value)}}}
        item {var preview by rememberSaveable {mutableStateOf("")};AppTextField(preview,{preview=it},label={Text(stringResource(R.string.input_preview))},supportingText={Text(stringResource(R.string.input_preview_hint))},modifier=Modifier.fillMaxWidth())}
        item {SettingSwitch(R.string.reduced_motion,R.string.reduced_motion_body,s.reducedMotion){value->vm.settings {it.copy(reducedMotion=value)}}}
        item {HorizontalDivider();SectionTitle(R.string.dashboard_widgets);Text(stringResource(R.string.dashboard_hint),style=MaterialTheme.typography.bodySmall)}
        listOf("progress" to R.string.block_progress,"next" to R.string.block_next,"journal" to R.string.block_journal,"chart" to R.string.block_chart).forEach {(block,title)->
            item {SettingSwitch(title,value=block in s.todayBlocks.split(',')){enabled->vm.settings {current->
                val chosen=current.todayBlocks.split(',').filter {it.isNotBlank()}.toMutableSet()
                if(enabled)chosen.add(block) else chosen.remove(block)
                current.copy(todayBlocks=chosen.joinToString(","))
            }}}
        }
        item {SettingSwitch(R.string.widget_confirm,R.string.widget_confirm_hint,s.confirmWidgetActions){v->vm.settings {it.copy(confirmWidgetActions=v)}}}
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("Спрашивать о самочувствии после приёма",Modifier.weight(1f));Switch(s.askWellbeing,{v->vm.settings {it.copy(askWellbeing=v)}})}
            Text("Опрос через выбранное время после первого приёма. Android может задержать доставку.")
            FlowRow {listOf(1,2,4,6).forEach {hours->FilterChip(s.surveyHours==hours,{vm.settings {it.copy(surveyHours=hours)}},label={Text("$hours ч")})}}
        }
        item {HorizontalDivider();SectionTitle(R.string.launcher_widgets)}
        item {
            if(s.widgetImage.isNotBlank())LocalImagePreview(s.widgetImage,"Подложка виджетов",Modifier.fillMaxWidth().height(100.dp))
            Text("Оформление виджетов",style=MaterialTheme.typography.titleMedium)
            Text("Прозрачность: ${s.widgetOpacity*100/255}%")
            Slider(s.widgetOpacity.toFloat(),{value->vm.settings {it.copy(widgetOpacity=value.toInt())}},valueRange=40f..255f)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("system" to "Как в приложении","light" to "Светлый","dark" to "Тёмный").forEach {(key,label)->FilterChip(s.widgetTheme==key,{vm.settings {it.copy(widgetTheme=key)}},label={Text(label)})}}
            OutlinedButton(onClick={widgetPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("Выбрать подложку")}
            if(s.widgetImage.isNotBlank())TextButton(onClick={imageScope.launch {storeLocalImage(vm,"",false);vm.refresh()}}){Text("Удалить подложку")}
            Text("Выбранное изображение копируется в закрытое хранилище и размывается. Живые обои Android не используются.",style=MaterialTheme.typography.bodySmall)
            if(imageError)Text("Не удалось открыть изображение")
        }
        item {WidgetGallery(vm)}
        item {SettingSwitch(R.string.private_widgets,R.string.private_widgets_hint,s.privateWidgets){value->vm.settings {it.copy(privateWidgets=value)}}}
        item {HorizontalDivider();SectionTitle(R.string.notifications)}
        item {PermissionStatus(vm)}
        item {SettingSwitch(R.string.enable_reminders,value=s.reminders){value->vm.settings {it.copy(reminders=value)}}}
        item {SettingSwitch(R.string.privacy_notifications,R.string.privacy_notifications_body,s.privateNotifications){value->vm.settings {it.copy(privateNotifications=value)}}}
        item {OutlinedButton(onClick={if(vm.app.reminders.notificationsAvailable())vm.testNotification() else notificationSettings(context)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.NotificationsActive,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.test_notification))}}
        item {Text(stringResource(R.string.delivery_limits),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {HorizontalDivider();SectionTitle(R.string.privacy);Text(stringResource(R.string.privacy_body),style=MaterialTheme.typography.bodyMedium)}
        item {Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=20.dp));Text(stringResource(R.string.version),style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable fun DesignChoices(selected:String,choices:List<Pair<String,Int>>,choose:(String)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        choices.forEach {(value,label)->FilterChip(selected==value,{choose(value)},label={Text(stringResource(label))},leadingIcon=if(selected==value){{Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))}}else null)}
    }
}

@Composable fun WidgetGallery(vm: AppViewModel) {
    val scope=rememberCoroutineScope()
    val appearance=LocalAppearance.current
    val context=LocalContext.current
    var manual by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        WidgetKind.entries.forEach {kind->Card {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Widgets,null,tint=MaterialTheme.colorScheme.primary)
                Text(stringResource(kind.title),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(kind.description),style=MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick={
                    val manager=AppWidgetManager.getInstance(context)
                    if(!manager.isRequestPinAppWidgetSupported)manual=true
                    else scope.launch(Dispatchers.IO) {
                        val preview=WidgetUpdater.render(context,kind,vm.app.repository.dao.occurrences(),appearance,activeMedicationIds=vm.app.repository.dao.medications().filter { !it.paused && !it.archived }.map {it.id}.toSet())
                        withContext(Dispatchers.Main) {
                            val extras=Bundle().apply {putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,preview)}
                            if(!manager.requestPinAppWidget(ComponentName(context,kind.provider),extras,null))manual=true
                        }
                    }
                }){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.add_widget))}
            }
        }}
    }
    if(manual)AlertDialog(onDismissRequest={manual=false},title={Text(stringResource(R.string.launcher_widgets))},text={Text(stringResource(R.string.widget_add_manual))},confirmButton={TextButton(onClick={manual=false}){Text(stringResource(R.string.close))}})
}
@Composable fun PalettePreview(seed:Long,dark:Boolean) {
    val scheme by produceState(if(dark)darkColorScheme() else lightColorScheme(),seed,dark){value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default){seedScheme(seed,dark)}}
    var checked by remember {mutableStateOf(true)}
    MaterialTheme(colorScheme=scheme) {Surface(color=scheme.surface,shape=MaterialTheme.shapes.large,modifier=Modifier.widthIn(min=140.dp,max=260.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if(dark)R.string.dark else R.string.light),style=MaterialTheme.typography.titleSmall)
            AppCard(colors=CardDefaults.cardColors(containerColor=scheme.primaryContainer)){Text(stringResource(R.string.preview_body),modifier=Modifier.padding(12.dp),color=scheme.onPrimaryContainer)}
            Button(onClick={checked=!checked}){Text(stringResource(if(checked)R.string.take else R.string.taken))}
            Switch(checked=checked,onCheckedChange={checked=it})
        }
    }}
}
