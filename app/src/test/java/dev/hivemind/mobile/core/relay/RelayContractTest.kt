package dev.hivemind.mobile.core.relay

import dev.hivemind.mobile.core.store.Pairing
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayContractTest {

    @Test
    fun `url publica exige https e loopback aceita http`() {
        assertEquals("https://relay.exemplo", RelayUrls.normalize("https://relay.exemplo/"))
        assertEquals("https://relay.exemplo:443", RelayUrls.normalize("https://relay.exemplo:443"))
        assertEquals("wss://relay.exemplo/api/v1/ws", RelayUrls.websocket("https://relay.exemplo"))
        assertEquals("http://127.0.0.1:8787", RelayUrls.normalize("http://127.0.0.1:8787/"))
        assertEquals("ws://localhost:8787/api/v1/ws", RelayUrls.websocket("http://localhost:8787"))
        assertEquals("http://[::1]:8787", RelayUrls.normalize("http://[::1]:8787"))
        assertThrows(IllegalArgumentException::class.java) { RelayUrls.normalize("http://relay.exemplo") }
        assertThrows(IllegalArgumentException::class.java) { RelayUrls.normalize("https://user:secret@relay.exemplo") }
        assertTrue(RelayUrls.same("https://relay.exemplo/", "https://relay.exemplo"))
    }

    @Test
    fun `link uri ignora ordem dos parametros e hifen do codigo`() {
        val parsed = LinkCodes.parse("hivemind://link?relay=https%3A%2F%2Frelay.exemplo&code=ABCD-EFGH-JK")
        assertEquals("https://relay.exemplo", parsed?.first)
        assertEquals("ABCDEFGHJK", parsed?.second)
        val otherOrder = LinkCodes.parse("hivemind://link?code=ABCDEFGHJK&relay=https%3A%2F%2Frelay.exemplo")
        assertEquals("ABCDEFGHJK", otherOrder?.second)
        assertEquals("ABCD-EFGH-JK", LinkCodes.format("abcdefghjk"))
        assertTrue(LinkCodes.isValid("ABCD-EFGH-JK"))
        assertFalse(LinkCodes.isValid("ABCD-EFGH-0I"))
        assertNull(LinkCodes.parse("hivemind://pair?urls=http://a&token=t"))
        assertTrue(LinkCodes.uri("https://relay.exemplo", "ABCDEFGHJK").startsWith("hivemind://link?"))
    }

    @Test
    fun `contas nao vazam segredo e a sessao zera ao trocar`() {
        val vault = MemoryAccountVault()
        vault.saveMeta(AccountMeta("acct_a", "a@x", "owner", "https://relay.exemplo"))
        vault.saveMeta(AccountMeta("acct_b", "b@x", "member", "https://outro.exemplo"))
        vault.putSecret("acct_a", SessionSecrets.REFRESH, "hmr_rt_aaa")
        vault.putSecret("acct_a", SessionSecrets.ACCESS, "hmr_at_aaa")
        vault.putSecret("acct_b", SessionSecrets.REFRESH, "hmr_rt_bbb")
        assertEquals("hmr_rt_aaa", vault.getSecret("acct_a", SessionSecrets.REFRESH))
        assertEquals("hmr_rt_bbb", vault.getSecret("acct_b", SessionSecrets.REFRESH))
        assertFalse(vault.debugSecrets("acct_b").contains("hmr_rt_aaa"))
        assertFalse(AccountNamespaces.alias("acct_a") == AccountNamespaces.alias("acct_b"))

        val session = AccountSession()
        session.load("acct_a", vault)
        assertEquals("hmr_rt_aaa", session.refreshToken)
        session.load("acct_b", vault)
        assertEquals("hmr_rt_bbb", session.refreshToken)
        assertFalse(session.refreshToken!!.contains("aaa"))
        session.wipe()
        assertNull(session.refreshToken)
        assertNull(session.accessToken)
        assertNull(session.keys)

        vault.deleteAccount("acct_a")
        assertNull(vault.getSecret("acct_a", SessionSecrets.REFRESH))
        assertEquals("hmr_rt_bbb", vault.getSecret("acct_b", SessionSecrets.REFRESH))
        assertEquals(listOf("acct_b"), vault.listAccounts().map { it.id })
    }

    @Test
    fun `rotacao substitui o refresh e nao guarda o antigo`() {
        val vault = MemoryAccountVault()
        vault.putSecret("acct_1", SessionSecrets.REFRESH, "hmr_rt_old")
        vault.putSecret("acct_1", SessionSecrets.ACCESS, "hmr_at_old")
        SessionSecrets.persistRotation(vault, "acct_1", "hmr_at_new", "hmr_rt_new", 50_000)
        assertEquals("hmr_rt_new", vault.getSecret("acct_1", SessionSecrets.REFRESH))
        assertEquals("hmr_at_new", vault.getSecret("acct_1", SessionSecrets.ACCESS))
        assertFalse(vault.debugSecrets("acct_1").contains("hmr_rt_old"))
        assertTrue(SessionSecrets.shouldRefresh(30_000, 10_000, true))
        assertFalse(SessionSecrets.shouldRefresh(90_000, 10_000, true))
        assertTrue(SessionSecrets.shouldRefresh(90_000, 10_000, false))
    }

    @Test
    fun `cursor ignora seq repetido e o backoff satura em 30s`() {
        val cursor = EventCursor(4)
        assertFalse(cursor.accept(4))
        assertFalse(cursor.accept(3))
        assertTrue(cursor.accept(5))
        assertEquals(5L, cursor.lastApplied)
        assertFalse(cursor.accept(5))
        assertEquals(1_000L, Backoff.delayMs(1))
        assertEquals(2_000L, Backoff.delayMs(2))
        assertEquals(30_000L, Backoff.delayMs(6))
        assertEquals(30_000L, Backoff.delayMs(9))
    }

    @Test
    fun `redige token senha e bearer`() {
        val dirty = """bearer hmr_at_abc refresh hmr_rt_zzz hmlan_abcDEF123 {"password":"segredo","refreshToken":"hmr_rt_yyy"}"""
        val clean = Redact.clean(dirty)
        assertFalse(clean.contains("hmr_at_abc"))
        assertFalse(clean.contains("hmr_rt_zzz"))
        assertFalse(clean.contains("hmlan_abcDEF123"))
        assertFalse(clean.contains("segredo"))
        assertFalse(clean.contains("hmr_rt_yyy"))
        assertTrue(clean.contains("hmlan_[redacted]"))
        assertTrue(clean.contains("[redacted]"))
    }

    @Test
    fun `allowlist nao tunela o stream SSE`() {
        assertTrue(RpcAllowlist.allowed("get", "/api/v1/projects/"))
        assertTrue(RpcAllowlist.allowed("POST", "/api/v1/session/read"))
        assertTrue(RpcAllowlist.allowed("GET", "/api/v1/approvals"))
        assertTrue(RpcAllowlist.allowed("POST", "/api/v1/project/permission"))
        assertTrue(RpcAllowlist.allowed("POST", "/api/v1/git/commit"))
        assertTrue(RpcAllowlist.allowed("POST", "/api/v1/git/pr"))
        assertTrue(RpcAllowlist.allowed("GET", "/api/v1/routines"))
        assertTrue(RpcAllowlist.allowed("POST", "/api/v1/routines/enabled"))
        assertFalse(RpcAllowlist.allowed("GET", "/api/v1/conversations"))
        assertTrue(RpcAllowlist.allowed("POST", "/api/v1/approvals/resolve"))
        assertFalse(RpcAllowlist.allowed("GET", "/api/v1/events"))
        assertFalse(RpcAllowlist.allowed("GET", "/api/v1/../admin"))
    }

    @Test
    fun `rota do pareamento marca relay sem parecer LAN`() {
        val relay = Pairing(listOf("https://relay.exemplo"), "pc-casa", "relay://dev_1")
        assertEquals("Relay · pc-casa", relay.routeLabel)
        val lan = Pairing(listOf("http://192.168.0.15:47890"), "pc", "http://192.168.0.15:47890", "lan_abc")
        assertEquals("Wi-Fi", lan.routeLabel)
        val ts = Pairing(listOf("http://100.1.2.3:47890"), "pc", "http://100.1.2.3:47890", "lan_abc")
        assertEquals("Tailscale", ts.routeLabel)
    }

    @Test
    fun `engine cifra rpc e aplica evento em ordem`() {
        val app = NaClBox.generate()
        val ide = NaClBox.generate()
        val engine = RelaySessionEngine(app, "dev_abc", resumeAfter = 4)
        val hello = engine.hello()
        assertTrue(hello.contains("\"role\":\"app\""))
        assertTrue(hello.contains("\"resumeAfter\":4"))
        assertTrue(hello.contains(NaClBox.encodeKey(app.publicKey)))

        engine.peerPublic = ide.publicKey
        val eventPlain = """{"type":"lane_event","data":{"laneId":"l1"}}"""
        val eventBox = NaClBox.seal(ide.privateKey, app.publicKey, eventPlain.toByteArray())
        val eventFrame = frame(
            type = "event",
            seq = 5,
            enc = true,
            nonce = eventBox.nonce,
            ct = eventBox.ciphertext,
        )
        val applied = engine.onFrame(eventFrame) as FrameEffect.Events
        assertEquals(1, applied.applied.size)
        assertEquals(5L, applied.applied[0].first)
        assertEquals("lane_event", applied.applied[0].second.type)
        assertTrue(engine.onFrame(eventFrame) is FrameEffect.Ignored)

        val rpc = engine.sealRpc("rpc-1", "GET", "/api/v1/projects", "workspace=%2Ftmp", null)
        val rpcObj = RelayJson.parseToJsonElement(rpc).let { it as kotlinx.serialization.json.JsonObject }
        val box = rpcObj["payload"] as kotlinx.serialization.json.JsonObject
        val opened = NaClBox.open(
            ide.privateKey,
            app.publicKey,
            NaClBox.Sealed(
                NaClBox.decodeFlexible(box.getValue("nonce").toString().trim('"')),
                NaClBox.decodeFlexible(box.getValue("ciphertext").toString().trim('"')),
            ),
        ).toString(Charsets.UTF_8)
        assertTrue(opened.contains("/api/v1/projects"))
        assertTrue(opened.contains("workspace=%2Ftmp"))

        val resultPlain = """{"status":200,"body":{"projects":[]}}"""
        val resultBox = NaClBox.seal(ide.privateKey, app.publicKey, resultPlain.toByteArray())
        val result = engine.onFrame(frame("rpc_result", 0, true, resultBox.nonce, resultBox.ciphertext, id = "rpc-1"))
        val rpcEffect = result as FrameEffect.Rpc
        assertEquals(200, rpcEffect.status)
        assertTrue(rpcEffect.body.contains("projects"))

        val ping = engine.onFrame("""{"v":1,"id":"ping-7","type":"ping","deviceId":"dev_abc"}""") as FrameEffect.Send
        assertTrue(ping.text.contains("\"type\":\"pong\""))
        assertTrue(ping.text.contains("ping-7"))

        val ok = engine.onFrame(
            """{"v":1,"id":"h","type":"hello_ok","payload":{"seq":9,"resumeFrom":4,"peerPublicKey":"${NaClBox.encodeKey(ide.publicKey)}","e2eReady":true,"online":false}}""",
        ) as FrameEffect.Hello
        assertFalse(ok.online)
        assertTrue(ok.e2eReady)
        assertEquals(9L, ok.seq)
    }

    private fun frame(
        type: String,
        seq: Long,
        enc: Boolean,
        nonce: ByteArray,
        ct: ByteArray,
        id: String = "ev",
    ): String {
        val payload = buildJsonObject {
            put("nonce", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(nonce))
            put("ciphertext", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ct))
        }
        return buildJsonObject {
            put("v", 1)
            put("id", id)
            put("type", type)
            put("deviceId", "dev_abc")
            if (seq > 0) put("seq", seq)
            if (enc) put("enc", "naclbox")
            put("payload", payload)
        }.toString()
    }
}
