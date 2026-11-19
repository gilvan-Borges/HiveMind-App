package dev.hivemind.mobile.core.relay

import java.net.URI

/** Normaliza a URL do relay. HTTP só é aceito em loopback; o resto exige HTTPS. */
object RelayUrls {

    fun normalize(raw: String): String {
        val text = raw.trim()
        val uri = try {
            URI(text)
        } catch (_: Exception) {
            throw IllegalArgumentException("URL do relay inválida")
        }
        val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("URL do relay inválida")
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || uri.userInfo != null) {
            throw IllegalArgumentException("URL do relay inválida")
        }
        if (scheme != "https" && scheme != "http") {
            throw IllegalArgumentException("URL do relay inválida")
        }
        if (scheme == "http" && !isLoopback(host)) {
            throw IllegalArgumentException("um relay público exige https")
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        var path = uri.rawPath?.trimEnd('/').orEmpty()
        if (path == "/") path = ""
        val hostFmt = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "$scheme://$hostFmt$port$path"
    }

    fun websocket(base: String): String {
        val normalized = normalize(base)
        val suffix = "/api/v1/ws"
        return when {
            normalized.startsWith("https://") -> "wss://" + normalized.removePrefix("https://") + suffix
            normalized.startsWith("http://") -> "ws://" + normalized.removePrefix("http://") + suffix
            else -> throw IllegalArgumentException("URL do relay inválida")
        }
    }

    fun isLoopback(host: String): Boolean {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        return h == "localhost" || h == "127.0.0.1" || h == "::1"
    }

    fun same(a: String, b: String): Boolean =
        runCatching { normalize(a) == normalize(b) }.getOrDefault(false)
}
