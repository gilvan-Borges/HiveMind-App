package dev.hivemind.mobile.core.relay

import java.net.URLDecoder
import java.net.URLEncoder

/** URI `hivemind://link` e o código de 10 caracteres do relay. */
object LinkCodes {

    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun normalize(code: String): String =
        code.uppercase().replace("-", "").replace(" ", "").trim()

    fun isValid(code: String): Boolean {
        val n = normalize(code)
        return n.length == 10 && n.all { it in ALPHABET }
    }

    /** `ABCD-EFGH-JK` para mostrar na tela. O relay ignora hífens. */
    fun format(code: String): String {
        val n = normalize(code)
        if (n.length != 10) return n
        return n.substring(0, 4) + "-" + n.substring(4, 8) + "-" + n.substring(8, 10)
    }

    fun uri(relayBase: String, code: String): String {
        val base = RelayUrls.normalize(relayBase)
        val c = normalize(code)
        val relay = URLEncoder.encode(base, "UTF-8")
        val encoded = URLEncoder.encode(c, "UTF-8")
        return "hivemind://link?relay=$relay&code=$encoded"
    }

    /** @return relay base (como veio no QR) e código normalizado. */
    fun parse(raw: String): Pair<String, String>? {
        val text = raw.trim()
        if (!text.startsWith("hivemind://link")) return null
        val query = text.substringAfter("?", "")
        if (query.isEmpty()) return null
        val params = query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
        val relay = params["relay"]?.trim().orEmpty()
        val code = normalize(params["code"].orEmpty())
        if (relay.isEmpty() || code.isEmpty()) return null
        return relay to code
    }

    /** @return relay base e ticket de uso único de `hivemind://qr-login?relay=...&ticket=...`. */
    fun parseQrLogin(raw: String): Pair<String, String>? {
        val text = raw.trim()
        if (!text.startsWith("hivemind://qr-login")) return null
        val query = text.substringAfter("?", "")
        if (query.isEmpty()) return null
        val params = query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
        val relay = params["relay"]?.trim().orEmpty()
        val ticket = params["ticket"]?.trim().orEmpty()
        if (relay.isEmpty() || ticket.isEmpty()) return null
        return relay to ticket
    }
}
