package dev.hivemind.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.hivemind.mobile.core.connect.ConnectivityService
import dev.hivemind.mobile.core.relay.KeyRotationNotice
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.features.chat.ChatScreen
import dev.hivemind.mobile.features.diffs.DiffsScreen
import dev.hivemind.mobile.features.flow.FlowScreen
import dev.hivemind.mobile.features.projects.ProjectsScreen
import dev.hivemind.mobile.features.relay.GateScreen
import dev.hivemind.mobile.features.relay.RelayDevicesScreen
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.HiveMindTheme
import dev.hivemind.mobile.theme.MonoFonts
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim

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

/**
 * POST_NOTIFICATIONS (API 33+): sem a concessão, aprovações pendentes e o
 * aviso de "equipe concluiu" são silenciosamente descartados pelo sistema.
 * Pede uma única vez na primeira composição; quem nega mantém o app
 * funcional — só perde os avisos.
 */
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

/**
 * Liga o serviço foreground dataSync quando o bridge está de fato conectado
 * (start a partir da Activity é permitido em todas as APIs), mantendo o
 * processo e o stream de eventos vivos com o app em segundo plano; desliga
 * quando não há mais pareamento ativo.
 */
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

/**
 * Confirmação explícita da rotação de chave TOFU (Spec 10 §3.1.3): a chave nova
 * da IDE só é adotada quando a pessoa aceita. Fechar o diálogo recusa (falha
 * fechada) — nunca há aceitação silenciosa.
 */
@Composable
fun KeyRotationDialog(container: AppContainer) {
    val rotation by container.relay.keyRotation.collectAsState()
    val notice: KeyRotationNotice = rotation ?: return
    AlertDialog(
        onDismissRequest = { container.relay.rejectKeyRotation() },
        title = { Text("Chave da IDE mudou", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A chave pública da IDE \"${notice.deviceName.ifBlank { notice.deviceId.take(8) }}\" " +
                        "não confere com a chave pareada neste aparelho (TOFU).",
                    style = MaterialTheme.typography.bodySmall,
                )
                FingerprintRow(label = "Antiga", fingerprint = notice.oldFingerprint)
                FingerprintRow(label = "Nova", fingerprint = notice.newFingerprint)
                Text(
                    "Só aceite se você mesmo rotacionou a chave da IDE. Em caso de dúvida, recuse e re-vincule a IDE pelo código de vínculo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { container.relay.acceptKeyRotation() },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) { Text("Aceitar nova chave", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { container.relay.rejectKeyRotation() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) { Text("Recusar", color = ErrorRed, fontWeight = FontWeight.SemiBold) }
        },
    )
}

@Composable
private fun FingerprintRow(label: String, fingerprint: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = ApprovalAmber,
                fontWeight = FontWeight.SemiBold,
            )
            Text(fingerprint, style = MaterialTheme.typography.labelSmall, fontFamily = MonoFonts)
        }
    }
}

@Composable
fun MainScaffold(container: AppContainer) {
    var tab by remember { mutableStateOf(MainTab.Projects) }
    var chatDraftSeed by remember { mutableStateOf<String?>(null) }
    val workspace by container.hubState.selectedWorkspace.collectAsState()
    val pendingApprovals by container.hubState.pendingApprovals.collectAsState()
    val lanes by container.hubState.lanes.collectAsState()
    val anyRunning = lanes.values.any { it.running }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                MainTab.entries.forEach { t ->
                    val selected = tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandGold,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = BrandGold.copy(alpha = 0.16f),
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim,
                        ),
                        icon = {
                            if (t == MainTab.Chat && (pendingApprovals.isNotEmpty() || anyRunning)) {
                                BadgedBox(
                                    badge = {
                                        if (pendingApprovals.isNotEmpty()) {
                                            Badge(containerColor = ApprovalAmber, contentColor = MaterialTheme.colorScheme.background) {
                                                Text(pendingApprovals.size.toString())
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(SuccessGreen, CircleShape),
                                            )
                                        }
                                    },
                                ) {
                                    Icon(t.icon, contentDescription = t.label)
                                }
                            } else {
                                Icon(t.icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.Projects -> ProjectsScreen(
                container = container,
                padding = padding,
                onOpenTab = { target -> tab = target },
            )
            MainTab.Chat -> ChatScreen(
                container = container,
                padding = padding,
                draftSeed = chatDraftSeed,
                onConsumeDraftSeed = { chatDraftSeed = null },
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
