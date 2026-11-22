package dev.hivemind.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.hivemind.mobile.features.chat.ChatScreen
import dev.hivemind.mobile.features.diffs.DiffsScreen
import dev.hivemind.mobile.features.flow.FlowScreen
import dev.hivemind.mobile.features.projects.ProjectsScreen
import dev.hivemind.mobile.features.relay.GateScreen
import dev.hivemind.mobile.features.relay.RelayDevicesScreen
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.BrandGoldBorder
import dev.hivemind.mobile.theme.BrandGoldSubtle
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.HiveMindTheme
import dev.hivemind.mobile.theme.ObsidianBg
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.SurfacePanel
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.theme.TextPrimary
import dev.hivemind.mobile.ui.ConnectionBadge
import dev.hivemind.mobile.ui.StatusPill
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val icon: ImageVector) {
    Projects("Projetos", Icons.Filled.Home),
    Chat("Chat", Icons.AutoMirrored.Filled.Send),
    Flow("Fluxo", Icons.Filled.Build),
    Diffs("Diffs", Icons.AutoMirrored.Filled.List),
}

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as HiveMindApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            HiveMindTheme {
                val pairing by container.hubState.pairing.collectAsState()
                val loggedIn by container.relay.loggedIn.collectAsState()
                when {
                    pairing != null -> MainScaffold(container)
                    loggedIn -> RelayDevicesScreen(container)
                    else -> GateScreen(container)
                }
            }
        }
    }
}

