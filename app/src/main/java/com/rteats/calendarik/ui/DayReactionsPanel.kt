package com.rteats.calendarik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.SharedHeart

@Composable
fun DayReactionsPanel(
    reactions: List<DailyReaction>,
    sharedHeart: SharedHeart?,
    actingAs: Int,
    onHeart: (Int?) -> Unit,
    onMood: (String?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val rating = sharedHeart?.heartChoice ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5E7FA)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("Наше сердечко", style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF48345F), fontWeight = FontWeight.Bold)
                Text("Общая оценка дня. Саша и Настя могут её изменить.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF736282))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (1..5).forEach { value ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .border(
                                    if (rating == value) 2.dp else 0.dp,
                                    if (rating == value) Color(0xFFE68BC7) else Color.Transparent,
                                    RoundedCornerShape(15.dp),
                                )
                                .background(
                                    if (rating == value) Color.White.copy(alpha = .85f)
                                    else Color.Transparent,
                                    RoundedCornerShape(15.dp),
                                )
                                .clickable { onHeart(if (rating == value) null else value) },
                            contentAlignment = Alignment.Center,
                        ) {
                            HeartGlyph(value, Modifier.size(35.dp), 15.sp)
                        }
                    }
                }
                if (rating > 0) {
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
                        if (reaction?.dayEmoji != null) {
                            MoodGlyph(reaction.dayEmoji, Modifier.size(28.dp),
                                if (author == Authors.SASHA) Color(0xFF6979B8) else Color(0xFFBA6BA7))
                        } else {
                            Text("—", style = MaterialTheme.typography.titleLarge)
                        }
                        if (mine) {
                            TextButton(onClick = { pickerOpen = true }) { Text("Выбрать") }
                        } else {
                            Text(if (reaction?.dayEmoji == null) "Нет эмоции" else
                                MoodCatalog.label(reaction.dayEmoji),
                                style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Эмоция за день") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 410.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MoodCatalog.choices.chunked(4).forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            group.forEach { mood ->
                                Column(
                                    modifier = Modifier.weight(1f)
                                        .clickable { onMood(mood.id); pickerOpen = false }
                                        .padding(vertical = 8.dp, horizontal = 1.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    MoodGlyph(mood.id, Modifier.size(31.dp))
                                    Text(
                                        mood.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        minLines = 2,
                                    )
                                }
                            }
                        }
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
