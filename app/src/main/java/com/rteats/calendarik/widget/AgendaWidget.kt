package com.rteats.calendarik.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rteats.calendarik.MainActivity
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AgendaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = CalendarDatabase.get(context).events().listForRange(start, end).take(5)
        val reactions = CalendarDatabase.get(context).reactions().forDay(day.toEpochDay())
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        val lines = events.map { item ->
            Instant.ofEpochMilli(item.startsAtMillis).atZone(zone).format(timeFormat) +
                "  " + item.title + " · " + Authors.label(item.authorMask)
        }
        provideContent {
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java)))) {
                Text("Календарик · Сегодня", style = TextStyle(fontSize = 16.sp))
                Spacer(GlanceModifier.height(8.dp))
                val summaries = listOf(Authors.SASHA, Authors.NASTYA).map { author ->
                    val reaction = reactions.firstOrNull { it.authorId == author }
                    Authors.label(author) + ": " +
                        (when (reaction?.heartChoice) {
                            1 -> "❤️"
                            2 -> "🩷"
                            else -> "♡"
                        }) + " " + (reaction?.dayEmoji ?: "")
                }
                summaries.forEach { summary ->
                    Text(summary, style = TextStyle(fontSize = 12.sp))
                    Spacer(GlanceModifier.height(4.dp))
                }
                Spacer(GlanceModifier.height(6.dp))
                if (lines.isEmpty()) {
                    Text("На сегодня событий нет")
                } else {
                    lines.forEach { line ->
                        Text(line, style = TextStyle(fontSize = 13.sp))
                        Spacer(GlanceModifier.height(5.dp))
                    }
                }
            }
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

suspend fun refreshWidgets(context: Context) {
    AgendaWidget().updateAll(context)
}

