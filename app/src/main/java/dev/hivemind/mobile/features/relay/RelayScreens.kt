package dev.hivemind.mobile.features.relay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.relay.AccountMeta
import dev.hivemind.mobile.core.relay.DeviceInfo
import dev.hivemind.mobile.core.relay.LinkCodes
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.features.pairing.PairingScreen
import dev.hivemind.mobile.features.pairing.QrScannerOverlay
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.MonoFonts
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.ui.EmptyStatePanel
import dev.hivemind.mobile.ui.EntryBackHeader
import dev.hivemind.mobile.ui.EntryBullet
import dev.hivemind.mobile.ui.EntryScaffold
import dev.hivemind.mobile.ui.EntryStep
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.GoldMark
import dev.hivemind.mobile.ui.HelpDisclosure
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.IconField
import dev.hivemind.mobile.ui.PillButton
import dev.hivemind.mobile.ui.QrHero
import dev.hivemind.mobile.ui.QuietLink
import dev.hivemind.mobile.ui.StaticEntry
import dev.hivemind.mobile.ui.StatusPill
import dev.hivemind.mobile.ui.edgeInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GateHomePreview() {
    StaticEntry {
        GateHome(
            accounts = emptyList(),
            notice = null,
            onRemote = {},
            onLan = {},
            onAccount = {},
        )
    }
}

@Composable
fun RelayLoginPreview() {
    StaticEntry { RelayEntryPreview(emailOpen = false) }
}

@Composable
fun RelayLoginEmailPreview() {
    StaticEntry { RelayEntryPreview(emailOpen = true, showPassword = true, password = "senha-exemplo") }
}

@Composable
private fun RelayEntryPreview(emailOpen: Boolean, showPassword: Boolean = false, password: String = "") {
    RelayEntry(
        relayUrl = "https://relay.example.com",
        email = if (emailOpen) "conta@example.com" else "",
        password = password,
        showPassword = showPassword,
        emailOpen = emailOpen,
        busy = false,
        error = null,
        onRelayUrl = {},
        onEmail = {},
        onPassword = {},
        onTogglePassword = {},
        onToggleEmail = {},
        onScan = {},
        onSubmit = {},
        onBack = {},
    )
}

@Composable
fun GateScreen(container: AppContainer) {
    val forceLogin by container.relay.forceLogin.collectAsState()
    val accounts by container.relay.accounts.collectAsState()
    val notice by container.lanNotice.collectAsState()
    var page by remember { mutableStateOf("gate") }
    val shown = if (forceLogin) "login" else page
    when (shown) {
        "login" -> LoginScreen(container, onBack = {
            container.relay.closeLogin()
            page = "gate"
        })
        "lan" -> PairingScreen(container, onBack = { page = "gate" })
        else -> GateHome(
            accounts = accounts,
            notice = notice,
            onRemote = { page = "login" },
            onLan = { page = "lan" },
            onAccount = { container.relay.openLogin(it) },
        )
    }
}

