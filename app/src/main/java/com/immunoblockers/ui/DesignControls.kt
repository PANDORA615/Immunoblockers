@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.immunoblockers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.immunoblockers.R
import com.immunoblockers.data.ThemeSettings
import java.time.*

val LocalAppearance = staticCompositionLocalOf { ThemeSettings() }

@Composable
fun AppTextField(
    value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false, isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, minLines: Int = 1
) {
    if (LocalAppearance.current.fieldStyle == "filled") {
        TextField(value, onValueChange, modifier=modifier, label=label, singleLine=singleLine,
            isError=isError, supportingText=supportingText, leadingIcon=leadingIcon,
            keyboardOptions=keyboardOptions, minLines=minLines, shape=MaterialTheme.shapes.large,
            textStyle=MaterialTheme.typography.bodyLarge,
            colors=TextFieldDefaults.colors(
                focusedContainerColor=MaterialTheme.colorScheme.secondaryContainer,
                unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerHigh))
    } else {
        OutlinedTextField(value, onValueChange, modifier=modifier, label=label, singleLine=singleLine,
            isError=isError, supportingText=supportingText, leadingIcon=leadingIcon,
            keyboardOptions=keyboardOptions, minLines=minLines, shape=MaterialTheme.shapes.large,
            textStyle=MaterialTheme.typography.bodyLarge,
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest))
    }
}

@Composable
fun AppDatePicker(value: String, select: (String) -> Unit, dismiss: () -> Unit) {
    val state=rememberDatePickerState(initialSelectedDateMillis=LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest=dismiss,
        confirmButton={TextButton(onClick={state.selectedDateMillis?.let {select(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())};dismiss()},enabled=state.selectedDateMillis!=null){Text(stringResource(R.string.save))}},
        dismissButton={TextButton(onClick=dismiss){Text(stringResource(R.string.cancel))}}) {
        DatePicker(state=state)
    }
}

@Composable
fun AppTimePicker(value: LocalTime, select: (LocalTime) -> Unit, dismiss: () -> Unit) {
    val state=rememberTimePickerState(value.hour,value.minute,is24Hour=true)
    var keyboard by rememberSaveable {mutableStateOf(false)}
    Dialog(onDismissRequest=dismiss) {
        Surface(shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.pick_time),style=MaterialTheme.typography.titleLarge)
                if(keyboard)TimeInput(state) else TimePicker(state)
                TextButton(onClick={keyboard=!keyboard}){Text(stringResource(if(keyboard)R.string.clock_time else R.string.enter_time))}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=dismiss){Text(stringResource(R.string.cancel))}
                    TextButton(onClick={select(LocalTime.of(state.hour,state.minute));dismiss()}){Text(stringResource(R.string.save))}
                }
            }
        }
    }
}

@Composable
fun FormSection(title: Int, description: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title),style=MaterialTheme.typography.titleLarge)
        if(description!=null)Text(stringResource(description),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
