package com.rteats.calendarik

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarEvent
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.HeartColor
import com.rteats.calendarik.ui.DayReactionsPanel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class CalendarView(val label: String) {
    DAY("День"), WEEK("Неделя"), MONTH("Месяц"), AGENDA("Список")
}

internal data class DateWindow(val start: LocalDate, val endExclusive: LocalDate) {
    val startMillis: Long
        get() = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val endMillis: Long
        get() = endExclusive.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

internal fun dateWindow(day: LocalDate, view: CalendarView): DateWindow = when (view) {
    CalendarView.DAY -> DateWindow(day, day.plusDays(1))
    CalendarView.WEEK -> {
        val start = day.minusDays((day.dayOfWeek.value - 1).toLong())
        DateWindow(start, start.plusDays(7))
    }
    CalendarView.MONTH -> {
        val first = day.withDayOfMonth(1)
        val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val last = day.withDayOfMonth(day.lengthOfMonth())
        val end = last.plusDays((7 - last.dayOfWeek.value).toLong()).plusDays(1)
        DateWindow(start, end)
    }
    CalendarView.AGENDA -> DateWindow(day, day.plusDays(31))
}

private val dateHeading = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru-RU"))
private val shortDay = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("ru-RU"))
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val lilac = Color(0xFFAC87E8)
private val blue = Color(0xFF89B6F6)
private val pink = Color(0xFFE2A2E4)

private fun eventDay(event: CalendarEvent): LocalDate =
    Instant.ofEpochMilli(event.startsAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun eventTime(event: CalendarEvent): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(event.startsAtMillis).atZone(zone).format(timeFormat) +
        "–" + Instant.ofEpochMilli(event.endsAtMillis).atZone(zone).format(timeFormat)
}

private fun eventStartMinute(event: CalendarEvent): Int {
    val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return start.hour * 60 + start.minute
}

private fun eventEndMinute(event: CalendarEvent): Int {
    val end = Instant.ofEpochMilli(event.endsAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return end.hour * 60 + end.minute
}

private fun eventColor(authorMask: Int): Color = when (authorMask) {
    Authors.SASHA -> blue
    Authors.NASTYA -> pink
    else -> lilac
}

@Composable
internal fun EventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val accent = eventColor(event.authorMask)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = .27f), accent.copy(alpha = .15f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = .75f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(43.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = .50f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.EventNote, contentDescription = null, tint = LavenderUi.violet)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LavenderUi.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (showDate) eventDay(event).format(shortDay) + " · " else "") + eventTime(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = LavenderUi.ink,
                )
                if (event.notes.isNotBlank()) {
                    Text(
                        event.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = LavenderUi.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    Authors.label(event.authorMask),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .47f))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = LavenderUi.ink,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            when (title) {
                "Утро" -> "☼"
                "Вечер" -> "☾"
                else -> "✧"
            },
            color = LavenderUi.violet,
            fontSize = 23.sp,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LavenderUi.ink,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = LavenderUi.violet.copy(alpha = .34f),
        )
    }
}

