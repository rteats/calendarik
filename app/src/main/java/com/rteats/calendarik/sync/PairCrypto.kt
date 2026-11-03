package com.rteats.calendarik.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedSnapshot(val nonce: String, val ciphertext: String)

object PairCrypto {
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun seal(key: ByteArray, pairId: String, writerUid: String, text: String): EncryptedSnapshot {
        require(key.size == 32)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(pairId, writerUid))
        return EncryptedSnapshot(b64(nonce), b64(cipher.doFinal(text.toByteArray(Charsets.UTF_8))))
    }

    fun open(key: ByteArray, pairId: String, writerUid: String, box: EncryptedSnapshot): String {
        require(key.size == 32)
        val nonce = unb64(box.nonce)
        require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(pairId, writerUid))
        return String(cipher.doFinal(unb64(box.ciphertext)), Charsets.UTF_8)
    }

    private fun aad(pairId: String, writerUid: String): ByteArray =
        ("calendarik|snapshot|1|" + pairId + "|" + writerUid).toByteArray(Charsets.UTF_8)

    fun b64(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    fun unb64(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}

data class PairInvite(val pairId: String, val creatorUid: String, val creatorRole: Int, val key: ByteArray) {
    fun asQrString(): String {
        require(creatorRole == 1 || creatorRole == 2)
        require(key.size == 32)
        return "calendarik:pair:1:" + pairId + ":" + creatorUid + ":" + creatorRole + ":" + PairCrypto.b64(key)
    }

    companion object {
        fun parse(input: String): PairInvite {
            val parts = input.trim().split(':')
            require(parts.size == 7 && parts[0] == "calendarik" && parts[1] == "pair" && parts[2] == "1") {
                "Это не приглашение Календарика"
            }
            val pairId = parts[3]
            val uid = parts[4]
            require(Regex("[a-f0-9-]{36}").matches(pairId))
            require(Regex("[A-Za-z0-9_-]{10,128}").matches(uid))
            val role = parts[5].toInt()
            require(role == 1 || role == 2)
            val key = PairCrypto.unb64(parts[6])
            require(key.size == 32) { "Неверный ключ" }
            return PairInvite(pairId, uid, role, key)
        }
    }
}
