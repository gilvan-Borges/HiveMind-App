package dev.hivemind.mobile.core.relay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NaClBoxTest {

    @Test
    fun `abre vetor gerado pelo nacl box do Go`() {
        val alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val alicePub = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        val bobPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val bobPub = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
        val nonce = hex("69696ee955b62b73cd62bda875fc73d68219e0036b7a0b37")
        val expected = hex("31bbfc2899a1203895a8336822f6788131f60d2c11c48dcf63e66edebc7b1bcc")

        val sealed = NaClBox.seal(alicePriv, bobPub, "hive-mind-relay".toByteArray(), nonce)
        assertArrayEquals(expected, sealed.ciphertext)

        val plain = NaClBox.open(bobPriv, alicePub, sealed)
        assertArrayEquals("hive-mind-relay".toByteArray(), plain)
        assertArrayEquals(alicePub, NaClBox.publicFromPrivate(alicePriv))
    }

    @Test
    fun `roundtrip e recusa caixa adulterada ou chave errada`() {
        val a = NaClBox.generate()
        val b = NaClBox.generate()
        val sealed = NaClBox.seal(a.privateKey, b.publicKey, "aprovar".toByteArray())
        assertArrayEquals("aprovar".toByteArray(), NaClBox.open(b.privateKey, a.publicKey, sealed))
        sealed.ciphertext[20] = (sealed.ciphertext[20].toInt() xor 1).toByte()
        assertThrows(NaClBox.CryptoException::class.java) {
            NaClBox.open(b.privateKey, a.publicKey, sealed)
        }
        val other = NaClBox.generate()
        val fresh = NaClBox.seal(a.privateKey, b.publicKey, byteArrayOf(1, 2, 3))
        assertThrows(NaClBox.CryptoException::class.java) {
            NaClBox.open(other.privateKey, a.publicKey, fresh)
        }
    }

    @Test
    fun `chave aceita base64 padrão e url`() {
        val pub = NaClBox.generate().publicKey
        val url = NaClBox.encodeKey(pub)
        val std = java.util.Base64.getEncoder().encodeToString(pub)
        assertTrue(NaClBox.sameKey(url, std))
        assertFalse(NaClBox.sameKey(url, NaClBox.encodeKey(NaClBox.generate().publicKey)))
        assertThrows(NaClBox.CryptoException::class.java) { NaClBox.parseKey("curta") }
    }

    private fun hex(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }
}
