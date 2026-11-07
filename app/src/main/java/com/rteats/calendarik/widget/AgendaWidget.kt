package com.rteats.calendarik.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rteats.calendarik.MainActivity
import com.rteats.calendarik.R
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarDatabase
import com.rteats.calendarik.data.CalendarEvent
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.ui.MoodCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WidgetNavigation {
    const val EXTRA_DAY = "calendarik_widget_day"
    const val EXTRA_EVENT_ID = "calendarik_widget_event_id"
    const val EXTRA_NEW_EVENT = "calendarik_widget_new_event"

    fun intent(context: Context, day: LocalDate, eventId: String? = null, createEvent: Boolean = false): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_DAY, day.toString())
            if (eventId != null) putExtra(EXTRA_EVENT_ID, eventId)
            if (createEvent) putExtra(EXTRA_NEW_EVENT, true)
            // Keep PendingIntent identity distinct for each event and date.
            data = Uri.parse("calendarik://widget/${day}/${eventId ?: if (createEvent) "new" else "day"}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}

// Glance requires both day and night values even for an intentionally fixed pastel widget palette.
private fun ColorProvider(color: Color): androidx.glance.unit.ColorProvider =
    androidx.glance.color.ColorProvider(color, color)

private val offsetKey = intPreferencesKey("dayOffset")
private val ink = Color(0xFF302758)
private val softText = Color(0xFF756898)
private val violet = Color(0xFF8164C9)
private val panel = Color(0xFFF9F5FF)
private val line = Color(0xFFE4DDF2)
private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("ru-RU"))
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru-RU"))
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val upcomingDateFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val widgetUpdateMutex = Mutex()
private val widgetOffsets = ConcurrentHashMap<GlanceId, MutableStateFlow<Int>>()
private val widgetRevision = MutableStateFlow(0L)

private data class WidgetData(
    val day: LocalDate,
    val events: List<CalendarEvent>,
    val upcoming: List<CalendarEvent>,
    val reactions: List<DailyReaction>,
)

private suspend fun loadWidgetData(context: Context, day: LocalDate, zone: ZoneId): WidgetData {
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val db = CalendarDatabase.get(context)
    return WidgetData(
        day = day,
        events = db.events().listForRange(start, end).sortedBy { it.startsAtMillis },
        upcoming = db.events().listUpcoming(end, Int.MAX_VALUE),
        reactions = db.reactions().forDay(day.toEpochDay()),
    )
}

internal data class WidgetLayout(
    val showSubtitle: Boolean,
    val showMoods: Boolean,
    val showEvents: Boolean,
    val upcomingDayLimit: Int,
)

internal fun widgetLayout(width: Int, height: Int): WidgetLayout = WidgetLayout(
    showSubtitle = width >= 190 && height >= 70,
    showMoods = height >= 120,
    showEvents = height >= 195,
    upcomingDayLimit = when {
        width < 220 || height < 320 -> 0
        height < 440 -> 1
        else -> 2
    },
)

internal fun upcomingEventDays(
    events: List<CalendarEvent>, zone: ZoneId, dayLimit: Int,
): List<Pair<LocalDate, List<CalendarEvent>>> = events
    .sortedBy { it.startsAtMillis }
    .groupBy { Instant.ofEpochMilli(it.startsAtMillis).atZone(zone).toLocalDate() }
    .toList()
    .take(dayLimit)

/** One responsive, navigable day agenda. Each installed widget stores its own day offset. */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val offsetFlow = widgetOffsets.getOrPut(id) {
            MutableStateFlow(state[offsetKey] ?: 0)
        }
        val zone = ZoneId.systemDefault()
        val initial = loadWidgetData(context, LocalDate.now(zone).plusDays(offsetFlow.value.toLong()), zone)
        provideContent {
            val offset by offsetFlow.collectAsState()
            val revision by widgetRevision.collectAsState()
            val day = LocalDate.now(zone).plusDays(offset.toLong())
            val data by produceState(initialValue = initial, key1 = day, key2 = revision) {
                value = loadWidgetData(context, day, zone)
            }
            AgendaContent(
                context, day,
                if (data.day == day) data.events else emptyList(),
                if (data.day == day) data.upcoming else emptyList(),
                if (data.day == day) data.reactions else emptyList(),
                zone,
            )
        }
    }
}

