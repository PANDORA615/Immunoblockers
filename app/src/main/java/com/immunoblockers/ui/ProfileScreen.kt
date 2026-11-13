package com.immunoblockers.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

suspend fun copyLocalImage(context:android.content.Context,uri:Uri):String=withContext(Dispatchers.IO) {
 val bitmap=context.contentResolver.openInputStream(uri)?.use {stream->
  val options=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
  android.graphics.BitmapFactory.decodeStream(stream,null,options)
  var sample=1
  while(maxOf(options.outWidth,options.outHeight)/sample>1024)sample*=2
  context.contentResolver.openInputStream(uri)?.use {android.graphics.BitmapFactory.decodeStream(it,null,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample})}
 }?:error("Изображение не удалось открыть")
 val target=File(context.filesDir,"images").apply {mkdirs()}.resolve("${java.util.UUID.randomUUID()}.jpg")
 target.outputStream().use {check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it))}
 bitmap.recycle();target.absolutePath
}
enum class LocalImageUse { AVATAR, WIDGET, WALLPAPER }
suspend fun storeLocalImage(vm:AppViewModel,path:String,avatar:Boolean) = storeLocalImage(vm,path,if(avatar)LocalImageUse.AVATAR else LocalImageUse.WIDGET)
suspend fun storeLocalImage(vm:AppViewModel,path:String,use:LocalImageUse) = withContext(Dispatchers.IO) {
 var previous=""
 vm.app.settings.update {settings->
  previous=when(use){LocalImageUse.AVATAR->settings.avatarPath;LocalImageUse.WIDGET->settings.widgetImage;LocalImageUse.WALLPAPER->settings.wallpaperPath}
  when(use){LocalImageUse.AVATAR->settings.copy(avatarPath=path);LocalImageUse.WIDGET->settings.copy(widgetImage=path);LocalImageUse.WALLPAPER->settings.copy(wallpaperPath=path)}
 }
 val current=vm.app.settings.flow.first()
 if(previous.isNotBlank()&&previous !in listOf(current.avatarPath,current.widgetImage,current.wallpaperPath)) {
  val file=File(previous)
  if(file.canonicalFile.parentFile==File(vm.app.filesDir,"images").canonicalFile)file.delete()
 }
}
@Composable fun LocalImagePreview(path:String,description:String,modifier:Modifier=Modifier) {
 val bitmap by produceState<android.graphics.Bitmap?>(null,path) {value=withContext(Dispatchers.IO){if(path.isBlank())null else android.graphics.BitmapFactory.decodeFile(path)}}
 bitmap?.let {Image(it.asImageBitmap(),description,modifier.clip(MaterialTheme.shapes.large),contentScale=ContentScale.Crop)}
}
@Composable fun ProfileAvatar(path:String,size:Int=32) {
 val bitmap by produceState<android.graphics.Bitmap?>(null,path) {value=withContext(Dispatchers.IO){if(path.isBlank())null else android.graphics.BitmapFactory.decodeFile(path)}}
 bitmap?.let {Image(it.asImageBitmap(),"Аватар",Modifier.size(size.dp).clip(CircleShape),contentScale=ContentScale.Crop)}?:Text("◉",Modifier.size(size.dp),style=MaterialTheme.typography.titleLarge)
}
@Composable fun ProfileScreen(vm:AppViewModel,back:()->Unit) {
 val settings by vm.settings.collectAsStateWithLifecycle()
 var nickname by rememberSaveable(settings.nickname){mutableStateOf(settings.nickname)}
 val scope=rememberCoroutineScope()
 var error by remember {mutableStateOf("")}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)scope.launch {
  try {val path=copyLocalImage(vm.app,uri);storeLocalImage(vm,path,true);error=""}catch(_:Exception){error="Не удалось открыть изображение"}
 }}
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  TextButton(onClick=back){Text("Назад")}
  Text("Мой профиль",style=MaterialTheme.typography.headlineMedium)
  ProfileAvatar(settings.avatarPath,80)
  AppTextField(nickname,{nickname=it.take(32)},label={Text("Ник · до 32 символов")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Button(onClick={vm.settings {it.copy(nickname=nickname.trim())}}){Text("Сохранить ник")}
  OutlinedButton(onClick={picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("Выбрать фото")}
  if(settings.avatarPath.isNotBlank())TextButton(onClick={scope.launch {storeLocalImage(vm,"",true)}}){Text("Удалить фото")}
  if(error.isNotEmpty())Text(error)
 }
}
