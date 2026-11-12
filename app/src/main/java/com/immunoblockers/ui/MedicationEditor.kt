@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.immunoblockers.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.immunoblockers.R
import com.immunoblockers.data.Medication
import java.time.*
import java.util.UUID

@Composable
fun MedicationEditor(existing:Medication?,back:()->Unit,vm:AppViewModel) {
    val context=LocalContext.current
    val units=stringArrayResource(R.array.units)
    val days=stringArrayResource(R.array.day_names)
    val id=rememberSaveable {existing?.id?:UUID.randomUUID().toString()}
    var name by rememberSaveable {mutableStateOf(existing?.name?:"")}
    var dose by rememberSaveable {mutableStateOf(existing?.dose?:"")}
    var unit by rememberSaveable {mutableStateOf(existing?.unit?:units[0])}
    var note by rememberSaveable {mutableStateOf(existing?.note?:"")}
    var start by rememberSaveable {mutableStateOf(existing?.start?:LocalDate.now().toString())}
    var end by rememberSaveable {mutableStateOf(existing?.end)}
    var selectedDays by rememberSaveable {mutableStateOf(existing?.days?:"1,2,3,4,5,6,7")}
    var times by rememberSaveable {mutableStateOf(existing?.times?:"09:00")}
    var tried by rememberSaveable {mutableStateOf(false)}
    var confirm by remember {mutableStateOf(false)}
    var duplicate by remember {mutableStateOf(false)}
    val initial=rememberSaveable {listOf(name,dose,unit,note,start,end?:"",selectedDays,times).joinToString("\u0001")}
    val dirty=initial!=listOf(name,dose,unit,note,start,end?:"",selectedDays,times).joinToString("\u0001")
    fun leave(){if(dirty)confirm=true else back()}
    BackHandler {leave()}
    val validName=name.isNotBlank()
    val validDose=dose.replace(',','.').toBigDecimalOrNull()?.let {it.signum()>0}==true
    val validDays=selectedDays.isNotEmpty()
    val validTimes=times.isNotEmpty()&&times.split(',').distinct().size==times.split(',').size
    val validDates=end==null||end!!>=start
    fun pickDate(value:String,done:(String)->Unit) {val d=LocalDate.parse(value);DatePickerDialog(context,{_,y,m,day->done(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()}
    fun save() {
        tried=true
        if(validName&&validDose&&validDays&&validTimes&&validDates) {
            val now=System.currentTimeMillis()
            vm.saveMedication(Medication(id,name.trim(),dose.replace(',','.'),unit,note.trim(),start,end,selectedDays,times,existing?.paused?:false,existing?.archived?:false,(existing?.version?:0)+1,existing?.createdAt?:now,now),back)
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title={Text(stringResource(if(existing==null)R.string.add_medication else R.string.edit_medication))},navigationIcon={IconButton(onClick={leave()}){Icon(Icons.Outlined.ArrowBack,stringResource(R.string.back))}})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.name))},isError=tried&&!validName,supportingText={if(tried&&!validName)Text(stringResource(R.string.required_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(dose,{dose=it},label={Text(stringResource(R.string.dose))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=tried&&!validDose,supportingText={if(tried&&!validDose)Text(stringResource(R.string.invalid_dose))},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text(stringResource(R.string.unit),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {units.forEach {u->FilterChip(unit==u,{unit=u},label={Text(u)})}}
            OutlinedTextField(note,{note=it},label={Text(stringResource(R.string.note))},modifier=Modifier.fillMaxWidth(),minLines=2)
            Text(stringResource(R.string.schedule),style=MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick={pickDate(start){start=it}},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.start_date)+": "+displayDate(start))}
            Row {Checkbox(end==null,{end=if(it)null else start});Text(stringResource(R.string.no_end),modifier=Modifier.padding(top=12.dp))}
            if(end!=null)OutlinedButton(onClick={pickDate(end!!){end=it}},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.end_date)+": "+displayDate(end!!))}
            if(tried&&!validDates)Text(stringResource(R.string.invalid_dates),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.weekdays),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { days.forEachIndexed {index,label ->
                val value=(index+1).toString()
                FilterChip(value in selectedDays.split(','),{
                    val set=selectedDays.split(',').filter {it.isNotBlank()}.toMutableSet()
                    if(!set.add(value))set.remove(value)
                    selectedDays=set.sorted().joinToString(",")
                },label={Text(label)})
            }}
            if(tried&&!validDays)Text(stringResource(R.string.invalid_days),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.daily_hint),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.times),style=MaterialTheme.typography.titleSmall)
            times.split(',').filter {it.isNotBlank()}.forEach {time ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    OutlinedButton(onClick={val t=LocalTime.parse(time);TimePickerDialog(context,{_,h,m->val new=LocalTime.of(h,m).toString();if(new!=time&&new in times.split(','))duplicate=true else {times=times.split(',').map {if(it==time)new else it}.sorted().joinToString(",");duplicate=false}},t.hour,t.minute,true).show()}){Text(time)}
                    IconButton(onClick={times=times.split(',').filter {it!=time}.joinToString(",")}){Icon(Icons.Outlined.DeleteOutline,stringResource(R.string.remove_time,time))}
                }
            }
            if(duplicate)Text(stringResource(R.string.time_conflict),color=MaterialTheme.colorScheme.error)
            if(tried&&!validTimes)Text(stringResource(R.string.invalid_times),color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->val t=LocalTime.of(h,m).toString();if(t in times.split(','))duplicate=true else {times=(times.split(',').filter {it.isNotBlank()}+t).sorted().joinToString(",");duplicate=false}},12,0,true).show()}){Icon(Icons.Outlined.Add,null);Text(stringResource(R.string.add_time))}
            Button(onClick={save()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(stringResource(R.string.save))}
            Spacer(Modifier.height(24.dp))
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.unsaved_title))},text={Text(stringResource(R.string.unsaved_body))},confirmButton={TextButton(onClick={confirm=false;save()}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=back){Text(stringResource(R.string.discard))}})
}
