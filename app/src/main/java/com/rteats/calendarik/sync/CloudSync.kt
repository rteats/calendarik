package com.rteats.calendarik.sync

import android.content.Context
import android.os.SystemClock
import com.rteats.calendarik.diagnostics.NetworkDiagnostics
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rteats.calendarik.data.Authors
import com.rteats.calendarik.data.CalendarDatabase
import com.rteats.calendarik.data.CalendarEvent
import com.rteats.calendarik.data.SharedHeartRules
import com.rteats.calendarik.data.SyncConflict
import com.rteats.calendarik.widget.refreshWidgets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

data class SyncResult(val receivedEvents: Int, val conflicts: Int)

/**
 * Each of the TWO accounts owns one encrypted snapshot document. This avoids
 * a server-side merge and keeps all event details off Firebase.
 *
 * For the first release a snapshot is limited to 600 KB plaintext to stay
 * below Firestore's 1 MiB document limit after AEAD + Base64 expansion.
 * It fails without discarding local data if the calendar grows too large.
 */
class CloudSync(private val context: Context) {
    private val database = CalendarDatabase.get(context)
    private val prefs = context.getSharedPreferences("calendarik_sync_v1", Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val diagnostics = NetworkDiagnostics.get(context)

    private suspend fun <T> traced(name: String, block: suspend () -> T): T {
        val started = SystemClock.elapsedRealtime()
        diagnostics.log("sync", name, "start")
        return try {
            block().also {
                diagnostics.log("sync", name, "ok", SystemClock.elapsedRealtime() - started)
            }
        } catch (failure: Exception) {
            diagnostics.log("sync", name, "error", SystemClock.elapsedRealtime() - started, failure = failure)
            throw failure
        }
    }

    suspend fun sync(): SyncResult = traced("cycle") {
        diagnostics.logConnectivity()
        syncLock.withLock {
        val manager = PairManager(context)
        val pair = manager.current() ?: return@withLock SyncResult(0, 0)
        if (!pair.active) return@withLock SyncResult(0, 0)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withLock SyncResult(0, 0)
        require(uid == pair.accountUid && pair.peerUid != uid)

        val pairDocument = firestore.collection("pairs").document(pair.pairId)
        val metadata = traced("pair_metadata_fetch") { pairDocument.get(Source.SERVER).await() }
        val members = (metadata.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
        require(metadata.getString("creatorUid") == pair.creatorUid &&
            members.size == 2 && members.contains(uid) && members.contains(pair.peerUid)) {
            "Членство в паре не подтверждено"
        }

        val snapshots = pairDocument.collection("snapshots")
        val own = snapshots.document(uid)
        val other = snapshots.document(pair.peerUid)
        pushIfChanged(pair, own.path)

        val remote = traced("peer_snapshot_fetch") { other.get(Source.SERVER).await() }
        var received = 0
        if (remote.exists()) {
            require(remote.getString("writerUid") == pair.peerUid)
            require(remote.getLong("schema") == 1L)
            val box = EncryptedSnapshot(
                nonce = requireNotNull(remote.getString("nonce")),
                ciphertext = requireNotNull(remote.getString("ciphertext")),
            )
            val plaintext = traced("peer_snapshot_decrypt") {
                PairCrypto.open(pair.key, pair.pairId, pair.peerUid, box)
            }
            val snapshot = SnapshotCodec.decode(plaintext)
            received = traced("peer_snapshot_merge") { applyRemote(pair, snapshot) }
            // Include newly accepted shared events in our own snapshot, making
            // them available even when the other phone is temporarily offline.
            pushIfChanged(pair, own.path)
        }
        if (received > 0) refreshWidgets(context)
        diagnostics.log("sync", "remote_changes", "applied", count = received)
        SyncResult(received, database.sync().conflictCount())
        }
    }

    private suspend fun localSnapshot(pair: LocalPair): String {
        val events = database.events().allIncludingDeleted().filter {
            Authors.hasPermission(it.authorMask, pair.role)
        }
        val reactions = database.reactions().allByAuthor(pair.role)
        return SnapshotCodec.encode(CalendarSnapshot(events, reactions, database.sharedHearts().all()))
    }

    private suspend fun pushIfChanged(pair: LocalPair, documentPath: String) {
        val data = localSnapshot(pair)
        require(data.toByteArray(Charsets.UTF_8).size <= 600_000) {
            "Календарь превышает лимит облачного документа. Локальные данные сохранены."
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val cacheKey = "uploaded:" + pair.pairId + ":" + pair.accountUid
        if (prefs.getString(cacheKey, null) == hash) {
            diagnostics.log("sync", "own_snapshot_upload", "unchanged")
            return
        }
        val box = PairCrypto.seal(pair.key, pair.pairId, pair.accountUid, data)
        traced("own_snapshot_upload") {
            firestore.document(documentPath).set(
            mapOf(
                "writerUid" to pair.accountUid,
                "schema" to 1,
                "nonce" to box.nonce,
                "ciphertext" to box.ciphertext,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        }
        check(prefs.edit().putString(cacheKey, hash).commit()) {
            "Не удалось сохранить состояние синхронизации"
        }
    }

    private suspend fun applyRemote(pair: LocalPair, remote: CalendarSnapshot): Int {
        val otherRole = if (pair.role == Authors.SASHA) Authors.NASTYA else Authors.SASHA
        var applied = 0
        database.withTransaction {
            remote.events.forEach { candidate ->
                // A peer is never entitled to publish our personal calendar.
                require(Authors.hasPermission(candidate.authorMask, otherRole)) {
                    "Сервер передал событие не от его владельца"
                }
                val current = database.events().byId(candidate.id)
                when {
                    current == null -> {
                        database.events().save(candidate)
                        applied++
                    }
                    current == candidate -> {
                        database.sync().clearDirty(candidate.id)
                        database.sync().deleteConflict(candidate.id)
                    }
                    current.authorMask != candidate.authorMask -> {
                        database.sync().saveConflict(
                            SyncConflict(candidate.id, SnapshotCodec.eventToJson(candidate).toString(), System.currentTimeMillis())
                        )
                    }
                    candidate.revision < current.revision -> Unit
                    candidate.authorMask == Authors.BOTH &&
                        database.sync().isDirty(candidate.id) -> {
                        database.sync().saveConflict(
                            SyncConflict(candidate.id, SnapshotCodec.eventToJson(candidate).toString(), System.currentTimeMillis())
                        )
                    }
                    candidate.revision == current.revision -> {
                        database.sync().saveConflict(
                            SyncConflict(candidate.id, SnapshotCodec.eventToJson(candidate).toString(), System.currentTimeMillis())
                        )
                    }
                    else -> {
                        database.events().save(candidate)
                        applied++
                    }
                }
            }
            remote.reactions.forEach { incoming ->
                require(incoming.authorId == otherRole) {
                    "Сервер передал реакцию не от её владельца"
                }
                val current = database.reactions().get(incoming.dateEpochDay, incoming.authorId)
                if (current == null || incoming.revision > current.revision) {
                    database.reactions().upsert(incoming)
                    applied++
                }
            }
            // Both devices publish and edit the same logical heart per day.
            // Lamport revision and author tie-break prevent divergent snapshots.
            remote.sharedHearts.forEach { incoming ->
                val current = database.sharedHearts().get(incoming.dateEpochDay)
                if (SharedHeartRules.shouldAccept(current, incoming)) {
                    database.sharedHearts().upsert(incoming)
                    applied++
                }
            }
        }
        return applied
    }

    suspend fun resolve(eventId: String, useRemote: Boolean) {
        val pair = requireNotNull(PairManager(context).current())
        require(pair.active)
        database.withTransaction {
            val conflict = database.sync().conflicts().firstOrNull { it.eventId == eventId }
                ?: return@withTransaction
            val incoming = SnapshotCodec.eventFromJson(JSONObject(conflict.remoteJson))
            val local = database.events().byId(eventId) ?: return@withTransaction
            require(local.authorMask == Authors.BOTH && incoming.authorMask == Authors.BOTH) {
                "Конфликт личного события требует отдельного восстановления"
            }
            if (useRemote) {
                // Accept the remote snapshot verbatim. Both devices can now
                // converge on the SAME value and clear their dirty markers.
                database.events().save(
                    SharedConflictResolution.choose(local, incoming, true, pair.role, System.currentTimeMillis())
                )
                database.sync().clearDirty(eventId)
            } else {
                // A local choice is a genuinely new revision for the peer.
                database.events().save(
                    SharedConflictResolution.choose(local, incoming, false, pair.role, System.currentTimeMillis())
                )
                database.sync().markDirty(com.rteats.calendarik.data.DirtySharedEvent(eventId))
            }
            database.sync().deleteConflict(eventId)
        }
        diagnostics.log("sync", "conflict_resolved", "ok")
        requestSoon(context)
    }

    companion object {
        private val syncLock = Mutex()
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun requestSoon(context: Context) {
            NetworkDiagnostics.get(context).log("work", "sync_enqueue_now", "queued")
            WorkManager.getInstance(context).enqueueUniqueWork(
                "calendarik-sync-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                    .setConstraints(constraints)
                    .build()
            )
        }

        fun schedulePeriodic(context: Context) {
            NetworkDiagnostics.get(context).log("work", "sync_schedule_periodic", "queued")
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "calendarik-sync-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()
            )
        }
    }
}

class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val diagnostics = NetworkDiagnostics.get(applicationContext)
        diagnostics.log("work", "sync_worker", "start", count = runAttemptCount)
        if (PairManager(applicationContext).current()?.active != true) {
            diagnostics.log("work", "sync_worker", "no_pair")
            return Result.success()
        }
        return try {
            CloudSync(applicationContext).sync()
            diagnostics.log("work", "sync_worker", "success")
            Result.success()
        } catch (failure: Exception) {
            diagnostics.log("work", "sync_worker", "retry", failure = failure)
            Result.retry()
        }
    }
}
