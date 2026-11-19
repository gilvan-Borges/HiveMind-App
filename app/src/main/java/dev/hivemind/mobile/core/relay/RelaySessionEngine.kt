package dev.hivemind.mobile.core.relay

import dev.hivemind.mobile.core.models.StreamEvent
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

sealed interface FrameEffect {
    data class Send(val text: String) : FrameEffect
    data class Events(val applied: List<Pair<Long, StreamEvent>>) : FrameEffect
    data class Rpc(val id: String, val status: Int, val body: String) : FrameEffect
    data class Hello(
        val peerPublicKey: String,
        val e2eReady: Boolean,
        val online: Boolean,
        val seq: Long,
    ) : FrameEffect
    data class Peer(val publicKey: String, val role: String) : FrameEffect
    data class RemoteError(val id: String, val code: String, val message: String) : FrameEffect
    data object Ignored : FrameEffect
}

/**
 * Estado do plano de dados de uma conexão: hello, ping/pong, RPC cifrado e
 * eventos em ordem de `seq`. Não abre socket.
 */
class RelaySessionEngine(
    private val keys: NaClBox.KeyPair,
    val deviceId: String,
    var resumeAfter: Long,
) {
    var peerPublic: ByteArray? = null
    private val cursor = EventCursor(resumeAfter)

    fun hello(): String {
        val payload = buildJsonObject {
            put("resumeAfter", resumeAfter)
            put("publicKey", NaClBox.encodeKey(keys.publicKey))
        }
        return frame(
            id = "hello-" + UUID.randomUUID().toString(),
            type = "hello",
            role = "app",
            payload = payload,
        )
    }

    fun sealRpc(id: String, method: String, path: String, query: String, bodyJson: String?): String {
        val peer = peerPublic ?: throw NaClBox.CryptoException("ainda não há chave da IDE")
        val plain = buildJsonObject {
            put("method", method)
            put("path", path)
            if (query.isNotBlank()) put("query", query)
            if (!bodyJson.isNullOrBlank()) put("body", RelayJson.parseToJsonElement(bodyJson))
        }
        val sealed = NaClBox.seal(keys.privateKey, peer, plain.toString().toByteArray(Charsets.UTF_8))
        val box = buildJsonObject {
            put("nonce", b64Any(sealed.nonce))
            put("ciphertext", b64Any(sealed.ciphertext))
        }
        return frame(id = id, type = "rpc", enc = "naclbox", payload = box)
    }

    fun onFrame(text: String): FrameEffect {
        val env = runCatching { RelayJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return FrameEffect.RemoteError("", "invalid_request", "envelope inválido")
        val type = env.str("type")
        val id = env.str("id")
        return when (type) {
            "ping" -> FrameEffect.Send(frame(id = id.ifBlank { "pong" }, type = "pong"))
            "pong", "ack" -> FrameEffect.Ignored
            "hello_ok" -> {
                val payload = env.obj("payload")
                FrameEffect.Hello(
                    peerPublicKey = payload?.str("peerPublicKey").orEmpty(),
                    e2eReady = payload?.bool("e2eReady") == true,
                    online = payload?.bool("online") == true,
                    seq = payload?.long("seq") ?: 0L,
                )
            }
            "peer_key" -> {
                val payload = env.obj("payload")
                FrameEffect.Peer(
                    publicKey = payload?.str("publicKey").orEmpty(),
                    role = payload?.str("role").orEmpty(),
                )
            }
            "error" -> {
                val payload = env.obj("payload")
                FrameEffect.RemoteError(
                    id = id,
                    code = payload?.str("code").orEmpty(),
                    message = payload?.str("message").orEmpty(),
                )
            }
            "event" -> openEvent(env)
            "rpc_result" -> openResult(id, env)
            else -> FrameEffect.Ignored
        }
    }

    private fun openEvent(env: JsonObject): FrameEffect {
        val seq = env.long("seq") ?: return FrameEffect.Ignored
        if (!cursor.accept(seq)) return FrameEffect.Ignored
        val plain = openBox(env.obj("payload")) ?: return FrameEffect.RemoteError("", "decrypt_failed", "não foi possível abrir o evento")
        val event = runCatching { RelayJson.decodeFromString<StreamEvent>(plain) }.getOrNull()
            ?: return FrameEffect.Ignored
        return FrameEffect.Events(listOf(seq to event))
    }

    private fun openResult(id: String, env: JsonObject): FrameEffect {
        val plain = openBox(env.obj("payload"))
            ?: return FrameEffect.RemoteError(id, "decrypt_failed", "não foi possível abrir a resposta")
        val obj = runCatching { RelayJson.parseToJsonElement(plain).jsonObject }.getOrNull()
            ?: return FrameEffect.RemoteError(id, "invalid_request", "resposta inválida")
        val status = obj.long("status")?.toInt() ?: 0
        val bodyEl = obj["body"]
        val body = when {
            bodyEl == null -> "{}"
            bodyEl.toString() == "null" -> "{}"
            else -> bodyEl.toString()
        }
        return FrameEffect.Rpc(id, status, body)
    }

    private fun openBox(payload: JsonObject?): String? {
        if (payload == null) return null
        val peer = peerPublic ?: return null
        val nonce = payload.str("nonce")
        val ct = payload.str("ciphertext")
        if (nonce.isBlank() || ct.isBlank()) return null
        return runCatching {
            val sealed = NaClBox.Sealed(NaClBox.decodeFlexible(nonce), NaClBox.decodeFlexible(ct))
            NaClBox.open(keys.privateKey, peer, sealed).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun frame(
        id: String,
        type: String,
        role: String? = null,
        enc: String? = null,
        payload: JsonObject? = null,
    ): String {
        val obj = buildJsonObject {
            put("v", 1)
            put("id", id)
            put("type", type)
            if (!role.isNullOrBlank()) put("role", role)
            put("deviceId", deviceId)
            if (!enc.isNullOrBlank()) put("enc", enc)
            if (payload != null) put("payload", payload)
        }
        return obj.toString()
    }

    private fun b64Any(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.bool(key: String): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull == true

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.obj(key: String): JsonObject? =
        this[key] as? JsonObject
}
