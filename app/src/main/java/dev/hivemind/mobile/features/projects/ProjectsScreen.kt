package dev.hivemind.mobile.features.projects

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.models.ProjectSummary
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.SteelBlue
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import kotlinx.coroutines.launch

@Composable
fun ProjectsScreen(container: AppContainer, padding: PaddingValues, onOpenChat: () -> Unit) {
    val projects by container.hubState.projects.collectAsState()
    val conn by container.hubState.conn.collectAsState()
    val pairing by container.hubState.pairing.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var unpairConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        container.refreshProjects()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Projetos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    connectionLabel(conn, pairing?.routeLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (conn) {
                        is ConnState.Connected -> SuccessGreen
                        is ConnState.Reconnecting -> ApprovalAmber
                        is ConnState.Connecting -> ApprovalAmber
                        else -> TextDim
                    },
                )
            }
            IconButton(onClick = {
                loading = true
                container.appScope.launch {
                    container.refreshProjects()
                    container.refreshLanes()
                    loading = false
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
            }
            TextButton(onClick = { unpairConfirm = true }) { Text("Desconectar") }
        }

        if (unpairConfirm) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Desconectar do PC?", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { unpairConfirm = false }) { Text("Cancelar") }
                TextButton(onClick = {
                    unpairConfirm = false
                    container.appScope.launch { container.deactivate() }
                }) { Text("Sim, desconectar", color = MaterialTheme.colorScheme.error) }
            }
        }

        if (loading && projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhum projeto aberto na IDE do PC.\nAbra uma pasta na HiveMind IDE para vê-la aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(projects) { p ->
                ProjectCard(
                    p = p,
                    onClick = {
                        container.hubState.selectedWorkspace.value = p.workspace
                        onOpenChat()
                    },
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(p: ProjectSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (p.pendingApprovals > 0) {
                Badge("${p.pendingApprovals} aprovações", ApprovalAmber)
            }
            if (p.runningLanes > 0) {
                Spacer(Modifier.width(6.dp))
                Badge("${p.runningLanes} rodando", SuccessGreen)
            }
        }
        Spacer(Modifier.padding(top = 4.dp))
        Text(
            buildString {
                append(if (p.branch.isNotBlank()) p.branch else "sem git")
                if (p.stack.isNotBlank()) append(" · ${p.stack}")
                if (p.activeTeam.isNotBlank()) append(" · ${p.activeTeam}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
        )
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun connectionLabel(conn: ConnState, route: String?): String = when (conn) {
    is ConnState.Connected -> "Conectado · ${route ?: "—"}"
    is ConnState.Connecting -> "Conectando…"
    is ConnState.Reconnecting -> "Reconectando (${conn.attempt})…"
    is ConnState.Disconnected -> "Desconectado"
}