@Composable
fun MainScaffold(container: AppContainer) {
    var tab by remember { mutableStateOf(MainTab.Chat) }
    var chatDraftSeed by remember { mutableStateOf<String?>(null) }
    val workspace by container.hubState.selectedWorkspace.collectAsState()
    val projects by container.hubState.projects.collectAsState()
    val conn by container.hubState.conn.collectAsState()
    val pairing by container.hubState.pairing.collectAsState()
    val ideOnline by container.relay.ideOnlineState.collectAsState()
    val pendingApprovals by container.hubState.pendingApprovals.collectAsState()
    val relayMode = pairing?.activeUrl?.startsWith("relay://") == true

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var refreshingProjects by remember { mutableStateOf(false) }
    var unpairConfirm by remember { mutableStateOf(false) }

    suspend fun syncWorkspaceSelection() {
        val currentProjects = container.hubState.projects.value
        val currentWorkspace = container.hubState.selectedWorkspace.value
        if (currentWorkspace == null || currentProjects.none { it.workspace == currentWorkspace }) {
            val first = currentProjects.firstOrNull()
            if (currentWorkspace != first?.workspace) {
                container.hubState.selectedLaneId.value = null
            }
            container.hubState.selectedWorkspace.value = first?.workspace
            if (first != null) {
                container.refreshLanes(first.workspace)
            }
        }
    }

    LaunchedEffect(pairing?.activeUrl) {
        refreshingProjects = true
        container.refreshProjects()
        container.refreshLanes()
        syncWorkspaceSelection()
        refreshingProjects = false
    }

    LaunchedEffect(projects) {
        syncWorkspaceSelection()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = tab == MainTab.Chat,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfacePanel,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(304.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "HiveMind",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = BrandGold,
                            )
                            ConnectionBadge(
                                conn = conn,
                                routeLabel = pairing?.activeUrl,
                                relayMode = relayMode,
                                ideOffline = !ideOnline,
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    refreshingProjects = true
                                    container.refreshProjects()
                                    container.refreshLanes()
                                    syncWorkspaceSelection()
                                    refreshingProjects = false
                                }
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            if (refreshingProjects) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandGold,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Atualizar projetos",
                                    tint = TextDim,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = ObsidianBorder)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "PROJETOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        if (projects.isEmpty() && !refreshingProjects) {
                            item {
                                Text(
                                    "Nenhum projeto aberto na IDE.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDim,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                )
                            }
                        }
                        items(projects, key = { it.workspace }) { project ->
                            val selected = project.workspace == workspace
                            val leaf = project.workspace.substringAfterLast('\\').substringAfterLast('/').ifBlank { project.workspace }
                            Surface(
                                onClick = {
                                    container.hubState.selectedWorkspace.value = project.workspace
                                    container.hubState.selectedLaneId.value = null
                                    tab = MainTab.Chat
                                    scope.launch {
                                        drawerState.close()
                                        container.refreshLanes(project.workspace)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) BrandGoldSubtle else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, BrandGoldBorder) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (project.runningLanes > 0) SuccessGreen else if (selected) BrandGold else ObsidianBorder,
                                                CircleShape,
                                            ),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            project.name.ifBlank { leaf },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) TextPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            project.workspace,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDim,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (project.runningLanes > 0) {
                                        StatusPill(text = "${project.runningLanes}", color = SuccessGreen)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = ObsidianBorder)
                    Spacer(Modifier.height(8.dp))

                    NavigationDrawerItem(
                        selected = tab == MainTab.Flow,
                        onClick = {
                            tab = MainTab.Flow
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                        label = { Text("Fluxo da equipe") },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = BrandGoldSubtle,
                            selectedIconColor = BrandGold,
                            selectedTextColor = TextPrimary,
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextPrimary,
                        ),
                    )
                    NavigationDrawerItem(
                        selected = tab == MainTab.Diffs,
                        onClick = {
                            tab = MainTab.Diffs
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Diffs") },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = BrandGoldSubtle,
                            selectedIconColor = BrandGold,
                            selectedTextColor = TextPrimary,
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextPrimary,
                        ),
                    )
                    NavigationDrawerItem(
                        selected = tab == MainTab.Projects,
                        onClick = {
                            tab = MainTab.Projects
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Gerenciar projetos") },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = BrandGoldSubtle,
                            selectedIconColor = BrandGold,
                            selectedTextColor = TextPrimary,
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextPrimary,
                        ),
                    )

                    Spacer(Modifier.height(6.dp))
                    Surface(
                        onClick = { unpairConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                if (relayMode) "Trocar IDE / Desconectar" else "Desparear desta IDE",
                                style = MaterialTheme.typography.labelLarge,
                                color = ErrorRed,
                            )
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = ObsidianBg,
            topBar = {
                if (tab != MainTab.Chat) {
                    Surface(color = SurfacePanel) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                IconButton(
                                    onClick = { tab = MainTab.Chat },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Voltar ao chat",
                                        tint = TextPrimary,
                                    )
                                }
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (pendingApprovals.isNotEmpty()) {
                                    StatusPill(
                                        text = "${pendingApprovals.size} pend.",
                                        color = ApprovalAmber,
                                        showDot = true,
                                    )
                                }
                            }
                            HorizontalDivider(color = ObsidianBorder)
                        }
                    }
                }
            },
        ) { padding ->
            when (tab) {
                MainTab.Chat -> ChatScreen(
                    container = container,
                    padding = padding,
                    draftSeed = chatDraftSeed,
                    onConsumeDraftSeed = { chatDraftSeed = null },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenFlow = { tab = MainTab.Flow },
                    onOpenDiffs = { tab = MainTab.Diffs },
                )
                MainTab.Projects -> ProjectsScreen(
                    container = container,
                    padding = padding,
                    onOpenTab = { target -> tab = target },
                )
                MainTab.Flow -> FlowScreen(
                    container = container,
                    padding = padding,
                    workspace = workspace,
                    onOpenChat = { tab = MainTab.Chat },
                )
                MainTab.Diffs -> DiffsScreen(
                    container = container,
                    padding = padding,
                    workspace = workspace,
                    onAskInChat = { prompt ->
                        chatDraftSeed = prompt
                        tab = MainTab.Chat
                    },
                )
            }
        }
    }

    if (unpairConfirm) {
        AlertDialog(
            onDismissRequest = { unpairConfirm = false },
            containerColor = ObsidianSurface,
            title = { Text("Desconectar desta IDE?") },
            text = {
                Text(
                    if (relayMode) "Você voltará para a lista de IDEs da sua conta no relay."
                    else "O vínculo LAN será removido deste aparelho.",
                    color = TextDim,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        unpairConfirm = false
                        scope.launch {
                            drawerState.close()
                            if (relayMode) container.relay.disconnectIde() else container.deactivate()
                        }
                    },
                ) {
                    Text("Desconectar", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { unpairConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
