package dev.hivemind.mobile.features.flow

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
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.ui.EmptyStatePanel
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.StatusPill
import kotlinx.coroutines.launch

@Composable
fun FlowScreen(
    container: AppContainer,
    padding: PaddingValues,
    workspace: String?,
    onOpenChat: (prompt: String?, runMode: String?) -> Unit = { _, _ -> },
) {
    var tab by remember { mutableIntStateOf(0) }
    if (workspace == null) {
        EmptyStatePanel(
            title = "Nenhum projeto selecionado",
            subtitle = "Escolha um projeto na tela inicial para ver tasks, pull requests e Auto-Review.",
            modifier = Modifier.padding(padding),
        )
        return
    }

    val projectLeaf = workspace.substringAfterLast('\\').substringAfterLast('/').ifBlank { workspace }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Surface(color = ObsidianCard) {
            Column {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        "Fluxo & Jira",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        projectLeaf,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TabRow(selectedTabIndex = tab, containerColor = ObsidianCard) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Minhas Tasks") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("PRs") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Auto-Review") })
                }
                HorizontalDivider(color = ObsidianBorder)
            }
        }
        when (tab) {
            0 -> JiraTasksTab(container, workspace, onOpenChat)
            1 -> PRsTab(container, workspace, onOpenChat)
            else -> AutoReviewTab(container, workspace)
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
                ErrorBanner(
                    message = error.orEmpty(),
                    actionLabel = "Tentar",
                    onAction = { container.appScope.launch { load() } },
                )
            }
            filtered.isEmpty() -> EmptyStatePanel(
                title = if (issues.isEmpty()) "Nenhuma task atribuída" else "Nenhum resultado",
                subtitle = if (issues.isEmpty()) {
                    "Tasks atribuídas a você no Jira ou GitHub aparecerão aqui."
                } else {
                    "Nenhuma task corresponde ao filtro \"$query\"."
                },
                actionLabel = if (query.isNotBlank()) "Limpar filtro" else "Atualizar",
                onAction = {
                    if (query.isNotBlank()) query = "" else container.appScope.launch { load() }
                },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.key.ifBlank { it.title } }) { issue ->
                    HiveCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusPill(text = issue.key, color = BrandGold)
                            StatusPill(
                                text = issue.status.ifBlank { issue.source },
                                color = if (issue.status.contains("progress", true) || issue.status.contains("andamento", true)) {
                                    ApprovalAmber
                                } else {
                                    TextDim
                                },
                            )
                            if (issue.isMine) {
                                StatusPill(text = "Minha", color = SuccessGreen)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            issue.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
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
                                Text("Resolver c/ Equipe", fontWeight = FontWeight.SemiBold)
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
fun FlowTasksPreview() {
    Column(Modifier.fillMaxSize()) {
        Surface(color = ObsidianCard) {
            Column {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Fluxo & Jira", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("org-alpha", style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
                TabRow(selectedTabIndex = 0, containerColor = ObsidianCard) {
                    Tab(selected = true, onClick = {}, text = { Text("Minhas Tasks") })
                    Tab(selected = false, onClick = {}, text = { Text("PRs") })
                    Tab(selected = false, onClick = {}, text = { Text("Auto-Review") })
                }
            }
        }
        HiveCard(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = "PROJ-123", color = BrandGold)
                StatusPill(text = "Em andamento", color = ApprovalAmber)
                StatusPill(text = "Minha", color = SuccessGreen)
            }
            Spacer(Modifier.height(8.dp))
            Text("Revisar o contraste das pílulas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = BrandGold, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) { Text("Resolver c/ Equipe", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun FlowEmptyPreview() {
    Column(Modifier.fillMaxSize()) {
        Surface(color = ObsidianCard) {
            Text("Fluxo & Jira", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        EmptyStatePanel(
            title = "Nenhuma task atribuída",
            subtitle = "Tasks atribuídas a você no Jira ou GitHub aparecerão aqui.",
            actionLabel = "Atualizar",
            onAction = {},
        )
    }
}

@Composable
fun FlowErrorPreview() {
    Column(Modifier.fillMaxSize()) {
        Surface(color = ObsidianCard) {
            Text("Fluxo & Jira", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            ErrorBanner(message = "O host respondeu 501: a IDE não está ligada.", actionLabel = "Tentar", onAction = {})
        }
    }
}

@Composable
fun FlowPrsPreview() {
    Column(Modifier.fillMaxSize()) {
        Surface(color = ObsidianCard) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Fluxo & Jira", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("1 PR(s) aberto(s)", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
        HiveCard(modifier = Modifier.padding(12.dp), highlighted = true, accentColor = ApprovalAmber) {
            StatusPill(text = "#42", color = BrandGold)
            Spacer(Modifier.height(8.dp))
            Text("Contraste do onboarding", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text("example-dev · revisão pedida", color = TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
