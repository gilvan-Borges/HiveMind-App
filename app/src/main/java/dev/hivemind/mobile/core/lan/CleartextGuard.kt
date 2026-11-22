package dev.hivemind.mobile.core.lan

import java.net.InetAddress

/**
 * Allowlist de cleartext do transporte LAN (Spec 10 §3.1.3): o bridge roda em
 * HTTP porque o IP do PC só existe depois do QR, e a plataforma Android não
 * aceita CIDR no network_security_config. A restrição "só LAN privada /
 * Tailscale" é aplicada aqui, antes de abrir o socket:
 *
 *  - "localhost" e loopback (127.0.0.0/8, ::1);
 *  - RFC1918: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16;
 *  - CGNAT 100.64.0.0/10 (faixa do Tailscale).
 *
 * Hostnames que não são literal de IP são sempre recusados: o QR do bridge
 * carrega IP, então nome em cleartext é anomalia — e assim a decisão nunca
 * depende de um lookup de DNS. Qualquer outro host em http:// é recusado;
 * hosts públicos exigem HTTPS.
 */
object CleartextGuard {

    fun isPrivateHost(rawHost: String): Boolean {
        val host = rawHost.trim().trimStart('[').trimEnd(']')
        if (host.isEmpty()) return false
        if (host.equals("localhost", ignoreCase = true)) return true
        // Hostname (não literal de IP): proibido sem consultar DNS.
        if (':' !in host && !isIpv4Literal(host)) return false
        // Literal de IP: InetAddress.getByName não faz rede para literais.
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        // IPv6: só loopback (::1). Site-local/ULA não entram na política documentada
        // (RFC1918 IPv4 + Tailscale 100.64/10); evita ampliar cleartext além do Spec.
        if (addr.address?.size != 4) {
            return addr.isLoopbackAddress
        }
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress) return true
        return inTailscaleRange(addr)
    }

    private fun isIpv4Literal(host: String): Boolean {
        if (host.count { it == '.' } != 3) return false
        return host.split('.').all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() }
        }
    }

    /** CGNAT 100.64.0.0/10 sem depender de API de plataforma. */
    private fun inTailscaleRange(addr: InetAddress): Boolean {
        val bytes = addr.address ?: return false
        if (bytes.size != 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        return b0 == 100 && b1 in 64..127
    }
}
