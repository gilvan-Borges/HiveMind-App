package dev.hivemind.mobile.core.state

import dev.hivemind.mobile.core.lan.LanProtocol
import dev.hivemind.mobile.core.models.ApprovalInfo
import dev.hivemind.mobile.core.models.ApprovalResolvedPayload
import dev.hivemind.mobile.core.models.AutoReviewStatus
import dev.hivemind.mobile.core.models.LaneEvent
import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.models.ProviderHealthPayload
import dev.hivemind.mobile.core.models.ProviderHealthView
import dev.hivemind.mobile.core.models.RelayGapPayload
import dev.hivemind.mobile.core.models.StreamEvent
import dev.hivemind.mobile.core.store.Pairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

sealed interface ConnState {
    data object Disconnected : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    data class Reconnecting(val attempt: Int) : ConnState
}

/** Aviso de um `relay_gap`: o buffer do relay transbordou e o fluxo pode ter furos. */
data class RelayGapNotice(val reason: String, val at: Long)

/**
 * Estado vivo do app: pareamento, conexão, projetos, lanes (snapshot por
 * workspace|laneId), aprovações pendentes, status do Auto-Review, saúde de
 * provedores (Jev) e avisos de `relay_gap`.
 */
class HubState(private val json: Json) {

    val pairing = MutableStateFlow<Pairing?>(null)
    val conn = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val latencyMs = MutableStateFlow<Long?>(null)

    val projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val lanes = MutableStateFlow<Map<String, LaneSnapshot>>(emptyMap())
    val autoreview = MutableStateFlow<Map<String, AutoReviewStatus>>(emptyMap())
    val providerHealth = MutableStateFlow<List<ProviderHealthView>>(emptyList())
    val relayGap = MutableStateFlow<RelayGapNotice?>(null)

    val selectedWorkspace = MutableStateFlow<String?>(null)
    val selectedLaneId = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    /** Hooks de notificação local (aprovado/terminado). */
    var onApprovalRequest: ((ApprovalInfo) -> Unit)? = null
    var onTeamFinished: ((LaneSnapshot) -> Unit)? = null

    val pendingApprovals: StateFlow<List<ApprovalInfo>> = MutableStateFlow(emptyList())

    private val _pendingApprovals = pendingApprovals as MutableStateFlow<List<ApprovalInfo>>

    fun setPairing(p: Pairing?) {
        pairing.value = p
    }

    fun setProjects(list: List<ProjectSummary>) {
        projects.value = list
    }

    fun setLanes(list: List<LaneSnapshot>) {
        lanes.value = list.associateBy { it.key }
        refreshPending()
    }

    fun setWorkspaceLanes(workspace: String, list: List<LaneSnapshot>) {
        val kept = lanes.value.filterValues { it.workspace != workspace }
        lanes.value = kept + list.associateBy { it.key }
        refreshPending()
    }

    fun pruneDeletedSessions(workspace: String, activeSessionIds: Set<String>) {
        if (workspace.isBlank()) return
        val next = lanes.value.filterValues { lane ->
            if (lane.workspace != workspace || lane.running) {
                true
            } else if (activeSessionIds.isEmpty()) {
                false
            } else if (lane.sessionId.isNotBlank() && lane.sessionId !in activeSessionIds) {
                false
            } else {
                true
            }
        }
        if (next.size != lanes.value.size) {
            lanes.value = next
            refreshPending()
        }
    }

    fun clearLaneConversation(workspace: String, laneId: String? = null) {
        if (workspace.isBlank()) return
        lanes.value = lanes.value.filterValues { lane ->
            if (lane.workspace != workspace || lane.running) {
                true
            } else if (!laneId.isNullOrBlank()) {
                lane.laneId != laneId
            } else {
                false
            }
        }
        refreshPending()
    }

    fun setLane(s: LaneSnapshot) {
        lanes.value = lanes.value + (s.key to s)
        refreshPending()
    }

    fun lane(workspace: String?, laneId: String?): LaneSnapshot? =
        if (workspace == null || laneId == null) null else lanes.value["$workspace|$laneId"]

    fun lanesOf(workspace: String): List<LaneSnapshot> =
        lanes.value.values.filter { it.workspace == workspace }.sortedBy { it.laneId }

