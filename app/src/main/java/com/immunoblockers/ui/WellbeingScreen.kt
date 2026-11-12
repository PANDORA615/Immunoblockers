@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immunoblockers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import java.util.UUID

@Composable fun WellbeingScreen(vm:AppViewModel,groupId:String?=null) {
 val entries by vm.app.repository.wellbeing.collectAsStateWithLifecycle(emptyList())
 val scope=rememberCoroutineScope()
 var editId by rememberSaveable {mutableStateOf<String?>(null)}
 var at by rememberSaveable {mutableLongStateOf(0)}
 var mood by rememberSaveable {mutableStateOf("")}
 var text by rememberSaveable {mutableStateOf("")}
 var linkedGroup by rememberSaveable(groupId) {mutableStateOf(groupId)}
 var busy by remember {mutableStateOf(false)}
 var message by remember {mutableStateOf("")}
 val members by produceState<List<Occurrence>>(emptyList(),groupId,entries) {
  value=withContext(Dispatchers.IO) {groupId?.let {id->vm.app.database.wellbeing().members(id).mapNotNull {vm.app.repository.dao.occurrence(it)}.filter {it.state==Occurrence.TAKEN}}?:emptyList()}
 }
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("Как вы себя чувствуете?",style=MaterialTheme.typography.headlineSmall)
  if(members.isNotEmpty())Text(members.joinToString {"${it.name} · ${it.dose} ${it.unit}"})
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("Хорошо","Так себе","Плохо").forEach {label->FilterChip(mood==label,{mood=if(mood==label)"" else label},label={Text(label)})}}
  AppTextField(text,{text=it},label={Text("Личная заметка о самочувствии")},modifier=Modifier.fillMaxWidth(),minLines=3)
  Text("Записи хранятся только на этом устройстве.",style=MaterialTheme.typography.bodySmall)
  Button(enabled=!busy&&(mood.isNotBlank()||text.isNotBlank()),onClick={
   busy=true;message=""
   val entry=WellbeingEntry(editId?:UUID.randomUUID().toString(),if(editId==null)System.currentTimeMillis() else at,mood.ifBlank {null},text,linkedGroup)
   scope.launch {
    try {withContext(Dispatchers.IO){vm.app.repository.saveWellbeing(entry)};editId=null;mood="";text="";linkedGroup=null;message="Самочувствие сохранено";vm.refresh()}
    catch(_:Exception){message="Не удалось сохранить запись"}finally {busy=false}
   }
  }){Text(if(editId==null)"Сохранить самочувствие" else "Сохранить изменения")}
  if(editId!=null)TextButton(onClick={editId=null;mood="";text="";linkedGroup=groupId}){Text("Отмена редактирования")}
  if(message.isNotEmpty())Text(message)
  entries.forEach {entry->AppCard(Modifier.fillMaxWidth()) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text(displayInstant(entry.recordedAt),style=MaterialTheme.typography.labelMedium)
    entry.mood?.let {Text(it,style=MaterialTheme.typography.titleMedium)}
    if(entry.text.isNotBlank())Text(entry.text)
    Row {
     TextButton(onClick={editId=entry.id;at=entry.recordedAt;mood=entry.mood.orEmpty();text=entry.text;linkedGroup=entry.groupId;message=""}){Text("Изменить")}
     TextButton(enabled=!busy,onClick={busy=true;scope.launch {try {withContext(Dispatchers.IO){vm.app.repository.deleteWellbeing(entry.id)};if(editId==entry.id){editId=null;mood="";text=""};message="Запись удалена";vm.refresh()}catch(_:Exception){message="Не удалось удалить запись"}finally{busy=false}}}){Text("Удалить")}
    }
   }
  }}
 }
}
