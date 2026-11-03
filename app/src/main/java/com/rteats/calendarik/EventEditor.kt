package com.rteats.calendarik

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru-RU"))
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventEditor(
    original: CalendarEvent?,
    defaultDay: LocalDate,
    identity: Int,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, LocalTime, LocalTime, Int) -> Unit,
    onDelete: (CalendarEvent) -> Unit,
) {
    val originalDay = remember(original?.id) {
        original?.let {
            Instant.ofEpochMilli(it.startsAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    fun displayTime(epoch: Long): String =
        Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(timeFormat)

    var day by remember(original?.id) { mutableStateOf(originalDay ?: defaultDay) }
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var notes by remember(original?.id) { mutableStateOf(original?.notes.orEmpty()) }
    var start by remember(original?.id) { mutableStateOf(original?.let { displayTime(it.startsAtMillis) } ?: "12:00") }
    var end by remember(original?.id) { mutableStateOf(original?.let { displayTime(it.endsAtMillis) } ?: "13:00") }
    var authors by remember(original?.id) { mutableIntStateOf(original?.authorMask ?: identity) }
    var dateOpen by remember { mutableStateOf(false) }
    val begin = runCatching { LocalTime.parse(start, timeFormat) }.getOrNull()
    val finish = runCatching { LocalTime.parse(end, timeFormat) }.getOrNull()
    val valid = title.isNotBlank() && begin != null && finish != null && finish > begin
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (original == null) "Новое событие" else "Редактировать событие",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { dateOpen = true }) {
                Text("Дата: " + day.format(dateFormat))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = start, onValueChange = { start = it },
                    label = { Text("Начало, ЧЧ:ММ") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = end, onValueChange = { end = it },
                    label = { Text("Конец, ЧЧ:ММ") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Описание") }, minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Авторство", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Authors.SASHA to "Саша", Authors.NASTYA to "Настя", Authors.BOTH to "Общее").forEach { (value, label) ->
                    FilterChip(
                        selected = authors == value,
                        enabled = original == null && (value == identity || value == Authors.BOTH),
                        onClick = { authors = value },
                        label = { Text(label) },
                    )
                }
            }
            if (original != null) {
                Text(
                    "Авторство существующего события нельзя изменять.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!valid) {
                Text("Введите название и корректное время окончания после начала (ЧЧ:ММ).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (original != null) {
                    TextButton(onClick = { onDelete(original) }) { Text("Удалить") }
                } else {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                }
                Button(
                    enabled = valid,
                    onClick = { if (begin != null && finish != null) onSave(day, title, notes, begin, finish, authors) },
                ) { Text("Сохранить") }
            }
        }
    }

    if (dateOpen) {
        val datePicker = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    datePicker.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    dateOpen = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Отмена") } },
        ) { DatePicker(state = datePicker) }
    }
}
