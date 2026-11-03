package com.rteats.calendarik.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class JoinRequest(val uid: String, val email: String)
data class PairStatus(val pair: LocalPair?, val pendingRequests: List<JoinRequest>)

/**
 * One creator and one joiner. Pair IDs are random, and the key is exchanged
 * out of band with a QR code. The creator explicitly approves the joiner.
 */
class PairManager(context: Context) {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val vault = PairVault(context)

    fun current(): LocalPair? = vault.load(auth.currentUser?.uid)

    suspend fun register(email: String, password: String) {
        require(password.length >= 6)
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() = auth.signOut()

    suspend fun create(role: Int): String {
        require(role in 1..2)
        val user = requireNotNull(auth.currentUser) { "Сначала войдите в аккаунт" }
        check(current() == null) { "Устройство уже связано с парой" }
        val key = PairCrypto.newKey()
        val pairId = UUID.randomUUID().toString()
        val ref = firestore.collection("pairs").document(pairId)
        ref.set(
            mapOf(
                "creatorUid" to user.uid,
                "creatorRole" to role,
                "memberUids" to listOf(user.uid),
                "joinerUid" to "",
                "schema" to 1,
            )
        ).await()
        val session = LocalPair(user.uid, pairId, user.uid, role, "", key)
        vault.save(session)
        return PairInvite(pairId, user.uid, role, key).asQrString()
    }

    fun invitation(): String {
        val pair = requireNotNull(current())
        check(pair.isCreator) { "Приглашение может создать только владелец пары" }
        return PairInvite(pair.pairId, pair.creatorUid, pair.role, pair.key).asQrString()
    }

    suspend fun requestJoin(invitation: String) {
        val invite = PairInvite.parse(invitation)
        val user = requireNotNull(auth.currentUser) { "Сначала войдите в аккаунт" }
        check(user.uid != invite.creatorUid) { "Нужен другой аккаунт" }
        check(current() == null) { "Устройство уже связано с парой" }
        val req = firestore.collection("pairs").document(invite.pairId)
            .collection("requests").document(user.uid)
        val previous = req.get(Source.SERVER).await()
        if (!previous.exists()) {
            req.set(
                mapOf(
                    "requesterUid" to user.uid,
                    "requesterEmail" to (user.email ?: ""),
                    "status" to "pending",
                )
            ).await()
        }
        vault.save(
            LocalPair(
                user.uid, invite.pairId, invite.creatorUid,
                if (invite.creatorRole == 1) 2 else 1,
                "", invite.key,
            )
        )
    }

    suspend fun requests(): List<JoinRequest> {
        val pair = requireNotNull(current())
        check(pair.isCreator)
        val snapshot = firestore.collection("pairs").document(pair.pairId)
            .collection("requests").get(Source.SERVER).await()
        return snapshot.documents.filter { it.getString("status") == "pending" }.map {
            JoinRequest(it.id, it.getString("requesterEmail") ?: "")
        }
    }

    suspend fun approve(requesterUid: String) {
        val pair = requireNotNull(current())
        check(pair.isCreator && !pair.active)
        val pairRef = firestore.collection("pairs").document(pair.pairId)
        val requestRef = pairRef.collection("requests").document(requesterUid)
        firestore.runTransaction { t ->
            val currentPair = t.get(pairRef)
            val request = t.get(requestRef)
            check(currentPair.getString("creatorUid") == pair.accountUid)
            check(currentPair.get("memberUids") is List<*>)
            check((currentPair.get("memberUids") as List<*>).size == 1)
            check(request.getString("status") == "pending")
            check(request.getString("requesterUid") == requesterUid)
            t.update(pairRef, mapOf(
                "memberUids" to listOf(pair.accountUid, requesterUid),
                "joinerUid" to requesterUid,
            ))
            t.update(requestRef, "status", "accepted")
            true
        }.await()
        vault.markActive(pair.accountUid, requesterUid)
    }

    suspend fun refresh(): LocalPair? {
        val pair = current() ?: return null
        val uid = requireNotNull(auth.currentUser).uid
        if (pair.active) return pair
        if (!pair.isCreator) {
            val req = firestore.collection("pairs").document(pair.pairId)
                .collection("requests").document(uid).get(Source.SERVER).await()
            if (req.getString("status") != "accepted") return pair
        }
        val metadata = firestore.collection("pairs").document(pair.pairId).get(Source.SERVER).await()
        val members = (metadata.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (metadata.getString("creatorUid") != pair.creatorUid || members.size != 2 ||
            !members.contains(uid)) return pair
        val peer = members.first { it != uid }
        vault.markActive(uid, peer)
        return current()
    }
}