    /** SSE lane_event — mescla no snapshot vivo da lane. */
    fun onLaneEvent(ev: LaneEvent) {
        if (ev.laneId.isBlank()) return
        val current = lanes.value.values.firstOrNull { it.laneId == ev.laneId }
        val snapshot = current ?: LaneSnapshot(laneId = ev.laneId, workspace = selectedWorkspace.value.orEmpty())
        val e = ev.event
        val now = System.currentTimeMillis().toString()
        val item = LaneEventItem(
            kind = e.kind,
            stage = e.stage.orEmpty(),
            author = e.author.orEmpty(),
            label = e.label.orEmpty(),
            text = e.text.orEmpty(),
            toolName = e.toolName.orEmpty(),
            tokens = e.tokens,
            provider = e.provider.orEmpty(),
            model = e.model.orEmpty(),
            fromProvider = e.fromProvider.orEmpty(),
            fromModel = e.fromModel.orEmpty(),
            tier = e.tier.orEmpty(),
            reasons = e.reasons,
            estimatedMicroUsd = e.estimatedMicroUsd,
            decisionMicros = e.decisionMicros,
            health = e.health,
            at = now,
        )
        var approvals = snapshot.pendingApprovals
        var running = snapshot.running
        var stage = snapshot.stage
        var lastError = snapshot.lastError
        var turnTokens = snapshot.turnTokens
        var sessionId = snapshot.sessionId
        when (e.kind) {
            "session" -> {
                val sid = e.text.orEmpty().trim()
                if (sid.isNotBlank()) sessionId = sid
            }
            "stage" -> {
                running = true
                stage = e.stage.orEmpty()
            }
            "usage" -> turnTokens += if (e.tokens > 0) e.tokens else e.inputTokens + e.outputTokens
            "approval_required" -> {
                val id = e.approvalId.orEmpty()
                if (id.isNotBlank() && approvals.none { it.approvalId == id }) {
                    approvals = approvals + ApprovalInfo(
                        approvalId = id,
                        laneId = ev.laneId,
                        workspace = snapshot.workspace,
                        prompt = e.text.orEmpty(),
                        label = e.label.orEmpty(),
                        toolName = e.toolName.orEmpty(),
                        riskTier = e.gate.orEmpty(),
                        createdAt = now,
                    )
                    approvals.lastOrNull()?.let { onApprovalRequest?.invoke(it) }
                }
            }
            "finish" -> {
                running = false
                stage = "done"
                approvals = emptyList()
                onTeamFinished?.invoke(snapshot)
            }
            "error" -> {
                running = false
                lastError = e.text.orEmpty()
                approvals = emptyList()
            }
            "approval_resolved" -> {
                val id = e.approvalId.orEmpty()
                approvals = approvals.filterNot { it.approvalId == id }
            }
        }
        val recent = if (e.kind == "usage" || e.kind == "session") {
            snapshot.recentEvents
        } else {
            snapshot.recentEvents.takeLast(59) + item
        }
        setLane(
            snapshot.copy(
                teamName = if (ev.team.isNotBlank()) ev.team else snapshot.teamName,
                sessionId = sessionId,
                running = running,
                stage = stage,
                lastError = lastError,
                turnTokens = turnTokens,
                pendingApprovals = approvals,
                recentEvents = recent,
            ),
        )
    }

    /** SSE approval_resolved — remove o gate de todas as lanes. */
    fun onApprovalResolved(id: String) {
        lanes.value = lanes.value.mapValues { (_, s) ->
            s.copy(pendingApprovals = s.pendingApprovals.filterNot { it.approvalId == id })
        }
        refreshPending()
    }

    fun onAutoReviewStatus(st: AutoReviewStatus) {
        autoreview.value = autoreview.value + (st.workspace to st)
    }

    /** SSE provider_health — últimos estados de circuito dos provedores (Jev). */
    fun onProviderHealth(payload: ProviderHealthPayload) {
        providerHealth.value = payload.providers
    }

    /**
     * SSE relay_gap — o relay perdeu eventos (backlog truncado). A suposição de
     * fluxo completo entre o último seq e agora é descartada: aplica o snapshot
     * vivo recebido (aprovações pendentes inclusas) e avisa a UI.
     */
    fun onRelayGap(payload: RelayGapPayload) {
        if (payload.lanes.isNotEmpty()) setLanes(payload.lanes)
        relayGap.value = RelayGapNotice(
            reason = payload.reason.ifBlank { "backlog_truncated" },
            at = System.currentTimeMillis(),
        )
    }

