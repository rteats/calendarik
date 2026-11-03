package com.rteats.calendarik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.HeartColor

private val moods = listOf(
    "🥰", "😊", "😍", "😂",
    "🥹", "😴", "😎", "🤩",
    "😢", "😔", "😡", "🤒",
    "😐", "🤗", "😇", "🤯",
)

/** Renders both users' reactions even when no events exist on the selected day. */
@Composable
fun DayReactionsPanel(
    reactions: List<DailyReaction>,
    actingAs: Int,
    onHeart: (HeartColor) -> Unit,
    onMood: (String?) -> Unit,
) {
    var moodPicker by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Сердечки и эмоции за день", style = MaterialTheme.typography.titleSmall)
            for (author in listOf(Authors.SASHA, Authors.NASTYA)) {
                val entry = reactions.firstOrNull { it.authorId == author }
                val isMe = author == actingAs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        Authors.label(author),
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                        fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (isMe) {
                        TextButton(onClick = { onHeart(HeartColor.RED) }) {
                            Text("❤️ " + (entry?.redHearts ?: 0))
                        }
                        TextButton(onClick = { onHeart(HeartColor.PINK) }) {
                            Text("🩷 " + (entry?.pinkHearts ?: 0))
                        }
                        TextButton(onClick = { moodPicker = true }) {
                            Text(entry?.dayEmoji ?: "🙂 +")
                        }
                    } else {
                        Text(
                            "❤️ " + (entry?.redHearts ?: 0) + "  🩷 " +
                                (entry?.pinkHearts ?: 0) + "  " + (entry?.dayEmoji ?: "—"),
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (moodPicker) {
        AlertDialog(
            onDismissRequest = { moodPicker = false },
            title = { Text("Моя эмоция за день") },
            text = {
                Column {
                    moods.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { mood ->
                                TextButton(onClick = {
                                    onMood(mood)
                                    moodPicker = false
                                }) { Text(mood, style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onMood(null)
                    moodPicker = false
                }) { Text("Убрать эмоцию") }
            },
            confirmButton = {
                TextButton(onClick = { moodPicker = false }) { Text("Отмена") }
            },
        )
    }
}

