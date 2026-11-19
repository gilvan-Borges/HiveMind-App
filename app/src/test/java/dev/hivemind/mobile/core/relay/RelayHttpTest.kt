package dev.hivemind.mobile.core.relay

import dev.hivemind.mobile.core.api.ApiClient
import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.api.BridgeTunnel
import dev.hivemind.mobile.core.api.TunnelResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayHttpTest {

    @Test
    fun `login grava tokens e nao a senha e o refresh substitui o anterior`() {
        val server = MockWebServer()
        val seenRefresh = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when (request.path) {
                    "/api/v1/auth/login" -> MockResponse().setBody(
                        """{"accessToken":"hmr_at_aaa","refreshToken":"hmr_rt_old","expiresIn":900,"tokenType":"Bearer","account":{"id":"acct_1","email":"owner@localhost","role":"owner"}}""",
                    )
                    "/api/v1/auth/refresh" -> {
                        seenRefresh += body
                        MockResponse().setBody(
                            """{"accessToken":"hmr_at_new","refreshToken":"hmr_rt_new","expiresIn":900,"tokenType":"Bearer","account":{"id":"acct_1","email":"owner@localhost","role":"owner"}}""",
                        )
                    }
                    "/api/v1/devices" -> MockResponse().setBody("""{"devices":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            var now = 1_000_000L
            val vault = MemoryAccountVault()
            val controller = RelayController(
                http = RelayHttp(),
                vault = vault,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                clock = { now },
            )
            runBlocking { controller.login(server.url("/").toString(), "owner@localhost", "senha-inicial-ok") }
            val dump = vault.debugAll()
            assertTrue(dump.contains("hmr_rt_old"))
            assertFalse(dump.contains("senha-inicial-ok"))
            assertEquals("owner@localhost", controller.active.value?.email)
            now += 890_000L
            runBlocking { controller.refreshDeviceList() }
            assertEquals(1, seenRefresh.size)
            assertTrue(seenRefresh[0].contains("hmr_rt_old"))
            assertFalse(seenRefresh[0].contains("hmr_rt_new"))
            val after = vault.debugSecrets("acct_1")
            assertTrue(after.contains("hmr_rt_new"))
            assertFalse(after.contains("hmr_rt_old"))
            assertFalse(after.contains("senha-inicial-ok"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `http publico nem chega a abrir conexao`() {
        val http = RelayHttp()
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { http.login("http://relay.exemplo", "a@b.c", "senha-inicial-ok") }
        }
        assertTrue(error.message!!.contains("https"))
    }

    @Test
    fun `401 vira erro sem ecoar o corpo cru`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error":{"code":"unauthorized","message":"credenciais inválidas hmr_at_nao_logar"}}""",
            ),
        )
        server.start()
        try {
            val error = assertThrows(RelayException::class.java) {
                runBlocking { RelayHttp().login(server.url("/").toString(), "a@b.c", "senha-inicial-ok") }
            }
            assertEquals("unauthorized", error.code)
            assertEquals(401, error.status)
            assertFalse(error.message!!.contains("nao_logar"))
            assertTrue(error.message!!.contains("[redacted]"))
        } finally {
            server.shutdown()
        }
    }
}

class ApiClientTunnelTest {

    @Test
    fun `tunnel entrega projetos e bloqueia o resto com a IDE offline`() = runBlocking {
        val calls = AtomicInteger(0)
        val tunnel = object : BridgeTunnel {
            override var ideOnline: Boolean = true
            override var ready: Boolean = true
            override suspend fun rpc(method: String, path: String, query: String, bodyJson: String?): TunnelResult {
                calls.incrementAndGet()
                return when (path) {
                    "/api/v1/projects" -> TunnelResult(200, """{"projects":[{"name":"demo","workspace":"/w"}]}""")
                    "/api/v1/approvals/resolve" -> TunnelResult(200, """{"resolved":true}""")
                    else -> TunnelResult(500, """{"error":{"code":"internal","message":"falhou"}}""")
                }
            }
        }
        val api = ApiClient(Json { ignoreUnknownKeys = true })
        api.tunnel = tunnel
        assertEquals("demo", api.projects().single().name)
        tunnel.ideOnline = false
        val offline = assertThrows(ApiException::class.java) { runBlocking { api.projects() } }
        assertEquals("IDE offline", offline.message)
        assertEquals(1, calls.get())
        api.resolveApproval("ap_1", true)
        assertEquals(2, calls.get())
        tunnel.ideOnline = true
        val failed = assertThrows(ApiException::class.java) { runBlocking { api.health() } }
        assertEquals("falhou", failed.message)
    }
}
