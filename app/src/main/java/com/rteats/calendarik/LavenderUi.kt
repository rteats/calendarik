package com.rteats.calendarik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rteats.calendarik.data.DailyReaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared decoration for the four real calendar modes; no demo data is drawn. */
internal object LavenderUi {
    val ink = Color(0xFF302758)
    val violet = Color(0xFF8261D4)
    val lavender = Color(0xFFD6C5FC)
    val pink = Color(0xFFF0D7FA)
    val blue = Color(0xFFD6E6FF)
    val muted = Color(0xFF736B96)
    val line = Color(0xFFD7CDF2)
}

@Composable
internal fun LavenderBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF8D86E7), Color(0xFFB5B7F4), Color(0xFFD9DDFC)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Low-contrast clouds are decorative and never cover event text.
            val r = size.width / 8f
            cloud(Offset(size.width * .06f, size.height * .10f), r)
            cloud(Offset(size.width * .62f, size.height * .06f), r * 1.10f)
            cloud(Offset(size.width * .98f, size.height * .12f), r * 1.25f)
            cloud(Offset(size.width * .23f, size.height * .91f), r * 1.10f)
            cloud(Offset(size.width * .84f, size.height * .95f), r * 1.30f)
        }
        content()
    }
}

private fun DrawScope.cloud(center: Offset, radius: Float) {
    val tint = Color.White.copy(alpha = .12f)
    drawCircle(tint, radius, center)
    drawCircle(tint, radius * .72f, Offset(center.x + radius * .75f, center.y + radius * .28f))
    drawCircle(tint, radius * .78f, Offset(center.x - radius * .74f, center.y + radius * .27f))
}

@Composable
internal fun LavenderPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = .82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .70f)),
        shadowElevation = 3.dp,
    ) { content() }
}

@Composable
internal fun LavenderBottomNavigation(
    view: CalendarView,
    onChange: (CalendarView) -> Unit,
) {
    val destinations = listOf(
        Triple(CalendarView.MONTH, Icons.Default.DateRange, "Месяц"),
        Triple(CalendarView.WEEK, Icons.Default.ViewWeek, "Неделя"),
        Triple(CalendarView.DAY, Icons.Default.Today, "День"),
        Triple(CalendarView.AGENDA, Icons.Default.ViewAgenda, "Список"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .88f))
            .border(1.dp, Color.White.copy(alpha = .80f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { (destination, icon, label) ->
            val selected = destination == view
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Tab) { onChange(destination) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(43.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Brush.linearGradient(
                                listOf(Color(0xFFB38BEF), Color(0xFF7155CE)),
                            ) else Brush.linearGradient(
                                listOf(Color.Transparent, Color.Transparent),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) Color.White else LavenderUi.muted,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    label,
                    color = if (selected) LavenderUi.violet else LavenderUi.muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private val weekdayFormatter = DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("ru-RU"))

/** Compact seven-day strip. All tap targets select real dates. */
@Composable
internal fun LavenderWeekStrip(
    day: LocalDate,
    reactions: List<DailyReaction>,
    onDate: (LocalDate) -> Unit,
) {
    val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(7) { offset ->
            val date = monday.plusDays(offset.toLong())
            val selected = date == day
            val hasHeart = reactions.any {
                it.dateEpochDay == date.toEpochDay() &&
                    it.heartChoice != 0
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { onDate(date) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.format(weekdayFormatter).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru-RU")) },
                    color = if (date.dayOfWeek.value >= 6) LavenderUi.violet else LavenderUi.ink,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Brush.linearGradient(
                                listOf(Color(0xFFAD88EC), Color(0xFF7459CF)),
                            ) else Brush.linearGradient(
                                listOf(Color.Transparent, Color.Transparent),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        color = if (selected) Color.White else LavenderUi.ink,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    if (hasHeart) "♥" else " ",
                    color = Color(0xFFD46AC0),
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun LavenderMoodOrb(
    emoji: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .72f), tint)))
            .border(1.dp, Color.White.copy(alpha = .80f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji ?: "·", fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Clip)
    }
}
