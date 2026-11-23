package dev.hivemind.mobile.features.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.models.AutoReviewEntry
import dev.hivemind.mobile.core.models.AutoReviewStatus
import dev.hivemind.mobile.core.models.ProjectIssue
import dev.hivemind.mobile.core.models.ProjectPullRequest
import dev.hivemind.mobile.features.inbox.InboxIndex
import dev.hivemind.mobile.theme.InfoBlue
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.theme.TextPrimary
import dev.hivemind.mobile.ui.EmptyStatePanel
import dev.hivemind.mobile.ui.EntryBackHeader
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveBlob
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.PillButton
import dev.hivemind.mobile.ui.StatusPill
import dev.hivemind.mobile.ui.blobColor
import kotlinx.coroutines.launch

@Composable
fun FlowScreen(
    container: AppContainer,
    padding: PaddingValues,
    workspace: String?,
    onBack: (() -> Unit)? = null,
    onOpenChat: (prompt: String?, runMode: String?) -> Unit = { _, _ -> },
) {
    var tab by remember { mutableIntStateOf(0) }
    if (workspace == null) {
        Column(Modifier.fillMaxSize().padding(padding)) {
            FlowStudio(project = "sem projeto", tab = 0, onTab = {}, onBack = onBack) {
                dev.hivemind.mobile.ui.StudioEmpty(
                    icon = Icons.Outlined.AccountTree,
                    title = "Nenhum projeto selecionado",
                    body = "Escolha um projeto na tela inicial para ver tasks, pull requests e Auto-Review.",
                )
            }
        }
        return
    }

    val projectLeaf = workspace.substringAfterLast('\\').substringAfterLast('/').ifBlank { workspace }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        FlowStudio(
            project = projectLeaf,
            tab = tab,
            onTab = { tab = it },
            onBack = onBack,
        ) {
            when (tab) {
                0 -> JiraTasksTab(container, workspace, onOpenChat)
                1 -> PRsTab(container, workspace, onOpenChat)
                else -> AutoReviewTab(container, workspace)
            }
        }
    }
}

