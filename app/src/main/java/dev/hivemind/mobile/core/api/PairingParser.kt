package dev.hivemind.mobile.core.api

import dev.hivemind.mobile.core.relay.NaClBox
import java.net.URLDecoder

/** Resultado de `hivemind://pair`. Token na query é recusado, inclusive em v=2. */
sealed interface PairQr {
    data class Offer(
        val urls: List<String>,
        val offer: String,
        val pk: String,
        val exp: Long,
        val host: String,
    ) : PairQr

    data object RejectedToken : PairQr
    data object Invalid : PairQr
}

object PairingParser {
    fun parse(raw: String): PairQr {
        val text = raw.trim()
        if (!text.startsWith("hivemind://pair")) return PairQr.Invalid
        val query = text.substringAfter("?", "")
        if (query.isEmpty() || query == text) return PairQr.Invalid
        val parts = query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = urlDecode(part.substring(0, idx))
            val value = urlDecode(part.substring(idx + 1))
            key to value
        }
        if (parts.any { it.first == "token" && it.second.isNotEmpty() }) return PairQr.RejectedToken
        val params = parts.toMap()
        if (params["v"] != "2") return PairQr.Invalid
        val urls = params["urls"].orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }
        val offer = params["offer"].orEmpty()
        val pk = params["pk"].orEmpty()
        val exp = params["exp"]?.toLongOrNull()
        if (urls.isEmpty() || exp == null || exp <= 0) return PairQr.Invalid
        val offerRaw = runCatching { NaClBox.decodeFlexible(offer) }.getOrNull()
        val pkRaw = runCatching { NaClBox.decodeFlexible(pk) }.getOrNull()
        if (offerRaw == null || offerRaw.size != 16 || pkRaw == null || pkRaw.size != 32) return PairQr.Invalid
        return PairQr.Offer(urls, offer, pk, exp, params["host"].orEmpty())
    }

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, Charsets.UTF_8.name())
}