@Composable
internal fun CalendarDayView(
    selectedDay: LocalDate,
    events: List<CalendarEvent>,
    reactions: List<DailyReaction>,
    identity: Int,
    onDate: (LocalDate) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
    onHeart: (HeartColor?) -> Unit,
    onMood: (String?) -> Unit,
) {
    val dayEvents = events.filter { eventDay(it) == selectedDay }.sortedBy { it.startsAtMillis }
    val groups = listOf("Утро" to (0 until 12), "День" to (12 until 18), "Вечер" to (18 until 24))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 11.dp, end = 11.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            LavenderWeekStrip(selectedDay, reactions, onDate)
        }
        if (selectedDay == LocalDate.now()) {
            item {
                Text(
                    "Сегодня",
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(LavenderUi.lavender.copy(alpha = .55f))
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = LavenderUi.violet,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        for ((heading, hours) in groups) {
            val part = dayEvents.filter { eventStartMinute(it) / 60 in hours }
            if (part.isNotEmpty()) {
                item { SectionTitle(heading) }
                items(part, key = { it.id }) { event ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.width(51.dp).padding(top = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                Instant.ofEpochMilli(event.startsAtMillis)
                                    .atZone(ZoneId.systemDefault()).format(timeFormat),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = LavenderUi.ink,
                            )
                            Box(
                                Modifier.size(9.dp)
                                    .clip(CircleShape)
                                    .background(LavenderUi.violet.copy(alpha = .70f)),
                            )
                        }
                        EventCard(
                            event = event,
                            onClick = { onOpen(event) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (dayEvents.isEmpty()) {
            item {
                Text(
                    "Пока никаких планов. Добавьте ваше первое событие на этот день.",
                    color = LavenderUi.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
        item {
            SectionTitle("Наш день")
            DayReactionsPanel(
                reactions = reactions.filter { it.dateEpochDay == selectedDay.toEpochDay() },
                actingAs = identity,
                onHeart = onHeart,
                onMood = onMood,
            )
        }
    }
}

@Composable
internal fun CalendarWeekView(
    selectedDay: LocalDate,
    events: List<CalendarEvent>,
    onDate: (LocalDate) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
) {
    val monday = selectedDay.minusDays((selectedDay.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val cellWidth = 114.dp
    val hourHeight = 62.dp
    val gridHeight = hourHeight * 24
    val scroll = rememberScrollState()

    val initialScroll = with(LocalDensity.current) { (hourHeight * 7).roundToPx() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState(initial = initialScroll))) {
        Text(
            "Листайте по горизонтали, чтобы увидеть всю неделю",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.horizontalScroll(scroll).padding(start = 8.dp, end = 16.dp)) {
            Column(modifier = Modifier.width(48.dp)) {
                Spacer(Modifier.height(55.dp))
                (0..23).forEach { hour ->
                    Text(
                        hour.toString().padStart(2, '0') + ":00",
                        modifier = Modifier.height(hourHeight).padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            days.forEach { date ->
                val selected = date == selectedDay
                Column(modifier = Modifier.width(cellWidth).padding(horizontal = 2.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(48.dp).clickable { onDate(date) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) LavenderUi.lavender else Color.White.copy(alpha = .40f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("ru-RU"))),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(modifier = Modifier.height(gridHeight).fillMaxWidth()) {
                        Column {
                            repeat(24) {
                                Box(modifier = Modifier.fillMaxWidth().height(hourHeight)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                }
                            }
                        }
                        // Assign side-by-side lanes so simultaneous personal events do not cover each other.
                        val timed = events.filter { eventDay(it) == date }.sortedBy { it.startsAtMillis }
                        val laneEndTimes = mutableListOf<Long>()
                        val positions = timed.map { event ->
                            val free = laneEndTimes.indexOfFirst { it <= event.startsAtMillis }
                            val lane = if (free == -1) {
                                laneEndTimes.add(event.endsAtMillis)
                                laneEndTimes.lastIndex
                            } else {
                                laneEndTimes[free] = event.endsAtMillis
                                free
                            }
                            event to lane
                        }
                        positions.forEach { (event, lane) ->
                            val start = eventStartMinute(event).coerceIn(0, 24 * 60)
                            val end = eventEndMinute(event).coerceIn(0, 24 * 60)
                            if (end > start) {
                                val position = start * 62f / 60f
                                val duration = ((end - start) * 62f / 60f).coerceAtLeast(30f)
                                val accent = eventColor(event.authorMask)
                                val overlap = timed.count { other ->
                                    event.startsAtMillis < other.endsAtMillis && event.endsAtMillis > other.startsAtMillis
                                }
                                val columns = maxOf(overlap, lane + 1)
                                val width = (cellWidth - 4.dp) / columns
                                Column(
                                    modifier = Modifier
                                        .offset(x = width * lane, y = position.dp)
                                        .width(width)
                                        .height(duration.dp)
                                        .background(accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                                        .border(1.dp, Color.White.copy(alpha = .70f), RoundedCornerShape(14.dp))
                                        .clickable { onOpen(event) }
                                        .padding(5.dp),
                                ) {
                                    Text(event.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    if (duration > 45f) Text(eventTime(event), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
internal fun CalendarMonthView(
    selectedDay: LocalDate,
    events: List<CalendarEvent>,
    reactions: List<DailyReaction>,
    identity: Int,
    onDate: (LocalDate) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
    onHeart: (HeartColor?) -> Unit,
    onMood: (String?) -> Unit,
) {
    var showMoods by rememberSaveable { mutableStateOf(false) }
    val window = dateWindow(selectedDay, CalendarView.MONTH)
    val days = (0 until java.time.temporal.ChronoUnit.DAYS.between(window.start, window.endExclusive).toInt())
        .map { window.start.plusDays(it.toLong()) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !showMoods, onClick = { showMoods = false }, label = { Text("События и сердечки") })
            FilterChip(selected = showMoods, onClick = { showMoods = true }, label = { Text("Настроения") })
        }
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = .37f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .67f)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)) {
                Row {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { index, label ->
                        Box(Modifier.weight(1f).height(29.dp), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (index >= 5) LavenderUi.violet else LavenderUi.ink,
                            )
                        }
                    }
                }
                days.chunked(7).forEach { week ->
                    Row {
                        week.forEach { date ->
                            val currentMonth = date.month == selectedDay.month && date.year == selectedDay.year
                            val selected = date == selectedDay
                            val entries = reactions.filter { it.dateEpochDay == date.toEpochDay() }
                            val dateEvents = events.filter { eventDay(it) == date }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (showMoods) 85.dp else 74.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(
                                        if (selected) LavenderUi.lavender.copy(alpha = .40f)
                                        else Color.Transparent,
                                    )
                                    .clickable { onDate(date) }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) LavenderUi.violet
                                            else Color.Transparent,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White
                                            else if (currentMonth) LavenderUi.ink
                                            else LavenderUi.muted.copy(alpha = .40f),
                                    )
                                }
                                if (showMoods) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                        listOf(Authors.SASHA to LavenderUi.blue, Authors.NASTYA to LavenderUi.pink)
                                            .forEach { (author, tint) ->
                                                val mood = entries.firstOrNull { it.authorId == author }?.dayEmoji
                                                if (mood != null) {
                                                    LavenderMoodOrb(mood, tint, Modifier.size(25.dp))
                                                }
                                            }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        dateEvents.map { it.authorMask }.distinct().take(3).forEach { author ->
                                            Box(Modifier.size(6.dp).background(eventColor(author), CircleShape))
                                        }
                                    }
                                    if (entries.any { it.heartChoice != 0 }) {
                                        Text(
                                            "♥",
                                            color = Color(0xFFCE60B9),
                                            fontSize = 14.sp,
                                            lineHeight = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle(
            selectedDay.format(dateHeading).replaceFirstChar {
                it.titlecase(Locale.forLanguageTag("ru-RU"))
            },
        )
        val todayEvents = events.filter { eventDay(it) == selectedDay }.sortedBy { it.startsAtMillis }
        if (todayEvents.isEmpty()) {
            Text(
                "На выбранный день нет событий",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = LavenderUi.muted,
            )
        } else {
            todayEvents.forEach { event ->
                EventCard(event = event, onClick = { onOpen(event) }, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        DayReactionsPanel(
            reactions = reactions.filter { it.dateEpochDay == selectedDay.toEpochDay() },
            actingAs = identity,
            onHeart = onHeart,
            onMood = onMood,
        )
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
internal fun CalendarAgendaView(
    selectedDay: LocalDate,
    events: List<CalendarEvent>,
    onDate: (LocalDate) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
) {
    val daysWithEvents = (0 until 31)
        .map { selectedDay.plusDays(it.toLong()) }
        .filter { date -> date == selectedDay || events.any { eventDay(it) == date } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        items(daysWithEvents, key = { it.toString() }) { date ->
            val dayEvents = events.filter { eventDay(it) == date }.sortedBy { it.startsAtMillis }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onDate(date) }) {
                        Text(
                            date.format(dateHeading).replaceFirstChar {
                                it.titlecase(Locale.forLanguageTag("ru-RU"))
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = LavenderUi.violet.copy(alpha = .25f),
                    )
                }
                if (dayEvents.isEmpty()) {
                    Text(
                        "Нет событий",
                        color = LavenderUi.muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
                    )
                } else {
                    dayEvents.forEach { event ->
                        EventCard(event, onClick = { onOpen(event) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(85.dp)) }
    }
}
