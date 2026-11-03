package com.rteats.calendarik.sync

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PairCryptoTest {
    @Test fun aesGcmRoundTripAndFreshNonce() {
        val key = PairCrypto.newKey()
        val pair = UUID.randomUUID().toString()
        val encrypted = PairCrypto.seal(key, pair, "uid", "private calendar 🩷")
        val second = PairCrypto.seal(key, pair, "uid", "private calendar 🩷")
        assertNotEquals(encrypted.nonce, second.nonce)
        assertFalse(encrypted.ciphertext.contains("private"))
        assertEquals("private calendar 🩷", PairCrypto.open(key, pair, "uid", encrypted))
    }

    @Test fun wrongUserOrKeyOrAlteredContentFails() {
        val key = PairCrypto.newKey()
        val encrypted = PairCrypto.seal(key, "pair", "sasha", "secret")
        assertThrows(Exception::class.java) { PairCrypto.open(key, "pair", "nastya", encrypted) }
        assertThrows(Exception::class.java) { PairCrypto.open(PairCrypto.newKey(), "pair", "sasha", encrypted) }
        assertThrows(Exception::class.java) {
            PairCrypto.open(key, "pair", "sasha", encrypted.copy(ciphertext = PairCrypto.b64(ByteArray(24))))
        }
    }

    @Test fun invitationRoundTrip() {
        val invite = PairInvite(UUID.randomUUID().toString(), "1234567890_abCDEF", 2, PairCrypto.newKey())
        val restored = PairInvite.parse(invite.asQrString())
        assertEquals(invite.pairId, restored.pairId)
        assertEquals(invite.creatorRole, restored.creatorRole)
        assertArrayEquals(invite.key, restored.key)
        assertThrows(Exception::class.java) { PairInvite.parse("http://bad.example.com") }
    }
}
