package com.rteats.calendarik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.HeartColor

private val emojiCategories = listOf(
    "Счастье" to listOf("🥰", "😊", "😍", "😂", "🤣", "😆", "😃", "😁", "🤩", "😇", "☺️", "🥳", "🤗", "😘", "😚", "😋", "😌", "😄"),
    "Настроение" to listOf("🥹", "😴", "😎", "😢", "😭", "😔", "😡", "🤒", "😐", "🤯", "🥺", "😤", "🙃", "😏", "😬", "🤔", "🤨", "😑", "😮", "🫠", "😫", "😩", "🥱", "🤢", "😵‍💫", "🥴", "😪", "🤧", "😠", "😳"),
    "Шутки" to listOf("💩", "🤡", "👻", "👽", "🤖", "👾", "😈", "🤪", "😜", "😝", "😛", "🫡", "🤓", "🙈", "🙉", "🙊", "🤠", "🤑"),
    "Символы" to listOf("❤️", "🩷", "💜", "💙", "💚", "💔", "🔥", "✨", "⭐", "🌈", "☀️", "🌙", "🌧️", "❄️", "🌸", "🌻", "🍀", "🎉", "💤", "💯"),
)

@Composable
fun DayReactionsPanel(
    reactions: List<DailyReaction>,
    actingAs: Int,
    onHeart: (HeartColor?) -> Unit,
    onMood: (String?) -> Unit,
) {
    val context = LocalContext.current
    val history = remember(context) { RecentEmojiStore(context) }
    var recent by remember(actingAs) { mutableStateOf(history.read(actingAs)) }
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
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(Authors.label(author), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    if (mine) {
                        TextButton(onClick = { moodPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text((reaction?.dayEmoji ?: "🙂") + "  Выбрать", maxLines = 1)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(HeartColor.RED, HeartColor.PINK).forEach { color ->
                                FilterChip(
                                    selected = reaction?.heartChoice == color.persisted,
                                    onClick = { onHeart(color) },
                                    label = { Text(color.emoji, fontSize = 16.sp) },
                                )
                            }
                        }
                        TextButton(onClick = { onHeart(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (reaction?.heartChoice == 0 || reaction == null) "Без сердечка ✓" else "Убрать сердечко",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text(reaction?.dayEmoji ?: "—", fontSize = 26.sp)
                        Text(
                            when (reaction?.heartChoice) {
                                1 -> "❤️ Красное сердечко"
                                2 -> "🩷 Розовое сердечко"
                                else -> "Без сердечка"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }

    if (moodPicker) {
        fun choose(emoji: String) {
            recent = history.record(actingAs, emoji)
            onMood(emoji)
            moodPicker = false
        }
        AlertDialog(
            onDismissRequest = { moodPicker = false },
            title = { Text("Эмоция за день") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (recent.isNotEmpty()) {
                        Text("Недавние", style = MaterialTheme.typography.titleSmall)
                        recent.chunked(5).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { emoji ->
                                    TextButton(onClick = { choose(emoji) }) { Text(emoji, fontSize = 23.sp) }
                                }
                            }
                        }
                    }
                    emojiCategories.forEach { (section, emojis) ->
                        Text(section, style = MaterialTheme.typography.titleSmall)
                        emojis.chunked(5).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { emoji ->
                                    TextButton(onClick = { choose(emoji) }) { Text(emoji, fontSize = 23.sp) }
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { onMood(null); moodPicker = false }) { Text("Убрать эмоцию") }
            },
            confirmButton = { TextButton(onClick = { moodPicker = false }) { Text("Закрыть") } },
        )
    }
}

