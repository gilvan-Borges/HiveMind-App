package dev.hivemind.mobile.features.inbox

import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.models.SessionSummary
import dev.hivemind.mobile.core.models.WorkspaceSessionsSummary
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConversationSource { IDE, AGENT_HIVE }

data class ConversationRow(
    val id: String,
    val workspace: String,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
    val unread: Int,
    val source: ConversationSource,
    val section: String,
    val laneId: String?,
    val sessionId: String?,
    val running: Boolean,
    val runMode: String = "auto",
)

data class ProjectRow(
    val workspace: String,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
    val unread: Int,
    val section: String,
    val running: Boolean,
)

data class InboxSection<T>(
    val name: String,
    val rows: List<T>,
)

interface InboxSource {
    suspend fun projects(): List<ProjectSummary>
    suspend fun lanes(workspace: String?): List<LaneSnapshot>
    suspend fun sessions(workspace: String): WorkspaceSessionsSummary
    suspend fun markRead(workspace: String, sessionId: String)
    suspend fun refreshApprovals(workspace: String)
    fun cachedProjects(): List<ProjectSummary> = emptyList()
    fun cachedLanes(): List<LaneSnapshot> = emptyList()
}

/**
 * Lista de conversas do contrato `GET /api/v1/sessions`: `kind=hive` é Agent
 * Hive (`runMode=resident`); kind vazio é equipe. `unread` vem do servidor.
 */
object InboxIndex {
    const val UNSECTIONED = "Sem seção"
    const val SECTION_EQUIPE = "Equipe"
    const val SECTION_HIVE = "Agent Hive"

    fun avatarSlot(seed: String): Int =
        (seed.hashCode() and Int.MAX_VALUE) % 4

    fun leaf(workspace: String): String =
        workspace.substringAfterLast('\\').substringAfterLast('/').ifBlank { workspace }

    fun parseActivityMillis(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return 0L
        text.toLongOrNull()?.let { value ->
            return if (value > 1_000_000_000_000L) value else value * 1000L
        }
        runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()?.let { return it }
        return 0L
    }

    fun formatInboxTime(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (millis <= 0L) return ""
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return when {
            date == today -> "%02d:%02d".format(time.hour, time.minute)
            date == today.minusDays(1) -> "Ontem"
            date.year == today.year -> "${date.dayOfMonth} de ${MONTHS[date.monthValue - 1]}"
            else -> "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
        }
    }

    fun fromSessions(
        workspace: String,
        sessions: List<SessionSummary>,
        lanes: List<LaneSnapshot>,
    ): List<ConversationRow> {
        val inWorkspace = lanes.filter { it.workspace.isBlank() || it.workspace == workspace }
        val bySession = inWorkspace.filter { it.sessionId.isNotBlank() }.associateBy { it.sessionId }
        val used = mutableSetOf<String>()
        val rows = mutableListOf<ConversationRow>()
        for (session in sessions) {
            if (session.id.isBlank()) continue
            used += session.id
            val hive = session.kind.equals("hive", true)
            val lane = if (hive) {
                inWorkspace.firstOrNull { it.laneId == "resident" && (it.sessionId.isBlank() || it.sessionId == session.id) }
            } else {
                bySession[session.id]
            }
            val liveText = lane?.recentEvents?.lastOrNull { it.kind == "text" && it.text.isNotBlank() }?.text.orEmpty()
            rows += ConversationRow(
                id = session.id,
                workspace = workspace,
                title = session.title.ifBlank { if (hive) "Agent Hive" else "Conversa" },
                preview = session.preview.ifBlank { liveText },
                updatedAtMillis = parseActivityMillis(session.updatedAt.ifBlank { lane?.startedAt.orEmpty() }),
                unread = session.unread.coerceAtLeast(0),
                source = if (hive) ConversationSource.AGENT_HIVE else ConversationSource.IDE,
                section = if (hive) SECTION_HIVE else SECTION_EQUIPE,
                laneId = if (hive) "resident" else lane?.laneId?.ifBlank { null },
                sessionId = session.id,
                running = lane?.running == true,
                runMode = if (hive) "resident" else lane?.runMode?.ifBlank { null } ?: "auto",
            )
        }
        for (lane in inWorkspace) {
            if (lane.laneId == "lane-autoreview") continue
            if (lane.sessionId.isNotBlank() && lane.sessionId in used) continue
            if (lane.sessionId.isBlank() && !lane.running && lane.recentEvents.isEmpty()) continue
            val hive = lane.laneId == "resident" || lane.runMode.equals("resident", true)
            val liveText = lane.recentEvents.lastOrNull { it.kind == "text" && it.text.isNotBlank() }?.text.orEmpty()
            rows += ConversationRow(
                id = lane.sessionId.ifBlank { "lane:${lane.laneId}" },
                workspace = workspace,
                title = lane.objective.ifBlank { lane.teamName.ifBlank { if (hive) "Agent Hive" else "Conversa" } },
                preview = liveText.ifBlank { lane.objective },
                updatedAtMillis = parseActivityMillis(lane.startedAt),
                unread = 0,
                source = if (hive) ConversationSource.AGENT_HIVE else ConversationSource.IDE,
                section = if (hive) SECTION_HIVE else SECTION_EQUIPE,
                laneId = lane.laneId.ifBlank { if (hive) "resident" else null },
                sessionId = lane.sessionId.ifBlank { null },
                running = lane.running,
                runMode = if (hive) "resident" else lane.runMode.ifBlank { "auto" },
            )
        }
        return rows
    }

