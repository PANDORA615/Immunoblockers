@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immunoblockers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.immunoblockers.R
import com.immunoblockers.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun IntakeChart(days: List<IntakeDay>, selected: String?, onDay: ((LocalDate) -> Unit)? = null) {
    val peak=maxOf(days.maxOfOrNull {it.planned}?:0,1)
    val colors=MaterialTheme.colorScheme
    val scroll=rememberScrollState()
    val dayWidth=(48f*maxOf(1f,LocalDensity.current.fontScale)).dp
    LaunchedEffect(days.firstOrNull()?.date,days.lastOrNull()?.date) {scroll.scrollTo(scroll.maxValue)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom) {
            days.forEach {day ->
                val label=stringResource(R.string.chart_day_accessibility,displayDate(day.date.toString()),day.planned,day.taken,day.skipped,day.unmarked,day.waiting)
                val chosen=selected==day.date.toString()
                val action=if(onDay==null)Modifier else Modifier.selectable(chosen,onClick={onDay(day.date)},role=Role.Button)
                Column(Modifier.width(dayWidth).then(action).semantics(mergeDescendants=true){contentDescription=label}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.completion_count,day.taken,day.planned),style=MaterialTheme.typography.labelSmall)
                    Box(Modifier.height(128.dp).width(24.dp),contentAlignment=Alignment.BottomCenter) {
                        if(day.planned==0)Box(Modifier.fillMaxWidth().height(3.dp).background(colors.outlineVariant,MaterialTheme.shapes.small))
                        else Column(Modifier.fillMaxWidth().height((128f*day.planned/peak).dp)) {
                            if(day.waiting>0)Box(Modifier.fillMaxWidth().weight(day.waiting.toFloat()).background(colors.secondaryContainer))
                            if(day.unmarked>0)Box(Modifier.fillMaxWidth().weight(day.unmarked.toFloat()).background(colors.outlineVariant))
                            if(day.skipped>0)Box(Modifier.fillMaxWidth().weight(day.skipped.toFloat()).background(colors.error))
                            if(day.taken>0)Box(Modifier.fillMaxWidth().weight(day.taken.toFloat()).background(colors.primary))
                        }
                    }
                    Surface(color=if(chosen)colors.primaryContainer else colors.surfaceContainerLow,shape=MaterialTheme.shapes.small) {
                        Text(day.date.format(DateTimeFormatter.ofPattern("d.MM")),style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(6.dp))
                    }
                }
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(R.string.taken to colors.primary,R.string.skip to colors.error,R.string.unmarked to colors.outlineVariant,R.string.pending to colors.secondaryContainer).forEach {(title,color)->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(color,CircleShape));Text(stringResource(title),style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun JournalScreen(rows: List<Occurrence>, meds: List<Medication>, vm: AppViewModel, onRow: (Occurrence) -> Unit) {
    val appearance=LocalAppearance.current
    var period by rememberSaveable {mutableIntStateOf(7)}
    var date by rememberSaveable {mutableStateOf<String?>(null)}
    var pickDate by rememberSaveable {mutableStateOf(false)}
    var med by rememberSaveable {mutableStateOf("")}
    var state by rememberSaveable {mutableStateOf("")}
    val today=LocalDate.now()
    val from=if(period==0)rows.minOfOrNull {LocalDate.parse(it.date)}?.coerceAtMost(today)?:today else today.minusDays(period-1L)
    val periodRows=rows.filter {it.date in from.toString()..today.toString()&&(med.isEmpty()||it.medicationId==med)}
    val selectedRows=periodRows.filter {date==null||it.date==date}
    val records=selectedRows.filter { row -> when(state) {
        "unmarked" -> row.state==Occurrence.PENDING&&row.date<today.toString()
        Occurrence.PENDING -> row.state==Occurrence.PENDING&&row.date>=today.toString()
        "" -> true
        else -> row.state==state
    }}.sortedWith(compareBy<Occurrence> {it.markedAt==null}.thenByDescending {it.markedAt?:it.scheduledAt}.thenBy {it.id})
    val chart=remember(periodRows,from,today){IntakeAnalytics.days(periodRows,maxOf(from,today.minusDays(29)),today,today)}
    val totals=remember(selectedRows){IntakeAnalytics.totals(selectedRows)}
    val states=listOf("" to stringResource(R.string.all_states),Occurrence.TAKEN to stringResource(R.string.taken),Occurrence.SKIPPED to stringResource(R.string.skipped),"unmarked" to stringResource(R.string.unmarked),Occurrence.PENDING to stringResource(R.string.pending))
    val medicines=listOf("" to stringResource(R.string.all_medications))+meds.map {it.id to "${it.name} · ${it.dose.replace('.',',')} ${it.unit}"}
    LazyColumn(modifier=Modifier.testTag("journalList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageHeading(stringResource(R.string.history));Text(stringResource(R.string.journal_title),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)}
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(7 to R.string.period_week,30 to R.string.period_month,0 to R.string.period_all).forEach {(value,label)->FilterChip(period==value,{period=value;date=null},label={Text(stringResource(label))})}
            }
            FilterMenu(medicines.firstOrNull {it.first==med}?.second?:stringResource(R.string.all_medications),medicines){med=it}
            Text(stringResource(R.string.journal_period,displayDate(from.toString()),displayDate(today.toString())),style=MaterialTheme.typography.bodySmall)
        }
        item {
            Card {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.intake_chart),style=MaterialTheme.typography.titleLarge)
                if(chart.isNotEmpty())Text(stringResource(R.string.chart_range,displayDate(chart.first().date.toString()),displayDate(chart.last().date.toString())),style=MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.chart_hint),style=MaterialTheme.typography.bodySmall)
                IntakeChart(chart,date){date=if(date==it.toString())null else it.toString()}
                Text(stringResource(R.string.chart_swipe),style=MaterialTheme.typography.labelSmall)
            }}
        }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                MetricChip(R.string.total_taken,selectedRows.count {it.state==Occurrence.TAKEN})
                MetricChip(R.string.total_skipped,selectedRows.count {it.state==Occurrence.SKIPPED})
                MetricChip(R.string.total_unmarked,selectedRows.count {it.state==Occurrence.PENDING&&it.date<today.toString()})
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={pickDate=true}){Icon(Icons.Outlined.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(date?.let(::displayDate)?:stringResource(R.string.date))}
                if(date!=null)TextButton(onClick={date=null}){Text(stringResource(R.string.show_all_days))}
            }
        }
        if(totals.isNotEmpty()) {
            item {Text(stringResource(R.string.medicine_totals),style=MaterialTheme.typography.titleLarge);Text(stringResource(R.string.medicine_totals_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(totals,key={"total|${it.medicationId}|${it.name}|${it.unit}"}) {total ->
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(total.name,style=MaterialTheme.typography.titleMedium)
                        Text(if(total.amount==null)stringResource(R.string.amount_unknown) else stringResource(R.string.total_medication,total.count,decimalLabel(total.amount),total.unit),style=MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.journal_records),style=MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterMenu(states.first {it.first==state}.second,states){state=it}
                FilterChip(appearance.compactJournal,{vm.settings {it.copy(compactJournal=!it.compactJournal)}},label={Text(stringResource(R.string.journal_compact))})
            }
        }
        if(records.isEmpty())item {EmptyState(R.string.journal_empty,R.string.journal_empty_hint)}
        records.groupBy {it.date}.forEach {(d,group)->
            item(key="date$d"){Text(displayDate(d),style=MaterialTheme.typography.titleMedium)}
            items(group,key={it.id}){row->JournalEntry(row,appearance.compactJournal){onRow(row)}}
        }
        item {Spacer(Modifier.height(16.dp))}
    }
    if(pickDate)AppDatePicker(date?:today.toString(),{date=it;period=0},{pickDate=false})
}

@Composable
fun MetricChip(title: Int, count: Int) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=MaterialTheme.shapes.medium) {
        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(count.toString(),style=MaterialTheme.typography.titleLarge)
            Text(stringResource(title),style=MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun JournalEntry(row: Occurrence, compact: Boolean=false, open: () -> Unit) {
    val colors=MaterialTheme.colorScheme
    val marked=row.markedAt?.let(::displayInstant)
    val actual=if(marked==null)stringResource(if(row.state==Occurrence.PENDING)R.string.pending else R.string.log_time_missing)
        else stringResource(if(row.state==Occurrence.SKIPPED)R.string.actual_skipped else R.string.actual_taken,marked)
    Card(onClick=open,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(if(compact)12.dp else 18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=if(row.state==Occurrence.TAKEN)colors.primaryContainer else colors.surfaceContainerHighest,shape=CircleShape) {
                Icon(if(row.state==Occurrence.TAKEN)Icons.Outlined.Check else if(row.state==Occurrence.SKIPPED)Icons.Outlined.Close else Icons.Outlined.Schedule,null,Modifier.padding(10.dp).size(20.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(row.name,style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.details_dose,row.dose.replace('.',','),row.unit),style=if(compact)MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
                Text(actual,style=MaterialTheme.typography.labelLarge,color=colors.primary)
                if(!compact) {
                    Text(stringResource(R.string.scheduled,"${displayDate(row.date)} · ${row.time}"),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    Text(stateText(row),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TodayHighlights(rows: List<Occurrence>, enabled: Set<String>, onRow: (Occurrence) -> Unit) {
    val today=LocalDate.now()
    val todayRows=rows.filter {it.date==today.toString()}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if("next" in enabled)todayRows.filter {it.state==Occurrence.PENDING}.minByOrNull {it.dueAt}?.let {next ->
            Card(onClick={onRow(next)},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.next_medication),style=MaterialTheme.typography.labelLarge)
                    Text("${next.time} · ${next.name}",style=MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.details_dose,next.dose.replace('.',','),next.unit),style=MaterialTheme.typography.titleMedium)
                }
            }
        }
        if("journal" in enabled) {
            val latest=todayRows.filter {it.markedAt!=null}.sortedByDescending {it.markedAt}.take(2)
            if(latest.isNotEmpty()) {
                Text(stringResource(R.string.last_records),style=MaterialTheme.typography.titleMedium)
                latest.forEach {row->JournalEntry(row,true){onRow(row)}}
            }
        }
        if("chart" in enabled)Card {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.block_chart),style=MaterialTheme.typography.titleMedium)
                IntakeChart(remember(rows,today){IntakeAnalytics.days(rows,today.minusDays(6),today,today)},null)
            }
        }
    }
}
