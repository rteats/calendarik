package com.rteats.calendarik.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The pair key is wrapped by a non-exportable AndroidKeyStore key. Backups are disabled. */
data class LocalPair(
    val accountUid: String,
    val pairId: String,
    val creatorUid: String,
    val role: Int,
    val peerUid: String,
    val key: ByteArray,
) {
    val active: Boolean get() = peerUid.isNotBlank()
    val isCreator: Boolean get() = accountUid == creatorUid
}

class PairVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calendarik_pair_v1", Context.MODE_PRIVATE)
    private val alias = "calendarik_pair_wrapping_v1"

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun load(uid: String?): LocalPair? {
        if (uid.isNullOrBlank() || prefs.getString("accountUid", "") != uid) return null
        val cipherBytes = PairCrypto.unb64(prefs.getString("wrappedKey", null) ?: return null)
        require(cipherBytes.size >= 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, cipherBytes.copyOfRange(0, 12)))
        val pairId = prefs.getString("pairId", null) ?: return null
        cipher.updateAAD((uid + "|" + pairId).toByteArray(Charsets.UTF_8))
        val key = cipher.doFinal(cipherBytes.copyOfRange(12, cipherBytes.size))
        require(key.size == 32)
        return LocalPair(
            accountUid = uid,
            pairId = pairId,
            creatorUid = prefs.getString("creatorUid", "") ?: "",
            role = prefs.getInt("role", 0),
            peerUid = prefs.getString("peerUid", "") ?: "",
            key = key,
        )
    }

    fun save(pair: LocalPair) {
        require(pair.key.size == 32 && pair.role in 1..2)
        val previousAccount = prefs.getString("accountUid", null)
        require(previousAccount == null || previousAccount == pair.accountUid) {
            "На этом устройстве уже сохранён ключ другой учётной записи. Нельзя перезаписывать его."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        cipher.updateAAD((pair.accountUid + "|" + pair.pairId).toByteArray(Charsets.UTF_8))
        val wrapped = cipher.iv + cipher.doFinal(pair.key)
        check(prefs.edit()
            .putString("accountUid", pair.accountUid)
            .putString("pairId", pair.pairId)
            .putString("creatorUid", pair.creatorUid)
            .putInt("role", pair.role)
            .putString("peerUid", pair.peerUid)
            .putString("wrappedKey", PairCrypto.b64(wrapped))
            .commit()) { "Не удалось сохранить ключ в защищённом хранилище" }
    }

    fun markActive(uid: String, otherUid: String) {
        require(otherUid.isNotBlank() && otherUid != uid)
        val local = requireNotNull(load(uid))
        save(local.copy(peerUid = otherUid))
    }
}
