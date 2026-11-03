package com.rteats.calendarik.sync

import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarEvent
import org.junit.Assert.*
import org.junit.Test

class SharedConflictResolutionTest {
    private val mine = CalendarEvent("shared-id", "16:00", "", 100, 200, Authors.BOTH,
        Authors.SASHA, 5, 10, 20, false, 1)
    private val peer = mine.copy(title = "17:00", lastEditor = Authors.NASTYA, revision = 5)

    @Test fun acceptingPeerConvergesInsteadOfPingPong() {
        val result = SharedConflictResolution.choose(mine, peer, true, Authors.SASHA, 30)
        assertEquals(peer, result)
        assertEquals(peer.revision, result.revision)
    }

    @Test fun keepingLocalCreatesNewerRevision() {
        val result = SharedConflictResolution.choose(mine, peer, false, Authors.SASHA, 30)
        assertEquals("16:00", result.title)
        assertEquals(6L, result.revision)
        assertEquals(30L, result.updatedAtMillis)
    }

    @Test fun cannotResolvePersonalEventsAsShared() {
        assertThrows(IllegalArgumentException::class.java) {
            SharedConflictResolution.choose(
                mine.copy(authorMask = Authors.SASHA), peer, true, Authors.SASHA, 30
            )
        }
    }
}
