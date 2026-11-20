package dev.hivemind.mobile

import android.content.Context
import android.util.Log
import dev.hivemind.mobile.core.api.ApiClient
import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.api.PairQr
import dev.hivemind.mobile.core.api.SseStreamManager
import dev.hivemind.mobile.core.lan.KeystoreLanVault
import dev.hivemind.mobile.core.lan.LanMessages
import dev.hivemind.mobile.core.lan.LanProtocol
import dev.hivemind.mobile.core.lan.LanRedeem
import dev.hivemind.mobile.core.lan.LanSession
import dev.hivemind.mobile.core.lan.PlainHttp
import dev.hivemind.mobile.core.lan.StoredLan
import dev.hivemind.mobile.core.notify.LocalNotifier
import dev.hivemind.mobile.core.relay.KeystoreAccountVault
import dev.hivemind.mobile.core.relay.NaClBox
import dev.hivemind.mobile.core.relay.Redact
import dev.hivemind.mobile.core.relay.RelayController
import dev.hivemind.mobile.core.relay.RelayHttp
import dev.hivemind.mobile.core.relay.RelayLog
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.core.state.HubState
import dev.hivemind.mobile.core.store.Pairing
import dev.hivemind.mobile.core.store.PairingStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Container de dependências simples (sem framework de DI). */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val plainHttp = PlainHttp()
    val lanVault = KeystoreLanVault(context)
    val pairingStore = PairingStore(context)
    val notifier = LocalNotifier(context)
    val hubState = HubState(json)
    val api = ApiClient(json, plainHttp)
    val lanNotice = MutableStateFlow<String?>(null)
    private val rebindGate = AtomicBoolean(false)

    val sse = SseStreamManager(
        json = json,
        session = { api.lanSession },
        onEvent = { hubState.onStreamEvent(it) },
        onState = { hubState.conn.value = it },
        onRebind = { scheduleRebind() },
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
                val url = pickActiveUrl(p.urls) ?: return@collect
                if (url != api.baseUrl) {
                    api.baseUrl = url
                    pairingStore.setActiveUrl(url)
                    sse.connect(url)
                }
            }
        }
    }

    /** Resgata a oferta v2, grava o cofre e conecta. */
    suspend fun pair(offer: PairQr.Offer) {
        resetRebind()
        lanNotice.value = null
        if (System.currentTimeMillis() / 1000 >= offer.exp) {
            throw ApiException(LanMessages.EXPIRED, 410, "expired")
        }
        val keys = NaClBox.generate()
        val pub = keys.publicKey.copyOf()
        val priv = keys.privateKey.copyOf()
        keys.wipe()
        try {
            var network: String? = null
            for (url in offer.urls) {
                val alive = runCatching { api.healthAt(url) }
                    .onFailure { network = it.message }
                    .getOrNull()?.ok == true
                if (!alive) continue
                try {
                    val redeemed = withContext(Dispatchers.IO) {
                        LanRedeem.post(plainHttp, url, offer.offer, offer.pk, pub, priv)
                    }
                    lanVault.save(
                        StoredLan(
                            deviceId = redeemed.deviceId,
                            privateKey = priv.copyOf(),
                            token = redeemed.token,
                            boxPub = redeemed.boxPub,
                            seq = 0,
                        ),
                    )
                    pairingStore.save(offer.urls, offer.host, url, redeemed.deviceId)
                    activate(Pairing(offer.urls, offer.host, url, redeemed.deviceId))
                    return
                } catch (e: ApiException) {
                    if (e.status == 410 || e.status == 429) throw e
                    network = e.message
                }
            }
            lastPickError = network
            throw ApiException(network?.let { "${LanMessages.NO_URL} — $it" } ?: LanMessages.NO_URL)
        } finally {
            priv.fill(0)
            pub.fill(0)
        }
    }

    /** Aplica o pareamento salvo no cliente HTTP + SSE e conecta o stream. */
    suspend fun activate(pairing: Pairing) {
        if (pairing.activeUrl?.startsWith("relay://") == true) return
        relay.preferLan()
        relay.suspendTunnel()
        api.tunnel = null
        val stored = lanVault.load()
        if (stored == null || stored.deviceId != pairing.deviceId || !stored.token.startsWith(LanProtocol.TOKEN_PREFIX)) {
            lanNotice.value = LanMessages.REBIND
            deactivate()
            return
        }
        api.lanSession = LanSession(
            deviceId = stored.deviceId,
            privateKey = stored.privateKey.copyOf(),
            boxPub = NaClBox.parseKey(stored.boxPub),
            token = stored.token,
            lastSeq = stored.seq,
            http = plainHttp,
            onAcceptedSeq = { seq -> runCatching { lanVault.updateSeq(seq) } },
            onResume = { plain -> hubState.onResumePayload(plain) },
            onUnauthorized = { scheduleRebind() },
        )
        api.token = ""
        hubState.setPairing(pairing)
        api.baseUrl = pairing.activeUrl ?: pairing.urls.first()
        sse.connect(api.baseUrl)
        refreshProjects()
        refreshLanes()
    }

    suspend fun deactivate() {
        sse.stop()
        api.lanSession = null
        api.tunnel = null
        api.token = ""
        api.baseUrl = ""
        pairingStore.clear()
        lanVault.delete()
        hubState.setPairing(null)
        hubState.setProjects(emptyList())
        hubState.setLanes(emptyList())
    }

    private fun scheduleRebind() {
        if (!rebindGate.compareAndSet(false, true)) return
        appScope.launch {
            lanNotice.value = LanMessages.REBIND
            deactivate()
        }
    }

    private fun resetRebind() {
        rebindGate.set(false)
    }

    private suspend fun bindRelay(deviceName: String, deviceId: String) {
        val meta = relay.active.value ?: return
        sse.stop(notify = false)
        api.lanSession = null
        api.tunnel = relay
        api.baseUrl = meta.relayUrl
        api.token = "relay-session"
        val marker = "relay://$deviceId"
        val current = hubState.pairing.value
        if (current?.activeUrl != marker) {
            hubState.setPairing(Pairing(listOf(meta.relayUrl), deviceName, marker))
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

    /** Tenta cada URL do pareamento e usa a primeira que responder no /health. */
    suspend fun pickActiveUrl(urls: List<String>): String? {
        var lastError: String? = null
        for (url in urls) {
            val ok = runCatching { api.healthAt(url) }
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
            val legacy = pairingStore.discardCleartextToken()
            val pairing = pairingStore.pairing.first()
            if (legacy && pairing == null) {
                lanNotice.value = LanMessages.REBIND
                lanVault.delete()
            }
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