    fun projectRows(
        projects: List<ProjectSummary>,
        conversations: Map<String, List<ConversationRow>>,
    ): List<ProjectRow> = projects.map { project ->
        val conv = conversations[project.workspace].orEmpty()
        val latest = conv.maxByOrNull { it.updatedAtMillis }
        val unread = conv.sumOf { it.unread } + project.pendingApprovals
        val preview = latest?.preview?.takeIf { it.isNotBlank() }
            ?: latest?.title?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                project.branch.takeIf { it.isNotBlank() },
                if (project.runningLanes > 0) "${project.runningLanes} em execução" else null,
            ).joinToString(" · ").ifBlank { "Sem atividade recente" }
        ProjectRow(
            workspace = project.workspace,
            title = project.name.ifBlank { leaf(project.workspace) },
            preview = preview,
            updatedAtMillis = latest?.updatedAtMillis ?: 0L,
            unread = unread,
            section = project.stack.ifBlank { UNSECTIONED },
            running = project.runningLanes > 0 || conv.any { it.running },
        )
    }

    fun <T> group(
        rows: List<T>,
        query: String,
        collapsed: Set<String>,
        sectionOf: (T) -> String,
        titleOf: (T) -> String,
        previewOf: (T) -> String,
        timeOf: (T) -> Long,
    ): List<InboxSection<T>> {
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) {
            rows
        } else {
            rows.filter {
                titleOf(it).contains(needle, ignoreCase = true) ||
                    previewOf(it).contains(needle, ignoreCase = true)
            }
        }
        val forceOpen = needle.isNotEmpty()
        return filtered
            .groupBy(sectionOf)
            .entries
            .sortedWith(compareBy<Map.Entry<String, List<T>>>({ it.key == UNSECTIONED }, { it.key.lowercase() }))
            .map { (name, items) ->
                val open = forceOpen || name !in collapsed
                InboxSection(
                    name = name,
                    rows = if (open) items.sortedByDescending(timeOf) else emptyList(),
                )
            }
    }

    fun draft(workspace: String, now: Long, hive: Boolean = false): ConversationRow = ConversationRow(
        id = "draft-" + UUID.randomUUID().toString().take(8),
        workspace = workspace,
        title = if (hive) "Agent Hive" else "Nova conversa",
        preview = "",
        updatedAtMillis = now,
        unread = 0,
        source = if (hive) ConversationSource.AGENT_HIVE else ConversationSource.IDE,
        section = if (hive) SECTION_HIVE else SECTION_EQUIPE,
        laneId = if (hive) "resident" else "lane-mobile",
        sessionId = null,
        running = false,
        runMode = if (hive) "resident" else "auto",
    )

    private val MONTHS = listOf(
        "jan.", "fev.", "mar.", "abr.", "mai.", "jun.",
        "jul.", "ago.", "set.", "out.", "nov.", "dez.",
    )
}

