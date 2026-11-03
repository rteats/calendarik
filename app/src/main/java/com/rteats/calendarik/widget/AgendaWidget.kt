package com.rteats.calendarik.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
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
import androidx.glance.action.actionParametersOf
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
import androidx.compose.ui.unit.DpSize

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
private val directionKey = ActionParameters.Key<Int>("direction")
private val ink = Color(0xFF302758)
private val softText = Color(0xFF756898)
private val violet = Color(0xFF8164C9)
private val pink = Color(0xFFE68BC7)
private val rose = Color(0xFFFFF6FB)
private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("ru-RU"))
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** One responsive, navigable day agenda. Each installed widget stores its own day offset. */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 175.dp), DpSize(320.dp, 240.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val offset = (state[offsetKey] ?: 0).coerceIn(-30, 30)
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(offset.toLong())
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val db = CalendarDatabase.get(context)
        val events = db.events().listForRange(start, end).sortedBy { it.startsAtMillis }.take(5)
        val reactions = db.reactions().forDay(day.toEpochDay())
        val rating = db.sharedHearts().get(day.toEpochDay())?.heartChoice ?: 0
        provideContent {
            AgendaContent(context, day, events, reactions, rating, zone)
        }
    }
}

@Composable
private fun AgendaContent(
    context: Context,
    day: LocalDate,
    events: List<CalendarEvent>,
    reactions: List<DailyReaction>,
    rating: Int,
    zone: ZoneId,
) {
    val size = LocalSize.current
    val compact = size.height < 165.dp
    val maxEvents = when {
        size.height < 165.dp -> 1
        size.height < 225.dp -> 2
        else -> 4
    }
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(Color(0xFFF7F1FF))
            .cornerRadius(24.dp)
            .padding(if (compact) 10.dp else 14.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()
                .clickable(actionStartActivity(WidgetNavigation.intent(context, day)))) {
                Text("Наш день", style = TextStyle(
                    color = ColorProvider(ink), fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 14.sp else 17.sp,
                ))
                Text(day.format(dateFormatter), maxLines = 1,
                    style = TextStyle(color = ColorProvider(softText), fontSize = 11.sp))
            }
            Text("‹", modifier = GlanceModifier.size(31.dp)
                .clickable(actionRunCallback<ChangeWidgetDay>(
                    actionParametersOf(directionKey to -1))),
                style = TextStyle(color = ColorProvider(violet), fontSize = 23.sp))
            Text("›", modifier = GlanceModifier.size(31.dp)
                .clickable(actionRunCallback<ChangeWidgetDay>(
                    actionParametersOf(directionKey to 1))),
                style = TextStyle(color = ColorProvider(violet), fontSize = 23.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .background(rose).cornerRadius(15.dp).padding(7.dp)
                .clickable(actionStartActivity(WidgetNavigation.intent(context, day))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rating in 1..5) {
                Box(GlanceModifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Image(
                        provider = ImageProvider(R.drawable.fa_heart),
                        contentDescription = "Сердечко $rating из 5",
                        colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(pink)),
                        modifier = GlanceModifier.size(30.dp),
                    )
                    Text(rating.toString(), style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    ))
                }
            } else {
                Text("♡", style = TextStyle(color = ColorProvider(pink), fontSize = 21.sp))
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(if (rating in 1..5) "Наше сердечко · $rating/5" else "Выбрать сердечко",
                maxLines = 1,
                style = TextStyle(color = ColorProvider(ink), fontSize = 12.sp))
        }
        if (!compact) {
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                listOf(Authors.SASHA, Authors.NASTYA).forEach { author ->
                    val mood = reactions.firstOrNull { it.authorId == author }?.dayEmoji
                    Row(
                        modifier = GlanceModifier.defaultWeight()
                            .clickable(actionStartActivity(WidgetNavigation.intent(context, day))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mood != null && MoodCatalog.resource(mood) != null) {
                            Image(
                                provider = ImageProvider(MoodCatalog.resource(mood)!!),
                                contentDescription = MoodCatalog.label(mood),
                                colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(
                                    if (author == Authors.SASHA) Color(0xFF6E7FB8) else Color(0xFFBD72AD))),
                                modifier = GlanceModifier.size(15.dp),
                            )
                        } else {
                            Text(mood ?: "·", style = TextStyle(
                                color = ColorProvider(softText), fontSize = 13.sp))
                        }
                        Spacer(GlanceModifier.width(4.dp))
                        Text(if (author == Authors.SASHA) "Саша" else "Настя",
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(softText), fontSize = 11.sp))
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(if (compact) 7.dp else 10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Планы", modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(ink),
                    fontWeight = FontWeight.Bold, fontSize = 12.sp))
            Text("+", modifier = GlanceModifier.size(28.dp)
                .clickable(actionStartActivity(WidgetNavigation.intent(context, day, createEvent = true))),
                style = TextStyle(color = ColorProvider(violet), fontSize = 21.sp))
        }
        if (events.isEmpty()) {
            Text("Пока нет событий", style = TextStyle(
                color = ColorProvider(softText), fontSize = 11.sp))
        } else {
            events.take(maxEvents).forEach { event ->
                val time = Instant.ofEpochMilli(event.startsAtMillis)
                    .atZone(zone).format(timeFormatter)
                Text("$time  ${event.title}", maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(actionStartActivity(WidgetNavigation.intent(context, day, event.id))),
                    style = TextStyle(color = ColorProvider(ink), fontSize = 12.sp))
            }
            if (events.size > maxEvents) {
                Text("+ ещё ${events.size - maxEvents}", modifier = GlanceModifier
                    .clickable(actionStartActivity(WidgetNavigation.intent(context, day))),
                    style = TextStyle(color = ColorProvider(violet), fontSize = 11.sp))
            }
        }
    }
}

class ChangeWidgetDay : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val direction = parameters[directionKey] ?: return
        if (direction !in -1..1 || direction == 0) return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[offsetKey] = ((prefs[offsetKey] ?: 0) + direction).coerceIn(-30, 30)
        }
        AgendaWidget().update(context, glanceId)
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

suspend fun refreshWidgets(context: Context) {
    AgendaWidget().updateAll(context)
}