@Composable
private fun JiraTasksTab(
    container: AppContainer,
    workspace: String,
    onOpenChat: (prompt: String?, runMode: String?) -> Unit,
) {
    var issues by remember { mutableStateOf<List<ProjectIssue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var solving by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    suspend fun load() {
        loading = true
        error = null
        runCatching { container.api.issues(workspace) }
            .onSuccess { issues = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(workspace) { load() }

    val filtered = issues.filter { i ->
        query.isBlank() ||
            i.key.contains(query, ignoreCase = true) ||
            i.title.contains(query, ignoreCase = true) ||
            i.status.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar por chave, título ou status…", color = TextDim) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextDim) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar busca", tint = TextDim)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGold,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            IconButton(
                onClick = { container.appScope.launch { load() } },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = TextDim)
            }
        }

        if (actionError != null) {
            ErrorBanner(
                message = actionError.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        when {
            loading -> Center { CircularProgressIndicator() }
            error != null -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                dev.hivemind.mobile.ui.StudioEmpty(
                    icon = Icons.Outlined.CloudOff,
                    title = "Sem resposta da IDE",
                    body = error.orEmpty(),
                )
            }
            filtered.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                dev.hivemind.mobile.ui.StudioEmpty(
                    icon = Icons.Outlined.TaskAlt,
                    title = if (issues.isEmpty()) "Nenhuma task atribuída" else "Nenhum resultado",
                    body = if (issues.isEmpty()) {
                        "Tasks atribuídas a você no Jira ou GitHub aparecem aqui."
                    } else {
                        "Nada corresponde a \"$query\"."
                    },
                )
                Spacer(Modifier.height(16.dp))
                dev.hivemind.mobile.ui.PillButton(
                    text = if (query.isNotBlank()) "Limpar filtro" else "Atualizar",
                    onClick = {
                        if (query.isNotBlank()) query = "" else container.appScope.launch { load() }
                    },
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.key.ifBlank { it.title } }) { issue ->
                    HiveCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HiveBlob(color = blobColor(InboxIndex.avatarSlot(issue.key.ifBlank { issue.title })), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(issue.key.ifBlank { issue.source.ifBlank { "Task" } }, color = BrandGold, fontWeight = FontWeight.SemiBold)
                                Text(
                                    issue.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                text = issue.status.ifBlank { "aberta" },
                                color = if (issue.status.contains("progress", true) || issue.status.contains("andamento", true)) {
                                    ApprovalAmber
                                } else {
                                    InfoBlue
                                },
                            )
                            if (issue.isMine) StatusPill(text = "Minha", color = SuccessGreen)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                solving = issue.key
                                actionError = null
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                container.appScope.launch {
                                    runCatching {
                                        container.startRun(
                                            laneId = "lane-mobile",
                                            workspace = workspace,
                                            objective = "Resolver a task ${issue.key} — ${issue.title}",
                                            jiraKey = issue.key,
                                        )
                                    }.onSuccess {
                                        container.hubState.selectedLaneId.value = "lane-mobile"
                                        container.refreshLanes(workspace)
                                        onOpenChat(null, null)
                                    }.onFailure { actionError = "Falha: ${it.message}" }
                                    solving = null
                                }
                            },
                            enabled = solving == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGold,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                        ) {
                            if (solving == issue.key) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Iniciando equipe…")
                            } else {
                                Text("Resolver c/ Equipe", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PRsTab(
    container: AppContainer,
    workspace: String,
    onOpenChat: (prompt: String?, runMode: String?) -> Unit,
) {
    var prs by remember { mutableStateOf<List<ProjectPullRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reviewingPr by remember { mutableStateOf<Int?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    suspend fun load() {
        loading = true
        error = null
        runCatching { container.api.prs(workspace) }
            .onSuccess { prs = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(workspace) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (prs.isEmpty()) "Pull Requests" else "${prs.size} PR(s) aberto(s)",
                style = MaterialTheme.typography.labelLarge,
                color = TextDim,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { container.appScope.launch { load() } },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = TextDim)
            }
        }
        if (actionError != null) {
            ErrorBanner(
                message = actionError.orEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        when {
            loading -> Center { CircularProgressIndicator() }
            error != null -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                ErrorBanner(
                    message = error.orEmpty(),
                    actionLabel = "Tentar",
                    onAction = { container.appScope.launch { load() } },
                )
            }
            prs.isEmpty() -> EmptyStatePanel(
                title = "Nenhum PR aberto",
                subtitle = "Pull requests abertos neste repositório aparecerão aqui.",
                actionLabel = "Atualizar",
                onAction = { container.appScope.launch { load() } },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(prs, key = { it.number }) { pr ->
                    HiveCard(
                        highlighted = pr.needsAttention || pr.reviewRequested,
                        accentColor = if (pr.needsAttention) ApprovalAmber else BrandGold,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusPill(text = "#${pr.number}", color = BrandGold)
                            if (pr.needsAttention) {
                                StatusPill(
                                    text = pr.attentionReason.ifBlank { "Atenção necessária" },
                                    color = ApprovalAmber,
                                    showDot = true,
                                )
                            } else if (pr.reviewRequested) {
                                StatusPill(text = "Review solicitado", color = BrandGold)
                            }
                            if (pr.isDraft) {
                                StatusPill(text = "Draft", color = TextDim)
                            }
                            if (pr.isMine) {
                                StatusPill(text = "Meu PR", color = SuccessGreen)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pr.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        val meta = listOfNotNull(
                            pr.author.ifBlank { null },
                            if (pr.headRef.isNotBlank() && pr.baseRef.isNotBlank()) "${pr.headRef} → ${pr.baseRef}" else null,
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = TextDim)
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!pr.isMine) {
                                    onOpenChat(reviewObjective(pr), null)
                                    return@Button
                                }
                                reviewingPr = pr.number
                                actionError = null
                                container.appScope.launch {
                                    runCatching { container.refreshProjects() }
                                    val lane = container.hubState.ideLane(workspace)
                                    val mode = container.hubState.ideRunMode(workspace)
                                    runCatching {
                                        container.startRun(
                                            laneId = lane?.laneId ?: "lane-mobile",
                                            workspace = workspace,
                                            team = lane?.teamName?.takeIf { it.isNotBlank() },
                                            objective = reviewObjective(pr),
                                            runMode = mode,
                                        )
                                    }.onSuccess {
                                        lane?.let { container.hubState.selectedLaneId.value = it.laneId }
                                        container.refreshLanes(workspace)
                                        onOpenChat(null, null)
                                    }.onFailure { actionError = "Falha: ${it.message}" }
                                    reviewingPr = null
                                }
                            },
                            enabled = reviewingPr == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGold,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp),
                        ) {
                            Text(
                                if (!pr.isMine) "Revisar" else if (reviewingPr == pr.number) "Iniciando…" else "Trabalhar c/ Equipe",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun reviewObjective(pr: ProjectPullRequest): String {
    val branchInfo = if (pr.headRef.isNotBlank() && pr.baseRef.isNotBlank()) {
        " (${pr.headRef} → ${pr.baseRef})"
    } else {
        ""
    }
    val bodyPart = if (pr.body.isNotBlank()) "\n\nDescrição do PR:\n${pr.body.take(1200)}" else ""
    val author = pr.author.ifBlank { "terceiro" }
    return if (pr.isMine) {
        "Atender feedback do nosso PR #${pr.number}$branchInfo — ${pr.title}. Leia o PR completo e todos os comentários/revisões dos revisores com github_get_pr antes de agir.$bodyPart"
    } else if (pr.needsAttention && pr.myResponse.isNotBlank()) {
        val reason = pr.attentionReason.ifBlank { "nova atualização" }.lowercase()
        "Reavaliar PR #${pr.number} de @$author$branchInfo — ${pr.title}. Houve $reason após minha última revisão; leia os comentários mais recentes com github_get_pr antes de responder.$bodyPart"
    } else {
        "Revisar PR #${pr.number} de @$author$branchInfo — ${pr.title}$bodyPart"
    }
}

@Composable
private fun AutoReviewTab(container: AppContainer, workspace: String) {
    var status by remember { mutableStateOf<AutoReviewStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var polling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val live by container.hubState.autoreview.collectAsState()
    val haptic = LocalHapticFeedback.current

    suspend fun load() {
        loading = true
        error = null
        runCatching { container.api.autoreviewStatus(workspace) }
            .onSuccess { status = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(workspace) { load() }

    val st = live[workspace] ?: status

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Estado em tempo real",
                style = MaterialTheme.typography.labelLarge,
                color = TextDim,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { container.appScope.launch { load() } },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = TextDim)
            }
        }
        when {
            loading && st == null -> Center { CircularProgressIndicator() }
            error != null && st == null -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                ErrorBanner(
                    message = error.orEmpty(),
                    actionLabel = "Tentar",
                    onAction = { container.appScope.launch { load() } },
                )
            }
            st != null -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    HiveCard(highlighted = st.enabled, accentColor = SuccessGreen) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Auto-Review (Sem Merge)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(
                                text = if (st.enabled) "LIGADO" else "DESLIGADO",
                                color = if (st.enabled) SuccessGreen else TextDim,
                                showDot = st.enabled,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(text = "Estado: ${st.state.ifBlank { "idle" }}", color = BrandGold)
                            StatusPill(text = "Fila: ${st.queueCount}", color = TextDim)
                            StatusPill(text = "Revisões/h: ${st.reviewedLastHour}", color = TextDim)
                        }
                        if (st.activePr > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Revisando PR #${st.activePr} — ${st.activePrTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandGold,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (st.lastError.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            ErrorBanner(message = st.lastError)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                polling = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                container.appScope.launch {
                                    runCatching { container.api.autoreviewPoll(workspace) }
                                        .onSuccess { status = it }
                                        .onFailure { error = it.message }
                                    polling = false
                                }
                            },
                            enabled = !polling,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGold,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                        ) {
                            Text(
                                if (polling) "Verificando…" else "Verificar agora",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (st.history.isNotEmpty()) {
                    item {
                        Text(
                            "Histórico recente",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextDim,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    items(st.history) { entry -> AutoReviewEntryRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun AutoReviewEntryRow(e: AutoReviewEntry) {
    val color = when (e.status) {
        "reviewed" -> SuccessGreen
        "error" -> ErrorRed
        else -> TextDim
    }
    HiveCard(
        containerColor = ObsidianSurface,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "#${e.prNumber} ${e.title}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusPill(text = e.status.ifBlank { "—" }, color = color)
        }
        if (e.summary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(e.summary, style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 3)
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun FlowStudio(
    project: String,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val tabs = listOf("Tasks", "PRs", "Review")
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to BrandGold.copy(alpha = 0.18f),
                        1f to dev.hivemind.mobile.theme.ObsidianBg,
                    ),
                ),
        )
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                if (onBack != null) {
                    EntryBackHeader("Fluxo", onBack)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Fluxo",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(project, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { index, label ->
                        val selected = tab == index
                        Surface(
                            onClick = { onTab(index) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) dev.hivemind.mobile.theme.ActionPrimary else ObsidianSurface,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, dev.hivemind.mobile.theme.BorderControl),
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    color = if (selected) dev.hivemind.mobile.theme.ActionPrimaryText else TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
fun FlowTasksPreview() {
    FlowStudio(project = "org-alpha", tab = 0, onTab = {}, onBack = {}) {
        HiveCard(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiveBlob(color = blobColor(1), modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PROJ-123", color = BrandGold, fontWeight = FontWeight.SemiBold)
                    Text("Revisar o contraste das pílulas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = "Em andamento", color = ApprovalAmber)
                StatusPill(text = "Minha", color = SuccessGreen)
            }
            Spacer(Modifier.height(12.dp))
            PillButton("Resolver c/ Equipe", onClick = {})
        }
    }
}

@Composable
fun FlowEmptyPreview() {
    FlowStudio(project = "org-alpha", tab = 0, onTab = {}, onBack = {}) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            dev.hivemind.mobile.ui.StudioEmpty(
                icon = Icons.Outlined.TaskAlt,
                title = "Nenhuma task atribuída",
                body = "Tasks atribuídas a você no Jira ou GitHub aparecem aqui.",
            )
        }
    }
}

@Composable
fun FlowErrorPreview() {
    FlowStudio(project = "org-alpha", tab = 0, onTab = {}, onBack = {}) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            dev.hivemind.mobile.ui.StudioEmpty(
                icon = Icons.Outlined.CloudOff,
                title = "Sem resposta da IDE",
                body = "O host respondeu 501: a IDE não está ligada.",
            )
        }
    }
}

@Composable
fun FlowPrsPreview() {
    FlowStudio(project = "org-alpha", tab = 1, onTab = {}, onBack = {}) {
        HiveCard(modifier = Modifier.padding(horizontal = 22.dp), highlighted = true, accentColor = ApprovalAmber) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiveBlob(color = blobColor(2), modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("#42", color = BrandGold, fontWeight = FontWeight.SemiBold)
                    Text("Contraste do onboarding", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text("example-dev · revisão pedida", color = TextDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
