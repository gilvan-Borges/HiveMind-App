package dev.hivemind.mobile

import android.content.Context
import dev.hivemind.mobile.core.api.ApiClient
import dev.hivemind.mobile.core.api.SseStreamManager
import dev.hivemind.mobile.core.notify.LocalNotifier
import dev.hivemind.mobile.core.state.HubState
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

    init {
        notifier.ensureChannels()
        hubState.onApprovalRequest = { info -> notifier.notifyApproval(info) }
        hubState.onTeamFinished = { snapshot -> notifier.notifyFinished(snapshot) }
    }

    /** Aplica o pareamento salvo no cliente HTTP + SSE e conecta o stream. */
    suspend fun activate(pairing: dev.hivemind.mobile.core.store.Pairing) {
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
        hubState.setPairing(null)
        hubState.setProjects(emptyList())
        hubState.setLanes(emptyList())
    }

    /** Tenta cada URL do pareamento e usa a primeira que responder. */
    suspend fun pickActiveUrl(urls: List<String>, token: String): String? {
        for (url in urls) {
            val ok = runCatching { api.healthAt(url, token) }.getOrNull()?.ok == true
            if (ok) return url
        }
        return null
    }

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

    /** Conecta tudo a partir do estado salvo (boot do app). */
    fun boot() {
        appScope.launch {
            val pairing = pairingStore.pairing.first() ?: return@launch
            activate(pairing)
        }
    }
}
