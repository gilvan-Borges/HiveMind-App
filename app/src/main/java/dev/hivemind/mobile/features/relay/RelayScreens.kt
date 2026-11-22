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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(text = "E2E Â· Relay & LAN", color = BrandGold)
        Spacer(Modifier.height(14.dp))
        Text(
            "HiveMind Mobile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Comande sua equipe de agentes pela rede local ou por um relay prÃ³prio.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        if (!notice.isNullOrBlank()) {
            ErrorBanner(message = notice)
            Spacer(Modifier.height(16.dp))
        }

        HiveCard(contentPadding = PaddingValues(18.dp)) {
            Button(
                onClick = onRemote,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGold,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Acesso remoto", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onLan,
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text("Parear na rede local")
            }
        }

        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "CONTAS SALVAS",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    HiveCard(
                        onClick = { onAccount(account) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                            Text("â€º", style = MaterialTheme.typography.titleMedium, color = BrandGold)
                        }
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
                    error = "QR invÃ¡lido para login remoto. Na IDE clique em 'Gerar QR Code para entrar no celular'."
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                "Entrar no relay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Use a conta criada no servidor do relay (HIVEMIND_SEED_EMAIL / initial-password.txt ou `hivemind-relay account set`).",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { scanningQr = true },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandGold,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Entrar com QR Code da IDE", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))

        HiveCard(contentPadding = PaddingValues(16.dp)) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandGold,
                unfocusedBorderColor = ObsidianBorder,
                focusedContainerColor = ObsidianSurface,
                unfocusedContainerColor = ObsidianSurface,
            )
            OutlinedTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it },
                label = { Text("URL do relay (https)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail ou usuÃ¡rio") },
                placeholder = { Text("gilvan.b ou voce@exemplo") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { submit() },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGold,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Entrar", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        val shown = error ?: lastError
        if (!shown.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(message = shown)
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

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = ObsidianCard) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Suas IDEs",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            listOfNotNull(active?.email, active?.relayUrl?.let(::hostOf)).joinToString(" Â· "),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (conn is ConnState.Connecting || conn is ConnState.Reconnecting) {
                        StatusPill(
                            text = if (conn is ConnState.Reconnecting) "Reconectandoâ€¦" else "Conectandoâ€¦",
                            color = ApprovalAmber,
                            showDot = true,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    Button(
                        onClick = onLink,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandGold,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                    ) {
                        Text("Vincular IDE", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { showAccounts = true },
                        border = BorderStroke(1.dp, ObsidianBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("Contas")
                    }
                }
                HorizontalDivider(color = ObsidianBorder)
            }
        }

        val shown = localError ?: error
        if (!shown.isNullOrBlank()) {
            ErrorBanner(
                message = shown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (devices.isEmpty()) {
            EmptyStatePanel(
                title = "Nenhuma IDE vinculada",
                subtitle = "Gere um cÃ³digo na IDE (Acesso remoto) ou escaneie o QR para comeÃ§ar.",
                actionLabel = "Vincular primeira IDE",
                onAction = onLink,
                secondaryLabel = "Pareamento na rede local",
                onSecondary = onLan,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        busy = busyId == device.id,
                        onOpen = {
                            busyId = device.id
                            localError = null
                            container.appScope.launch {
                                runCatching { container.relay.connect(device.id) }
                                    .onFailure { localError = it.message }
                                busyId = null
                            }
                        },
                        onRevoke = { revoke = device },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = onLan, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Pareamento na rede local", color = TextDim)
                }
            }
        }
    }

    if (revoke != null) {
        val target = revoke!!
        AlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text("Revogar ${target.name.ifBlank { "IDE" }}?") },
            text = { Text("O PC deixa de atender este aparelho atÃ© um vÃ­nculo novo.") },
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
                                account.email + if (account.id == active?.id) " Â· atual" else "",
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
    busy: Boolean,
    onOpen: () -> Unit,
    onRevoke: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    HiveCard(
        highlighted = device.online,
        accentColor = SuccessGreen,
        onClick = { if (!busy) onOpen() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (device.online) SuccessGreen else TextDim, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                device.name.ifBlank { device.id },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusPill(
                text = if (device.online) "online" else "offline",
                color = if (device.online) SuccessGreen else TextDim,
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "AÃ§Ãµes", tint = TextDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Revogar vÃ­nculo", color = ErrorRed) },
                        onClick = {
                            menuOpen = false
                            onRevoke()
                        },
                    )
                }
            }
        }
        if (!device.e2eReady) {
            Spacer(Modifier.height(6.dp))
            StatusPill(text = "Criptografia ainda incompleta", color = ApprovalAmber)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen()
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandGold,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Controlar IDE", fontWeight = FontWeight.SemiBold)
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
                        "Esse QR Ã© de pareamento LAN (hivemind://pair)."
                    } else {
                        "QR de vÃ­nculo invÃ¡lido."
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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    container.relay.clearLink()
                    onBack()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Vincular IDE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Os dois lados precisam estar na mesma conta. O cÃ³digo vale 10 minutos e Ã© de uso Ãºnico.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))

        TabRow(selectedTabIndex = modeTab, containerColor = ObsidianCard) {
            Tab(selected = modeTab == 0, onClick = { modeTab = 0 }, text = { Text("Ler QR / CÃ³digo") })
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
                    label = { Text("Ou digite o cÃ³digo (ABCD-EFGH-JK)") },
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
                    Text("Confirmar cÃ³digo")
                }
            }
        } else {
            HiveCard(contentPadding = PaddingValues(16.dp)) {
                Text("Gere um cÃ³digo para digitar na IDE", style = MaterialTheme.typography.titleSmall)
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
                    Text("Gerar cÃ³digo", fontWeight = FontWeight.SemiBold)
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
                            "expired" -> "CÃ³digo expirado"
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
                    "SÃ³ o owner cria contas. A senha nÃ£o fica gravada neste aparelho.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail ou usuÃ¡rio") },
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

private fun hostOf(url: String): String =
    url.removePrefix("https://").removePrefix("http://").substringBefore("/").ifBlank { url }
