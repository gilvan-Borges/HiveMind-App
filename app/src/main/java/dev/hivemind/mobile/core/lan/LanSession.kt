package dev.hivemind.mobile.core.lan

import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.relay.NaClBox
import dev.hivemind.mobile.core.relay.Redact
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** O SSE precisa de outro quadro depois do resume; não é falha de rede. */
class LanStreamRestart : Exception()

class LanUnauthorized : ApiException(LanMessages.REBIND, 401, "unauthorized")

/**
 * Sessão LAN já pareada. O seq começa em 1, é monótono e só avança quando o PC aceita.
 * Buraco pede resume; seq repetido sonda o `expectSeq` sem executar a rota de novo.
 */
class LanSession(
    val deviceId: String,
    private val privateKey: ByteArray,
    private val boxPub: ByteArray,
    private val token: String,
    lastSeq: Long,
    private val http: HttpTransport,
    private val onAcceptedSeq: (Long) -> Unit,
    private val onResume: (String) -> Unit,
    private val onUnauthorized: () -> Unit,
) {
    private val lock = Any()
    private var seq = lastSeq

    fun call(method: String, url: String, body: String?): String = synchronized(lock) {
        callLocked(method, url, body, recover = true)
    }

    /**
     * Envia o quadro do stream e devolve a resposta já aceita.
     * O chamador lê as linhas `data:` fora do cadeado, para o próximo pedido poder sair.
     */
    fun openStream(url: String): WireStream = synchronized(lock) {
        val sent = ++seq
        val frame = LanProtocol.frame(deviceId, sent, privateKey, boxPub, LanProtocol.withToken(null, token))
        val stream = try {
            http.open(
                WireRequest(
                    method = "GET",
                    url = url,
                    body = frame.toByteArray(Charsets.UTF_8),
                    accept = "text/event-stream",
                    readTimeoutMs = 60_000,
                ),
            )
        } catch (e: IOException) {
            throw ApiException(Redact.clean("falha de rede: ${e.message}"), null)
        }
        if (stream.status == 401) {
            val text = stream.bodyText()
            stream.close()
            onUnauthorized()
            throw unauthorizedOr(text)
        }
        if (stream.status == 409) {
            val text = stream.bodyText()
            stream.close()
            recoverLocked(text, url)
            throw LanStreamRestart()
        }
        if (stream.status !in 200..299) {
            val text = stream.bodyText()
            stream.close()
            throw apiError(stream.status, text)
        }
        persist(sent)
        stream
    }

    fun openEvent(data: String): String = LanProtocol.openSealed(privateKey, boxPub, data)

    private fun callLocked(method: String, url: String, body: String?, recover: Boolean): String {
        val sent = ++seq
        val plain = LanProtocol.withToken(body, token)
        val frame = LanProtocol.frame(deviceId, sent, privateKey, boxPub, plain)
        val resp = try {
            http.execute(WireRequest(method, url, frame.toByteArray(Charsets.UTF_8)))
        } catch (e: IOException) {
            throw ApiException(Redact.clean("falha de rede: ${e.message}"), null)
        }
        if (resp.status == 401) {
            onUnauthorized()
            throw unauthorizedOr(resp.body)
        }
        if (resp.status == 409 && recover) {
            recoverLocked(resp.body, url)
            return callLocked(method, url, body, recover = false)
        }
        val opened = runCatching { LanProtocol.openSealed(privateKey, boxPub, resp.body) }.getOrNull()
        if (resp.status !in 200..299) {
            throw apiError(resp.status, opened ?: resp.body)
        }
        if (opened == null) throw ApiException(LanMessages.BAD_RESPONSE, resp.status)
        persist(sent)
        return opened
    }

    private fun recoverLocked(raw: String, url: String) {
        val opened = runCatching { LanProtocol.openSealed(privateKey, boxPub, raw) }.getOrNull()
            ?: throw ApiException(LanMessages.SEQ, 409, "conflict")
        when (val conflict = LanProtocol.conflictOf(opened)) {
            is LanConflict.Gap -> resumeAt(conflict.expectSeq, url)
            LanConflict.Repeated -> resumeAt(probeExpect(url), url)
            LanConflict.Other -> throw apiError(409, opened)
        }
    }

    private fun probeExpect(url: String): Long {
        val frame = LanProtocol.frame(
            deviceId,
            LanProtocol.PROBE_SEQ,
            privateKey,
            boxPub,
            LanProtocol.withToken(null, token),
        )
        val resp = try {
            http.execute(WireRequest("GET", url, frame.toByteArray(Charsets.UTF_8)))
        } catch (e: IOException) {
            throw ApiException(Redact.clean("falha de rede: ${e.message}"), null)
        }
        if (resp.status == 401) {
            onUnauthorized()
            throw unauthorizedOr(resp.body)
        }
        val opened = runCatching { LanProtocol.openSealed(privateKey, boxPub, resp.body) }.getOrNull()
            ?: throw ApiException(LanMessages.SEQ, resp.status, "conflict")
        val gap = LanProtocol.conflictOf(opened) as? LanConflict.Gap
            ?: throw ApiException(LanMessages.SEQ, resp.status, "conflict")
        return gap.expectSeq
    }

    private fun resumeAt(expectSeq: Long, url: String) {
        if (expectSeq <= 0) throw ApiException(LanMessages.SEQ, 409, "conflict")
        seq = expectSeq - 1
        val sent = ++seq
        val frame = LanProtocol.frame(
            deviceId,
            sent,
            privateKey,
            boxPub,
            LanProtocol.resumePlain(token, expectSeq - 1),
        )
        val resp = try {
            http.execute(WireRequest("POST", url, frame.toByteArray(Charsets.UTF_8)))
        } catch (e: IOException) {
            throw ApiException(Redact.clean("falha de rede: ${e.message}"), null)
        }
        if (resp.status == 401) {
            onUnauthorized()
            throw unauthorizedOr(resp.body)
        }
        val opened = runCatching { LanProtocol.openSealed(privateKey, boxPub, resp.body) }.getOrNull()
        if (resp.status !in 200..299 || opened == null) {
            throw ApiException(LanMessages.SEQ, resp.status, "conflict")
        }
        persist(sent)
        onResume(opened)
    }

    private fun persist(accepted: Long) {
        seq = accepted
        runCatching { onAcceptedSeq(accepted) }
    }

    private fun unauthorizedOr(body: String): LanUnauthorized {
        Redact.clean(body)
        return LanUnauthorized()
    }

    private fun apiError(status: Int, body: String): ApiException {
        val code = errorCode(body)
        val message = when {
            status == 410 || code == "expired" -> LanMessages.EXPIRED
            status == 429 || code == "rate_limited" -> LanMessages.RATE_LIMITED
            status == 401 || code == "unauthorized" -> LanMessages.REBIND
            else -> errorMessage(body) ?: "HTTP $status"
        }
        return ApiException(Redact.clean(message), status, code)
    }

    private fun errorCode(text: String): String? {
        val root = runCatching { LanJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val err = root["error"] as? JsonObject ?: return null
        return err["code"]?.jsonPrimitive?.contentOrNull
    }

    private fun errorMessage(text: String): String? {
        val root = runCatching { LanJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val err = root["error"] ?: return null
        return when (err) {
            is JsonPrimitive -> err.contentOrNull
            is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }
}

object LanRedeem {
    fun post(
        http: HttpTransport,
        baseUrl: String,
        offer: String,
        offerPk: String,
        publicKey: ByteArray,
        privateKey: ByteArray,
    ): Redeemed {
        val pk = try {
            NaClBox.parseKey(offerPk)
        } catch (_: NaClBox.CryptoException) {
            throw ApiException(LanMessages.BAD_QR)
        }
        val payload = LanProtocol.redeemRequest(offer, pk, publicKey, privateKey)
        val resp = try {
            http.execute(
                WireRequest(
                    method = "POST",
                    url = baseUrl.trimEnd('/') + "/api/v1/pair/redeem",
                    body = payload.toByteArray(Charsets.UTF_8),
                ),
            )
        } catch (e: IOException) {
            throw ApiException(Redact.clean("falha de rede: ${e.message}"), null)
        }
        val code = runCatching {
            val root = LanJson.parseToJsonElement(resp.body) as? JsonObject
            (root?.get("error") as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        when {
            resp.status == 410 || code == "expired" -> throw ApiException(LanMessages.EXPIRED, 410, "expired")
            resp.status == 429 || code == "rate_limited" -> throw ApiException(LanMessages.RATE_LIMITED, 429, "rate_limited")
            resp.status !in 200..299 -> throw ApiException(LanMessages.BAD_OFFER, resp.status, code)
        }
        return try {
            LanProtocol.openRedeem(privateKey, pk, resp.body)
        } catch (_: Exception) {
            throw ApiException(LanMessages.BAD_RESPONSE, resp.status)
        }
    }
}
