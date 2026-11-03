package com.rteats.calendarik.sync

import com.rteats.calendarik.data.CalendarEvent
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.SharedHeart
import org.json.JSONArray
import org.json.JSONObject

data class CalendarSnapshot(
    val events: List<CalendarEvent>,
    val reactions: List<DailyReaction>,
    val sharedHearts: List<SharedHeart> = emptyList(),
)

/** Versioned plaintext exists only in device memory and Room, never in Firestore. */
object SnapshotCodec {
    const val VERSION = 3

    fun encode(snapshot: CalendarSnapshot): String = JSONObject()
        .put("version", VERSION)
        .put("events", JSONArray().apply {
            snapshot.events.sortedBy { it.id }.forEach { put(eventToJson(it)) }
        })
        .put("reactions", JSONArray().apply {
            snapshot.reactions.sortedWith(compareBy({ it.dateEpochDay }, { it.authorId }))
                .forEach { put(reactionToJson(it)) }
        })
        .put("sharedHearts", JSONArray().apply {
            snapshot.sharedHearts.sortedBy { it.dateEpochDay }.forEach { put(heartToJson(it)) }
        })
        .toString()

    fun decode(source: String): CalendarSnapshot {
        require(source.length <= 750_000) { "Снимок слишком большой" }
        val data = JSONObject(source)
        val receivedVersion = data.getInt("version")
        require(receivedVersion in 1..VERSION) {
            "Неизвестная версия формата синхронизации; обновите приложение"
        }
        val events = data.getJSONArray("events")
        val reactions = data.getJSONArray("reactions")
        val hearts = if (receivedVersion >= 3) data.getJSONArray("sharedHearts") else JSONArray()
        require(events.length() < 5_000 && reactions.length() < 20_000 && hearts.length() < 20_000)
        return CalendarSnapshot(
            (0 until events.length()).map { eventFromJson(events.getJSONObject(it)) },
            (0 until reactions.length()).map { reactionFromJson(reactions.getJSONObject(it), receivedVersion) },
            (0 until hearts.length()).map { heartFromJson(hearts.getJSONObject(it)) },
        )
    }

    fun eventToJson(e: CalendarEvent): JSONObject = JSONObject()
        .put("id", e.id)
        .put("title", e.title)
        .put("notes", e.notes)
        .put("startsAtMillis", e.startsAtMillis)
        .put("endsAtMillis", e.endsAtMillis)
        .put("authorMask", e.authorMask)
        .put("lastEditor", e.lastEditor)
        .put("revision", e.revision)
        .put("createdAtMillis", e.createdAtMillis)
        .put("updatedAtMillis", e.updatedAtMillis)
        .put("deleted", e.deleted)
        .put("payloadVersion", e.payloadVersion)

    fun eventFromJson(j: JSONObject): CalendarEvent {
        require(j.getInt("payloadVersion") == 1) { "Неподдерживаемая версия события" }
        return CalendarEvent(
            id = j.getString("id").also { require(it.length in 1..100) },
            title = j.getString("title").also { require(it.length <= 1000) },
            notes = j.getString("notes").also { require(it.length <= 10_000) },
            startsAtMillis = j.getLong("startsAtMillis"),
            endsAtMillis = j.getLong("endsAtMillis"),
            authorMask = j.getInt("authorMask").also { require(it in 1..3) },
            lastEditor = j.getInt("lastEditor").also { require(it in 1..2) },
            revision = j.getLong("revision").also { require(it >= 1) },
            createdAtMillis = j.getLong("createdAtMillis"),
            updatedAtMillis = j.getLong("updatedAtMillis"),
            deleted = j.getBoolean("deleted"),
            payloadVersion = 1,
        )
    }

    fun heartToJson(h: SharedHeart): JSONObject = JSONObject()
        .put("dateEpochDay", h.dateEpochDay)
        .put("heartChoice", h.heartChoice)
        .put("revision", h.revision)
        .put("lastEditor", h.lastEditor)
        .put("updatedAtMillis", h.updatedAtMillis)

    fun heartFromJson(j: JSONObject): SharedHeart = SharedHeart(
        dateEpochDay = j.getLong("dateEpochDay"),
        heartChoice = j.getInt("heartChoice").also { require(it in 0..2) },
        revision = j.getLong("revision").also { require(it >= 1) },
        lastEditor = j.getInt("lastEditor").also { require(it in 1..2) },
        updatedAtMillis = j.getLong("updatedAtMillis"),
    )

    fun reactionToJson(r: DailyReaction): JSONObject = JSONObject()
        .put("dateEpochDay", r.dateEpochDay)
        .put("authorId", r.authorId)
        .put("redHearts", r.redHearts)
        .put("pinkHearts", r.pinkHearts)
        .put("heartChoice", r.heartChoice)
        .put("dayEmoji", r.dayEmoji ?: JSONObject.NULL)
        .put("revision", r.revision)
        .put("updatedAtMillis", r.updatedAtMillis)

    fun reactionFromJson(j: JSONObject, version: Int = VERSION): DailyReaction =
        DailyReaction(
            dateEpochDay = j.getLong("dateEpochDay"),
            authorId = j.getInt("authorId").also { require(it == 1 || it == 2) },
            redHearts = j.getLong("redHearts").also { require(it >= 0) },
            pinkHearts = j.getLong("pinkHearts").also { require(it >= 0) },
            heartChoice = if (version == 1) {
                com.rteats.calendarik.data.DailyReactionRules.legacyHeart(
                    j.getLong("redHearts"), j.getLong("pinkHearts")
                )
            } else {
                j.getInt("heartChoice").also { require(it in 0..2) }
            },
            dayEmoji = if (j.isNull("dayEmoji")) null else j.getString("dayEmoji"),
            revision = j.getLong("revision").also { require(it >= 0) },
            updatedAtMillis = j.getLong("updatedAtMillis"),
        )
}
