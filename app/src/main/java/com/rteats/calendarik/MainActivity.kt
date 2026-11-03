package com.rteats.calendarik

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarDatabase
import com.rteats.calendarik.data.CalendarEvent
import com.rteats.calendarik.data.HeartColor
import com.rteats.calendarik.data.SyncEditRepository
import com.rteats.calendarik.data.SyncConflict
import com.rteats.calendarik.data.createOrEditEvent
import com.rteats.calendarik.data.deleteEvent
import com.rteats.calendarik.ui.DayReactionsPanel
import com.rteats.calendarik.ui.PairingDialog
import com.rteats.calendarik.sync.PairManager
import com.rteats.calendarik.sync.CloudSync
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rteats.calendarik.update.GithubUpdater
import com.rteats.calendarik.update.UpdateManifest
import com.rteats.calendarik.widget.refreshWidgets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val lavender = Color(0xFF7959C4)
private val violetBackground = Color(0xFFF7F4FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val light = lightColorScheme(
                primary = LavenderUi.violet,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE9DFFF),
                onPrimaryContainer = Color(0xFF2F175F),
                background = Color(0xFFE5E2FC),
                onBackground = Color(0xFF292345),
                surface = Color(0xFFFFFBFF),
                onSurface = LavenderUi.ink,
                surfaceVariant = Color(0xFFEEE8FC),
                secondaryContainer = Color(0xFFE7E5FF),
            )
            val dark = darkColorScheme(
                primary = Color(0xFFC9B5FF),
                onPrimary = Color(0xFF39206E),
                background = Color(0xFF171423),
                surface = Color(0xFF242031),
                surfaceVariant = Color(0xFF383146),
            )
            // This illustration-led surface uses a consistent light pastel palette.
            // Dark system colors would make labels unreadable on the translucent cards.
            MaterialTheme(colorScheme = light) {
                LavenderBackdrop { CalendarikScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarikScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { CalendarDatabase.get(context) }
    val editRepository = remember { SyncEditRepository(db) }
    val pairManager = remember { PairManager(context) }
    val cloudSync = remember { CloudSync(context) }
    val prefs = remember { context.getSharedPreferences("calendarik_preferences", 0) }

    var paired by remember { mutableStateOf(pairManager.current()) }
    var accountUid by remember { mutableStateOf(pairManager.auth.currentUser?.uid) }
    var pairingOpen by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("") }
    var conflicts by remember { mutableStateOf(emptyList<SyncConflict>()) }
    var conflictsOpen by remember { mutableStateOf(false) }

    var identity by remember {
        mutableIntStateOf(
            if (prefs.getInt("identity", Authors.SASHA) == Authors.NASTYA) Authors.NASTYA
            else Authors.SASHA
        )
    }
    var profileNotSelected by remember { mutableStateOf(!prefs.contains("identity") && accountUid == null && paired == null) }
    val activeIdentity = paired?.role ?: identity
    // Authentication is the source of identity once a pair is established.
    // Keep the local profile selector exclusively for offline/demo use.
    DisposableEffect(pairManager) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            accountUid = auth.currentUser?.uid
            paired = pairManager.current()
        }
        pairManager.auth.addAuthStateListener(listener)
        onDispose { pairManager.auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(paired?.pairId, paired?.peerUid) {
        if (paired?.active == true) {
            CloudSync.schedulePeriodic(context)
            runCatching { cloudSync.sync() }
                .onSuccess {
                    conflicts = db.sync().conflicts()
                    syncStatus = "Синхронизировано"
                }
                .onFailure { syncStatus = "Ошибка синхронизации: " + (it.localizedMessage ?: "нет соединения") }
        }
    }
    DisposableEffect(paired?.pairId, paired?.peerUid) {
        val current = paired
        val registration = if (current?.active == true) {
            FirebaseFirestore.getInstance()
                .collection("pairs").document(current.pairId)
                .collection("snapshots").document(current.peerUid)
                .addSnapshotListener { snapshot, failure ->
                    if (failure != null) {
                        syncStatus = "Ошибка соединения: " + (failure.localizedMessage ?: "нет доступа")
                    } else if (snapshot?.exists() == true) {
                        scope.launch {
                            runCatching { cloudSync.sync() }
                                .onSuccess {
                                    conflicts = db.sync().conflicts()
                                    syncStatus = "Синхронизировано"
                                }
                                .onFailure { syncStatus = "Ошибка синхронизации: " + (it.localizedMessage ?: "сеть") }
                        }
                    }
                }
        } else null
        onDispose { registration?.remove() }
    }
    fun syncNow() {
        if (paired?.active != true) return
        CloudSync.requestSoon(context)
        scope.launch {
            runCatching { cloudSync.sync() }
                .onSuccess {
                    conflicts = db.sync().conflicts()
                    syncStatus = "Синхронизировано"
                }
                .onFailure { syncStatus = "Ошибка синхронизации: " + (it.localizedMessage ?: "нет сети") }
        }
    }

    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selectedDay = remember(dateText) { LocalDate.parse(dateText) }
    var view by rememberSaveable { mutableStateOf(CalendarView.MONTH) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var modeMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var viewing by remember { mutableStateOf<CalendarEvent?>(null) }
    var deleteCandidate by remember { mutableStateOf<CalendarEvent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var updateOpen by remember { mutableStateOf(false) }
    var updateText by remember { mutableStateOf("") }
    var updateAvailable by remember { mutableStateOf<UpdateManifest?>(null) }

    val window = remember(selectedDay, view) { dateWindow(selectedDay, view) }
    val eventsFlow = remember(window) { db.events().observeRange(window.startMillis, window.endMillis) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    // The day strip displays hearts for all seven visible dates, not only the selected day.
    val reactionWindow = remember(window, selectedDay, view) {
        if (view == CalendarView.DAY) {
            val monday = selectedDay.minusDays((selectedDay.dayOfWeek.value - 1).toLong())
            DateWindow(monday, monday.plusDays(7))
        } else window
    }
    val reactionsFlow = remember(reactionWindow) {
        db.reactions().observeBetween(
            reactionWindow.start.toEpochDay(),
            reactionWindow.endExclusive.toEpochDay(),
        )
    }
    val reactions by reactionsFlow.collectAsState(initial = emptyList())
    val searchFlow = remember(searchOpen, searchQuery) {
        if (searchOpen && searchQuery.isNotBlank()) db.events().observeSearch(searchQuery.trim())
        else flowOf(emptyList())
    }
    val searchEvents by searchFlow.collectAsState(initial = emptyList())
    val visibleEvents = remember(events, filter) { events.filter { matchesFilter(it, filter) } }

    fun selectDate(date: LocalDate) { dateText = date.toString() }
    fun showEvent(event: CalendarEvent) { viewing = event }
    fun addEvent() { editing = null; editorOpen = true }
    fun changeIdentity(newIdentity: Int) {
        if (paired != null) return
        identity = newIdentity
        prefs.edit().putInt("identity", newIdentity).apply()
    }
    fun checkUpdate() {
        settingsOpen = false
        updateAvailable = null
        updateText = "Проверяю обновление…"
        updateOpen = true
        scope.launch {
            runCatching { GithubUpdater.check() }
                .onSuccess { latest ->
                    if (latest.versionCode > BuildConfig.VERSION_CODE) {
                        updateAvailable = latest
                        updateText = "Доступна версия " + latest.versionName
                    } else {
                        updateText = "Установлена актуальная версия (" + BuildConfig.VERSION_NAME + ")"
                    }
                }.onFailure { updateText = "Ошибка проверки: " + (it.message ?: "нет соединения") }
        }
    }
    fun addHeart(color: HeartColor) {
        scope.launch {
            runCatching {
                editRepository.heart(selectedDay.toEpochDay(), activeIdentity, color)
                refreshWidgets(context)
                syncNow()
            }.onFailure { error = it.message ?: "Не удалось сохранить сердечко" }
        }
    }
    fun setMood(mood: String?) {
        scope.launch {
            runCatching {
                editRepository.mood(selectedDay.toEpochDay(), activeIdentity, mood)
                refreshWidgets(context)
                syncNow()
            }.onFailure { error = it.message ?: "Не удалось сохранить настроение" }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Настройки и профиль")
                    }
                },
                title = {
                    Column {
                        Text(view.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            dateTitle(selectedDay, view),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = .93f),
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) {
                        Icon(
                            if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchOpen) "Закрыть поиск" else "Поиск",
                        )
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Выбрать дату")
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Дополнительно")
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Сегодня") },
                                onClick = { moreMenu = false; selectDate(LocalDate.now()) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (accountUid == null) "Вход и синхронизация" else "Аккаунт и синхронизация") },
                                onClick = { moreMenu = false; pairingOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = { moreMenu = false; settingsOpen = true },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!searchOpen) {
                LavenderBottomNavigation(view = view, onChange = { view = it })
            }
        },
        floatingActionButton = {
            if (!searchOpen) {
                FloatingActionButton(
                    onClick = ::addEvent,
                    containerColor = LavenderUi.violet,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Создать событие")
                }
            }
        },
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            LavenderPanel(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(top = 7.dp)) {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Поиск по событиям") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        SearchResults(
                            events = searchEvents.filter { matchesFilter(it, filter) },
                            query = searchQuery,
                            onOpen = ::showEvent,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                when (view) {
                                    CalendarView.DAY -> "План на день"
                                    CalendarView.WEEK -> "Наша неделя"
                                    CalendarView.MONTH -> "Наш календарь"
                                    CalendarView.AGENDA -> "Лента событий"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LavenderUi.ink,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    selectDate(when (view) {
                                        CalendarView.MONTH -> selectedDay.minusMonths(1)
                                        CalendarView.WEEK -> selectedDay.minusWeeks(1)
                                        CalendarView.DAY -> selectedDay.minusDays(1)
                                        CalendarView.AGENDA -> selectedDay.minusDays(7)
                                    })
                                }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                                TextButton(onClick = { showDatePicker = true }) {
                                    Text(
                                        selectedDay.dayOfMonth.toString() + " " +
                                            selectedDay.month.getDisplayName(
                                                java.time.format.TextStyle.SHORT_STANDALONE,
                                                Locale.forLanguageTag("ru-RU"),
                                            ),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                TextButton(onClick = {
                                    selectDate(when (view) {
                                        CalendarView.MONTH -> selectedDay.plusMonths(1)
                                        CalendarView.WEEK -> selectedDay.plusWeeks(1)
                                        CalendarView.DAY -> selectedDay.plusDays(1)
                                        CalendarView.AGENDA -> selectedDay.plusDays(7)
                                    })
                                }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            listOf("Все", "Саша", "Настя", "Общие").forEachIndexed { index, title ->
                                FilterChip(
                                    selected = filter == index,
                                    onClick = { filter = index },
                                    label = { Text(title) },
                                    leadingIcon = if (index == 0) null else {
                                        { Box(Modifier.width(9.dp).height(9.dp).background(authorColor(index), CircleShape)) }
                                    },
                                )
                            }
                        }
                        HorizontalDivider(color = LavenderUi.line.copy(alpha = .5f))
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp)) {
                            when (view) {
                                CalendarView.DAY -> CalendarDayView(
                                    selectedDay, visibleEvents, reactions, activeIdentity,
                                    ::selectDate, ::showEvent, ::addHeart, ::setMood,
                                )
                                CalendarView.WEEK -> CalendarWeekView(
                                    selectedDay, visibleEvents, ::selectDate, ::showEvent,
                                )
                                CalendarView.MONTH -> CalendarMonthView(
                                    selectedDay, visibleEvents, reactions, activeIdentity,
                                    ::selectDate, ::showEvent, ::addHeart, ::setMood,
                                )
                                CalendarView.AGENDA -> CalendarAgendaView(
                                    selectedDay, visibleEvents, ::selectDate, ::showEvent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (profileNotSelected) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Кто пользуется приложением?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Выберите локальный профиль или подключите аккаунт для синхронизации с другим телефоном.")
                    TextButton(onClick = { profileNotSelected = false; pairingOpen = true }) {
                        Text("Войти или зарегистрироваться")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { changeIdentity(Authors.SASHA); profileNotSelected = false }) {
                    Text("Я Саша")
                }
            },
            dismissButton = {
                TextButton(onClick = { changeIdentity(Authors.NASTYA); profileNotSelected = false }) {
                    Text("Я Настя")
                }
            },
        )
    }

    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selectedDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { selectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) { DatePicker(state = picker) }
    }

    if (viewing != null) {
        val event = viewing!!
        val canEdit = Authors.hasPermission(event.authorMask, activeIdentity)
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(event.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatEventDate(event))
                    Text(Authors.label(event.authorMask), color = MaterialTheme.colorScheme.primary)
                    if (event.notes.isNotBlank()) Text(event.notes)
                    if (!canEdit) Text("Только просмотр: это личное событие другого человека.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                if (canEdit) {
                    Button(onClick = { viewing = null; editing = event; editorOpen = true }) { Text("Редактировать") }
                } else {
                    TextButton(onClick = { viewing = null }) { Text("Закрыть") }
                }
            },
            dismissButton = {
                if (canEdit) TextButton(onClick = { viewing = null }) { Text("Закрыть") }
            },
        )
    }

    if (editorOpen) {
        EventEditor(
            original = editing,
            defaultDay = selectedDay,
            identity = activeIdentity,
            onDismiss = { editorOpen = false },
            onSave = { day, title, notes, begin, finish, authors ->
                runCatching {
                    createOrEditEvent(editing, activeIdentity, title, notes, day, begin, finish, authors)
                }.onSuccess { record ->
                    scope.launch {
                        runCatching {
                            editRepository.save(record)
                            refreshWidgets(context)
                            syncNow()
                        }.onSuccess {
                            editorOpen = false
                            selectDate(day)
                        }.onFailure { error = it.message ?: "Не удалось сохранить событие" }
                    }
                }.onFailure { error = it.message ?: "Проверьте данные события" }
            },
            onDelete = { old -> deleteCandidate = old },
        )
    }

    if (deleteCandidate != null) {
        val old = deleteCandidate!!
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить событие?") },
            text = { Text(old.title) },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    scope.launch {
                        runCatching {
                            editRepository.save(deleteEvent(old, activeIdentity))
                            refreshWidgets(context)
                            syncNow()
                        }.onSuccess { editorOpen = false }
                            .onFailure { error = it.message ?: "Не удалось удалить событие" }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") } },
        )
    }

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Саша и Настя", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Один календарь для двоих. Личные события изменяет только их автор, общие — оба.", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("Аккаунт и синхронизация", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        paired?.active == true -> "Пара подключена · " + Authors.label(activeIdentity)
                        paired != null -> "Пара создана, ожидается подтверждение второго телефона"
                        accountUid != null -> "Вход выполнен, пару ещё нужно создать"
                        else -> "Не подключено. События сохраняются только на телефоне"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { settingsOpen = false; pairingOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (accountUid == null) "Войти или зарегистрироваться" else "Управление аккаунтом и парой")
                }
                if (paired?.active == true) {
                    TextButton(onClick = { syncNow() }) { Text("Синхронизировать сейчас") }
                    if (syncStatus.isNotBlank()) {
                        Text(syncStatus, style = MaterialTheme.typography.bodySmall)
                    }
                    if (conflicts.isNotEmpty()) {
                        Button(onClick = { settingsOpen = false; conflictsOpen = true }) {
                            Text("Разрешить конфликты (" + conflicts.size + ")")
                        }
                    }
                }
                HorizontalDivider()
                Text("Локальный профиль для тестирования", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = activeIdentity == Authors.SASHA, enabled = paired == null, onClick = { changeIdentity(Authors.SASHA) }, label = { Text("Я Саша") })
                    FilterChip(selected = activeIdentity == Authors.NASTYA, enabled = paired == null, onClick = { changeIdentity(Authors.NASTYA) }, label = { Text("Я Настя") })
                }
                Text(
                    if (paired != null) "Профиль закреплён за связанной учётной записью." else "Локальный выбор не подтверждает личность. Для синхронизации используйте вход через Firebase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Button(onClick = ::checkUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить обновление")
                }
                Text("Версия " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (pairingOpen) {
        PairingDialog(
            manager = pairManager,
            onClose = { pairingOpen = false },
            onPairChanged = { updated ->
                paired = updated
                accountUid = pairManager.auth.currentUser?.uid
                if (updated != null) {
                    // Preserve role across the next launch; avoid demo profile onboarding after pairing.
                    prefs.edit().putInt("identity", updated.role).apply()
                    profileNotSelected = false
                }
                if (updated?.active == true) syncNow()
            },
        )
    }
    if (conflictsOpen && conflicts.isNotEmpty()) {
        val currentConflict = conflicts.first()
        val remote = runCatching {
            com.rteats.calendarik.sync.SnapshotCodec.eventFromJson(
                org.json.JSONObject(currentConflict.remoteJson)
            )
        }.getOrNull()
        AlertDialog(
            onDismissRequest = { conflictsOpen = false },
            title = { Text("Конфликт общего события") },
            text = {
                Text("Оба телефона изменили событие «" + (remote?.title ?: currentConflict.eventId) +
                    "». Выберите версию, которую сохранить.")
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching { cloudSync.resolve(currentConflict.eventId, true) }
                            .onSuccess {
                                conflicts = db.sync().conflicts()
                                conflictsOpen = conflicts.isNotEmpty()
                                syncNow()
                            }.onFailure { error = it.localizedMessage ?: "Ошибка разрешения конфликта" }
                    }
                }) { Text("Принять вторую") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { cloudSync.resolve(currentConflict.eventId, false) }
                            .onSuccess {
                                conflicts = db.sync().conflicts()
                                conflictsOpen = conflicts.isNotEmpty()
                                syncNow()
                            }.onFailure { error = it.localizedMessage ?: "Ошибка разрешения конфликта" }
                    }
                }) { Text("Оставить мою") }
            },
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Ошибка") },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("ОК") } },
        )
    }

    if (updateOpen) {
        AlertDialog(
            onDismissRequest = { updateOpen = false },
            title = { Text("Обновление Календарика") },
            text = { Text(updateText) },
            confirmButton = {
                val latest = updateAvailable
                if (latest == null) {
                    TextButton(onClick = { updateOpen = false }) { Text("Закрыть") }
                } else {
                    Button(onClick = {
                        updateAvailable = null
                        updateText = "Скачиваю и проверяю APK…"
                        scope.launch {
                            runCatching { GithubUpdater.downloadAndCheck(context, latest) }
                                .onSuccess { apk ->
                                    updateText = "Открываю установщик…"
                                    context.startActivity(GithubUpdater.installIntent(context, apk))
                                    updateOpen = false
                                }.onFailure { updateText = "Ошибка загрузки: " + (it.message ?: "неизвестно") }
                        }
                    }) { Text("Установить") }
                }
            },
        )
    }
}

