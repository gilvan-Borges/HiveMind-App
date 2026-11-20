package dev.hivemind.mobile.features.projects

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.MainTab
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.SteelBlue
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.ui.ConnectionBadge
import dev.hivemind.mobile.ui.EmptyStatePanel
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.StatusPill
import kotlinx.coroutines.launch

@Composable
fun ProjectsScreen(
    container: AppContainer,
    padding: PaddingValues,
    onOpenTab: (MainTab) -> Unit,
) {
    val projects by container.hubState.projects.collectAsState()
    val selectedWorkspace by container.hubState.selectedWorkspace.collectAsState()
    val conn by container.hubState.conn.collectAsState()
    val pairing by container.hubState.pairing.collectAsState()
    val ideOnline by container.relay.ideOnlineState.collectAsState()
    val pendingApprovals by container.hubState.pendingApprovals.collectAsState()
    val relayMode = pairing?.activeUrl?.startsWith("relay://") == true
    var loading by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var unpairConfirm by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    fun refreshAll() {
        loading = true
        container.appScope.launch {
            container.refreshProjects()
            container.refreshLanes()
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        container.refreshProjects()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        // Cabeçalho com status de conexão + menu ⋮ (Lei de Jakob & Lei de Fitts: ações secundárias no overflow).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Projetos", style = MaterialTheme.typography.titleLarge)
                    val hostName = pairing?.host?.trim().orEmpty()
                    if (hostName.isNotBlank()) {
                        StatusPill(text = hostName, color = TextDim)
                    }
                }
                ConnectionBadge(
                    conn = conn,
                    routeLabel = pairing?.routeLabel,
                    relayMode = relayMode,
                    ideOffline = ideOnline.not(),
                )
            }

            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    refreshAll()
                },
                modifier = Modifier.size(48.dp),
            ) {
                if (loading && projects.isNotEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Atualizar projetos")
                }
            }

            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (relayMode) "Trocar IDE" else "Desconectar do PC") },
                        onClick = {
                            menuOpen = false
                            unpairConfirm = true
                        },
                    )
                }
            }
        }

        // Banner prioritário de aprovações pendentes (Efeito Zeigarnik + Von Restorff).
        val firstPending = pendingApprovals.firstOrNull()
        val projectPendingCount = projects.sumOf { it.pendingApprovals }
        val totalPending = maxOf(pendingApprovals.size, projectPendingCount)
        if (totalPending > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = ApprovalAmber.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ApprovalAmber.copy(alpha = 0.42f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$totalPending aprovação(ões) aguardando você",
                            style = MaterialTheme.typography.titleSmall,
                            color = ApprovalAmber,
                        )
                        Text(
                            "A equipe pausou aguardando sua decisão.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                        )
                    }
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val targetWs = firstPending?.workspace?.takeIf { it.isNotBlank() }
                                ?: projects.firstOrNull { it.pendingApprovals > 0 }?.workspace
                                ?: projects.firstOrNull()?.workspace
                            if (targetWs != null) {
                                container.hubState.selectedWorkspace.value = targetWs
                                firstPending?.laneId?.takeIf { it.isNotBlank() }?.let {
                                    container.hubState.selectedLaneId.value = it
                                }
                            }
                            onOpenTab(MainTab.Chat)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApprovalAmber,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 42.dp),
                    ) {
                        Text("Revisar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        when {
            loading && projects.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            projects.isEmpty() -> {
                EmptyStatePanel(
                    title = "Nenhum projeto aberto na IDE",
                    subtitle = "Abra uma pasta na HiveMind IDE do seu PC para controlar as equipes, diffs e tarefas aqui pelo celular.",
                    actionLabel = "Atualizar agora",
                    onAction = { refreshAll() },
                    secondaryLabel = if (relayMode) "Trocar IDE" else "Desconectar",
                    onSecondary = { unpairConfirm = true },
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(projects, key = { it.workspace }) { p ->
                        val isActive = selectedWorkspace == p.workspace
                        ProjectCard(
                            p = p,
                            isActive = isActive,
                            onSelectChat = {
                                container.hubState.selectedWorkspace.value = p.workspace
                                onOpenTab(MainTab.Chat)
                            },
                            onSelectFlow = {
                                container.hubState.selectedWorkspace.value = p.workspace
                                onOpenTab(MainTab.Flow)
                            },
                            onSelectDiffs = {
                                container.hubState.selectedWorkspace.value = p.workspace
                                onOpenTab(MainTab.Diffs)
                            },
                        )
                    }
                }
            }
        }
    }

    if (unpairConfirm) {
        AlertDialog(
            onDismissRequest = { unpairConfirm = false },
            title = { Text(if (relayMode) "Trocar de IDE?" else "Desconectar do PC?") },
            text = {
                Text(
                    if (relayMode) "Você voltará para a lista de IDEs vinculadas à sua conta."
                    else "O pareamento local será removido e você precisará escanear o QR novamente.",
                    color = TextDim,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        unpairConfirm = false
                        container.appScope.launch {
                            if (relayMode) container.relay.disconnectIde() else container.deactivate()
                        }
                    },
                ) {
                    Text(if (relayMode) "Trocar IDE" else "Desconectar", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { unpairConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectCard(
    p: ProjectSummary,
    isActive: Boolean,
    onSelectChat: () -> Unit,
    onSelectFlow: () -> Unit,
    onSelectDiffs: () -> Unit,
) {
    HiveCard(
        highlighted = isActive || p.pendingApprovals > 0,
        accentColor = if (p.pendingApprovals > 0) ApprovalAmber else SteelBlue,
        onClick = onSelectChat,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = p.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isActive) {
                        StatusPill(text = "Ativo", color = SteelBlue)
                    }
                }
            }
            if (p.pendingApprovals > 0) {
                StatusPill(text = "${p.pendingApprovals} aprovação", color = ApprovalAmber, showDot = true)
            }
            if (p.runningLanes > 0) {
                if (p.pendingApprovals > 0) Spacer(Modifier.width(6.dp))
                StatusPill(text = "${p.runningLanes} rodando", color = SuccessGreen, showDot = true)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Metadados em chips visuais (Chunking + Miller's Law).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetaChip(p.branch.ifBlank { "sem git" })
            if (p.stack.isNotBlank()) MetaChip(p.stack)
            if (p.activeTeam.isNotBlank()) MetaChip("Equipe: ${p.activeTeam}")
        }

        Spacer(Modifier.height(12.dp))

        // Ações rápidas com alvo >= 40dp (Lei de Fitts + Pareto 80/20).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSelectChat,
                modifier = Modifier
                    .weight(1.2f)
                    .heightIn(min = 40.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Abrir Chat", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onSelectFlow,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("Fluxo", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onSelectDiffs,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("Diffs", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

