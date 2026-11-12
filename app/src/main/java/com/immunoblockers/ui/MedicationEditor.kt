@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.immunoblockers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.immunoblockers.R
import com.immunoblockers.data.Medication
import java.time.*
import java.util.UUID

@Composable
fun MedicationEditor(existing: Medication?, back: () -> Unit, vm: AppViewModel) {
    val units=stringArrayResource(R.array.units)
    val days=stringArrayResource(R.array.day_names)
    val id=rememberSaveable {existing?.id?:UUID.randomUUID().toString()}
    var name by rememberSaveable {mutableStateOf(existing?.name?:"")}
    var dose by rememberSaveable {mutableStateOf(existing?.dose?:"")}
    var unit by rememberSaveable {mutableStateOf(existing?.unit?.takeIf {it in units}?:if(existing==null)units[0] else units.last())}
    var customUnit by rememberSaveable {mutableStateOf(existing?.unit?.takeIf {it !in units}.orEmpty())}
    var note by rememberSaveable {mutableStateOf(existing?.note?:"")}
    var start by rememberSaveable {mutableStateOf(existing?.start?:LocalDate.now().toString())}
    var end by rememberSaveable {mutableStateOf(existing?.end)}
    var selectedDays by rememberSaveable {mutableStateOf(existing?.days?:"1,2,3,4,5,6,7")}
    var times by rememberSaveable {mutableStateOf(existing?.times?:"09:00")}
    var tried by rememberSaveable {mutableStateOf(false)}
    var confirm by remember {mutableStateOf(false)}
    var duplicate by remember {mutableStateOf(false)}
    var dateTarget by rememberSaveable {mutableStateOf<String?>(null)}
    var timeTarget by rememberSaveable {mutableStateOf<String?>(null)}
    val initial=rememberSaveable {listOf(name,dose,unit,customUnit,note,start,end?:"",selectedDays,times).joinToString("\u0001")}
    val dirty=initial!=listOf(name,dose,unit,customUnit,note,start,end?:"",selectedDays,times).joinToString("\u0001")
    fun leave(){if(dirty)confirm=true else back()}
    BackHandler {leave()}
    val validName=name.isNotBlank()
    val validDose=dose.replace(',','.').toBigDecimalOrNull()?.let {it.signum()>0}==true
    val validUnit=unit!=units.last()||customUnit.isNotBlank()
    val validDays=selectedDays.isNotEmpty()
    val validTimes=times.isNotEmpty()&&times.split(',').distinct().size==times.split(',').size
    val validDates=end==null||end!!>=start
    fun save() {
        tried=true
        if(validName&&validDose&&validUnit&&validDays&&validTimes&&validDates) {
            val now=System.currentTimeMillis()
            vm.saveMedication(Medication(id,name.trim(),dose.replace(',','.'),if(unit==units.last())customUnit.trim() else unit,note.trim(),start,end,selectedDays,times,existing?.paused?:false,existing?.archived?:false,(existing?.version?:0)+1,existing?.createdAt?:now,now),back)
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title={Text(stringResource(if(existing==null)R.string.add_medication else R.string.edit_medication))},navigationIcon={IconButton(onClick={leave()}){Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.back))}})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FormSection(R.string.medication_information,R.string.medication_information_hint)
            AppTextField(name,{name=it},label={Text(stringResource(R.string.name))},leadingIcon={Icon(Icons.Outlined.Medication,null)},isError=tried&&!validName,supportingText={if(tried&&!validName)Text(stringResource(R.string.required_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
            AppTextField(dose,{dose=it},label={Text(stringResource(R.string.dose))},leadingIcon={Icon(Icons.Outlined.Numbers,null)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=tried&&!validDose,supportingText={if(tried&&!validDose)Text(stringResource(R.string.invalid_dose))},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text(stringResource(R.string.unit),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {units.forEach {u->FilterChip(unit==u,{unit=u},label={Text(u)},leadingIcon=if(unit==u){{Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))}}else null)}}
            if(unit==units.last())AppTextField(customUnit,{customUnit=it},label={Text(stringResource(R.string.custom_unit))},isError=tried&&!validUnit,supportingText={if(tried&&!validUnit)Text(stringResource(R.string.invalid_unit))},singleLine=true,modifier=Modifier.fillMaxWidth())
            AppTextField(note,{note=it},label={Text(stringResource(R.string.note))},modifier=Modifier.fillMaxWidth(),minLines=2)
            HorizontalDivider()
            FormSection(R.string.course_settings)
            FilledTonalButton(onClick={dateTarget="start"},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Icon(Icons.Outlined.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.start_date)+": "+displayDate(start))}
            SettingSwitch(R.string.no_end,value=end==null){end=if(it)null else start}
            if(end!=null)FilledTonalButton(onClick={dateTarget="end"},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(stringResource(R.string.end_date)+": "+displayDate(end!!))}
            if(tried&&!validDates)Text(stringResource(R.string.invalid_dates),color=MaterialTheme.colorScheme.error)
            HorizontalDivider()
            FormSection(R.string.schedule_settings,R.string.daily_hint)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {days.forEachIndexed {index,label ->
                val value=(index+1).toString()
                FilterChip(value in selectedDays.split(','),{val set=selectedDays.split(',').filter {it.isNotBlank()}.toMutableSet();if(!set.add(value))set.remove(value);selectedDays=set.sorted().joinToString(",")},label={Text(label)})
            }}
            if(tried&&!validDays)Text(stringResource(R.string.invalid_days),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.times),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                times.split(',').filter {it.isNotBlank()}.forEach {time ->
                    ElevatedCard {
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick={timeTarget=time}){Icon(Icons.Outlined.Schedule,null);Spacer(Modifier.width(8.dp));Text(time,style=MaterialTheme.typography.titleMedium)}
                            IconButton(onClick={times=times.split(',').filter {it!=time}.joinToString(",")}){Icon(Icons.Outlined.Close,stringResource(R.string.remove_time,time))}
                        }
                    }
                }
            }
            if(duplicate)Text(stringResource(R.string.time_conflict),color=MaterialTheme.colorScheme.error)
            if(tried&&!validTimes)Text(stringResource(R.string.invalid_times),color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick={timeTarget="new"}){Icon(Icons.Outlined.Add,null);Text(stringResource(R.string.add_time))}
            Button(onClick={save()},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Icon(Icons.Outlined.Check,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.save))}
            Spacer(Modifier.height(24.dp))
        }
    }
    dateTarget?.let {target -> AppDatePicker(if(target=="start")start else end?:start,{if(target=="start")start=it else end=it},{dateTarget=null})}
    timeTarget?.let {target -> AppTimePicker(if(target=="new")LocalTime.NOON else LocalTime.parse(target),{newTime ->
        val next=newTime.toString()
        val previous=times.split(',').filter {it.isNotBlank()}
        if(next in previous&&next!=target)duplicate=true else {times=(if(target=="new")previous+next else previous.map {if(it==target)next else it}).sorted().joinToString(",");duplicate=false}
    },{timeTarget=null})}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.unsaved_title))},text={Text(stringResource(R.string.unsaved_body))},confirmButton={TextButton(onClick={confirm=false;save()}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=back){Text(stringResource(R.string.discard))}})
}
