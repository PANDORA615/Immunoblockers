@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immunoblockers.ui

import android.content.*
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immunoblockers.R
import com.immunoblockers.BuildConfig
import com.immunoblockers.features.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate

val toolNames=listOf("stock" to R.string.tools_stock,"calendar" to R.string.tools_calendar,"mood" to R.string.tools_mood,"permissions" to R.string.tools_permissions,"update" to R.string.tools_update)
@Composable fun HomeTools(open:(String)->Unit) {
    Text(stringResource(R.string.tools_title),style=MaterialTheme.typography.titleLarge)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        toolNames.forEach {(key,title)->AppCard(onClick={open(key)},modifier=Modifier.widthIn(min=140.dp,max=190.dp)){
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(18.dp))
        }}
    }
}
@Composable fun ToolScreen(kind:String,vm:AppViewModel,back:()->Unit,openHistory:(String)->Unit) {
    val context=LocalContext.current
    val rows by vm.occurrences.collectAsStateWithLifecycle()
    val meds by vm.medications.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    var saved by remember {mutableStateOf(false)}
    var revision by remember {mutableIntStateOf(0)}
    fun run(job:suspend ()->Unit){scope.launch {busy=true;error=false;saved=false;try{withContext(Dispatchers.IO){job()};revision++;saved=true}catch(_:Exception){error=true}finally{busy=false}}}
    val book=remember {LocalBook(context)}
    var release by remember {mutableStateOf<ReleaseUpdate?>(null)}
    var checked by remember {mutableStateOf(false)}
    var apk by remember {mutableStateOf<File?>(null)}
    var progress by remember {mutableFloatStateOf(0f)}
    var selectedMed by rememberSaveable {mutableStateOf("")}
    var amount by rememberSaveable {mutableStateOf("")}
    var threshold by rememberSaveable {mutableStateOf("3")}
    var day by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    Column {
        TopAppBar(title={Text(stringResource(toolNames.firstOrNull {it.first==kind}?.second?:R.string.tools_title))},navigationIcon={IconButton(back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.back))}})
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(busy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            if(error)Text(stringResource(R.string.feature_error),color=MaterialTheme.colorScheme.error)
            if(saved)Text(stringResource(R.string.feature_saved),color=MaterialTheme.colorScheme.primary)
            when(kind) {
                "stock"->{
                    Text(stringResource(R.string.stock_hint))
                    val choices=meds.filter {!it.archived}.map {it.id to "${it.name} · ${it.unit}"}
                    if(choices.isEmpty())Text(stringResource(R.string.stock_empty)) else {
                        FilterMenu(choices.firstOrNull {it.first==selectedMed}?.second?:stringResource(R.string.all_medications),choices){selectedMed=it}
                        AppTextField(amount,{amount=it},label={Text(stringResource(R.string.stock_amount))},modifier=Modifier.fillMaxWidth())
                        AppTextField(threshold,{threshold=it},label={Text(stringResource(R.string.stock_threshold))},modifier=Modifier.fillMaxWidth())
                        Button(enabled=!busy&&meds.any {it.id==selectedMed},onClick={run{val med=meds.first {it.id==selectedMed};book.setStock(med,amount.replace(',','.').toBigDecimal(),threshold.replace(',','.').toBigDecimal(),rows);vm.app.reminders.reconcileAndSchedule()}}){Text(stringResource(R.string.save))}
                    }
                    remember(rows,revision){book.stocks(rows)}.forEach {stock->AppCard {
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(stock.name,style=MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.stock_remaining,com.immunoblockers.data.decimalLabel(stock.remaining),stock.unit))
                            if(stock.remaining<=stock.threshold)Text(stringResource(R.string.stock_low),color=MaterialTheme.colorScheme.error)
                            TextButton(onClick={run{book.removeStock(stock.id);vm.app.reminders.reconcileAndSchedule()}}){Text(stringResource(R.string.stock_remove))}
                        }
                    }}
                }
                "mood"->{ WellbeingScreen(vm) }
                "update"->{
                    Text(stringResource(R.string.update_current,BuildConfig.VERSION_NAME),style=MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.update_hint))
                    Button(enabled=!busy,onClick={run{val result=Updates.check();withContext(Dispatchers.Main){release=result;checked=true;apk=null}}}){Text(stringResource(R.string.update_check))}
                    if(checked&&release==null)Text(stringResource(R.string.update_none))
                    release?.let {r->
                        Text(stringResource(R.string.update_found,r.version,"%.1f".format(r.size/1_000_000f)),style=MaterialTheme.typography.titleLarge)
                        Text(r.notes)
                        Button(enabled=!busy,onClick={run{val file=Updates.download(context,r){progress=it};withContext(Dispatchers.Main){apk=file}}}){Text(stringResource(R.string.update_download))}
                        if(busy)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                    }
                    apk?.let {file->Text(stringResource(R.string.update_verified));Button(onClick={Updates.install(context,file)}){Text(stringResource(R.string.update_install))}}
                }
                "permissions"->{PermissionStatus(vm);Text(stringResource(R.string.delivery_limits));OutlinedButton(onClick={vm.testNotification()}){Text(stringResource(R.string.test_notification))}}
                "calendar"->{
                    Text(stringResource(R.string.calendar_hint))
                    val calendar=rememberDatePickerState(initialSelectedDateMillis=LocalDate.parse(day).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
                    LaunchedEffect(calendar.selectedDateMillis){calendar.selectedDateMillis?.let {day=java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()}}
                    DatePicker(calendar)
                    Button(onClick={openHistory(day)}){Text(stringResource(R.string.calendar_open))}
                    Text(displayDate(day))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
