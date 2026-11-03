package com.rteats.calendarik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.HeartColor
import com.rteats.calendarik.data.SharedHeart

/** A single heart for the couple; private moods remain independently editable. */
@Composable
fun DayReactionsPanel(
    reactions: List<DailyReaction>,
    sharedHeart: SharedHeart?,
    actingAs: Int,
    onHeart: (HeartColor?) -> Unit,
    onMood: (String?) -> Unit,
) {
    val context = LocalContext.current
    val history = remember(context) { RecentEmojiStore(context) }
    var recent by remember(actingAs) { mutableStateOf(history.read(actingAs)) }
    var pickerOpen by remember { mutableStateOf(false) }
    val choice = HeartColor.fromPersisted(sharedHeart?.heartChoice ?: 0)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5E7FA)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("Общее сердечко", style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF48345F), fontWeight = FontWeight.Bold)
                Text("Одно на день для Саши и Насти. Любой из вас может изменить его.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF736282))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(HeartColor.RED, HeartColor.PINK).forEach { color ->
                        FilterChip(
                            selected = choice == color,
                            onClick = { onHeart(if (choice == color) null else color) },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    HeartGlyph(color, Modifier.size(18.dp))
                                    Text(if (color == HeartColor.RED) "Красное" else "Розовое")
                                }
                            },
                        )
                    }
                }
                if (choice != null) {
                    TextButton(onClick = { onHeart(null) }) { Text("Убрать сердечко") }
                }
            }
        }
        Text("Наши эмоции", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = Color(0xFF48345F))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Authors.SASHA, Authors.NASTYA).forEach { author ->
                val reaction = reactions.firstOrNull { it.authorId == author }
                val mine = author == actingAs
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (author == Authors.SASHA) Color(0xFFE8EAFB) else Color(0xFFF4E5F7),
                        contentColor = Color(0xFF292345),
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(Authors.label(author), fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        MoodGlyph(reaction?.dayEmoji ?: "fa:face-smile", Modifier.size(28.dp),
                            if (author == Authors.SASHA) Color(0xFF6979B8) else Color(0xFFBA6BA7))
                        if (mine) {
                            TextButton(onClick = { pickerOpen = true }) { Text("Выбрать") }
                        } else {
                            Text(if (reaction?.dayEmoji == null) "Нет эмоции" else
                                MoodCatalog.label(reaction.dayEmoji),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        fun choose(id: String) {
            recent = history.record(actingAs, id)
            onMood(id)
            pickerOpen = false
        }
        @Composable
        fun pickerRow(ids: List<String>) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ids.forEach { id ->
                    TextButton(
                        onClick = { choose(id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            MoodGlyph(id, Modifier.size(25.dp))
                            Text(MoodCatalog.label(id), fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Эмоция за день") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (recent.isNotEmpty()) {
                        Text("Недавние", style = MaterialTheme.typography.titleSmall)
                        recent.chunked(4).forEach { pickerRow(it) }
                    }
                    MoodCatalog.categories.forEach { (section, options) ->
                        Text(section, style = MaterialTheme.typography.titleSmall)
                        options.chunked(4).forEach { row -> pickerRow(row.map { it.id }) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { onMood(null); pickerOpen = false }) { Text("Убрать эмоцию") }
            },
            confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text("Закрыть") } },
        )
    }
}