@Composable
private fun AgendaContent(
    context: Context,
    day: LocalDate,
    events: List<CalendarEvent>,
    upcoming: List<CalendarEvent>,
    reactions: List<DailyReaction>,
    zone: ZoneId,
) {
    val size = LocalSize.current
    val layout = widgetLayout(size.width.value.toInt(), size.height.value.toInt())
    val compact = size.height < 230.dp
    val veryNarrow = size.width < 150.dp
    val narrow = size.width < 190.dp
    val today = LocalDate.now(zone)
    val upcomingDays = upcomingEventDays(upcoming, zone, layout.upcomingDayLimit)
    val heading = when {
        veryNarrow -> day.dayOfMonth.toString()
        narrow -> day.format(shortDateFormatter)
        day == today -> "Сегодня"
        day == today.plusDays(1) -> "Завтра"
        day == today.minusDays(1) -> "Вчера"
        else -> "Выбранный день"
    }
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(panel)
            .cornerRadius(28.dp)
            .padding(if (veryNarrow || !layout.showMoods) 8.dp else 16.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(heading, maxLines = 1,
                    modifier = GlanceModifier.clickable(actionStartActivity(WidgetNavigation.intent(context, day))),
                    style = TextStyle(
                    color = ColorProvider(violet), fontWeight = FontWeight.Bold,
                    fontSize = if (narrow) 14.sp else 22.sp,
                ))
                if (layout.showSubtitle) {
                    Text(day.format(dateFormatter), maxLines = 1,
                        modifier = GlanceModifier.clickable(actionStartActivity(WidgetNavigation.intent(context, day))),
                        style = TextStyle(color = ColorProvider(softText), fontSize = 13.sp))
                }
            }
            WidgetDayArrow("‹", previous = true, veryNarrow = veryNarrow)
            WidgetDayArrow("›", previous = false, veryNarrow = veryNarrow)
        }
        if (layout.showMoods) {
            Spacer(GlanceModifier.height(if (compact) 8.dp else 16.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf(Authors.SASHA, Authors.NASTYA).forEachIndexed { index, author ->
                    if (index > 0) {
                        Spacer(GlanceModifier.width(1.dp).height(50.dp).background(line))
                    }
                    val mood = reactions.firstOrNull { it.authorId == author }?.dayEmoji
                    Column(
                        modifier = GlanceModifier.defaultWeight()
                            .clickable(actionStartActivity(WidgetNavigation.intent(context, day))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (author == Authors.SASHA) "Саша" else "Настя", maxLines = 1,
                            style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp))
                        Spacer(GlanceModifier.height(4.dp))
                        Box(
                            modifier = GlanceModifier.size(29.dp).background(
                                if (mood == null) Color(0xFFE9E1F5) else violet,
                            ).cornerRadius(15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val resource = MoodCatalog.resource(mood)
                            when {
                                resource != null -> Image(
                                    ImageProvider(resource), MoodCatalog.label(mood!!),
                                    modifier = GlanceModifier.size(18.dp),
                                    colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Color.White)),
                                )
                                mood != null -> Text(mood, maxLines = 1,
                                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp))
                                else -> Image(
                                    ImageProvider(R.drawable.fa_face_meh),
                                    "Настроение не выбрано",
                                    modifier = GlanceModifier.size(18.dp),
                                    colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(softText)),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (layout.showEvents) {
            Spacer(GlanceModifier.height(if (compact) 8.dp else 17.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (day == today) "Планы на сегодня" else "Планы на день",
                    modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                    style = TextStyle(color = ColorProvider(ink), fontWeight = FontWeight.Bold,
                        fontSize = if (narrow) 13.sp else 15.sp))
                Box(
                    modifier = GlanceModifier.size(30.dp)
                        .clickable(actionStartActivity(WidgetNavigation.intent(context, day, createEvent = true))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(color = ColorProvider(violet), fontSize = 26.sp))
                }
            }
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (events.isEmpty()) {
                    item {
                        Text("Нет событий", style = TextStyle(color = ColorProvider(softText), fontSize = 12.sp))
                    }
                } else {
                    items(events.size) { index ->
                        WidgetEventRow(context, events[index], zone, compact)
                    }
                }
                if (upcomingDays.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(GlanceModifier.height(17.dp))
                            Text("Планы на ближайшее время", maxLines = 1,
                                style = TextStyle(color = ColorProvider(ink), fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp))
                        }
                    }
                    upcomingDays.forEach { (eventDay, dayEvents) ->
                        item {
                            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(eventDay.format(upcomingDateFormatter),
                                    style = TextStyle(color = ColorProvider(violet), fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp))
                            }
                        }
                        items(dayEvents.size) { index ->
                            WidgetEventRow(context, dayEvents[index], zone)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetDayArrow(glyph: String, previous: Boolean, veryNarrow: Boolean) {
    Box(
        modifier = GlanceModifier.size(if (veryNarrow) 36.dp else 44.dp).clickable(
            if (previous) actionRunCallback<PreviousWidgetDay>()
            else actionRunCallback<NextWidgetDay>(),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(color = ColorProvider(violet), fontSize = 24.sp))
    }
}

@Composable
private fun WidgetEventRow(context: Context, event: CalendarEvent, zone: ZoneId, compact: Boolean = false) {
    val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(zone)
    Text("${start.format(timeFormatter)}  ${event.title}", maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 4.dp)
            .clickable(actionStartActivity(WidgetNavigation.intent(context, start.toLocalDate(), event.id))),
        style = TextStyle(color = ColorProvider(ink), fontSize = if (compact) 12.sp else 14.sp))
}

private suspend fun moveWidgetDay(context: Context, glanceId: GlanceId, direction: Int) {
    widgetUpdateMutex.withLock {
        var newOffset = 0
        updateAppWidgetState(context, glanceId) { prefs ->
            newOffset = (prefs[offsetKey] ?: 0) + direction
            prefs[offsetKey] = newOffset
        }
        widgetOffsets[glanceId]?.value = newOffset
        AgendaWidget().update(context, glanceId)
    }
}

class PreviousWidgetDay : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        moveWidgetDay(context, glanceId, -1)
    }
}

class NextWidgetDay : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        moveWidgetDay(context, glanceId, 1)
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

suspend fun refreshWidgets(context: Context) {
    widgetUpdateMutex.withLock {
        widgetRevision.value += 1
        AgendaWidget().updateAll(context)
    }
}
