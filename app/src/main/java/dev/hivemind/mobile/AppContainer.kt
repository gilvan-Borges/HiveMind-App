package dev.hivemind.mobile

import android.content.Context
import android.util.Log
import dev.hivemind.mobile.core.api.ApiClient
import dev.hivemind.mobile.core.api.SseStreamManager
import dev.hivemind.mobile.core.notify.LocalNotifier
import dev.hivemind.mobile.core.relay.KeystoreAccountVault
import dev.hivemind.mobile.core.relay.Redact
import dev.hivemind.mobile.core.relay.RelayController
import dev.hivemind.mobile.core.relay.RelayHttp
import dev.hivemind.mobile.core.relay.RelayLog
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.core.state.HubState
import dev.hivemind.mobile.core.store.Pairing
import dev.hivemind.mobile.core.store.PairingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Container de dependências simples (sem framework de DI). */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val pairingStore = PairingStore(context)
    val notifier = LocalNotifier(context)
    val hubState = HubState(json)
    val api = ApiClient(json)
    val sse = SseStreamManager(
        json = json,
        onEvent = { hubState.onStreamEvent(it) },
        onState = { hubState.conn.value = it },
    )
    val relay = RelayController(
        http = RelayHttp(),
        vault = KeystoreAccountVault(context),
        scope = appScope,
        log = RelayLog { Log.w("HiveMind", Redact.clean(it)) },
        onConn = { hubState.conn.value = it },
        onEvent = { hubState.onStreamEvent(it) },
        onBeforeTunnel = { sse.stop(notify = false) },
        onHello = { device -> bindRelay(device.name, device.id) },
        onTunnelClosed = { clearRelayBinding() },
    )

    init {
        notifier.ensureChannels()
        hubState.onApprovalRequest = { info -> notifier.notifyApproval(info) }
        hubState.onTeamFinished = { snapshot -> notifier.notifyFinished(snapshot) }

        // Troca 4G ↔ Wi-Fi só no pareamento LAN. O relay tem o próprio backoff.
        appScope.launch {
            hubState.conn.collect { state ->
                if (state !is ConnState.Reconnecting || state.attempt != 1) return@collect
                val p = hubState.pairing.value ?: return@collect
                if (p.activeUrl?.startsWith("relay://") == true) return@collect
                val url = pickActiveUrl(p.urls, p.token) ?: return@collect
                if (url != api.baseUrl) {
                    api.baseUrl = url
                    pairingStore.setActiveUrl(url)
                    sse.connect(url, p.token)
                }
            }
        }
    }

    /** Aplica o pareamento salvo no cliente HTTP + SSE e conecta o stream. */
    suspend fun activate(pairing: Pairing) {
        relay.preferLan()
        relay.suspendTunnel()
        api.tunnel = null
        hubState.setPairing(pairing)
        api.baseUrl = pairing.activeUrl ?: pairing.urls.first()
        api.token = pairing.token
        sse.connect(api.baseUrl, pairing.token)
        refreshProjects()
        refreshLanes()
    }

    suspend fun deactivate() {
        sse.stop()
        pairingStore.clear()
        api.tunnel = null
        hubState.setPairing(null)
        hubState.setProjects(emptyList())
        hubState.setLanes(emptyList())
    }

    private suspend fun bindRelay(deviceName: String, deviceId: String) {
        val meta = relay.active.value ?: return
        sse.stop(notify = false)
        api.tunnel = relay
        api.baseUrl = meta.relayUrl
        api.token = "relay-session"
        val marker = "relay://$deviceId"
        val current = hubState.pairing.value
        if (current?.activeUrl != marker) {
            hubState.setPairing(Pairing(listOf(meta.relayUrl), "relay-session", deviceName, marker))
        }
        refreshProjects()
        refreshLanes()
    }

    private fun clearRelayBinding() {
        api.tunnel = null
        api.token = ""
        if (hubState.pairing.value?.activeUrl?.startsWith("relay://") == true) {
            hubState.setPairing(null)
            hubState.setProjects(emptyList())
            hubState.setLanes(emptyList())
            hubState.conn.value = ConnState.Disconnected
        }
    }

    /** Tenta cada URL do pareamento e usa a primeira que responder. */
    suspend fun pickActiveUrl(urls: List<String>, token: String): String? {
        var lastError: String? = null
        for (url in urls) {
            val ok = runCatching { api.healthAt(url, token) }
                .onFailure { lastError = it.message }
                .getOrNull()?.ok == true
            if (ok) return url
        }
        lastPickError = lastError
        return null
    }

    /** Último erro do probe de URLs (para diagnóstico na tela de pareamento). */
    @Volatile
    var lastPickError: String? = null

    suspend fun refreshProjects() {
        runCatching { api.projects() }.onSuccess { hubState.setProjects(it) }
    }

    suspend fun refreshLanes(workspace: String? = null) {
        runCatching { api.lanes(workspace) }.onSuccess { hubState.setLanes(it) }
    }

    suspend fun startRun(
        laneId: String,
        workspace: String,
        objective: String,
        team: String? = null,
        jiraKey: String? = null,
        sessionId: String? = null,
        runMode: String? = null,
        images: List<dev.hivemind.mobile.core.api.ImageInput> = emptyList(),
    ) {
        api.startRun(
            dev.hivemind.mobile.core.api.RunStartRequest(
                laneId = laneId,
                workspace = workspace,
                team = team,
                objective = objective,
                jiraKey = jiraKey,
                sessionId = sessionId,
                runMode = runMode,
                images = images,
            ),
        )
    }

    /**
     * Restaura o último transporte. Relay com IDE escolhida tem preferência
     * quando foi o último modo; o pareamento LAN continua independente.
     */
    fun boot() {
        appScope.launch {
            relay.load()
            val pairing = pairingStore.pairing.first()
            val prefer = relay.preferred()
            if (prefer == "lan" && pairing != null) {
                activate(pairing)
                return@launch
            }
            if (relay.canResumeDevice() && prefer != "lan") {
                relay.resumeSelected()
                return@launch
            }
            if (pairing != null && prefer != "relay") {
                activate(pairing)
            }
        }
    }
}