    fun clearRelayGap() {
        relayGap.value = null
    }

    /**
     * Aplica o quadro resume. O hub manda no máximo 60 eventos por lane;
     * o que passar disso fica de fora para não crescer a transcrição.
     */
    fun onResumePayload(plain: String) {
        val root = runCatching { json.parseToJsonElement(plain) }.getOrNull() as? JsonObject ?: return
        val eventsEl = root["events"] as? JsonArray ?: return
        val events = runCatching { json.decodeFromJsonElement<List<StreamEvent>>(eventsEl) }.getOrDefault(emptyList())
        val grouped = linkedMapOf<String, MutableList<LaneEventItem>>()
        val spaces = mutableMapOf<String, String>()
        val other = mutableListOf<StreamEvent>()
        for (ev in events) {
            val data = ev.data
            if (ev.type == "lane_event" && data is JsonObject && data.containsKey("item")) {
                val replay = runCatching { json.decodeFromJsonElement<ReplayLane>(data) }.getOrNull()
                if (replay != null && replay.laneId.isNotBlank()) {
                    grouped.getOrPut(replay.laneId) { mutableListOf() }.add(replay.item)
                    if (replay.workspace.isNotBlank()) spaces[replay.laneId] = replay.workspace
                    continue
                }
            }
            other += ev
        }
        if (grouped.isNotEmpty()) {
            val next = lanes.value.toMutableMap()
            for ((laneId, items) in grouped) {
                val ws = spaces[laneId].orEmpty()
                val current = next.values.firstOrNull { it.laneId == laneId && (ws.isBlank() || it.workspace == ws) }
                    ?: LaneSnapshot(workspace = ws, laneId = laneId)
                val capped = if (items.size > LanProtocol.MAX_RESUME_EVENTS) {
                    items.takeLast(LanProtocol.MAX_RESUME_EVENTS)
                } else {
                    items
                }
                val updated = current.copy(workspace = ws.ifBlank { current.workspace }, recentEvents = capped)
                next[updated.key] = updated
            }
            lanes.value = next
            refreshPending()
        }
        other.forEach(::onStreamEvent)
    }

    /** Roteia um evento SSE bruto. */
    fun onStreamEvent(ev: StreamEvent) {
        when (ev.type) {
            "lane_event" -> {
                val data = ev.data ?: return
                runCatching { json.decodeFromString<LaneEvent>(data.toString()) }.onSuccess(::onLaneEvent)
            }
            "approval_resolved" -> {
                val data = ev.data ?: return
                runCatching { json.decodeFromString<ApprovalResolvedPayload>(data.toString()) }
                    .onSuccess { onApprovalResolved(it.approvalId) }
            }
            "autoreview_status" -> {
                val data = ev.data ?: return
                runCatching { json.decodeFromString<AutoReviewStatus>(data.toString()) }.onSuccess(::onAutoReviewStatus)
            }
            "provider_health" -> {
                val data = ev.data ?: return
                runCatching { json.decodeFromString<ProviderHealthPayload>(data.toString()) }
                    .onSuccess { onProviderHealth(it) }
            }
            "relay_gap" -> {
                val data = ev.data ?: return
                runCatching { json.decodeFromString<RelayGapPayload>(data.toString()) }
                    .onSuccess { onRelayGap(it) }
            }
            "lanes_cleared" -> {
                val obj = ev.data as? JsonObject
                val ws = obj?.get("workspace")?.toString()?.trim('"').orEmpty()
                val sid = obj?.get("sessionId")?.toString()?.trim('"').orEmpty()
                if (ws.isNotBlank()) {
                    if (sid.isBlank()) {
                        clearLaneConversation(ws, null)
                    } else {
                        lanes.value = lanes.value.filterValues { lane ->
                            lane.workspace != ws || lane.running || (lane.sessionId.isNotBlank() && lane.sessionId != sid)
                        }
                        refreshPending()
                    }
                }
            }
            else -> Unit
        }
    }

    private fun refreshPending() {
        _pendingApprovals.value = lanes.value.values.flatMap { it.pendingApprovals }
    }
}

@Serializable
private data class ReplayLane(
    val workspace: String = "",
    val laneId: String = "",
    val item: LaneEventItem = LaneEventItem(),
)
