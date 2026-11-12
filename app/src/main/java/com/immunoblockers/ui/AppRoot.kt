@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.immunoblockers.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.immunoblockers.R
import com.immunoblockers.data.*
import kotlinx.coroutines.*
import java.time.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun AppRoot(vm: AppViewModel, requestedDose: String?, consumeDose: () -> Unit, requestedScreen: String? = null, consumeScreen: () -> Unit = {}) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(Unit) { while(true) { delay(60_000); vm.refresh() } }
    val meds by vm.medications.collectAsStateWithLifecycle()
    val rows by vm.occurrences.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    LaunchedEffect(requestedScreen) {
        if(requestedScreen in listOf("today","history")) {
            nav.navigate(requestedScreen!!) { popUpTo(nav.graph.startDestinationId) {saveState=true};launchSingleTop=true;restoreState=true }
            consumeScreen()
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "today"
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedDose, rows) { if(requestedDose != null && rows.any { it.id == requestedDose }) { selected = requestedDose; consumeDose() } }
    LaunchedEffect(Unit) {
        for(event in vm.events) {
            when(event) {
                is UiEvent.Marked -> scope.launch {
                    snack.currentSnackbarData?.dismiss()
                    val result = withTimeoutOrNull(5000) { snack.showSnackbar(context.getString(R.string.mark_saved), context.getString(R.string.undo), duration=SnackbarDuration.Indefinite) }
                    if(result == SnackbarResult.ActionPerformed) vm.undo(event.before)
                }
                UiEvent.Snoozed -> scope.launch { snack.showSnackbar(context.getString(R.string.snooze_saved)) }
                UiEvent.Error -> scope.launch { snack.showSnackbar(context.getString(R.string.save_error)) }
            }
        }
    }
    val tabs = listOf(Triple("today", R.string.today, Icons.Outlined.Today), Triple("medications", R.string.medications, Icons.Outlined.Medication), Triple("history", R.string.history, Icons.Outlined.History), Triple("settings", R.string.settings, Icons.Outlined.Tune))
    val main = tabs.any { it.first == route }
    val duration = if(LocalReducedMotion.current) Motion.SHORT else Motion.SCREEN
    Scaffold(
        topBar={ if(main) CenterAlignedTopAppBar(title={Text(stringResource(R.string.app_name), style=MaterialTheme.typography.titleLarge)}) },
        snackbarHost={ SnackbarHost(snack) },
        bottomBar={ if(main) NavigationBar { tabs.forEach { (path, title, icon) -> NavigationBarItem(selected=route==path, onClick={nav.navigate(path) { popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true }}, icon={Icon(icon, null)}, label={Text(stringResource(title))}) } } },
        floatingActionButton={ if(route == "today" || route == "medications") ExtendedFloatingActionButton(modifier=Modifier.testTag("addMedication"),onClick={nav.navigate("edit/new")}, icon={Icon(Icons.Outlined.Add,null)}, text={Text(stringResource(R.string.add_medication))}) }
    ) { padding ->
        NavHost(nav, "today", modifier=Modifier.padding(padding), enterTransition={ fadeIn(tween(duration)) }, exitTransition={ fadeOut(tween(duration)) }) {
            composable("today") { TodayScreen(rows, meds, vm, {nav.navigate("edit/new")}, {selected=it.id}) }
            composable("medications") { MedicationsScreen(meds, {nav.navigate("detail/${it.id}")}) }
            composable("history") { JournalScreen(rows, meds, vm, onRow={selected=it.id}) }
            composable("settings") { SettingsScreen(settings, vm) }
            composable("edit/{id}") { back ->
                val id = back.arguments?.getString("id")
                if(id == "new" || meds.any { it.id==id }) MedicationEditor(meds.firstOrNull {it.id==id}, {nav.popBackStack()}, vm)
            }
            composable("detail/{id}") { back -> meds.firstOrNull {it.id==back.arguments?.getString("id")}?.let { med -> MedicationDetails(med, rows.filter {it.medicationId==med.id}, vm, {nav.popBackStack()}, {nav.navigate("edit/${med.id}")}, {selected=it.id}) } }
        }
    }
    rows.firstOrNull {it.id == selected}?.let { row -> DoseSheet(row, {selected=null}, vm) }
}

@Composable
fun DoseSheet(row: Occurrence, dismiss: () -> Unit, vm: AppViewModel) {
    ModalBottomSheet(onDismissRequest=dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=32.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(row.name, style=MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.details_dose,row.dose,row.unit), style=MaterialTheme.typography.titleMedium)
            Text("${row.date} · ${row.time}", color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stateText(row), style=MaterialTheme.typography.labelLarge)
            if(row.state == Occurrence.PENDING) {
                Button(onClick={vm.mark(row,Occurrence.TAKEN);dismiss()},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.Check,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.take))}
                Text(stringResource(R.string.snooze))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(5,10,15,30).forEach { minutes -> OutlinedButton(onClick={vm.snooze(row,minutes);dismiss()}) {Text(stringResource(R.string.minutes,minutes))} } }
                TextButton(onClick={vm.mark(row,Occurrence.SKIPPED);dismiss()}) {Text(stringResource(R.string.skip))}
            } else {
                Text(stringResource(R.string.correction_title),style=MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={vm.mark(row,Occurrence.TAKEN,true);dismiss()}){Text(stringResource(R.string.taken))}
                    TextButton(onClick={vm.mark(row,Occurrence.SKIPPED,true);dismiss()}){Text(stringResource(R.string.skip))}
                    TextButton(onClick={vm.mark(row,Occurrence.PENDING,true);dismiss()}){Text(stringResource(R.string.reset_mark))}
                }
            }
        }
    }
}
