package com.example.bookera.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bookera.data.local.LocalSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditor(settings: LocalSettings, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(settings.name.value) }
    var email by remember { mutableStateOf(settings.email.value) }
    var birthday by remember { mutableStateOf(runCatching { LocalDate.parse(settings.birthday.value) }.getOrNull()) }
    var calendar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Ваш профиль", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Почта · необязательно") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { calendar = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(birthday?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Добавить дату рождения", Modifier.weight(1f))
                if (birthday != null) IconButton(onClick = { birthday = null }) { Icon(Icons.Outlined.Close, "Убрать дату рождения") }
            }
            Text("Данные сохраняются на этом устройстве.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                runCatching { settings.updateProfile(name, birthday, email) }.onSuccess { onDismiss() }.onFailure { error = it.message }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Сохранить") }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (calendar) {
        val today = LocalDate.now()
        val picker = rememberDatePickerState(initialSelectedDateMillis = birthday?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = 1900..today.year, selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            })
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(onClick = {
                birthday = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                calendar = false
            }) { Text("Выбрать") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Отмена") } }) { DatePicker(picker) }
    }
}
