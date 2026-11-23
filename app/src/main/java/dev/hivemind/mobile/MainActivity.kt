package dev.hivemind.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.hivemind.mobile.core.connect.ConnectivityService
import dev.hivemind.mobile.core.relay.KeyRotationNotice
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.features.chat.ChatScreen
import dev.hivemind.mobile.features.diffs.DiffsScreen
import dev.hivemind.mobile.features.flow.FlowScreen
import dev.hivemind.mobile.features.inbox.ContainerInboxSource
import dev.hivemind.mobile.features.inbox.ConversationRow
import dev.hivemind.mobile.features.inbox.InboxIndex
import dev.hivemind.mobile.features.inbox.InboxViewModel
import dev.hivemind.mobile.features.inbox.ConversationsInboxScreen
import dev.hivemind.mobile.features.inbox.ProjectsInboxScreen
import dev.hivemind.mobile.features.projects.ProjectsScreen
import dev.hivemind.mobile.features.relay.GateScreen
import dev.hivemind.mobile.features.relay.RelayDevicesScreen
import dev.hivemind.mobile.features.settings.SettingsSheet
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.HiveMindTheme
import dev.hivemind.mobile.theme.MonoFonts
import dev.hivemind.mobile.theme.ObsidianBg
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.TextDim
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val icon: ImageVector) {
    Chat("Chat", Icons.AutoMirrored.Filled.Send),
    Flow("Fluxo", Icons.Filled.Build),
    Diffs("Diffs", Icons.AutoMirrored.Filled.List),
    Projects("Projetos", Icons.Filled.Home),
}