@Composable
private fun GateHome(
    accounts: List<AccountMeta>,
    notice: String?,
    onRemote: () -> Unit,
    onLan: () -> Unit,
    onAccount: (AccountMeta) -> Unit,
) {
    EntryScaffold(
        footer = {
            if (!notice.isNullOrBlank()) {
                ErrorBanner(message = notice)
            }
            PillButton(text = "Acesso remoto", onClick = onRemote)
            PillButton(text = "Parear na rede local", onClick = onLan, primary = false)
        },
    ) {
        GoldMark()
        Text(
            "HiveMind",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Comande a equipe de agentes pelo celular.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        EntryBullet(
            icon = Icons.Outlined.Groups,
            title = "Projetos e conversas",
            body = "A mesma equipe da IDE, com prévia e não lidas.",
        )
        EntryBullet(
            icon = Icons.Outlined.QrCodeScanner,
            title = "Pareia por QR",
            body = "Rede local, Tailscale ou um relay seu.",
        )
        EntryBullet(
            icon = Icons.Outlined.Shield,
            title = "Canal cifrado",
            body = "Sessão local ou caixa E2E até a IDE.",
        )
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "CONTAS SALVAS",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
            Spacer(Modifier.height(8.dp))
            accounts.forEach { account ->
                Surface(
                    onClick = { onAccount(account) },
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.email,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                hostOf(account.relayUrl),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextDim,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(container: AppContainer, onBack: () -> Unit, onLoggedIn: () -> Unit = {}) {
    val draft by container.relay.loginDraft.collectAsState()
    val lastError by container.relay.lastError.collectAsState()
    var relayUrl by remember(draft?.id) { mutableStateOf(draft?.relayUrl ?: "https://") }
    var email by remember(draft?.id) { mutableStateOf(draft?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var emailOpen by remember(draft?.id) { mutableStateOf(!draft?.email.isNullOrBlank()) }
    var busy by remember { mutableStateOf(false) }
    var scanningQr by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    if (scanningQr) {
        QrScannerOverlay(
            onResult = { raw ->
                scanningQr = false
                val qrLogin = LinkCodes.parseQrLogin(raw)
                if (qrLogin == null) {
                    error = "QR inválido para login remoto. Na IDE clique em 'Gerar QR Code para entrar no celular'."
                    return@QrScannerOverlay
                }
                busy = true
                error = null
                container.appScope.launch {
                    runCatching { container.relay.redeemQrLogin(qrLogin.first, qrLogin.second) }
                        .onSuccess { onLoggedIn() }
                        .onFailure { error = it.message }
                    busy = false
                }
            },
            onClose = { scanningQr = false },
        )
        return
    }

    fun submit() {
        if (busy) return
        busy = true
        error = null
        focusManager.clearFocus()
        container.appScope.launch {
            runCatching { container.relay.login(relayUrl, email, password) }
                .onSuccess { onLoggedIn() }
                .onFailure { error = it.message }
            password = ""
            busy = false
        }
    }

    RelayEntry(
        relayUrl = relayUrl,
        email = email,
        password = password,
        showPassword = showPassword,
        emailOpen = emailOpen,
        busy = busy,
        error = error ?: lastError,
        onRelayUrl = { relayUrl = it },
        onEmail = { email = it },
        onPassword = { password = it },
        onTogglePassword = { showPassword = !showPassword },
        onToggleEmail = { emailOpen = !emailOpen },
        onScan = { scanningQr = true },
        onSubmit = { submit() },
        onBack = onBack,
    )
}

@Composable
private fun RelayEntry(
    relayUrl: String,
    email: String,
    password: String,
    showPassword: Boolean,
    emailOpen: Boolean,
    busy: Boolean,
    error: String?,
    onRelayUrl: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onToggleEmail: () -> Unit,
    onScan: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    EntryScaffold(
        footer = {
            if (!error.isNullOrBlank()) {
                ErrorBanner(message = error)
            }
            if (emailOpen) {
                PillButton(text = "Entrar", onClick = onSubmit, enabled = !busy, busy = busy)
                QuietLink(text = "Escanear QR da IDE", onClick = onScan, enabled = !busy)
            } else {
                PillButton(text = "Escanear QR da IDE", onClick = onScan, enabled = !busy, busy = busy)
                QuietLink(text = "Entrar com e-mail", onClick = onToggleEmail, enabled = !busy)
            }
        },
    ) {
        EntryBackHeader(title = "Relay", onBack = onBack)
        if (!emailOpen) {
            QrHero()
            Text(
                "Entrar no relay",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Na IDE, gere o QR de acesso remoto e aponte a câmera.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            EntryStep(index = 1, title = "Abra a IDE", body = "No computador, com o HiveMind aberto.")
            EntryStep(index = 2, title = "Acesso remoto", body = "Gere o QR para entrar no celular.")
            EntryStep(index = 3, title = "Aponte a câmera", body = "O app entra na conta sem digitar senha.")
        } else {
            Spacer(Modifier.height(18.dp))
            Text(
                "Entrar com e-mail",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Use a conta criada no servidor do relay.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
            )
            Spacer(Modifier.height(22.dp))
            IconField(
                value = relayUrl,
                onValueChange = onRelayUrl,
                label = "URL do relay",
                icon = Icons.Outlined.Link,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
            IconField(
                value = email,
                onValueChange = onEmail,
                label = "E-mail ou usuário",
                icon = Icons.Outlined.Email,
                placeholder = "conta@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
            IconField(
                value = password,
                onValueChange = onPassword,
                label = "Senha",
                icon = Icons.Outlined.Lock,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                trailing = {
                    IconButton(onClick = onTogglePassword, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha",
                        )
                    }
                },
            )
            HelpDisclosure(
                "A conta nasce no servidor do relay. O e-mail inicial pode estar em HIVEMIND_SEED_EMAIL, em initial-password.txt ou no comando hivemind-relay account set.",
            )
        }
    }
}

@Composable
fun RelayDevicesScreen(container: AppContainer) {
    var page by remember { mutableStateOf("list") }
    when (page) {
        "login" -> LoginScreen(container, onBack = { page = "list" }, onLoggedIn = { page = "list" })
        "lan" -> PairingScreen(container, onBack = { page = "list" })
        "link" -> LinkIdeScreen(container, onBack = { page = "list" })
        else -> DeviceList(
            container = container,
            onLink = { page = "link" },
            onAddAccount = { page = "login" },
            onLan = { page = "lan" },
        )
    }
}

@Composable
private fun DeviceList(
    container: AppContainer,
    onLink: () -> Unit,
    onAddAccount: () -> Unit,
    onLan: () -> Unit,
) {
    val devices by container.relay.devices.collectAsState()
    val active by container.relay.active.collectAsState()
    val accounts by container.relay.accounts.collectAsState()
    val conn by container.hubState.conn.collectAsState()
    val error by container.relay.lastError.collectAsState()
    var busyId by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var revoke by remember { mutableStateOf<DeviceInfo?>(null) }
    var showAccounts by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(active?.id) {
        // Poll com decaimento: 10s no primeiro minuto (setup de vínculo é
        // hands-on), desacelerando até 60s — presença instantânea via socket
        // cobre as mudanças no meio; o poll é só reposição de estado.
        var periodMs = 10_000L
        while (true) {
            runCatching { container.relay.refreshDeviceList() }
            delay(periodMs)
            periodMs = minOf(periodMs * 2, 60_000L)
        }
    }

    IdeGallery(
        subtitle = listOfNotNull(active?.email, active?.relayUrl?.let(::hostOf)).joinToString(" · "),
        route = "Relay",
        devices = devices,
        error = localError ?: error,
        busyId = busyId,
        connecting = conn is ConnState.Connecting || conn is ConnState.Reconnecting,
        onOpen = { device ->
            busyId = device.id
            localError = null
            container.appScope.launch {
                runCatching { container.relay.connect(device.id) }
                    .onFailure { localError = it.message }
                busyId = null
            }
        },
        onRevoke = { revoke = it },
        onLink = onLink,
        onAccounts = { showAccounts = true },
        onLan = onLan,
    )

    if (revoke != null) {
        val target = revoke!!
        AlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text("Revogar ${target.name.ifBlank { "IDE" }}?") },
            text = { Text("O PC deixa de atender este aparelho até um vínculo novo.") },
            confirmButton = {
                TextButton(onClick = {
                    revoke = null
                    container.appScope.launch {
                        runCatching { container.relay.revoke(target.id) }
                            .onFailure { localError = it.message }
                    }
                }) { Text("Revogar", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { revoke = null }) { Text("Cancelar") } },
        )
    }

    if (showAccounts) {
        AlertDialog(
            onDismissRequest = { showAccounts = false },
            title = { Text("Contas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    accounts.forEach { account ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                account.email + if (account.id == active?.id) " · atual" else "",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (account.id != active?.id) {
                                TextButton(onClick = {
                                    showAccounts = false
                                    container.appScope.launch { container.relay.switchAccount(account.id) }
                                }) { Text("Usar") }
                            }
                            TextButton(onClick = {
                                showAccounts = false
                                container.appScope.launch { container.relay.removeAccount(account.id) }
                            }) { Text("Apagar", color = ErrorRed) }
                        }
                    }
                    HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = { showAccounts = false; onAddAccount() }) { Text("Adicionar conta") }
                    TextButton(onClick = { showAccounts = false; showPassword = true }) { Text("Trocar senha") }
                    if (active?.role == "owner") {
                        TextButton(onClick = { showAccounts = false; showCreate = true }) {
                            Text("Criar conta de membro")
                        }
                    }
                    TextButton(onClick = {
                        showAccounts = false
                        container.appScope.launch { container.relay.logout() }
                    }) { Text("Sair da conta", color = ErrorRed) }
                }
            },
            confirmButton = { TextButton(onClick = { showAccounts = false }) { Text("Fechar") } },
        )
    }

    if (showPassword) {
        PasswordDialog(
            onDismiss = { showPassword = false },
            onSubmit = { current, next ->
                showPassword = false
                container.appScope.launch {
                    runCatching { container.relay.changePassword(current, next) }
                        .onFailure { localError = it.message }
                }
            },
        )
    }
    if (showCreate) {
        CreateMemberDialog(
            onDismiss = { showCreate = false },
            onSubmit = { mail, pass ->
                showCreate = false
                container.appScope.launch {
                    runCatching { container.relay.createMember(mail, pass) }
                        .onFailure { localError = it.message }
                }
            },
        )
    }
}

@Composable
private fun DeviceRow(
    device: DeviceInfo,
    route: String,
    busy: Boolean,
    onOpen: () -> Unit,
    onRevoke: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val laptop = device.name.contains("note", ignoreCase = true) || device.name.contains("book", ignoreCase = true)
    HiveCard(highlighted = device.online, accentColor = if (device.online) SuccessGreen else ObsidianBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(ObsidianSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (laptop) Icons.Outlined.Laptop else Icons.Outlined.DesktopWindows,
                    contentDescription = null,
                    tint = BrandGold,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name.ifBlank { "IDE" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.online) {
                        dev.hivemind.mobile.ui.PulseDot(SuccessGreen)
                    } else {
                        Box(Modifier.size(8.dp).background(TextDim, CircleShape))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (device.online) "online" else "offline", color = if (device.online) SuccessGreen else TextDim)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ações", tint = TextDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Revogar vínculo", color = ErrorRed) },
                        onClick = { menuOpen = false; onRevoke() },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(text = route, color = BrandGold)
            StatusPill(
                text = if (device.e2eReady) "E2E pronto" else "E2E incompleto",
                color = if (device.e2eReady) SuccessGreen else ApprovalAmber,
            )
        }
        Spacer(Modifier.height(12.dp))
        dev.hivemind.mobile.ui.PillButton(
            text = "Controlar",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen()
            },
            enabled = !busy,
            busy = busy,
        )
    }
}

@Composable
private fun IdeGallery(
    subtitle: String,
    route: String,
    devices: List<DeviceInfo>,
    error: String?,
    busyId: String?,
    connecting: Boolean,
    onOpen: (DeviceInfo) -> Unit,
    onRevoke: (DeviceInfo) -> Unit,
    onLink: () -> Unit,
    onAccounts: () -> Unit,
    onLan: () -> Unit,
) {
    dev.hivemind.mobile.ui.EntryScaffold(
        footer = {
            if (connecting) {
                StatusPill(text = "Conectando…", color = ApprovalAmber, showDot = true)
            }
            dev.hivemind.mobile.ui.PillButton("Vincular IDE", onLink)
            dev.hivemind.mobile.ui.QuietLink("Contas", onAccounts)
            dev.hivemind.mobile.ui.QuietLink("Parear na rede local", onLan)
        },
    ) {
        Text(
            "Suas IDEs",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        if (!error.isNullOrBlank()) {
            ErrorBanner(message = error)
            Spacer(Modifier.height(12.dp))
        }
        if (devices.isEmpty() && error.isNullOrBlank()) {
            dev.hivemind.mobile.ui.StudioEmpty(
                icon = Icons.Outlined.Computer,
                title = "Nenhuma IDE vinculada",
                body = "Gere um código na IDE, em Acesso remoto, ou escaneie o QR.",
            )
        } else if (devices.isEmpty()) {
            dev.hivemind.mobile.ui.StudioEmpty(
                icon = Icons.Outlined.CloudOff,
                title = "Não deu para listar as IDEs",
                body = error.orEmpty(),
            )
        } else {
            devices.forEach { device ->
                DeviceRow(
                    device = device,
                    route = route,
                    busy = busyId == device.id,
                    onOpen = { onOpen(device) },
                    onRevoke = { onRevoke(device) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LinkIdeScreen(container: AppContainer, onBack: () -> Unit) {
    val link by container.relay.link.collectAsState()
    var modeTab by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (scanning) {
        QrScannerOverlay(
            hint = "Aponte para o QR hivemind://link da IDE",
            onClose = { scanning = false },
            onResult = { raw ->
                scanning = false
                val parsed = LinkCodes.parse(raw)
                if (parsed == null) {
                    error = if (raw.trim().startsWith("hivemind://pair")) {
                        "Esse QR é de pareamento LAN (hivemind://pair)."
                    } else {
                        "QR de vínculo inválido."
                    }
                } else {
                    busy = true
                    container.appScope.launch {
                        runCatching { container.relay.confirmLink(parsed.second, parsed.first) }
                            .onSuccess { onBack() }
                            .onFailure { error = it.message }
                        busy = false
                    }
                }
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .edgeInsets()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    container.relay.clearLink()
                    onBack()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                "Vincular IDE",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Os dois lados precisam estar na mesma conta. O código vale 10 minutos e é de uso único.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))

        TabRow(selectedTabIndex = modeTab, containerColor = ObsidianCard) {
            Tab(selected = modeTab == 0, onClick = { modeTab = 0 }, text = { Text("Ler QR / Código") })
            Tab(selected = modeTab == 1, onClick = { modeTab = 1 }, text = { Text("Gerar no App") })
        }
        Spacer(Modifier.height(14.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandGold,
            unfocusedBorderColor = ObsidianBorder,
            focusedContainerColor = ObsidianSurface,
            unfocusedContainerColor = ObsidianSurface,
        )

        if (modeTab == 0) {
            HiveCard(contentPadding = PaddingValues(16.dp)) {
                Button(
                    onClick = { scanning = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandGold,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Escanear QR da IDE", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = ObsidianBorder)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Ou digite o código (ABCD-EFGH-JK)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        busy = true
                        error = null
                        container.appScope.launch {
                            runCatching { container.relay.confirmLink(code, null) }
                                .onSuccess { onBack() }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = !busy && code.isNotBlank(),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                ) {
                    Text("Confirmar código")
                }
            }
        } else {
            HiveCard(contentPadding = PaddingValues(16.dp)) {
                Text("Gere um código para digitar na IDE", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da IDE") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        busy = true
                        error = null
                        container.appScope.launch {
                            runCatching { container.relay.beginAppLink(name) }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = !busy && name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandGold,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                ) {
                    Text("Gerar código", fontWeight = FontWeight.SemiBold)
                }
                if (link != null) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = ObsidianBorder)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        LinkCodes.format(link!!.code),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFonts,
                        color = BrandGold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(link!!.uri, style = MaterialTheme.typography.bodySmall, color = TextDim)
                    Spacer(Modifier.height(6.dp))
                    val status = link!!.status.ifBlank { "pending" }
                    StatusPill(
                        text = when (status) {
                            "ready" -> "IDE vinculada"
                            "expired" -> "Código expirado"
                            else -> "Aguardando a IDE"
                        },
                        color = when (status) {
                            "ready" -> SuccessGreen
                            "expired" -> ErrorRed
                            else -> ApprovalAmber
                        },
                        showDot = true,
                    )
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(message = error.orEmpty())
        }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trocar senha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Senha atual") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text("Senha nova") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cur = current
                val nxt = next
                current = ""
                next = ""
                onSubmit(cur, nxt)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CreateMemberDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conta de membro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Só o owner cria contas. A senha não fica gravada neste aparelho.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail ou usuário") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mail = email
                val pass = password
                password = ""
                onSubmit(mail, pass)
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun GateAccountsPreview() {
    StaticEntry {
        GateHome(
            accounts = listOf(
                dev.hivemind.mobile.core.relay.AccountMeta("a1", "conta@example.com", "owner", "https://relay.example.com"),
            ),
            notice = null,
            onRemote = {},
            onLan = {},
            onAccount = {},
        )
    }
}

@Composable
fun RelayErrorPreview() {
    StaticEntry {
        RelayEntry(
            relayUrl = "https://relay.example.com",
            email = "conta@example.com",
            password = "",
            showPassword = false,
            emailOpen = true,
            busy = false,
            error = "Senha recusada. Confira a conta ou gere um QR novo na IDE.",
            onRelayUrl = {},
            onEmail = {},
            onPassword = {},
            onTogglePassword = {},
            onToggleEmail = {},
            onScan = {},
            onSubmit = {},
            onBack = {},
        )
    }
}

@Composable
fun DevicesPreview() {
    StaticEntry {
        IdeGallery(
            subtitle = "conta@example.com · relay.example.com",
            route = "Relay",
            devices = listOf(
                DeviceInfo(id = "ide-1", name = "pc-lab", online = true, e2eReady = true),
                DeviceInfo(id = "ide-2", name = "notebook", online = false, e2eReady = false),
            ),
            error = null,
            busyId = null,
            connecting = false,
            onOpen = {},
            onRevoke = {},
            onLink = {},
            onAccounts = {},
            onLan = {},
        )
    }
}

@Composable
fun DevicesEmptyPreview() {
    StaticEntry {
        IdeGallery(
            subtitle = "conta@example.com",
            route = "Relay",
            devices = emptyList(),
            error = null,
            busyId = null,
            connecting = false,
            onOpen = {},
            onRevoke = {},
            onLink = {},
            onAccounts = {},
            onLan = {},
        )
    }
}

@Composable
fun DevicesErrorPreview() {
    StaticEntry {
        IdeGallery(
            subtitle = "conta@example.com",
            route = "Relay",
            devices = emptyList(),
            error = "O relay não respondeu. Tente de novo em instantes.",
            busyId = null,
            connecting = false,
            onOpen = {},
            onRevoke = {},
            onLink = {},
            onAccounts = {},
            onLan = {},
        )
    }
}

@Composable
fun LinkIdePreview() {
    Column(Modifier.fillMaxSize().edgeInsets().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Vincular IDE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Os dois lados precisam estar na mesma conta. O código vale 10 minutos.", color = TextDim, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        HiveCard(contentPadding = PaddingValues(16.dp)) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = BrandGold, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Escanear QR da IDE", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Ou digite o código (ABCD-EFGH-JK)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun hostOf(url: String): String =
    url.removePrefix("https://").removePrefix("http://").substringBefore("/").ifBlank { url }
