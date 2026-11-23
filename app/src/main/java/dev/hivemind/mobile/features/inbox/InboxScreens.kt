package dev.hivemind.mobile.features.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.R
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.models.WorkspaceSessionsSummary
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.InfoBlue
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.theme.TextPrimary
import dev.hivemind.mobile.ui.AppsGlyph
import dev.hivemind.mobile.ui.CircleIconButton
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveBlob
import dev.hivemind.mobile.ui.blobColor

@Composable
fun ProjectsInboxScreen(
    container: AppContainer,
    viewModel: InboxViewModel,
    padding: PaddingValues,
    onOpenProject: (ProjectSummary) -> Unit,
    onOpenDraft: (ConversationRow) -> Unit,
    onProfile: () -> Unit,
) {
    val projects by viewModel.projects.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val query by viewModel.query.collectAsState()
    val collapsed by viewModel.collapsed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val status by viewModel.statusLabel.collectAsState()
    val page = remember(projects, conversations, query, collapsed) { viewModel.projectPage() }
    var picker by remember { mutableStateOf(false) }
    var kindFor by remember { mutableStateOf<String?>(null) }
    val initial = profileInitial(container)

    InboxScaffold(
        padding = padding,
        title = "HiveMind",
        showMark = true,
        query = query,
        onQuery = viewModel::search,
        onProfile = onProfile,
        onCreate = {
            when (projects.size) {
                0 -> picker = true
                1 -> kindFor = projects.first().workspace
                else -> picker = true
            }
        },
        onRefresh = viewModel::refresh,
        profileInitial = initial,
        statusLabel = status,
        back = null,
    ) {
        InboxBody(
            loading = loading && projects.isEmpty(),
            error = error,
            empty = projects.isEmpty() && error == null,
            emptyTitle = "Nenhum projeto aberto na IDE",
            emptyBody = "Abra uma pasta na HiveMind do computador para ela aparecer aqui.",
            noResults = projects.isNotEmpty() && page.all { it.rows.isEmpty() } && query.isNotBlank(),
            onRetry = viewModel::refresh,
        ) {
            Sections(
                sections = page,
                onToggle = viewModel::toggleProject,
                row = { row ->
                    InboxRow(
                        seed = row.workspace,
                        title = row.title,
                        preview = row.preview.ifBlank { "Sem atividade recente" },
                        time = InboxIndex.formatInboxTime(row.updatedAtMillis, System.currentTimeMillis()),
                        unread = row.unread,
                        running = row.running,
                        onClick = {
                            projects.firstOrNull { it.workspace == row.workspace }?.let(onOpenProject)
                        },
                    )
                },
            )
        }
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            containerColor = ObsidianSurface,
            title = { Text("Nova conversa", fontWeight = FontWeight.SemiBold) },
            text = {
                if (projects.isEmpty()) {
                    Text("Nenhum projeto aberto na IDE ainda.", color = TextDim)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Escolha o projeto.", color = TextDim, style = MaterialTheme.typography.bodySmall)
                        projects.forEach { project ->
                            Surface(
                                onClick = {
                                    picker = false
                                    kindFor = project.workspace
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurface,
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(
                                    project.name.ifBlank { InboxIndex.leaf(project.workspace) },
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = false }) { Text("Fechar") }
            },
        )
    }

    kindFor?.let { workspace ->
        KindDialog(
            onEquipe = {
                kindFor = null
                onOpenDraft(viewModel.draft(workspace, hive = false))
            },
            onHive = {
                kindFor = null
                onOpenDraft(viewModel.draft(workspace, hive = true))
            },
            onDismiss = { kindFor = null },
        )
    }
}

@Composable
fun ConversationsInboxScreen(
    viewModel: InboxViewModel,
    workspace: String,
    projectTitle: String,
    padding: PaddingValues,
    profileInitial: String,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onOpen: (ConversationRow) -> Unit,
    onCreate: (ConversationRow) -> Unit,
) {
    val conversations by viewModel.conversations.collectAsState()
    val query by viewModel.query.collectAsState()
    val collapsed by viewModel.collapsed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val status by viewModel.statusLabel.collectAsState()
    val page = remember(conversations, query, collapsed, workspace) {
        viewModel.conversationPage(workspace)
    }
    var chooseKind by remember { mutableStateOf(false) }
    val hasAny = conversations[workspace].orEmpty().isNotEmpty()

    InboxScaffold(
        padding = padding,
        title = projectTitle,
        showMark = true,
        query = query,
        onQuery = viewModel::search,
        onProfile = onProfile,
        onCreate = { chooseKind = true },
        onRefresh = viewModel::refresh,
        profileInitial = profileInitial,
        statusLabel = status,
        back = onBack,
    ) {
        InboxBody(
            loading = loading && !hasAny,
            error = if (!hasAny) error else null,
            empty = !loading && !hasAny && error == null,
            emptyTitle = "Nenhuma conversa neste projeto",
            emptyBody = "Toque em + para começar. As conversas da IDE e do Agent Hive aparecem aqui.",
            noResults = hasAny && page.all { it.rows.isEmpty() } && query.isNotBlank(),
            onRetry = viewModel::refresh,
        ) {
            Sections(
                sections = page,
                onToggle = viewModel::toggleConversation,
                row = { row ->
                    InboxRow(
                        seed = row.id,
                        title = row.title,
                        preview = row.preview.ifBlank { "Sem mensagens ainda" },
                        time = InboxIndex.formatInboxTime(row.updatedAtMillis, System.currentTimeMillis()),
                        unread = row.unread,
                        running = row.running,
                        onClick = { onOpen(row) },
                    )
                },
            )
        }
    }
    if (chooseKind) {
        KindDialog(
            onEquipe = {
                chooseKind = false
                onCreate(viewModel.draft(workspace, hive = false))
            },
            onHive = {
                chooseKind = false
                onCreate(viewModel.draft(workspace, hive = true))
            },
            onDismiss = { chooseKind = false },
        )
    }
}

@Composable
private fun KindDialog(onEquipe: () -> Unit, onHive: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Nova conversa", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Equipe segue o modo Auto. Agent Hive continua no modo residente.", color = TextDim, style = MaterialTheme.typography.bodySmall)
                Surface(onClick = onEquipe, shape = RoundedCornerShape(12.dp), color = ObsidianSurface, border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Equipe", modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp))
                }
                Surface(onClick = onHive, shape = RoundedCornerShape(12.dp), color = ObsidianSurface, border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Agent Hive", modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun InboxScaffold(
    padding: PaddingValues,
    title: String,
    showMark: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onProfile: () -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    profileInitial: String,
    statusLabel: String,
    back: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                onClick = onProfile,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (profileInitial.isBlank()) {
                        Icon(Icons.Filled.Person, contentDescription = "Abrir configurações", tint = TextPrimary)
                    } else {
                        Text(profileInitial, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
            }
            CircleIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Nova conversa",
                onClick = onCreate,
                tint = TextPrimary,
            )
        }
        if (statusLabel.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                dev.hivemind.mobile.ui.StatusPill(
                    text = statusLabel,
                    color = if (statusLabel.startsWith("IDE online")) SuccessGreen else TextDim,
                    showDot = true,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showMark) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.hivemind_mark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (back != null) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar aos projetos",
                    onClick = back,
                    tint = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp),
                    size = 40.dp,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
        SearchBar(query = query, onQuery = onQuery, onRefresh = onRefresh)
    }
}

