package dev.hivemind.mobile.core.lan

import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.relay.NaClBox
import dev.hivemind.mobile.core.state.HubState
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LanProtocolTest {

    private val token = "hmlan_testtokenvalue0001"

    @Test
    fun `resgate sela o plaintext v2 e abre a resposta com a pk da oferta`() {
        val offerKey = NaClBox.generate()
        val app = NaClBox.generate()
        val offer = NaClBox.encodeKey(ByteArray(16) { 9 })
        val request = LanProtocol.redeemRequest(offer, offerKey.publicKey, app.publicKey, app.privateKey)
        val outer = LanJson.parseToJsonElement(request).jsonObject
        assertEquals(setOf("appPub", "nonce", "ciphertext"), outer.keys)
        assertFalse(request.contains(token))
        assertFalse(outer.containsKey("token"))
        assertEquals(24, NaClBox.decodeFlexible(outer["nonce"]!!.jsonPrimitive.content).size)
        val plain = NaClBox.open(
            offerKey.privateKey,
            app.publicKey,
            NaClBox.Sealed(
                NaClBox.decodeFlexible(outer["nonce"]!!.jsonPrimitive.content),
                NaClBox.decodeFlexible(outer["ciphertext"]!!.jsonPrimitive.content),
            ),
        ).toString(Charsets.UTF_8)
        val inner = LanJson.parseToJsonElement(plain).jsonObject
        assertEquals(2, inner["v"]!!.jsonPrimitive.content.toInt())
        assertEquals(offer, inner["offer"]!!.jsonPrimitive.content)
        assertEquals(outer["appPub"]!!.jsonPrimitive.content, inner["appPub"]!!.jsonPrimitive.content)
        assertEquals(16, NaClBox.decodeFlexible(inner["nonce"]!!.jsonPrimitive.content).size)

        val pc = NaClBox.generate()
        val responsePlain = """{"v":2,"token":"$token","boxPub":"${NaClBox.encodeKey(pc.publicKey)}","deviceId":"lan_abc"}"""
        val sealed = NaClBox.seal(offerKey.privateKey, app.publicKey, responsePlain.toByteArray())
        val wire = LanJson.encodeToString(
            BoxWire.serializer(),
            BoxWire(NaClBox.encodeKey(sealed.nonce), NaClBox.encodeKey(sealed.ciphertext)),
        )
        val opened = LanProtocol.openRedeem(app.privateKey, offerKey.publicKey, wire)
        assertEquals(token, opened.token)
        assertEquals("lan_abc", opened.deviceId)
        assertEquals(NaClBox.encodeKey(pc.publicKey), opened.boxPub)
        assertThrows(NaClBox.CryptoException::class.java) {
            LanProtocol.openRedeem(NaClBox.generate().privateKey, offerKey.publicKey, wire)
        }
    }

    @Test
    fun `quadro leva o json atual mais o token e a resposta abre com a boxPub`() {
        val app = NaClBox.generate()
        val pc = NaClBox.generate()
        val body = """{"laneId":"l1","objective":"plano-secreto"}"""
        val frame = LanProtocol.frame( "lan_1", 1, app.privateKey, pc.publicKey, LanProtocol.withToken(body, token))
        val outer = LanJson.decodeFromString(LanFrameWire.serializer(), frame)
        assertEquals(2, outer.v)
        assertEquals("lan_1", outer.deviceId)
        assertEquals(1L, outer.seq)
        assertEquals("naclbox", outer.enc)
        assertFalse(outer.payload.ciphertext.contains("plano-secreto"))
        assertFalse(outer.payload.ciphertext.contains(token))
        val plain = NaClBox.open(
            pc.privateKey,
            app.publicKey,
            NaClBox.Sealed(NaClBox.decodeFlexible(outer.payload.nonce), NaClBox.decodeFlexible(outer.payload.ciphertext)),
        ).toString(Charsets.UTF_8)
        val obj = LanJson.parseToJsonElement(plain).jsonObject
        assertEquals("l1", obj["laneId"]!!.jsonPrimitive.content)
        assertEquals("plano-secreto", obj["objective"]!!.jsonPrimitive.content)
        assertEquals(token, obj["token"]!!.jsonPrimitive.content)
        val back = LanProtocol.openSealed(app.privateKey, pc.publicKey, sealToApp(pc, app, """{"accepted":true}"""))
        assertTrue(back.contains("accepted"))
        assertThrows(NaClBox.CryptoException::class.java) {
            LanProtocol.openSealed(app.privateKey, NaClBox.generate().publicKey, sealToApp(pc, app, """{"accepted":true}"""))
        }
    }

    @Test
    fun `seq comeca em 1 buraco pede resume e repetido nao executa de novo`() {
        val app = NaClBox.generate()
        val pc = NaClBox.generate()
        val bridge = FakeBridge(app.publicKey, pc, token)
        var resumes = 0
        val session = session(app, pc, bridge, lastSeq = 0) { resumes++ }

        val first = session.call("POST", "http://127.0.0.1/api/v1/runs/start", """{"laneId":"l1"}""")
        val second = session.call("POST", "http://127.0.0.1/api/v1/runs/start", """{"laneId":"l2"}""")
        assertTrue(first.contains("accepted"))
        assertTrue(second.contains("accepted"))
        assertEquals(listOf(1L, 2L), bridge.accepted)
        assertEquals(2, bridge.executed)
        assertEquals(0, resumes)

        bridge.lastSeq = 0
        bridge.executed = 0
        bridge.accepted.clear()
        val ahead = session(app, pc, bridge, lastSeq = 5) { resumes++ }
        ahead.call("POST", "http://127.0.0.1/api/v1/runs/start", """{"laneId":"gap"}""")
        assertEquals(1, bridge.executed)
        assertEquals(listOf(1L, 2L), bridge.accepted.toList())
        assertTrue(resumes >= 1)

        bridge.lastSeq = 3
        bridge.executed = 0
        bridge.accepted.clear()
        val behind = session(app, pc, bridge, lastSeq = 0) { resumes++ }
        behind.call("POST", "http://127.0.0.1/api/v1/runs/start", """{"laneId":"repeat"}""")
        assertEquals(1, bridge.executed)
        assertEquals(5L, bridge.accepted.last())
    }

    @Test
    fun `401 sem caixa forca pareamento novo`() {
        val app = NaClBox.generate()
        val pc = NaClBox.generate()
        val http = object : HttpTransport {
            override fun execute(request: WireRequest): WireResponse =
                WireResponse(401, """{"error":{"code":"unauthorized","message":"não autorizado"}}""")
            override fun open(request: WireRequest): WireStream = error("sem stream")
        }
        var rebound = false
        val session = LanSession("lan_1", app.privateKey, pc.publicKey, token, 0, http, {}, {}, { rebound = true })
        val error = assertThrows(LanUnauthorized::class.java) {
            session.call("GET", "http://127.0.0.1/api/v1/projects", null)
        }
        assertTrue(rebound)
        assertEquals(401, error.status)
        assertTrue(error.message!!.contains("QR novo"))
    }

    @Test
    fun `410 e 429 do resgate viram mensagens claras`() {
        val app = NaClBox.generate()
        val offerPk = NaClBox.encodeKey(NaClBox.generate().publicKey)
        val http = Scripted { req ->
            assertEquals("POST", req.method)
            assertTrue(req.url.endsWith("/api/v1/pair/redeem"))
            assertFalse(String(req.body!!, Charsets.UTF_8).contains("Bearer"))
            val code = if (req.url.contains("limited")) "rate_limited" else "expired"
            val status = if (code == "rate_limited") 429 else 410
            WireResponse(status, """{"error":{"code":"$code","message":"x"}}""")
        }
        val expired = assertThrows(ApiException::class.java) {
            LanRedeem.post(http, "http://127.0.0.1:9", "AAAAAAAAAAAAAAAAAAAAAA", offerPk, app.publicKey, app.privateKey)
        }
        assertEquals(410, expired.status)
        assertTrue(expired.message!!.contains("venceu"))
        val limited = assertThrows(ApiException::class.java) {
            LanRedeem.post(http, "http://limited", "AAAAAAAAAAAAAAAAAAAAAA", offerPk, app.publicKey, app.privateKey)
        }
        assertEquals(429, limited.status)
        assertTrue(limited.message!!.contains("15 minutos"))
    }

    @Test
    fun `linha data do SSE abre com o tipo dentro do plaintext`() {
        val app = NaClBox.generate()
        val pc = NaClBox.generate()
        val session = LanSession("lan_1", app.privateKey, pc.publicKey, token, 0, Scripted { error("nao") }, {}, {}, {})
        val event = """{"type":"lane_event","data":{"laneId":"l1"}}"""
        val opened = session.openEvent(sealToApp(pc, app, event))
        assertEquals("lane_event", LanJson.parseToJsonElement(opened).jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(opened.contains("laneId"))
    }

    @Test
    fun `resume guarda no maximo 60 eventos por lane`() {
        val hub = HubState(Json { ignoreUnknownKeys = true })
        val items = (1..61).joinToString(",") { i ->
            """{"type":"lane_event","data":{"workspace":"w","laneId":"l","item":{"kind":"stage","text":"e$i"}}}"""
        }
        hub.onResumePayload("""{"v":2,"type":"resume","events":[$items]}""")
        val lane = hub.lane("w", "l")
        assertEquals(60, lane?.recentEvents?.size)
        assertEquals("e2", lane?.recentEvents?.first()?.text)
        assertEquals("e61", lane?.recentEvents?.last()?.text)
    }

    @Test
    fun `GET com corpo continua GET e chunked volta inteiro`() {
        val server = ServerSocket(0)
        val seen = AtomicReference("")
        val method = AtomicReference("")
        val ready = CountDownLatch(1)
        Thread {
            server.accept().use { socket ->
                ready.countDown()
                val input = socket.getInputStream()
                val header = readHeader(input)
                val text = header.toString(StandardCharsets.ISO_8859_1)
                method.set(text.lineSequence().first())
                val length = text.lineSequence()
                    .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim()?.toInt() ?: 0
                val body = ByteArray(length)
                var off = 0
                while (off < length) {
                    val n = input.read(body, off, length - off)
                    if (n < 0) break
                    off += n
                }
                seen.set(text + body.toString(Charsets.UTF_8))
                val payload = "data: {\"type\":\"ping\"}\n\n"
                val chunked = payload.toByteArray().size.toString(16) + "\r\n" + payload + "\r\n0\r\n\r\n"
                val resp = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n$chunked"
                socket.getOutputStream().write(resp.toByteArray(StandardCharsets.ISO_8859_1))
                socket.getOutputStream().flush()
            }
        }.start()
        val port = server.localPort
        val resp = PlainHttp().execute(
            WireRequest("GET", "http://127.0.0.1:$port/api/v1/events", """{"v":2}""".toByteArray()),
        )
        ready.await()
        assertTrue(method.get().startsWith("GET /api/v1/events HTTP/1.1"))
        assertFalse(method.get().startsWith("POST"))
        assertFalse(seen.get().contains("Authorization"))
        assertFalse(seen.get().contains("Bearer"))
        assertTrue(seen.get().contains("""{"v":2}"""))
        assertEquals("data: {\"type\":\"ping\"}\n\n", resp.body)
        server.close()
    }

    private fun session(
        app: NaClBox.KeyPair,
        pc: NaClBox.KeyPair,
        bridge: FakeBridge,
        lastSeq: Long,
        onResume: () -> Unit,
    ) = LanSession(
        deviceId = "lan_1",
        privateKey = app.privateKey,
        boxPub = pc.publicKey,
        token = token,
        lastSeq = lastSeq,
        http = bridge,
        onAcceptedSeq = {},
        onResume = { onResume() },
        onUnauthorized = {},
    )

    private fun sealToApp(pc: NaClBox.KeyPair, app: NaClBox.KeyPair, plain: String): String {
        val sealed = NaClBox.seal(pc.privateKey, app.publicKey, plain.toByteArray())
        return LanJson.encodeToString(
            BoxWire.serializer(),
            BoxWire(NaClBox.encodeKey(sealed.nonce), NaClBox.encodeKey(sealed.ciphertext)),
        )
    }

    private fun readHeader(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val tail = IntArray(4) { -1 }
        while (out.size() < 65536) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            tail[0] = tail[1]
            tail[1] = tail[2]
            tail[2] = tail[3]
            tail[3] = b
            if (tail[0] == 13 && tail[1] == 10 && tail[2] == 13 && tail[3] == 10) break
        }
        return out.toByteArray()
    }
}

