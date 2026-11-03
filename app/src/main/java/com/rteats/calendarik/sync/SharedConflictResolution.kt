package com.rteats.calendarik.sync

import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarEvent

/** Choosing the peer's revision must not invent another competing revision. */
object SharedConflictResolution {
    fun choose(
        local: CalendarEvent,
        remote: CalendarEvent,
        acceptRemote: Boolean,
        localAuthor: Int,
        now: Long,
    ): CalendarEvent {
        require(local.id == remote.id)
        require(local.authorMask == Authors.BOTH && remote.authorMask == Authors.BOTH)
        require(localAuthor == Authors.SASHA || localAuthor == Authors.NASTYA)
        return if (acceptRemote) remote else local.copy(
            revision = maxOf(local.revision, remote.revision) + 1,
            lastEditor = localAuthor,
            updatedAtMillis = now,
        )
    }
}
