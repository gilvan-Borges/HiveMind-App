package dev.hivemind.mobile.core.relay

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

/**
 * NaCl box (`crypto_box`: X25519 + XSalsa20-Poly1305), o mesmo de
 * `golang.org/x/crypto/nacl/box`. O plaintext leva um byte de versão `0x01`
 * na frente. O nonce de 24 bytes nunca é reutilizado com o mesmo par.
 */
object NaClBox {

    class CryptoException(message: String) : Exception(message)

    data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
        init {
            require(publicKey.size == 32 && privateKey.size == 32)
        }

        fun wipe() {
            privateKey.fill(0)
        }
    }

    data class Sealed(val nonce: ByteArray, val ciphertext: ByteArray)

    fun generate(random: SecureRandom = SecureRandom()): KeyPair {
        val priv = X25519PrivateKeyParameters(random)
        val pub = priv.generatePublicKey()
        return KeyPair(pub.encoded.copyOf(), priv.encoded.copyOf())
    }

    fun seal(
        privateKey: ByteArray,
        peerPublic: ByteArray,
        plaintext: ByteArray,
        nonce: ByteArray = randomNonce(),
        random: SecureRandom = SecureRandom(),
    ): Sealed {
        require(nonce.size == 24) { "nonce inválido" }
        val msg = ByteArray(1 + plaintext.size)
        msg[0] = 0x01
        System.arraycopy(plaintext, 0, msg, 1, plaintext.size)
        val shared = precompute(privateKey, peerPublic)
        try {
            return Sealed(nonce.copyOf(), secretboxSeal(shared, nonce, msg))
        } finally {
            shared.fill(0)
            msg.fill(0)
        }
    }

    fun open(privateKey: ByteArray, peerPublic: ByteArray, sealed: Sealed): ByteArray {
        if (sealed.nonce.size != 24) throw CryptoException("nonce inválido")
        val shared = precompute(privateKey, peerPublic)
        try {
            val opened = secretboxOpen(shared, sealed.nonce, sealed.ciphertext)
                ?: throw CryptoException("falha ao abrir naclbox")
            if (opened.isEmpty() || opened[0] != 0x01.toByte()) {
                opened.fill(0)
                throw CryptoException("falha ao abrir naclbox")
            }
            return opened.copyOfRange(1, opened.size)
        } finally {
            shared.fill(0)
        }
    }

    fun encodeKey(key: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    /** Pública X25519 correspondente à privada de 32 bytes. */
    fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32)
        return X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded.copyOf()
    }

    /** Aceita base64 padrão ou base64url, com ou sem padding. */
    fun parseKey(raw: String): ByteArray {
        val decoded = try {
            decodeFlexible(raw.trim())
        } catch (_: IllegalArgumentException) {
            throw CryptoException("chave pública inválida")
        }
        if (decoded.size != 32) throw CryptoException("chave pública inválida")
        return decoded
    }

    fun sameKey(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return runCatching { MessageDigest.isEqual(parseKey(a), parseKey(b)) }.getOrDefault(false)
    }

    fun decodeFlexible(raw: String): ByteArray {
        val text = raw.trim()
        if (text.isEmpty()) return ByteArray(0)
        val padded = when (val mod = text.length % 4) {
            0 -> text
            else -> text + "=".repeat(4 - mod)
        }
        return try {
            Base64.getUrlDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            Base64.getDecoder().decode(padded)
        }
    }

    private fun randomNonce(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(24).also { random.nextBytes(it) }

    /** HSalsa20(zeros, X25519(priv, peer)) — `box.Precompute`. */
    private fun precompute(privateKey: ByteArray, peerPublic: ByteArray): ByteArray {
        val shared = ByteArray(32)
        try {
            X25519PrivateKeyParameters(privateKey, 0)
                .generateSecret(X25519PublicKeyParameters(peerPublic, 0), shared, 0)
            return hsalsa20(ByteArray(16), shared)
        } finally {
            shared.fill(0)
        }
    }

    private fun secretboxSeal(key: ByteArray, nonce: ByteArray, message: ByteArray): ByteArray {
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        val input = ByteArray(32 + message.size)
        System.arraycopy(message, 0, input, 32, message.size)
        val stream = ByteArray(input.size)
        engine.processBytes(input, 0, input.size, stream, 0)
        val polyKey = stream.copyOfRange(0, 32)
        val ct = stream.copyOfRange(32, stream.size)
        try {
            val tag = poly1305(polyKey, ct)
            val out = ByteArray(16 + ct.size)
            System.arraycopy(tag, 0, out, 0, 16)
            System.arraycopy(ct, 0, out, 16, ct.size)
            return out
        } finally {
            polyKey.fill(0)
            input.fill(0)
        }
    }

    private fun secretboxOpen(key: ByteArray, nonce: ByteArray, box: ByteArray): ByteArray? {
        if (box.size < 16) return null
        val tag = box.copyOfRange(0, 16)
        val ct = box.copyOfRange(16, box.size)
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        val polyKey = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, polyKey, 0)
        val expect = poly1305(polyKey, ct)
        polyKey.fill(0)
        if (!MessageDigest.isEqual(tag, expect)) return null
        val plain = ByteArray(ct.size)
        engine.processBytes(ct, 0, ct.size, plain, 0)
        return plain
    }

    private fun poly1305(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Poly1305()
        mac.init(KeyParameter(key))
        mac.update(message, 0, message.size)
        val tag = ByteArray(16)
        mac.doFinal(tag, 0)
        return tag
    }

    /** HSalsa20 de 20 rodadas, constante sigma "expand 32-byte k". */
    private fun hsalsa20(input16: ByteArray, key32: ByteArray): ByteArray {
        val c = "expand 32-byte k".toByteArray(Charsets.US_ASCII)
        var x0 = u32(c, 0)
        var x1 = u32(key32, 0)
        var x2 = u32(key32, 4)
        var x3 = u32(key32, 8)
        var x4 = u32(key32, 12)
        var x5 = u32(c, 4)
        var x6 = u32(input16, 0)
        var x7 = u32(input16, 4)
        var x8 = u32(input16, 8)
        var x9 = u32(input16, 12)
        var x10 = u32(c, 8)
        var x11 = u32(key32, 16)
        var x12 = u32(key32, 20)
        var x13 = u32(key32, 24)
        var x14 = u32(key32, 28)
        var x15 = u32(c, 12)
        repeat(10) {
            var u: Int
            u = x0 + x12; x4 = x4 xor rotl(u, 7); u = x4 + x0; x8 = x8 xor rotl(u, 9); u = x8 + x4; x12 = x12 xor rotl(u, 13); u = x12 + x8; x0 = x0 xor rotl(u, 18)
            u = x5 + x1; x9 = x9 xor rotl(u, 7); u = x9 + x5; x13 = x13 xor rotl(u, 9); u = x13 + x9; x1 = x1 xor rotl(u, 13); u = x1 + x13; x5 = x5 xor rotl(u, 18)
            u = x10 + x6; x14 = x14 xor rotl(u, 7); u = x14 + x10; x2 = x2 xor rotl(u, 9); u = x2 + x14; x6 = x6 xor rotl(u, 13); u = x6 + x2; x10 = x10 xor rotl(u, 18)
            u = x15 + x11; x3 = x3 xor rotl(u, 7); u = x3 + x15; x7 = x7 xor rotl(u, 9); u = x7 + x3; x11 = x11 xor rotl(u, 13); u = x11 + x7; x15 = x15 xor rotl(u, 18)
            u = x0 + x3; x1 = x1 xor rotl(u, 7); u = x1 + x0; x2 = x2 xor rotl(u, 9); u = x2 + x1; x3 = x3 xor rotl(u, 13); u = x3 + x2; x0 = x0 xor rotl(u, 18)
            u = x5 + x4; x6 = x6 xor rotl(u, 7); u = x6 + x5; x7 = x7 xor rotl(u, 9); u = x7 + x6; x4 = x4 xor rotl(u, 13); u = x4 + x7; x5 = x5 xor rotl(u, 18)
            u = x10 + x9; x11 = x11 xor rotl(u, 7); u = x11 + x10; x8 = x8 xor rotl(u, 9); u = x8 + x11; x9 = x9 xor rotl(u, 13); u = x9 + x8; x10 = x10 xor rotl(u, 18)
            u = x15 + x14; x12 = x12 xor rotl(u, 7); u = x12 + x15; x13 = x13 xor rotl(u, 9); u = x13 + x12; x14 = x14 xor rotl(u, 13); u = x14 + x13; x15 = x15 xor rotl(u, 18)
        }
        val out = ByteArray(32)
        put(out, 0, x0); put(out, 4, x5); put(out, 8, x10); put(out, 12, x15)
        put(out, 16, x6); put(out, 20, x7); put(out, 24, x8); put(out, 28, x9)
        return out
    }

    private fun rotl(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 255) or
            ((b[o + 1].toInt() and 255) shl 8) or
            ((b[o + 2].toInt() and 255) shl 16) or
            ((b[o + 3].toInt() and 255) shl 24)

    private fun put(o: ByteArray, off: Int, v: Int) {
        o[off] = v.toByte()
        o[off + 1] = (v ushr 8).toByte()
        o[off + 2] = (v ushr 16).toByte()
        o[off + 3] = (v ushr 24).toByte()
    }
}