private class Scripted(private val handler: (WireRequest) -> WireResponse) : HttpTransport {
    override fun execute(request: WireRequest): WireResponse = handler(request)
    override fun open(request: WireRequest): WireStream = error("sem stream")
}

private class FakeBridge(
    private val appPub: ByteArray,
    private val pc: NaClBox.KeyPair,
    private val token: String,
) : HttpTransport {
    var lastSeq = 0L
    var executed = 0
    val accepted = mutableListOf<Long>()

    override fun execute(request: WireRequest): WireResponse {
        val raw = request.body!!.toString(Charsets.UTF_8)
        assertFalse(raw.contains("Bearer"))
        val frame = LanJson.decodeFromString(LanFrameWire.serializer(), raw)
        assertEquals(2, frame.v)
        assertEquals("naclbox", frame.enc)
        assertEquals("lan_1", frame.deviceId)
        val plain = NaClBox.open(
            pc.privateKey,
            appPub,
            NaClBox.Sealed(NaClBox.decodeFlexible(frame.payload.nonce), NaClBox.decodeFlexible(frame.payload.ciphertext)),
        ).toString(Charsets.UTF_8)
        val obj = LanJson.parseToJsonElement(plain).jsonObject
        assertEquals(token, obj["token"]!!.jsonPrimitive.content)
        if (frame.seq <= lastSeq) {
            return box(409, """{"error":{"code":"conflict","message":"seq repetido"}}""")
        }
        if (frame.seq > lastSeq + 1) {
            return box(409, """{"v":2,"type":"resume","expectSeq":${lastSeq + 1}}""")
        }
        lastSeq = frame.seq
        accepted += frame.seq
        if (obj["type"]?.jsonPrimitive?.contentOrNull == "resume") {
            return box(200, """{"v":2,"type":"resume","events":[]}""")
        }
        executed++
        return box(200, """{"accepted":true,"laneId":"${obj["laneId"]!!.jsonPrimitive.content}"}""")
    }

    override fun open(request: WireRequest): WireStream = error("sem stream")

    private fun box(status: Int, json: String): WireResponse =
        WireResponse(status, seal(json))

    private fun seal(json: String): String {
        val sealed = NaClBox.seal(pc.privateKey, appPub, json.toByteArray())
        return LanJson.encodeToString(
            BoxWire.serializer(),
            BoxWire(NaClBox.encodeKey(sealed.nonce), NaClBox.encodeKey(sealed.ciphertext)),
        )
    }
}
