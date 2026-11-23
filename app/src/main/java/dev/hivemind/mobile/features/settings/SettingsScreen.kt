package dev.hivemind.mobile.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.models.RoutineInfo
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.SurfacePanel
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.theme.TextPrimary
import dev.hivemind.mobile.ui.CircleIconButton
import kotlinx.coroutines.launch

data class SettingsRow(
    val title: String,
    val subtitle: String = "",
    val destructive: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun SettingsPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfacePanel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CircleIconButton(Icons.Filled.Close, "Fechar configurações", {}, tint = TextPrimary)
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ObsidianCard,
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = ObsidianSurface, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("C", fontWeight = FontWeight.SemiBold, color = BrandGold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("conta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("conta@example.com", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingsGroup(
            listOf(
                SettingsRow("IDE conectada", "Relay · pc-lab · Conectado") {},
                SettingsRow("Projetos na IDE", "2 aberto(s)") {},
            ),
        )
        Spacer(Modifier.height(12.dp))
        SettingsGroup(
            listOf(
                SettingsRow("Fluxo da equipe", "Tasks, PRs e Auto-Review") {},
                SettingsRow("Diffs", "Alterações do workspace") {},
            ),
        )
        Spacer(Modifier.height(12.dp))
        SettingsGroup(listOf(SettingsRow("Trocar IDE", "Volta para as IDEs da conta no relay", destructive = true) {}))
        Spacer(Modifier.height(12.dp))
        SettingsGroup(listOf(SettingsRow("Permissão do projeto", "governed") {}))
        Spacer(Modifier.height(12.dp))
        RoutineBlock()
    }
}

@Composable
private fun RoutineBlock() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                "Rotinas do Agent Hive",
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = TextDim,
            )
            RoutineRow("Revisar PRs do org-alpha", "0 9 * * 1-5", true)
            HorizontalDivider(color = ObsidianBorder)
            RoutineRow("Resumo da manhã", "30 8 * * *", false)
        }
    }
}

