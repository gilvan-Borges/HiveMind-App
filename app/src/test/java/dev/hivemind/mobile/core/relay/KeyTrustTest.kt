package dev.hivemind.mobile.core.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTrustTest {

    @Test
    fun `primeira chave e pinada, chave igual e adotada, divergente exige confirmacao`() {
        // Sem pin: primeiro uso (TOFU).
        val first = KeyTrustPolicy.evaluate(null, "pk_a")
        assertTrue(first is KeyTrust.FirstUse)
        assertEquals("pk_a", (first as KeyTrust.FirstUse).newKey)

        // Mesma chave: adoção silenciosa.
        assertTrue(KeyTrustPolicy.evaluate("pk_a", "pk_a") is KeyTrust.Match)

        // Chave diferente: rotação pendente de confirmação — nunca silenciosa.
        val rotated = KeyTrustPolicy.evaluate("pk_a", "pk_b")
        assertTrue(rotated is KeyTrust.Rotated)
        assertEquals("pk_a", (rotated as KeyTrust.Rotated).pinnedKey)
        assertEquals("pk_b", rotated.incomingKey)

        // Chave vazia: a IDE ainda não publicou.
        assertTrue(KeyTrustPolicy.evaluate("pk_a", "") is KeyTrust.NoKey)
    }

    @Test
    fun `fingerprint e curto, deterministico e diferente por chave`() {
        val pair = NaClBox.generate()
        val key = NaClBox.encodeKey(pair.publicKey)
        val fp1 = KeyTrustPolicy.fingerprint(key)
        val fp2 = KeyTrustPolicy.fingerprint(key)
        assertEquals(fp1, fp2)
        assertEquals(16, fp1.length)
        assertTrue(fp1.all { it.isDigit() || it in 'A'..'F' })

        val other = NaClBox.generate()
        assertNotEquals(fp1, KeyTrustPolicy.fingerprint(NaClBox.encodeKey(other.publicKey)))
        assertEquals("—", KeyTrustPolicy.fingerprint("lixo!!"))
    }

    @Test
    fun `peer_presence vira effect com online e role`() {
        val engine = RelaySessionEngine(NaClBox.generate(), "dev_abc", resumeAfter = 0)
        val effect = engine.onFrame(
            """{"v":1,"type":"peer_presence","deviceId":"dev_abc","payload":{"online":true,"role":"ide"}}""",
        ) as FrameEffect.Presence
        assertTrue(effect.online)
        assertEquals("ide", effect.role)

        val offline = engine.onFrame(
            """{"v":1,"type":"peer_presence","deviceId":"dev_abc","payload":{"online":false,"role":"ide"}}""",
        ) as FrameEffect.Presence
        assertTrue(!offline.online)
    }

    @Test
    fun `peer key de outra role de app nao e effect relevante`() {
        val engine = RelaySessionEngine(NaClBox.generate(), "dev_abc", resumeAfter = 0)
        val effect = engine.onFrame(
            """{"v":1,"type":"peer_key","payload":{"publicKey":"abc","role":"app"}}""",
        ) as FrameEffect.Peer
        // O controller decide pelo role; o engine só espelha o envelope.
        assertEquals("app", effect.role)
        assertEquals("abc", effect.publicKey)
    }
}