private sealed interface Dest {
    data object Home : Dest
    data class Conversations(val workspace: String) : Dest
    data class Chat(
        val workspace: String,
        val conversationId: String,
        val title: String,
        val sessionId: String?,
        val laneId: String?,
        val unread: Boolean,
        val runMode: String = "auto",
        val draftSeed: String? = null,
        val draftMode: String? = null,
    ) : Dest
    data object Flow : Dest
    data object Diffs : Dest
    data object ManageProjects : Dest
}

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as HiveMindApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HiveMindTheme {
                val pairing by container.hubState.pairing.collectAsState()
                val loggedIn by container.relay.loggedIn.collectAsState()
                NotificationPermissionRequest()
                BackgroundConnectionBinder(container)
                KeyRotationDialog(container)
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
fun KeyRotationDialog(container: AppContainer) {
    val rotation by container.relay.keyRotation.collectAsState()
    val notice: KeyRotationNotice = rotation ?: return
    AlertDialog(
        onDismissRequest = { container.relay.rejectKeyRotation() },
        containerColor = ObsidianSurface,
        title = { Text("Chave da IDE mudou", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Text(
                    "A chave pública da IDE \"${notice.deviceName.ifBlank { notice.deviceId.take(8) }}\" " +
                        "não confere com a chave pareada neste aparelho (TOFU).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Antiga: ${notice.oldFingerprint}", style = MaterialTheme.typography.labelSmall, fontFamily = MonoFonts, color = TextDim)
                Text("Nova: ${notice.newFingerprint}", style = MaterialTheme.typography.labelSmall, fontFamily = MonoFonts, color = BrandGold)
            }
        },
        confirmButton = {
            Button(
                onClick = { container.relay.acceptKeyRotation() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGold,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            ) { Text("Confiar na chave nova", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { container.relay.rejectKeyRotation() },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            ) { Text("Recusar", color = ErrorRed) }
        },
    )
}

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val alreadyGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(alreadyGranted) {
        if (!alreadyGranted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun BackgroundConnectionBinder(container: AppContainer) {
    val context = LocalContext.current
    val pairing by container.hubState.pairing.collectAsState()
    val conn by container.hubState.conn.collectAsState()
    LaunchedEffect(pairing, conn) {
        if (pairing != null && conn is ConnState.Connected) {
            runCatching { ConnectivityService.start(context) }
        } else if (pairing == null) {
            runCatching { ConnectivityService.stop(context) }
        }
    }
}

@Composable
fun MainScaffold(container: AppContainer) {
    val viewModel = remember {
        InboxViewModel(ContainerInboxSource(container), container.appScope)
    }
    var stack by remember { mutableStateOf(listOf<Dest>(Dest.Home)) }
    var settingsOpen by remember { mutableStateOf(false) }
    val pairing by container.hubState.pairing.collectAsState()
    val projects by container.hubState.projects.collectAsState()
    val accounts by container.relay.accounts.collectAsState()
    val dest = stack.last()

    LaunchedEffect(pairing?.activeUrl) {
        viewModel.refresh()
    }

    fun push(next: Dest) {
        viewModel.clearSearch()
        stack = stack + next
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun openChat(row: ConversationRow, unread: Boolean) {
        container.hubState.selectedWorkspace.value = row.workspace
        if (!row.laneId.isNullOrBlank()) container.hubState.selectedLaneId.value = row.laneId
        push(
            Dest.Chat(
                workspace = row.workspace,
                conversationId = row.id,
                title = row.title,
                sessionId = row.sessionId,
                laneId = row.laneId,
                unread = unread,
                runMode = row.runMode,
            ),
        )
    }

    BackHandler(enabled = stack.size > 1 && !settingsOpen) { pop() }

    val profileInitial = (
        accounts.firstOrNull()?.email?.substringBefore("@")
            ?: pairing?.host
            ?: "H"
        ).trim().firstOrNull()?.uppercase() ?: "H"

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        when (val current = dest) {
            Dest.Home -> ProjectsInboxScreen(
                container = container,
                viewModel = viewModel,
                padding = padding,
                onOpenProject = { project ->
                    container.hubState.selectedWorkspace.value = project.workspace
                    push(Dest.Conversations(project.workspace))
                },
                onOpenDraft = { row -> openChat(row, unread = false) },
                onProfile = { settingsOpen = true },
            )
            is Dest.Conversations -> {
                val title = projects.firstOrNull { it.workspace == current.workspace }?.name
                    ?.ifBlank { null }
                    ?: InboxIndex.leaf(current.workspace)
                ConversationsInboxScreen(
                    viewModel = viewModel,
                    workspace = current.workspace,
                    projectTitle = title,
                    padding = padding,
                    profileInitial = profileInitial,
                    onBack = ::pop,
                    onProfile = { settingsOpen = true },
                    onOpen = { row ->
                        row.sessionId?.let { viewModel.markRead(row.workspace, it) }
                        openChat(row, unread = row.unread > 0)
                    },
                    onCreate = { row -> openChat(row, unread = false) },
                )
            }
            is Dest.Chat -> ChatScreen(
                container = container,
                padding = padding,
                draftSeed = current.draftSeed,
                draftMode = current.draftMode,
                onBack = ::pop,
                conversationTitle = current.title,
                openedSessionId = current.sessionId,
                openedLaneId = current.laneId,
                initialRunMode = current.runMode,
                showUnreadDivider = current.unread,
                onOpenFlow = { push(Dest.Flow) },
                onOpenDiffs = { push(Dest.Diffs) },
            )
            Dest.Flow -> ToolHost(padding, "Fluxo", ::pop) { inner ->
                FlowScreen(
                    container = container,
                    padding = inner,
                    workspace = container.hubState.selectedWorkspace.collectAsState().value,
                    onOpenChat = open@{ prompt, mode ->
                        val ws = container.hubState.selectedWorkspace.value ?: return@open
                        val lane = container.hubState.ideLane(ws)
                        push(
                            Dest.Chat(
                                workspace = ws,
                                conversationId = lane?.sessionId?.takeIf { it.isNotBlank() } ?: "draft-flow",
                                title = lane?.teamName?.ifBlank { null } ?: "Fluxo",
                                sessionId = lane?.sessionId?.takeIf { it.isNotBlank() },
                                laneId = lane?.laneId ?: "lane-mobile",
                                unread = false,
                                draftSeed = prompt,
                                draftMode = mode,
                            ),
                        )
                    },
                )
            }
            Dest.Diffs -> ToolHost(padding, "Diffs", ::pop) { inner ->
                DiffsScreen(
                    container = container,
                    padding = inner,
                    workspace = container.hubState.selectedWorkspace.collectAsState().value,
                    onAskInChat = ask@{ prompt ->
                        val ws = container.hubState.selectedWorkspace.value ?: return@ask
                        push(
                            Dest.Chat(
                                workspace = ws,
                                conversationId = "draft-diff",
                                title = "Diffs",
                                sessionId = null,
                                laneId = "lane-mobile",
                                unread = false,
                                draftSeed = prompt,
                            ),
                        )
                    },
                )
            }
            Dest.ManageProjects -> ToolHost(padding, "Projetos", ::pop) { inner ->
                ProjectsScreen(
                    container = container,
                    padding = inner,
                    onOpenTab = { tab ->
                        val ws = container.hubState.selectedWorkspace.value
                        when (tab) {
                            MainTab.Chat -> if (ws != null) push(Dest.Conversations(ws))
                            MainTab.Flow -> push(Dest.Flow)
                            MainTab.Diffs -> push(Dest.Diffs)
                            MainTab.Projects -> Unit
                        }
                    },
                )
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            container = container,
            onClose = { settingsOpen = false },
            onFlow = {
                settingsOpen = false
                val ws = container.hubState.selectedWorkspace.value ?: projects.firstOrNull()?.workspace
                if (ws != null) container.hubState.selectedWorkspace.value = ws
                push(Dest.Flow)
            },
            onDiffs = {
                settingsOpen = false
                val ws = container.hubState.selectedWorkspace.value ?: projects.firstOrNull()?.workspace
                if (ws != null) container.hubState.selectedWorkspace.value = ws
                push(Dest.Diffs)
            },
            onProjects = {
                settingsOpen = false
                push(Dest.ManageProjects)
            },
        )
    }
}

@Composable
private fun ToolHost(
    padding: PaddingValues,
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            content(PaddingValues(0.dp))
        }
    }
}