class InboxViewModel(
    private val source: InboxSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    private val _conversations = MutableStateFlow<Map<String, List<ConversationRow>>>(emptyMap())
    private val _query = MutableStateFlow("")
    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val projects: StateFlow<List<ProjectSummary>> = _projects.asStateFlow()
    val conversations: StateFlow<Map<String, List<ConversationRow>>> = _conversations.asStateFlow()
    val query: StateFlow<String> = _query.asStateFlow()
    val collapsed: StateFlow<Set<String>> = _collapsed.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        scope.launch { reload() }
    }

    suspend fun reload() {
        _loading.value = true
        if (_projects.value.isEmpty()) {
            val cached = source.cachedProjects()
            if (cached.isNotEmpty()) _projects.value = cached
        }
        try {
            val listed = source.projects()
            _projects.value = listed
            val lanes = runCatching { source.lanes(null) }.getOrElse { source.cachedLanes() }
            val built = linkedMapOf<String, List<ConversationRow>>()
            for (project in listed) {
                val ws = project.workspace
                if (ws.isBlank()) continue
                val sessions = runCatching { source.sessions(ws) }.getOrNull()
                built[ws] = InboxIndex.fromSessions(ws, sessions?.sessions.orEmpty(), lanes)
                runCatching { source.refreshApprovals(ws) }
            }
            _conversations.value = built
            _error.value = null
        } catch (e: Exception) {
            _error.value = e.message?.ifBlank { null } ?: "Não foi possível carregar os projetos."
        } finally {
            _loading.value = false
        }
    }

    fun search(value: String) {
        _query.value = value
    }

    fun clearSearch() {
        _query.value = ""
    }

    fun toggleProject(section: String) = toggle("p:$section")

    fun toggleConversation(section: String) = toggle("c:$section")

    private fun toggle(key: String) {
        _collapsed.value = if (key in _collapsed.value) _collapsed.value - key else _collapsed.value + key
    }

    fun markRead(workspace: String, sessionId: String) {
        if (sessionId.isBlank() || sessionId.startsWith("draft-") || sessionId.startsWith("lane:")) return
        _conversations.value = _conversations.value.mapValues { (ws, rows) ->
            if (ws != workspace) {
                rows
            } else {
                rows.map { row ->
                    if (row.sessionId == sessionId || row.id == sessionId) row.copy(unread = 0) else row
                }
            }
        }
        scope.launch { runCatching { source.markRead(workspace, sessionId) } }
    }

    fun projectPage(): List<InboxSection<ProjectRow>> {
        val rows = InboxIndex.projectRows(_projects.value, _conversations.value)
        return InboxIndex.group(
            rows = rows,
            query = _query.value,
            collapsed = prefixed("p:"),
            sectionOf = { it.section },
            titleOf = { it.title },
            previewOf = { it.preview },
            timeOf = { it.updatedAtMillis },
        )
    }

    fun conversationPage(workspace: String): List<InboxSection<ConversationRow>> {
        val raw = _conversations.value[workspace].orEmpty()
        return InboxIndex.group(
            rows = raw,
            query = _query.value,
            collapsed = prefixed("c:"),
            sectionOf = { it.section },
            titleOf = { it.title },
            previewOf = { it.preview },
            timeOf = { it.updatedAtMillis },
        )
    }

    private fun prefixed(prefix: String): Set<String> =
        _collapsed.value.mapNotNull { key -> key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix) }.toSet()

    fun draft(workspace: String, hive: Boolean = false): ConversationRow =
        InboxIndex.draft(workspace, clock(), hive)
}
