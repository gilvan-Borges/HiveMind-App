package dev.hivemind.mobile.core.state

import dev.hivemind.mobile.core.models.ApprovalInfo
import dev.hivemind.mobile.core.models.ApprovalResolvedPayload
import dev.hivemind.mobile.core.models.AutoReviewStatus
import dev.hivemind.mobile.core.models.LaneEvent
import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.models.StreamEvent
import dev.hivemind.mobile.core.store.Pairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

sealed interface ConnState {
    data object Disconnected : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    data class Reconnecting(val attempt: Int) : ConnState
}

/**
 * Estado vivo do app: pareamento, conexão, projetos, lanes (snapshot por
 * workspace|laneId), aprovações pendentes e status do Auto-Review.
 */
class HubState(private val json: Json) {

    val pairing = MutableStateFlow<Pairing?>(null)
    val conn = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val latencyMs = MutableStateFlow<Long?>(null)

    val projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val lanes = MutableStateFlow<Map<String, LaneSnapshot>>(emptyMap())
    val autoreview = MutableStateFlow<Map<String, AutoReviewStatus>>(emptyMap())

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
            at = now,
        )
        var approvals = snapshot.pendingApprovals
        var running = snapshot.running
        var stage = snapshot.stage
        var lastError = snapshot.lastError
        var turnTokens = snapshot.turnTokens
        when (e.kind) {
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
        val recent = if (e.kind == "usage") snapshot.recentEvents else snapshot.recentEvents.takeLast(59) + item
        setLane(
            snapshot.copy(
                teamName = if (ev.team.isNotBlank()) ev.team else snapshot.teamName,
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
            else -> Unit
        }
    }

    private fun refreshPending() {
        _pendingApprovals.value = lanes.value.values.flatMap { it.pendingApprovals }
    }
}
