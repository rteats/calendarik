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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private val lilac = Color(0xFF9774D4)
private val blue = Color(0xFF819AE4)
private val pink = Color(0xFFD092D0)

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
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).height(46.dp).background(accent, CircleShape))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    (if (showDate) eventDay(event).format(shortDay) + " · " else "") + eventTime(event),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (event.notes.isNotBlank()) {
                    Text(event.notes, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                Authors.label(event.authorMask),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.27f))
    }
}

@Composable
internal fun CalendarDayView(
    selectedDay: LocalDate,
    events: List<CalendarEvent>,
    reactions: List<DailyReaction>,
    identity: Int,
    onOpen: (CalendarEvent) -> Unit,
    onHeart: (HeartColor) -> Unit,
    onMood: (String?) -> Unit,
) {
    val dayEvents = events.filter { eventDay(it) == selectedDay }.sortedBy { it.startsAtMillis }
    val groups = listOf("Утро" to (0 until 12), "День" to (12 until 18), "Вечер" to (18 until 24))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                selectedDay.format(dateHeading).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru-RU")) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (selectedDay == LocalDate.now()) {
            item {
                Text(
                    "Сейчас " + java.time.LocalTime.now().format(timeFormat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        for ((heading, hours) in groups) {
            val part = dayEvents.filter { eventStartMinute(it) / 60 in hours }
            if (part.isNotEmpty()) {
                item { SectionTitle(heading) }
                items(part, key = { it.id }) { event ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            Instant.ofEpochMilli(event.startsAtMillis).atZone(ZoneId.systemDefault()).format(timeFormat),
                            modifier = Modifier.width(49.dp).padding(top = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        EventCard(event, onClick = { onOpen(event) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (dayEvents.isEmpty()) {
            item {
                Text("На этот день событий нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 30.dp))
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
    val cellWidth = 142.dp
    val hourHeight = 62.dp
    val gridHeight = hourHeight * 24
    val scroll = rememberScrollState()

    val initialScroll = with(LocalDensity.current) { (hourHeight * 7).roundToPx() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState(initial = initialScroll))) {
        Text(
            "Нажмите на дату, чтобы выбрать день. Сетка прокручивается по горизонтали.",
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
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                                        .background(accent.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
                                        .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
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
    onHeart: (HeartColor) -> Unit,
    onMood: (String?) -> Unit,
) {
    var showMoods by rememberSaveable { mutableStateOf(false) }
    val window = dateWindow(selectedDay, CalendarView.MONTH)
    val days = (0 until java.time.temporal.ChronoUnit.DAYS.between(window.start, window.endExclusive).toInt())
        .map { window.start.plusDays(it.toLong()) }
    val background = MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !showMoods, onClick = { showMoods = false }, label = { Text("События") })
            FilterChip(selected = showMoods, onClick = { showMoods = true }, label = { Text("Настроения") })
        }
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = background)) {
            Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp)) {
                Row {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { label ->
                        Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                days.chunked(7).forEach { week ->
                    Row {
                        week.forEach { date ->
                            val isCurrentMonth = date.month == selectedDay.month && date.year == selectedDay.year
                            val selected = date == selectedDay
                            val entries = reactions.filter { it.dateEpochDay == date.toEpochDay() }
                            val dateEvents = events.filter { eventDay(it) == date }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (showMoods) 81.dp else 76.dp)
                                    .padding(2.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable { onDate(date) }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                                )
                                if (showMoods) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                        listOf(Authors.SASHA, Authors.NASTYA).forEach { author ->
                                            val mood = entries.firstOrNull { it.authorId == author }?.dayEmoji
                                            Text(mood ?: "·", fontSize = 14.sp, maxLines = 1)
                                        }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        dateEvents.map { it.authorMask }.distinct().take(3).forEach { author ->
                                            Box(Modifier.width(7.dp).height(7.dp).background(eventColor(author), CircleShape))
                                        }
                                    }
                                    if (entries.any { it.redHearts > 0 || it.pinkHearts > 0 }) {
                                        Text("♥", color = Color(0xFFC764AD), fontSize = 13.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(selectedDay.format(dateHeading).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru-RU")) })
        val todayEvents = events.filter { eventDay(it) == selectedDay }
        if (todayEvents.isEmpty()) {
            Text("Нет событий", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            todayEvents.forEach { event ->
                EventCard(event = event, onClick = { onOpen(event) }, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        DayReactionsPanel(
            reactions = reactions.filter { it.dateEpochDay == selectedDay.toEpochDay() },
            actingAs = identity,
            onHeart = onHeart,
            onMood = onMood,
        )
        Spacer(Modifier.height(105.dp))
    }
}

@Composable
internal fun CalendarAgendaView(
    selectedDay: LocalDate,
    events: List<CalendarEvent>,
    onDate: (LocalDate) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(31) { offset ->
            val date = selectedDay.plusDays(offset.toLong())
            val dayEvents = events.filter { eventDay(it) == date }.sortedBy { it.startsAtMillis }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onDate(date) }) {
                        Text(
                            date.format(dateHeading).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru-RU")) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                }
                if (dayEvents.isEmpty()) {
                    Text("Нет событий", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp))
                } else {
                    dayEvents.forEach { event ->
                        EventCard(event, onClick = { onOpen(event) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}