@Composable
private fun RoutineRow(title: String, expr: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2)
            Text(expr, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
        Switch(
            checked = enabled,
            onCheckedChange = {},
            colors = SwitchDefaults.colors(
                checkedTrackColor = BrandGold,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
fun SettingsSwitchDialogPreview() {
    Box(Modifier.fillMaxSize().background(SurfacePanel)) {
        SettingsPreview()
        AlertDialog(
            onDismissRequest = {},
            containerColor = ObsidianSurface,
            title = { Text("Trocar de IDE?") },
            text = { Text("A lista de IDEs da conta no relay abre em seguida. O pareamento da conta permanece.", color = TextDim) },
            confirmButton = { TextButton(onClick = {}) { Text("Trocar IDE", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = {}) { Text("Cancelar") } },
        )
    }
}

@Composable
fun SettingsPermissionDialogPreview() {
    Box(Modifier.fillMaxSize().background(SurfacePanel)) {
        SettingsPreview()
        AlertDialog(
            onDismissRequest = {},
            containerColor = ObsidianSurface,
            title = { Text("Permissão do projeto") },
            text = { Text("Full grava o modo no projeto. Governado volta a pedir aprovação.", color = TextDim) },
            confirmButton = { TextButton(onClick = {}) { Text("Full", color = BrandGold) } },
            dismissButton = { TextButton(onClick = {}) { Text("Governado") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    container: AppContainer,
    onClose: () -> Unit,
    onFlow: () -> Unit,
    onDiffs: () -> Unit,
    onProjects: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = SurfacePanel,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        SettingsBody(
            container = container,
            onClose = {
                scope.launch { sheetState.hide() }
                onClose()
            },
            onFlow = onFlow,
            onDiffs = onDiffs,
            onProjects = onProjects,
        )
    }
}

@Composable
fun SettingsBody(
    container: AppContainer,
    onClose: () -> Unit,
    onFlow: () -> Unit,
    onDiffs: () -> Unit,
    onProjects: () -> Unit,
) {
    val pairing by container.hubState.pairing.collectAsState()
    val conn by container.hubState.conn.collectAsState()
    val accounts by container.relay.accounts.collectAsState()
    val projects by container.hubState.projects.collectAsState()
    val workspace by container.hubState.selectedWorkspace.collectAsState()
    val relayMode = pairing?.activeUrl?.startsWith("relay://") == true
    var confirm by remember { mutableStateOf(false) }
    var permOpen by remember { mutableStateOf(false) }
    var routines by remember { mutableStateOf<List<RoutineInfo>>(emptyList()) }
    var transport by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { container.api.health() }.onSuccess { probe ->
            transport = probe.transport.ifBlank { null }
            if (workspace == null && !probe.openProject?.workspace.isNullOrBlank()) {
                container.hubState.selectedWorkspace.value = probe.openProject?.workspace
            }
        }
        runCatching { container.api.routines() }.onSuccess { routines = it }
    }
    val route = when (transport) {
        "relay" -> "Relay"
        "tailscale" -> "Tailscale"
        "lan" -> "LAN"
        else -> pairing?.routeLabel
    }
    val account = accounts.firstOrNull()
    val title = account?.email?.substringBefore("@")?.ifBlank { null }
        ?: pairing?.host?.ifBlank { null }
        ?: "HiveMind"
    val subtitle = account?.email?.ifBlank { null }
        ?: pairing?.routeLabel
        ?: "Sem IDE conectada"
    val initial = title.trim().firstOrNull()?.uppercase() ?: "H"
    val link = when (conn) {
        is ConnState.Connected -> "Conectado"
        is ConnState.Connecting -> "Conectando"
        is ConnState.Reconnecting -> "Reconectando"
        ConnState.Disconnected -> "Desconectado"
    }
    val groups = listOf(
        listOf(
            SettingsRow(
                title = "IDE conectada",
                subtitle = listOfNotNull(route, link).joinToString(" · "),
                onClick = { confirm = true },
            ),
            SettingsRow(
                title = "Projetos na IDE",
                subtitle = if (projects.isEmpty()) "Nenhum aberto" else "${projects.size} aberto(s)",
                onClick = onProjects,
            ),
        ),
        listOf(
            SettingsRow(title = "Fluxo da equipe", subtitle = "Tasks, PRs e Auto-Review", onClick = onFlow),
            SettingsRow(title = "Diffs", subtitle = "Alterações do workspace", onClick = onDiffs),
        ),
        listOf(
            SettingsRow(
                title = if (relayMode) "Trocar IDE" else "Desconectar desta IDE",
                subtitle = if (relayMode) "Volta para as IDEs da conta no relay" else "Remove o vínculo LAN deste aparelho",
                destructive = true,
                onClick = { confirm = true },
            ),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.94f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CircleIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Fechar configurações",
            onClick = onClose,
            tint = TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ObsidianCard,
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = ObsidianSurface, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(initial, fontWeight = FontWeight.SemiBold, color = BrandGold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        groups.forEach { group ->
            SettingsGroup(group)
            Spacer(Modifier.height(12.dp))
        }
        if (!notice.isNullOrBlank()) {
            Text(notice.orEmpty(), color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }
        val current = projects.firstOrNull { it.workspace == workspace }
        if (current != null) {
            SettingsGroup(
                listOf(
                    SettingsRow(
                        title = "Permissão do projeto",
                        subtitle = current.permissionMode.ifBlank { "governed" },
                        onClick = { permOpen = true },
                    ),
                ),
            )
            Spacer(Modifier.height(12.dp))
        }
        if (routines.isEmpty()) {
            SettingsGroup(listOf(SettingsRow("Rotinas do Agent Hive", "Nenhuma rotina publicada pela IDE.")))
            Spacer(Modifier.height(12.dp))
        }
        if (routines.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        "Rotinas do Agent Hive",
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextDim,
                    )
                    routines.forEach { routine ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(routine.text.ifBlank { routine.id }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(routine.expr, style = MaterialTheme.typography.bodySmall, color = TextDim, maxLines = 1)
                            }
                            Switch(
                                checked = routine.enabled,
                                onCheckedChange = { enabled ->
                                    container.appScope.launch {
                                        runCatching { container.api.setRoutineEnabled(routine.id, enabled) }
                                            .onSuccess {
                                                routines = routines.map { item ->
                                                    if (item.id == routine.id) item.copy(enabled = enabled) else item
                                                }
                                            }
                                            .onFailure { notice = it.message }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = BrandGold,
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (permOpen && workspace != null) {
        AlertDialog(
            onDismissRequest = { permOpen = false },
            containerColor = ObsidianSurface,
            title = { Text("Permissão do projeto") },
            text = { Text("Full grava o modo no projeto. Governado volta a pedir aprovação. Host sem a IDE responde 501.", color = TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    val ws = workspace ?: return@TextButton
                    permOpen = false
                    container.appScope.launch {
                        runCatching { container.api.setPermission(ws, "full") }
                            .onSuccess { container.refreshProjects() }
                            .onFailure { notice = it.message }
                    }
                }) { Text("Full", color = BrandGold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val ws = workspace ?: return@TextButton
                    permOpen = false
                    container.appScope.launch {
                        runCatching { container.api.setPermission(ws, "governed") }
                            .onSuccess { container.refreshProjects() }
                            .onFailure { notice = it.message }
                    }
                }) { Text("Governado") }
            },
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = ObsidianSurface,
            title = { Text(if (relayMode) "Trocar de IDE?" else "Desconectar desta IDE?") },
            text = {
                Text(
                    if (relayMode) "A lista de IDEs da conta no relay abre em seguida. O pareamento da conta permanece."
                    else "O vínculo LAN sai deste aparelho. Dá para parear de novo pelo QR.",
                    color = TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onClose()
                    container.appScope.launch {
                        if (relayMode) container.relay.disconnectIde() else container.deactivate()
                    }
                }) { Text(if (relayMode) "Trocar IDE" else "Desconectar", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SettingsGroup(rows: List<SettingsRow>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = ObsidianBorder)
                val click = row.onClick
                if (click != null) {
                    Surface(onClick = click, color = ObsidianCard, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        SettingsRowContent(row)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) { SettingsRowContent(row) }
                }
            }
        }
    }
}

@Composable
private fun SettingsRowContent(row: SettingsRow) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (row.destructive) ErrorRed else TextPrimary,
            )
            if (row.subtitle.isNotBlank()) {
                Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (row.onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextDim,
            )
        }
    }
}