private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru-RU"))
private val dayFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru-RU"))
private val shortTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun dateTitle(day: LocalDate, view: CalendarView): String {
    val raw = if (view == CalendarView.MONTH) day.format(monthFormat)
        else if (view == CalendarView.WEEK) {
            val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
            monday.format(dayFormat) + " – " + monday.plusDays(6).format(dayFormat)
        } else day.format(dayFormat)
    return raw.replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru-RU")) }
}

private fun formatEventDate(event: CalendarEvent): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(zone)
    val end = Instant.ofEpochMilli(event.endsAtMillis).atZone(zone)
    return start.toLocalDate().format(dayFormat) + ", " + start.format(shortTimeFormat) + "–" + end.format(shortTimeFormat)
}

private fun matchesFilter(event: CalendarEvent, filter: Int): Boolean = when (filter) {
    1 -> event.authorMask == Authors.SASHA
    2 -> event.authorMask == Authors.NASTYA
    3 -> event.authorMask == Authors.BOTH
    else -> true
}

private fun authorColor(filter: Int): Color = when (filter) {
    1 -> Color(0xFF8096DC)
    2 -> Color(0xFFD48FCB)
    else -> Color(0xFF9876D7)
}

@Composable
private fun SearchResults(
    events: List<CalendarEvent>,
    query: String,
    onOpen: (CalendarEvent) -> Unit,
) {
    if (query.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Введите название или описание события", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("События не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(events.size, key = { events[it].id }) { index ->
                EventCard(events[index], onClick = { onOpen(events[index]) }, showDate = true)
            }
        }
    }
}