@Composable
private fun InboxBody(
    loading: Boolean,
    error: String?,
    empty: Boolean,
    emptyTitle: String,
    emptyBody: String,
    noResults: Boolean,
    onRetry: () -> Unit,
    list: @Composable () -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandGold, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        error != null && empty -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorBanner(message = error)
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                color = BrandGold,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text(
                    "Tentar de novo",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        empty -> Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emptyTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(emptyBody, style = MaterialTheme.typography.bodySmall, color = TextDim, textAlign = TextAlign.Center)
        }
        noResults -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum resultado.", color = TextDim)
        }
        else -> Column(Modifier.fillMaxSize()) {
            if (!error.isNullOrBlank()) {
                ErrorBanner(message = error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Box(Modifier.weight(1f)) { list() }
        }
    }
}

@Composable
private fun <T> Sections(
    sections: List<InboxSection<T>>,
    onToggle: (String) -> Unit,
    row: @Composable (T) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        sections.forEach { section ->
            item(key = "section-${section.name}") {
                Surface(
                    onClick = { onToggle(section.name) },
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            section.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            if (section.rows.isEmpty()) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (section.rows.isEmpty()) "Expandir ${section.name}" else "Recolher ${section.name}",
                            tint = TextDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            items(section.rows, key = { rowKey(it) }) { item -> row(item) }
        }
    }
}

private fun rowKey(row: Any?): String = when (row) {
    is ProjectRow -> "p:${row.workspace}"
    is ConversationRow -> "c:${row.workspace}:${row.id}"
    else -> row?.hashCode()?.toString() ?: "row"
}

@Composable
private fun InboxRow(
    seed: String,
    title: String,
    preview: String,
    time: String,
    unread: Int,
    running: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                HiveBlob(color = blobColor(InboxIndex.avatarSlot(seed)), modifier = Modifier.size(48.dp))
                if (running) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .padding(1.dp),
                    ) {
                        Surface(shape = CircleShape, color = SuccessGreen, modifier = Modifier.fillMaxSize()) {}
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (time.isNotBlank()) {
                    Text(time, style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
                if (unread > 0) {
                    Surface(shape = CircleShape, color = InfoBlue, modifier = Modifier.size(10.dp)) {}
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onRefresh,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppsGlyph(tint = TextDim, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(BrandGold),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Buscar", color = TextDim, fontSize = 16.sp)
                            inner()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun profileInitial(container: AppContainer): String {
    val accounts by container.relay.accounts.collectAsState()
    val pairing by container.hubState.pairing.collectAsState()
    val source = accounts.firstOrNull()?.email?.substringBefore("@")
        ?: pairing?.host
        ?: "H"
    return source.trim().firstOrNull()?.uppercase() ?: "H"
}

@Composable
fun ProjectsInboxPreview() {
    InboxScaffold(
        padding = PaddingValues(0.dp),
        title = "HiveMind",
        showMark = true,
        query = "",
        onQuery = {},
        onProfile = {},
        onCreate = {},
        onRefresh = {},
        profileInitial = "A",
        statusLabel = "IDE online · LAN · acme",
        back = null,
    ) {
        PreviewSections(sampleProjectSections())
    }
}

@Composable
fun ConversationsInboxPreview() {
    InboxScaffold(
        padding = PaddingValues(0.dp),
        title = "org-alpha",
        showMark = true,
        query = "",
        onQuery = {},
        onProfile = {},
        onCreate = {},
        onRefresh = {},
        profileInitial = "A",
        statusLabel = "IDE online · Relay · org-alpha",
        back = {},
    ) {
        PreviewSections(sampleConversationSections())
    }
}

@Composable
private fun PreviewSections(sections: List<InboxSection<out Any>>) {
    when (val first = sections.firstOrNull()?.rows?.firstOrNull()) {
        is ProjectRow -> Sections(sections as List<InboxSection<ProjectRow>>, onToggle = {}) { row ->
            InboxRow(row.workspace, row.title, row.preview, InboxIndex.formatInboxTime(row.updatedAtMillis, previewNow()), row.unread, row.running) {}
        }
        is ConversationRow -> Sections(sections as List<InboxSection<ConversationRow>>, onToggle = {}) { row ->
            InboxRow(row.id, row.title, row.preview, InboxIndex.formatInboxTime(row.updatedAtMillis, previewNow()), row.unread, row.running) {}
        }
        null -> Unit
        else -> Unit
    }
}

private fun previewNow(): Long = InboxIndex.parseActivityMillis("2026-11-23T16:10:00Z")

private fun sampleProjectSections(): List<InboxSection<ProjectRow>> {
    val now = previewNow()
    return listOf(
        InboxSection(
            "mobile",
            listOf(
                ProjectRow("/work/org-alpha", "org-alpha", "A T017 está pronta para revisão no módulo", now - 86_400_000L, 1, "mobile", false),
            ),
        ),
        InboxSection(
            "Sem seção",
            listOf(
                ProjectRow("/work/hive", "HiveMind", "Fechado, o Agent Hive continua flutuante", now, 1, "Sem seção", true),
                ProjectRow("/work/acme", "acme", "Rodei os testes depois de cada cor", InboxIndex.parseActivityMillis("2026-11-16T12:00:00Z"), 0, "Sem seção", false),
            ),
        ),
    )
}

private fun sampleConversationSections(): List<InboxSection<ConversationRow>> = listOf(
    InboxSection(
        "IDE",
        listOf(
            ConversationRow("s1", "/work/org-alpha", "org-alpha", "A T017 está pronta para revisão", previewNow() - 3_600_000L, 0, ConversationSource.IDE, "IDE", "lane-1", "s1", false),
        ),
    ),
    InboxSection(
        "Agent Hive",
        listOf(
            ConversationRow("s2", "/work/org-alpha", "Agent Hive", "Fechado, o Agent Hive continua flutuante", previewNow(), 1, ConversationSource.AGENT_HIVE, "Agent Hive", "resident", "s2", true),
        ),
    ),
)

class ContainerInboxSource(private val container: AppContainer) : InboxSource {
    override suspend fun projects(): List<ProjectSummary> =
        container.api.projects().also { container.hubState.setProjects(it) }

    override suspend fun lanes(workspace: String?): List<LaneSnapshot> {
        val list = container.api.lanes(workspace)
        if (workspace.isNullOrBlank()) container.hubState.setLanes(list)
        else container.hubState.setWorkspaceLanes(workspace, list)
        return list
    }

    override suspend fun sessions(workspace: String): WorkspaceSessionsSummary {
        val summary = container.api.sessions(workspace)
        val ids = summary.sessions.map { it.id }.filter { it.isNotBlank() }.toSet()
        container.hubState.pruneDeletedSessions(workspace, ids)
        return summary
    }

    override suspend fun markRead(workspace: String, sessionId: String) {
        container.api.markSessionRead(workspace, sessionId)
    }

    override suspend fun refreshApprovals(workspace: String) {
        val items = container.api.approvals(workspace)
        container.hubState.mergeApprovals(workspace, items)
    }

    override suspend fun health(): dev.hivemind.mobile.core.models.Health? =
        runCatching { container.api.health() }.getOrNull()

    override fun cachedProjects(): List<ProjectSummary> = container.hubState.projects.value

    override fun cachedLanes(): List<LaneSnapshot> = container.hubState.lanes.value.values.toList()
}
