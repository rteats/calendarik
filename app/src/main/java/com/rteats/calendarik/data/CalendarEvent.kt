package com.rteats.calendarik.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Public fields persist as the durable local source of truth. Never reuse an ID. */
@Entity(
    tableName = "calendar_events",
    indices = [Index("startsAtMillis"), Index("updatedAtMillis")],
)
data class CalendarEvent(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val authorMask: Int,
    val lastEditor: Int,
    val revision: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deleted: Boolean,
    /** Version of the eventual wire format, independent of Room's database version. */
    val payloadVersion: Int,
)

object Authors {
    const val SASHA = 1
    const val NASTYA = 2
    const val BOTH = SASHA or NASTYA

    fun hasPermission(authorMask: Int, actingAs: Int): Boolean =
        actingAs in listOf(SASHA, NASTYA) && (authorMask and actingAs) != 0

    fun label(authorMask: Int): String = when (authorMask) {
        SASHA -> "Саша"
        NASTYA -> "Настя"
        BOTH -> "Общее"
        else -> "Неизвестно"
    }
}

fun createOrEditEvent(
    old: CalendarEvent?,
    editor: Int,
    title: String,
    notes: String,
    day: LocalDate,
    start: LocalTime,
    end: LocalTime,
    authors: Int,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): CalendarEvent {
    require(title.isNotBlank()) { "Укажите название" }
    require(authors in listOf(Authors.SASHA, Authors.NASTYA, Authors.BOTH))
    require(old == null || Authors.hasPermission(old.authorMask, editor))
    require(old == null || old.authorMask == authors) { "Нельзя менять авторов существующего события" }
    val from = day.atTime(start).atZone(zone).toInstant().toEpochMilli()
    val until = day.atTime(end).atZone(zone).toInstant().toEpochMilli()
    require(until > from) { "Время окончания должно быть позже начала" }
    return CalendarEvent(
        id = old?.id ?: UUID.randomUUID().toString(),
        title = title.trim(),
        notes = notes.trim(),
        startsAtMillis = from,
        endsAtMillis = until,
        authorMask = authors,
        lastEditor = editor,
        revision = (old?.revision ?: 0L) + 1L,
        createdAtMillis = old?.createdAtMillis ?: now,
        updatedAtMillis = now,
        deleted = false,
        payloadVersion = 1,
    )
}

fun deleteEvent(old: CalendarEvent, editor: Int, now: Long = System.currentTimeMillis()): CalendarEvent {
    require(Authors.hasPermission(old.authorMask, editor))
    return old.copy(deleted = true, updatedAtMillis = now, lastEditor = editor, revision = old.revision + 1)
}

