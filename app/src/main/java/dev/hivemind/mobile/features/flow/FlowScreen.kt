package dev.hivemind.mobile.features.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.models.AutoReviewEntry
import dev.hivemind.mobile.core.models.AutoReviewStatus
import dev.hivemind.mobile.core.models.ProjectIssue
import dev.hivemind.mobile.core.models.ProjectPullRequest
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.SteelBlue
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import kotlinx.coroutines.launch

@Composable
fun FlowScreen(container: AppContainer, padding: PaddingValues, workspace: String?) {
    var tab by remember { mutableIntStateOf(0) }
    if (workspace == null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Selecione um projeto na aba Projetos.", color = TextDim)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Text(
            "Fluxo & Jira",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Minhas Tasks") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("PRs") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Auto-Review") })
        }
        when (tab) {
            0 -> JiraTasksTab(container, workspace)
            1 -> PRsTab(container, workspace)
            else -> AutoReviewTab(container, workspace)
        }
    }
}

@Composable
private fun JiraTasksTab(container: AppContainer, workspace: String) {
    var issues by remember { mutableStateOf<List<ProjectIssue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var solving by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        runCatching { container.api.issues(workspace) }
            .onSuccess { issues = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val filtered = issues.filter { i ->
        query.isBlank() || i.key.contains(query, true) || i.title.contains(query, true) || i.status.contains(query, true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar task (chave, título, status)…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = { container.appScope.launch { load() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
            }
        }
        if (message != null) {
            Text(message.orEmpty(), color = SuccessGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        when {
            loading -> Center { CircularProgressIndicator() }
            error != null -> Center { Text(error.orEmpty(), color = ErrorRed) }
            filtered.isEmpty() -> Center {
                Text(
                    if (issues.isEmpty()) "Nenhuma task sua no Jira/GitHub (assignee = currentUser())." else "Nenhum resultado.",
                    color = TextDim,
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { issue ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { message = null },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(issue.key, style = MaterialTheme.typography.labelMedium, color = SteelBlue, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        issue.status.ifBlank { issue.source },
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.padding(top = 4.dp))
                            Text(issue.title, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.padding(top = 8.dp))
                            Button(
                                onClick = {
                                    solving = issue.key
                                    container.appScope.launch {
                                        runCatching {
                                            container.startRun(
                                                laneId = "lane-mobile",
                                                workspace = workspace,
                                                objective = "Resolver a task ${issue.key} — ${issue.title}",
                                                jiraKey = issue.key,
                                            )
                                        }.onSuccess {
                                            message = "Equipe iniciada para resolver ${issue.key} (veja na aba Chat)."
                                        }.onFailure { message = "Falha: ${it.message}" }
                                        solving = null
                                    }
                                },
                                enabled = solving == null,
                            ) {
                                Text(if (solving == issue.key) "Iniciando…" else "Resolver c/ Equipe")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PRsTab(container: AppContainer, workspace: String) {
    var prs by remember { mutableStateOf<List<ProjectPullRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        runCatching { container.api.prs(workspace) }
            .onSuccess { prs = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { container.appScope.launch { load() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
            }
        }
        when {
            loading -> Center { CircularProgressIndicator() }
            error != null -> Center { Text(error.orEmpty(), color = ErrorRed) }
            prs.isEmpty() -> Center { Text("Nenhum PR aberto.", color = TextDim) }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(prs) { pr ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${pr.number}", style = MaterialTheme.typography.labelMedium, color = SteelBlue, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(pr.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            }
                            Spacer(Modifier.padding(top = 4.dp))
                            val attention = if (pr.needsAttention) pr.attentionReason.ifBlank { "Atenção necessária" } else ""
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    listOfNotNull(
                                        pr.state,
                                        if (pr.isDraft) "draft" else null,
                                        pr.author.ifBlank { null },
                                        attention.ifBlank { null },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        pr.isMine -> TextDim
                                        pr.needsAttention -> ApprovalAmber
                                        pr.reviewRequested -> SteelBlue
                                        else -> TextDim
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReviewTab(container: AppContainer, workspace: String) {
    var status by remember { mutableStateOf<AutoReviewStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val live by container.hubState.autoreview.collectAsState()

    suspend fun load() {
        loading = true
        runCatching { container.api.autoreviewStatus(workspace) }
            .onSuccess { status = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val st = live[workspace] ?: status

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { container.appScope.launch { load() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
            }
        }
        when {
            loading && st == null -> Center { CircularProgressIndicator() }
            error != null && st == null -> Center { Text(error.orEmpty(), color = ErrorRed) }
            st != null -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-Review (Sem Merge)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                if (st.enabled) "LIGADO" else "DESLIGADO",
                                color = if (st.enabled) SuccessGreen else TextDim,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.padding(top = 6.dp))
                        Text("Estado: ${st.state.ifBlank { "idle" }}", style = MaterialTheme.typography.bodySmall)
                        if (st.activePr > 0) {
                            Text("Revisando PR #${st.activePr} — ${st.activePrTitle}", style = MaterialTheme.typography.bodySmall, color = SteelBlue)
                        }
                        Text("Fila: ${st.queueCount} · Revisões/h: ${st.reviewedLastHour}", style = MaterialTheme.typography.bodySmall, color = TextDim)
                        if (st.lastError.isNotBlank()) {
                            Text("Erro: ${st.lastError}", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                        }
                        Spacer(Modifier.padding(top = 10.dp))
                        Button(
                            onClick = {
                                container.appScope.launch {
                                    runCatching { container.api.autoreviewPoll(workspace) }
                                        .onSuccess { status = it }
                                }
                            },
                        ) { Text("Verificar agora") }
                    }
                }
                Spacer(Modifier.padding(top = 12.dp))
                if (st.history.isNotEmpty()) {
                    Text("Histórico recente", style = MaterialTheme.typography.labelLarge, color = TextDim)
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 340.dp)) {
                        items(st.history) { entry -> AutoReviewEntryRow(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReviewEntryRow(e: AutoReviewEntry) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${e.prNumber} ${e.title}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                Text(e.status, style = MaterialTheme.typography.labelSmall, color = when (e.status) {
                    "reviewed" -> SuccessGreen
                    "error" -> ErrorRed
                    else -> TextDim
                })
            }
            if (e.summary.isNotBlank()) {
                Text(e.summary, style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 2)
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
