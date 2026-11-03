package com.rteats.calendarik.sync

import com.rteats.calendarik.data.CalendarEvent
import com.rteats.calendarik.data.DailyReaction
import com.rteats.calendarik.data.SharedHeart
import org.junit.Assert.*
import org.junit.Test

class SnapshotCodecTest {
    private val event = CalendarEvent("uuid", "Учёба", "notes", 100, 200,
        3, 1, 10, 12, 13, false, 1)

    @Test fun snapshotRoundTripKeepsAuthorsAndTombstones() {
        val original = CalendarSnapshot(
            listOf(event.copy(deleted = true)),
            listOf(DailyReaction(42, 2, redHearts = 123, dayEmoji = "fa:poo-storm", revision = 9)),
            listOf(SharedHeart(42, 5, 12, 1, 999)),
        )
        val restored = SnapshotCodec.decode(SnapshotCodec.encode(original))
        assertEquals(original.events, restored.events)
        assertEquals(original.reactions, restored.reactions)
        assertEquals(original.sharedHearts, restored.sharedHearts)
    }

    @Test fun allFiveRatingsRoundTrip() {
        val source = CalendarSnapshot(emptyList(), emptyList(),
            (1..5).map { SharedHeart(it.toLong(), it, it.toLong(), 1, 100) })
        assertEquals(source.sharedHearts, SnapshotCodec.decode(SnapshotCodec.encode(source)).sharedHearts)
    }

    @Test fun rejectsUnknownPayloadVersions() {
        val doc = SnapshotCodec.encode(CalendarSnapshot(listOf(event), emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotCodec.decode(doc.replace("\"version\":3", "\"version\":20"))
        }
    }

    @Test fun heartRemovalRoundTripsAsTombstone() {
        val hearts = listOf(SharedHeart(17, 0, 3, 2, 1245))
        assertEquals(hearts, SnapshotCodec.decode(
            SnapshotCodec.encode(CalendarSnapshot(emptyList(), emptyList(), hearts))
        ).sharedHearts)
    }

    @Test fun oldV1ReactionsDecodeToOneHeart() {
        val oldSnapshot = """{"version":1,"events":[],"reactions":[{"dateEpochDay":42,"authorId":1,"redHearts":3,"pinkHearts":9,"dayEmoji":"💩","revision":4,"updatedAtMillis":13}]}"""
        val migrated = SnapshotCodec.decode(oldSnapshot)
        assertEquals(2, migrated.reactions.single().heartChoice)
        assertEquals(3L, migrated.reactions.single().redHearts)
        assertEquals(9L, migrated.reactions.single().pinkHearts)
        assertEquals("💩", migrated.reactions.single().dayEmoji)
        assertTrue(migrated.sharedHearts.isEmpty())
    }

    @Test fun v2SnapshotPreservesChoiceAndLegacyCounts() {
        val reaction = DailyReaction(43, 2, redHearts = 12, pinkHearts = 11, heartChoice = 2, revision = 29)
        val encoded = SnapshotCodec.encode(CalendarSnapshot(emptyList(), listOf(reaction)))
        assertEquals(2, SnapshotCodec.decode(encoded).reactions.single().heartChoice)
        assertEquals(12L, SnapshotCodec.decode(encoded).reactions.single().redHearts)
    }

    @Test fun encryptedSnapshotCannotBeDecryptedWithoutKey() {
        val key = PairCrypto.newKey()
        val serialized = SnapshotCodec.encode(CalendarSnapshot(listOf(event), emptyList()))
        val cipher = PairCrypto.seal(key, "pair", "author-uid", serialized)
        assertEquals(serialized, PairCrypto.open(key, "pair", "author-uid", cipher))
        assertFalse(cipher.ciphertext.contains("Учёба"))
    }
}
