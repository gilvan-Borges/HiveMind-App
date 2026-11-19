package dev.hivemind.mobile.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// --- Health ---

@Serializable
data class Health(
    val ok: Boolean = false,
    val hostName: String = "",
    val version: String = "",
    val time: String = "",
)

// --- Projects & Lanes ---

@Serializable
data class ProjectSummary(
    val workspace: String = "",
    val name: String = "",
    val branch: String = "",
    val stack: String = "",
    val activeTeam: String = "",
    val runningLanes: Int = 0,
    val pendingApprovals: Int = 0,
)

@Serializable
data class ApprovalInfo(
    val approvalId: String = "",
    val laneId: String = "",
    val workspace: String = "",
    val prompt: String = "",
    val label: String = "",
    val createdAt: String = "",
)

@Serializable
data class LaneEventItem(
    val kind: String = "",
    val stage: String = "",
    val author: String = "",
    val label: String = "",
    val text: String = "",
    val toolName: String = "",
    val tokens: Long = 0,
    val at: String = "",
)

@Serializable
data class LaneSnapshot(
    val workspace: String = "",
    val laneId: String = "",
    val teamName: String = "",
    val sessionId: String = "",
    val runMode: String = "",
    val objective: String = "",
    val running: Boolean = false,
    val stage: String = "",
    val lastError: String = "",
    val startedAt: String = "",
    val turnTokens: Long = 0,
    val recentEvents: List<LaneEventItem> = emptyList(),
    val pendingApprovals: List<ApprovalInfo> = emptyList(),
) {
    val key: String get() = "$workspace|$laneId"
}

// --- Stream (SSE) ---

@Serializable
data class OrchestratorEvent(
    val kind: String = "",
    val stage: String? = null,
    val author: String? = null,
    val label: String? = null,
    val text: String? = null,
    val toolName: String? = null,
    val detail: String? = null,
    val approvalId: String? = null,
    val partial: Boolean = false,
    val thought: Boolean = false,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val tokens: Long = 0,
)

@Serializable
data class LaneEvent(
    val laneId: String = "",
    val team: String = "",
    val event: OrchestratorEvent = OrchestratorEvent(),
)

@Serializable
data class StreamEvent(
    val type: String = "",
    val data: JsonElement? = null,
)

@Serializable
data class ApprovalResolvedPayload(
    val approvalId: String = "",
    val approved: Boolean = false,
)

// --- Sessions ---

@Serializable
data class SessionSummary(
    val id: String = "",
    val updatedAt: String = "",
    val title: String = "",
    val jiraKey: String = "",
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
)

@Serializable
data class WorkspaceSessionsSummary(
    val sessions: List<SessionSummary> = emptyList(),
    val projectInputTokens: Long = 0,
    val projectOutputTokens: Long = 0,
    val projectTotalTokens: Long = 0,
)

@Serializable
data class TokenStat(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val calls: Int = 0,
)

@Serializable
data class RestoredMessage(
    val id: String = "",
    val kind: String = "",
    val label: String = "",
    val text: String = "",
    val tokens: Long = 0,
)

@Serializable
data class SessionDetail(
    val id: String = "",
    val title: String = "",
    val jiraKey: String = "",
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val agentUsage: Map<String, TokenStat> = emptyMap(),
    val messages: List<RestoredMessage> = emptyList(),
)

// --- Git / Diffs ---

@Serializable
data class DiffLine(
    val kind: String = "",
    val text: String = "",
)

@Serializable
data class FileDiff(
    val path: String = "",
    val status: String = "",
    val staged: Boolean = false,
    val added: Int = 0,
    val removed: Int = 0,
    val lines: List<DiffLine> = emptyList(),
)

@Serializable
data class ProjectPullRequest(
    val number: Int = 0,
    val title: String = "",
    val body: String = "",
    val url: String = "",
    val state: String = "",
    @SerialName("isDraft") val isDraft: Boolean = false,
    val author: String = "",
    val headRef: String = "",
    val baseRef: String = "",
    val updatedAt: String = "",
    val reviewDecision: String = "",
    val requestedReviewers: List<String> = emptyList(),
    @SerialName("isMine") val isMine: Boolean = false,
    val reviewRequested: Boolean = false,
    val needsAttention: Boolean = false,
    val myResponse: String = "",
    val attentionReason: String = "",
)

@Serializable
data class ProjectIssue(
    val source: String = "",
    val key: String = "",
    val title: String = "",
    val body: String = "",
    val status: String = "",
    val author: String = "",
    val url: String = "",
    @SerialName("isMine") val isMine: Boolean = false,
)

// --- Auto-Review ---

@Serializable
data class AutoReviewEntry(
    val prNumber: Int = 0,
    val title: String = "",
    val author: String = "",
    val headRef: String = "",
    val baseRef: String = "",
    val status: String = "",
    val summary: String = "",
    val tokens: Long = 0,
    val durationMs: Long = 0,
    val sessionId: String = "",
    val reviewedAt: String = "",
    val error: String = "",
)

@Serializable
data class AutoReviewSettings(
    val enabled: Boolean = false,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 120,
    @SerialName("run_tests") val runTests: Boolean? = null,
    @SerialName("review_action") val reviewAction: String = "",
    @SerialName("include_drafts") val includeDrafts: Boolean = false,
    @SerialName("max_prs_per_hour") val maxPrsPerHour: Int = 0,
    @SerialName("max_tokens_per_pr") val maxTokensPerPr: Long = 0,
    val model: String = "",
)

@Serializable
data class AutoReviewStatus(
    val workspace: String = "",
    val enabled: Boolean = false,
    val state: String = "",
    val config: AutoReviewSettings = AutoReviewSettings(),
    val activePr: Int = 0,
    val activePrTitle: String = "",
    val queueCount: Int = 0,
    val reviewedLastHour: Int = 0,
    val lastPollAt: String = "",
    val nextPollAt: String = "",
    val lastError: String = "",
    val history: List<AutoReviewEntry> = emptyList(),
)

// --- Erros ---

@Serializable
data class ApiErrorBody(
    val error: String = "",
)
