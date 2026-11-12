@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.immunoblockers.ui

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immunoblockers.R
import com.immunoblockers.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val ru: Locale = Locale.forLanguageTag("ru")
fun displayDate(date: String) = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy",ru))
fun displayInstant(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm",ru))
@Composable fun stateText(row: Occurrence): String = when(row.state) {
    Occurrence.TAKEN -> stringResource(R.string.taken)
    Occurrence.SKIPPED -> stringResource(R.string.skipped)
    else -> if(row.snoozedUntil != null) stringResource(R.string.snoozed,displayInstant(row.snoozedUntil)) else if(row.date < LocalDate.now().toString()) stringResource(R.string.unmarked) else stringResource(R.string.pending)
}
@Composable fun PageHeading(title: String) {Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp,bottom=12.dp))}
@Composable fun EmptyState(title: Int, body: Int? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.HealthAndSafety,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
        Text(stringResource(title),style=MaterialTheme.typography.headlineSmall)
        if(body != null) Text(stringResource(body),color=MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
@Composable fun TodayScreen(rows: List<Occurrence>, meds: List<Medication>, vm: AppViewModel, add: () -> Unit, onRow:(Occurrence)->Unit) {
    val today = LocalDate.now()
    val doses = rows.filter {it.date==today.toString()}.sortedBy {it.time}
    val taken = doses.count {it.state==Occurrence.TAKEN}
    val skipped = doses.count {it.state==Occurrence.SKIPPED}
    val progress by animateFloatAsState(if(doses.isEmpty()) 0f else (taken+skipped).toFloat()/doses.size,tween(if(LocalReducedMotion.current) Motion.SHORT else Motion.PROGRESS),label="progress")
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeading(stringResource(R.string.today))
            Text(stringResource(R.string.today_label,today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE",ru))),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { PermissionStatus(vm, compact=true) }
        if(doses.isNotEmpty()) item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.day_progress),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.completion_count,taken+skipped,doses.size),style=MaterialTheme.typography.displayMedium)
                    LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(8.dp),gapSize=0.dp)
                    Text(stringResource(R.string.progress_counts,taken,skipped,doses.size),style=MaterialTheme.typography.bodyMedium)
                    doses.firstOrNull {it.state==Occurrence.PENDING}?.let {Text(stringResource(R.string.next_dose,it.time),style=MaterialTheme.typography.labelLarge)}
                }
            }
        }
        if(meds.none {!it.archived}) item { EmptyState(R.string.no_medications,R.string.no_medications_body) { Button(onClick=add){Text(stringResource(R.string.add_medication))} } }
        else if(doses.isEmpty()) item {EmptyState(R.string.no_doses,R.string.no_doses_body)}
        else if(taken+skipped==doses.size) item {EmptyState(R.string.all_done,R.string.all_done_body)}
        doses.groupBy {it.time}.forEach { (time, group) ->
            item(key="time$time") {Text(time,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))}
            items(group,key={it.id}) {row -> DoseCard(row,{onRow(row)},{vm.mark(row,Occurrence.TAKEN)})}
        }
    }
}
@Composable fun DoseCard(row: Occurrence, open:()->Unit, take:(()->Unit)? = null) {
    val complete=row.state!=Occurrence.PENDING
    val color by animateColorAsState(if(complete) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,tween(Motion.SCREEN),label="card")
    Card(onClick=open,colors=CardDefaults.cardColors(containerColor=color),modifier=Modifier.fillMaxWidth().animateContentSize(tween(if(LocalReducedMotion.current) Motion.SHORT else Motion.SCREEN))) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(if(row.state==Occurrence.TAKEN) Icons.Outlined.CheckCircle else if(row.state==Occurrence.SKIPPED) Icons.Outlined.RemoveCircleOutline else Icons.Outlined.Medication,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {Text(row.name,style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.details_dose,row.dose,row.unit),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Text(stateText(row),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(take!=null && !complete) FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick=open){Text(stringResource(R.string.snooze))}
                Button(onClick=take){Text(stringResource(R.string.take))}
            }
        }
    }
}
@Composable fun scheduleSummary(med: Medication): String {
    val days=stringArrayResource(R.array.day_names)
    val selected=med.days.split(',').mapNotNull {it.toIntOrNull()?.minus(1)}
    return (if(selected.size==7) stringResource(R.string.daily) else selected.joinToString(" "){days[it]})+" · "+med.times.replace(",", " · ")
}
@Composable fun MedicationsScreen(meds: List<Medication>, open:(Medication)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var archive by rememberSaveable {mutableStateOf(false)}
    val filtered=meds.filter {it.archived==archive && it.name.contains(query,true)}
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {PageHeading(stringResource(R.string.medications))}
        item {OutlinedTextField(query,{query=it},label={Text(stringResource(R.string.search))},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(!archive,{archive=false},label={Text(stringResource(R.string.active))});FilterChip(archive,{archive=true},label={Text(stringResource(R.string.archive))})}}
        if(filtered.isEmpty()) item {EmptyState(if(query.isEmpty()) R.string.no_medications else R.string.empty_search)}
        items(filtered,key={it.id}) {med -> Card(onClick={open(med)},modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(med.name,style=MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.details_dose,med.dose,med.unit),style=MaterialTheme.typography.bodyLarge)
                Text(scheduleSummary(med),color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(med.paused || med.archived) SuggestionChip(onClick={open(med)},label={Text(stringResource(if(med.archived) R.string.archived else R.string.paused))})
            }
        } }
    }
}
@Composable fun MedicationDetails(med: Medication, rows: List<Occurrence>, vm:AppViewModel, back:()->Unit, edit:()->Unit, onRow:(Occurrence)->Unit) {
    var confirm by remember {mutableStateOf(false)}
    Column {
        TopAppBar(title={Text(stringResource(R.string.details))},navigationIcon={IconButton(back){Icon(Icons.Outlined.ArrowBack,stringResource(R.string.back))}},actions={IconButton(edit){Icon(Icons.Outlined.Edit,stringResource(R.string.edit))}})
        LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(med.name,style=MaterialTheme.typography.headlineLarge);Text(stringResource(R.string.details_dose,med.dose,med.unit),style=MaterialTheme.typography.titleLarge)}
            item {Text(scheduleSummary(med));Text(stringResource(R.string.course,displayDate(med.start),med.end?.let(::displayDate) ?: stringResource(R.string.indefinite)),modifier=Modifier.padding(top=8.dp));if(med.note.isNotBlank())Text(med.note,modifier=Modifier.padding(top=12.dp))}
            item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.changeMedication(med.copy(paused=!med.paused))}) {Text(stringResource(if(med.paused)R.string.resume else R.string.pause))}
                OutlinedButton(onClick={if(med.archived)vm.changeMedication(med.copy(archived=false,paused=false)) else confirm=true}){Text(stringResource(if(med.archived)R.string.restore else R.string.archive_action))}
            }}
            item {Text(stringResource(R.string.medication_history),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=16.dp))}
            items(rows.filter {it.date<=LocalDate.now().toString()}.take(100),key={it.id}) {row -> HistoryRow(row){onRow(row)}}
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.archive_confirm))},text={Text(stringResource(R.string.archive_explain))},confirmButton={TextButton(onClick={vm.changeMedication(med.copy(archived=true));confirm=false}){Text(stringResource(R.string.archive_action))}},dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.cancel))}})
}
@Composable fun FilterMenu(label:String, choices:List<Pair<String,String>>, select:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {OutlinedButton(onClick={expanded=true}){Text(label);Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(expanded,{expanded=false}){choices.forEach {(id,title)->DropdownMenuItem(text={Text(title)},onClick={select(id);expanded=false})}}}
}
@Composable fun HistoryScreen(rows:List<Occurrence>,meds:List<Medication>,onRow:(Occurrence)->Unit) {
    var date by rememberSaveable {mutableStateOf<String?>(null)}
    var med by rememberSaveable {mutableStateOf("")}
    var state by rememberSaveable {mutableStateOf("")}
    val context=LocalContext.current
    val states=listOf("" to stringResource(R.string.all_states),Occurrence.TAKEN to stringResource(R.string.taken),Occurrence.SKIPPED to stringResource(R.string.skipped),Occurrence.PENDING to stringResource(R.string.unmarked))
    val choices=listOf("" to stringResource(R.string.all_medications))+meds.map {it.id to it.name}
    val filtered=rows.filter {it.date<=LocalDate.now().toString() && (date==null||it.date==date) && (med.isEmpty()||it.medicationId==med) && (state.isEmpty()||it.state==state) && (state!=Occurrence.PENDING||it.date<LocalDate.now().toString())}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {PageHeading(stringResource(R.string.history))}
        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={val d=date?.let(LocalDate::parse)?:LocalDate.now();DatePickerDialog(context,{_,y,m,day->date=LocalDate.of(y,m+1,day).toString()},d.year,d.monthValue-1,d.dayOfMonth).show()}){Icon(Icons.Outlined.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(date?.let(::displayDate)?:stringResource(R.string.all_dates))}
            if(date!=null)TextButton(onClick={date=null}){Text(stringResource(R.string.clear_date))}
            FilterMenu(choices.firstOrNull {it.first==med}?.second?:stringResource(R.string.all_medications),choices){med=it}
            FilterMenu(states.first {it.first==state}.second,states){state=it}
        }}
        if(filtered.isEmpty()) item {EmptyState(R.string.empty_history,R.string.empty_history_body)}
        filtered.groupBy {it.date}.forEach {(d,group)->item(key=d){Text(displayDate(d),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))};items(group,key={it.id}){row->HistoryRow(row){onRow(row)}}}
    }
}
@Composable fun HistoryRow(row:Occurrence,open:()->Unit) {
    Card(onClick=open,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(row.name,style=MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.details_dose,row.dose,row.unit))
        Text(stringResource(R.string.scheduled,"${row.date} ${row.time}"),style=MaterialTheme.typography.bodySmall)
        row.markedAt?.let {Text(stringResource(R.string.marked,displayInstant(it)),style=MaterialTheme.typography.bodySmall)}
        Text(stateText(row),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
    }}
}
