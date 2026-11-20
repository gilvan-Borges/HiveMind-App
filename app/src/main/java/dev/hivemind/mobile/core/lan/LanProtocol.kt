package dev.hivemind.mobile.core.lan

import dev.hivemind.mobile.core.relay.NaClBox
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** JSON estável do fio LAN. Não depende do `encodeDefaults` do resto do app. */
internal val LanJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class BoxWire(val nonce: String, val ciphertext: String)

@Serializable
data class LanFrameWire(
    val v: Int,
    val deviceId: String,
    val seq: Long,
    val enc: String,
    val payload: BoxWire,
)

@Serializable
data class RedeemWire(val appPub: String, val nonce: String, val ciphertext: String)

@Serializable
private data class RedeemPlain(val v: Int, val offer: String, val appPub: String, val nonce: String)

@Serializable
data class Redeemed(val v: Int = 0, val token: String = "", val boxPub: String = "", val deviceId: String = "")

sealed interface LanConflict {
    data class Gap(val expectSeq: Long) : LanConflict
    data object Repeated : LanConflict
    data object Other : LanConflict
}

/**
 * Oferta v2 e quadro autenticado, no mesmo formato do bridge:
 * `crypto_box` (X25519 + XSalsa20-Poly1305) com o byte de versão `0x01`.
 */
object LanProtocol {
    const val VERSION = 2
    const val ENC = "naclbox"
    const val TOKEN_PREFIX = "hmlan_"
    const val PROBE_SEQ = Long.MAX_VALUE
    const val MAX_RESUME_EVENTS = 60

    fun innerNonce(random: SecureRandom = SecureRandom()): String {
        val raw = ByteArray(16)
        random.nextBytes(raw)
        return NaClBox.encodeKey(raw)
    }

    /** Corpo de `POST /api/v1/pair/redeem`: appPub, nonce da caixa e ciphertext. */
    fun redeemRequest(
        offer: String,
        offerPk: ByteArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
        random: SecureRandom = SecureRandom(),
    ): String {
        val appPub = NaClBox.encodeKey(publicKey)
        val inner = innerNonce(random)
        val plain = LanJson.encodeToString(
            RedeemPlain.serializer(),
            RedeemPlain(v = VERSION, offer = offer, appPub = appPub, nonce = inner),
        )
        val sealed = NaClBox.seal(privateKey, offerPk, plain.toByteArray(Charsets.UTF_8), random = random)
        return LanJson.encodeToString(
            RedeemWire.serializer(),
            RedeemWire(
                appPub = appPub,
                nonce = NaClBox.encodeKey(sealed.nonce),
                ciphertext = NaClBox.encodeKey(sealed.ciphertext),
            ),
        )
    }

    /** Abre a resposta do resgate com a privada do app e a pk da oferta. */
    fun openRedeem(privateKey: ByteArray, offerPk: ByteArray, response: String): Redeemed {
        val plain = openSealed(privateKey, offerPk, response)
        val opened = LanJson.decodeFromString(Redeemed.serializer(), plain)
        if (opened.v != VERSION || !opened.token.startsWith(TOKEN_PREFIX) || opened.deviceId.isBlank()) {
            throw NaClBox.CryptoException("resposta de pareamento inválida")
        }
        NaClBox.parseKey(opened.boxPub)
        return opened
    }

    fun withToken(body: String?, token: String): String {
        val base = if (body.isNullOrBlank()) {
            buildJsonObject { }
        } else {
            val el = LanJson.parseToJsonElement(body)
            el as? JsonObject ?: throw IllegalArgumentException("corpo precisa ser objeto JSON")
        }
        return LanJson.encodeToString(JsonObject.serializer(), JsonObject(base + ("token" to JsonPrimitive(token))))
    }

    fun resumePlain(token: String, lastSeq: Long): String =
        LanJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("v", JsonPrimitive(VERSION))
                put("type", JsonPrimitive("resume"))
                put("lastSeq", JsonPrimitive(lastSeq))
                put("token", JsonPrimitive(token))
            },
        )

    fun frame(deviceId: String, seq: Long, privateKey: ByteArray, boxPub: ByteArray, plaintext: String): String {
        val sealed = NaClBox.seal(privateKey, boxPub, plaintext.toByteArray(Charsets.UTF_8))
        return LanJson.encodeToString(
            LanFrameWire.serializer(),
            LanFrameWire(
                v = VERSION,
                deviceId = deviceId,
                seq = seq,
                enc = ENC,
                payload = BoxWire(NaClBox.encodeKey(sealed.nonce), NaClBox.encodeKey(sealed.ciphertext)),
            ),
        )
    }

    fun openSealed(privateKey: ByteArray, peerPublic: ByteArray, raw: String): String {
        val box = LanJson.decodeFromString(BoxWire.serializer(), raw.trim())
        val opened = NaClBox.open(
            privateKey,
            peerPublic,
            NaClBox.Sealed(NaClBox.decodeFlexible(box.nonce), NaClBox.decodeFlexible(box.ciphertext)),
        )
        return opened.toString(Charsets.UTF_8)
    }

    fun conflictOf(plain: String): LanConflict {
        val root = runCatching { LanJson.parseToJsonElement(plain) }.getOrNull() as? JsonObject
            ?: return LanConflict.Other
        val type = root["type"]?.jsonPrimitive?.contentOrNull
        val expect = root["expectSeq"]?.jsonPrimitive?.longOrNull
        if (type == "resume" && expect != null && expect > 0) return LanConflict.Gap(expect)
        val code = (root["error"] as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull
        if (code == "conflict") return LanConflict.Repeated
        return LanConflict.Other
    }
}
