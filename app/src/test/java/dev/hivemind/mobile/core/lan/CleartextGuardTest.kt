package dev.hivemind.mobile.core.lan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextGuardTest {

    @Test
    fun `loopback e localhost sao cleartext permitido`() {
        assertTrue(CleartextGuard.isPrivateHost("localhost"))
        assertTrue(CleartextGuard.isPrivateHost("127.0.0.1"))
        assertTrue(CleartextGuard.isPrivateHost("127.1.2.3"))
    }

    @Test
    fun `rfc1918 aceita cleartext`() {
        assertTrue(CleartextGuard.isPrivateHost("192.168.0.15"))
        assertTrue(CleartextGuard.isPrivateHost("10.42.0.7"))
        assertTrue(CleartextGuard.isPrivateHost("172.16.0.1"))
        assertTrue(CleartextGuard.isPrivateHost("172.31.255.254"))
    }

    @Test
    fun `faixa tailscale cgnat aceita cleartext`() {
        assertTrue(CleartextGuard.isPrivateHost("100.64.0.1"))
        assertTrue(CleartextGuard.isPrivateHost("100.127.255.254"))
    }

    @Test
    fun `hosts publicos e faixas vizinhas sao proibidos`() {
        assertFalse(CleartextGuard.isPrivateHost("189.45.12.9"))
        assertFalse(CleartextGuard.isPrivateHost("8.8.8.8"))
        // Limite da faixa CGNAT: 100.63.x e 100.128.x são públicos.
        assertFalse(CleartextGuard.isPrivateHost("100.63.255.254"))
        assertFalse(CleartextGuard.isPrivateHost("100.128.0.1"))
        // 172.32+ é público (RFC1918 termina em 172.31).
        assertFalse(CleartextGuard.isPrivateHost("172.32.0.1"))
        // 192.169 é público (RFC1918 termina em 192.168).
        assertFalse(CleartextGuard.isPrivateHost("192.169.0.1"))
        assertFalse(CleartextGuard.isPrivateHost("relay.exemplo.com"))
        assertFalse(CleartextGuard.isPrivateHost(""))
    }
}
