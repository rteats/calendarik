package com.rteats.calendarik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.HeartColor

private val moods = listOf(
    "🥰", "😊", "😍", "😂",
    "🥹", "😴", "😎", "🤩",
    "😢", "😔", "😡", "🤒",
    "😐", "🤗", "😇", "🤯",
)

/** Reactions are attached to a calendar day, not to individual events. */
@Composable
fun DayReactionsPanel(
    reactions: List<DailyReaction>,
    actingAs: Int,
    onHeart: (HeartColor) -> Unit,
    onMood: (String?) -> Unit,
) {
    var moodPicker by remember { mutableStateOf(false) }
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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(Authors.label(author), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    if (mine) {
                        TextButton(onClick = { moodPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text((reaction?.dayEmoji ?: "🙂") + "  Изменить", maxLines = 1)
                        }
                    } else {
                        Text(reaction?.dayEmoji ?: "—", fontSize = 26.sp)
                    }
                    Text("❤️ " + (reaction?.redHearts ?: 0) + "   🩷 " + (reaction?.pinkHearts ?: 0), style = MaterialTheme.typography.bodySmall)
                    if (mine) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { onHeart(HeartColor.RED) }) { Text("❤️ +", style = MaterialTheme.typography.labelMedium) }
                            TextButton(onClick = { onHeart(HeartColor.PINK) }) { Text("🩷 +", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }

    if (moodPicker) {
        AlertDialog(
            onDismissRequest = { moodPicker = false },
            title = { Text("Моё настроение") },
            text = {
                Column {
                    moods.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { mood ->
                                TextButton(onClick = {
                                    onMood(mood)
                                    moodPicker = false
                                }) { Text(mood, fontSize = 22.sp) }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onMood(null)
                    moodPicker = false
                }) { Text("Убрать") }
            },
            confirmButton = { TextButton(onClick = { moodPicker = false }) { Text("Отмена") } },
        )
    }
}
