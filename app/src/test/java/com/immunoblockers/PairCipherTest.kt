package com.immunoblockers
import com.immunoblockers.features.*
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException
class PairCipherTest {
    @Test fun authenticatedCiphertextRoundTripsWithoutExposingHealthText() {
        val key=ByteArray(32){it.toByte()};val aad="room|actor".toByteArray()
        val encrypted=PairCipher.seal(key,"лекарство 1.25 мг",aad)
        assertEquals("лекарство 1.25 мг",PairCipher.open(key,encrypted,aad))
        assertThrows(AEADBadTagException::class.java){PairCipher.open(key,encrypted,"other".toByteArray())}
        encrypted[encrypted.lastIndex]=(encrypted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java){PairCipher.open(key,encrypted,aad)}
    }
    @Test fun semanticVersionsRejectDowngradesAndNonReleaseTags() {
        assertTrue(Updates.newer("v0.3.1","0.3.0"));assertTrue(Updates.newer("v1.0.0","0.9.9"))
        assertFalse(Updates.newer("v0.2.0","0.3.0"));assertFalse(Updates.newer("v0.3.0-beta","0.2.0"));assertFalse(Updates.newer("v0.3.0","0.3.0"))
    }
}
