package com.rteats.calendarik

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarDatabase
import com.rteats.calendarik.data.CalendarEvent
import com.rteats.calendarik.data.createOrEditEvent
import com.rteats.calendarik.data.deleteEvent
import com.rteats.calendarik.ui.DayReactionsPanel
import com.rteats.calendarik.update.GithubUpdater
import com.rteats.calendarik.update.UpdateManifest
import com.rteats.calendarik.widget.refreshWidgets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) { CalendarikScreen() }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru-RU"))
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun range(day: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    return day.atStartOfDay(zone).toInstant().toEpochMilli() to
        day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

@Composable
private fun CalendarikScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { CalendarDatabase.get(context) }
    val preferences = remember { context.getSharedPreferences("calendarik_preferences", 0) }
    var identity by remember {
        mutableIntStateOf(
            if (preferences.getInt("identity", Authors.SASHA) == Authors.NASTYA)
                Authors.NASTYA else Authors.SASHA
        )
    }
    var day by remember { mutableStateOf(LocalDate.now()) }
    val limits = remember(day) { range(day) }
    val stream = remember(limits) { db.events().observeRange(limits.first, limits.second) }
    val events by stream.collectAsState(initial = emptyList())
    val reactionStream = remember(day) { db.reactions().observeDay(day.toEpochDay()) }
    val reactions by reactionStream.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var availableUpdate by remember { mutableStateOf<UpdateManifest?>(null) }
    var savingError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Календарик", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    updateMessage = "Проверяю новую версию..."
                    availableUpdate = null
                    updateDialog = true
                    scope.launch {
                        runCatching { GithubUpdater.check() }
                            .onSuccess { latest ->
                                if (latest.versionCode > BuildConfig.VERSION_CODE) {
                                    availableUpdate = latest
                                    updateMessage = "Доступна версия " + latest.versionName
                                } else {
                                    updateMessage = "Установлена актуальная версия (" + BuildConfig.VERSION_NAME + ")"
                                }
                            }.onFailure { updateMessage = "Ошибка проверки: " + (it.message ?: "нет соединения") }
                    }
                }) { Text("Обновить") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Мой профиль:", modifier = Modifier.padding(top = 12.dp))
                listOf(Authors.SASHA, Authors.NASTYA).forEach { author ->
                    FilterChip(
                        selected = identity == author,
                        onClick = {
                            identity = author
                            preferences.edit().putInt("identity", author).apply()
                        },
                        label = { Text(Authors.label(author)) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { day = day.minusDays(1) }) { Text("‹") }
                TextButton(onClick = { day = LocalDate.now() }) {
                    Text(day.format(dayFormat).replaceFirstChar { it.uppercaseChar() })
                }
                TextButton(onClick = { day = day.plusDays(1) }) { Text("›") }
            }
            if (events.isEmpty()) {
                Text("Нет событий на выбранный день", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    val canEdit = Authors.hasPermission(event.authorMask, identity)
                    val bg = when (event.authorMask) {
                        Authors.SASHA -> Color(0x1F3978DB)
                        Authors.NASTYA -> Color(0x1FAC66C1)
                        else -> Color(0x1F2D9C74)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = canEdit) {
                            editing = event
                            showEditor = true
                        },
                        colors = CardDefaults.cardColors(containerColor = bg),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                Instant.ofEpochMilli(event.startsAtMillis).atZone(ZoneId.systemDefault()).format(timeFormat) +
                                    "–" + Instant.ofEpochMilli(event.endsAtMillis).atZone(ZoneId.systemDefault()).format(timeFormat),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            if (event.notes.isNotBlank()) Text(event.notes, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                Authors.label(event.authorMask) + (if (canEdit) " · редактировать" else " · только просмотр"),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            DayReactionsPanel(
                reactions = reactions,
                actingAs = identity,
                onHeart = { color ->
                    scope.launch {
                        runCatching {
                            db.reactions().addHeart(day.toEpochDay(), identity, color)
                            refreshWidgets(context)
                        }.onFailure { savingError = it.message ?: "Не удалось добавить сердечко" }
                    }
                },
                onMood = { emoji ->
                    scope.launch {
                        runCatching {
                            db.reactions().setMood(day.toEpochDay(), identity, emoji)
                            refreshWidgets(context)
                        }.onFailure { savingError = it.message ?: "Не удалось сохранить эмоцию" }
                    }
                },
            )
            Text(
                "Сейчас данные локальные. Firebase подключён как SDK; обмен между устройствами пока выключен.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    if (showEditor) {
        EventEditor(
            original = editing,
            day = day,
            identity = identity,
            onDismiss = { showEditor = false },
            onSave = { title, note, begin, finish, authors ->
                runCatching {
                    createOrEditEvent(editing, identity, title, note, day, begin, finish, authors)
                }.onSuccess { record ->
                    showEditor = false
                    scope.launch {
                        db.events().save(record)
                        refreshWidgets(context)
                    }
                }.onFailure {
                    savingError = it.message ?: "Не удалось сохранить событие"
                }
            },
            onDelete = {
                val old = editing
                showEditor = false
                if (old != null) scope.launch {
                    db.events().save(deleteEvent(old, identity))
                    refreshWidgets(context)
                }
            },
        )
    }
    if (savingError != null) {
        AlertDialog(
            onDismissRequest = { savingError = null },
            title = { Text("Ошибка") },
            text = { Text(savingError.orEmpty()) },
            confirmButton = { TextButton(onClick = { savingError = null }) { Text("ОК") } },
        )
    }
    if (updateDialog) {
        AlertDialog(
            onDismissRequest = { updateDialog = false },
            title = { Text("Обновление Календарика") },
            text = { Text(updateMessage) },
            confirmButton = {
                val release = availableUpdate
                if (release != null) {
                    Button(onClick = {
                        availableUpdate = null
                        updateMessage = "Скачиваю и проверяю APK..."
                        scope.launch {
                            runCatching { GithubUpdater.downloadAndCheck(context, release) }
                                .onSuccess { apk ->
                                    updateMessage = "Открываю установщик..."
                                    context.startActivity(GithubUpdater.installIntent(context, apk))
                                    updateDialog = false
                                }.onFailure {
                                    updateMessage = "Ошибка загрузки: " + (it.message ?: "неизвестно")
                                }
                        }
                    }) { Text("Установить") }
                } else {
                    TextButton(onClick = { updateDialog = false }) { Text("Закрыть") }
                }
            },
        )
    }
}

@Composable
private fun EventEditor(
    original: CalendarEvent?,
    day: LocalDate,
    identity: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalTime, LocalTime, Int) -> Unit,
    onDelete: () -> Unit,
) {
    fun timeOf(milliseconds: Long): String =
        Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault()).format(timeFormat)

    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var notes by remember(original?.id) { mutableStateOf(original?.notes.orEmpty()) }
    var start by remember(original?.id) { mutableStateOf(original?.let { timeOf(it.startsAtMillis) } ?: "12:00") }
    var end by remember(original?.id) { mutableStateOf(original?.let { timeOf(it.endsAtMillis) } ?: "13:00") }
    var authors by remember(original?.id) { mutableIntStateOf(original?.authorMask ?: identity) }
    val begin = runCatching { LocalTime.parse(start, timeFormat) }.getOrNull()
    val finish = runCatching { LocalTime.parse(end, timeFormat) }.getOrNull()
    val valid = title.isNotBlank() && begin != null && finish != null && finish > begin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Новое событие" else "Изменить событие") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(day.format(dayFormat))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start, onValueChange = { start = it }, label = { Text("С") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = end, onValueChange = { end = it }, label = { Text("До") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Описание") })
                if (original == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(identity, Authors.BOTH).distinct().forEach { value ->
                            FilterChip(
                                selected = authors == value,
                                onClick = { authors = value },
                                label = { Text(if (value == Authors.BOTH) "Общее" else "Личное") },
                            )
                        }
                    }
                }
                if (!valid) Text("Проверьте название и время в формате ЧЧ:ММ", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            Row {
                if (original != null) {
                    TextButton(onClick = onDelete) { Text("Удалить") }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { if (begin != null && finish != null) onSave(title, notes, begin, finish, authors) },
            ) { Text("Сохранить") }
        },
    )
}

