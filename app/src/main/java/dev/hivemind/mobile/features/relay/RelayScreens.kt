package dev.hivemind.mobile.features.relay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.api.PairingParser
import dev.hivemind.mobile.core.relay.AccountMeta
import dev.hivemind.mobile.core.relay.DeviceInfo
import dev.hivemind.mobile.core.relay.LinkCodes
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.features.pairing.PairingScreen
import dev.hivemind.mobile.features.pairing.QrScannerOverlay
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GateScreen(container: AppContainer) {
    val forceLogin by container.relay.forceLogin.collectAsState()
    val accounts by container.relay.accounts.collectAsState()
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
            onRemote = { page = "login" },
            onLan = { page = "lan" },
            onAccount = { container.relay.openLogin(it) },
        )
    }
}

@Composable
private fun GateHome(
    accounts: List<AccountMeta>,
    onRemote: () -> Unit,
    onLan: () -> Unit,
    onAccount: (AccountMeta) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("HiveMind Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Comande o HiveMind pela rede local ou por um relay seu.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRemote, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Acesso remoto")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onLan) { Text("Parear na rede local") }
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Contas neste aparelho", style = MaterialTheme.typography.labelSmall, color = TextDim)
            Spacer(Modifier.height(8.dp))
            accounts.forEach { account ->
                TextButton(onClick = { onAccount(account) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${account.email} · ${hostOf(account.relayUrl)}")
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
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onBack) { Text("Voltar") }
        Text("Entrar no relay", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Não há cadastro público. Use a conta criada na primeira subida do relay.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = relayUrl,
            onValueChange = { relayUrl = it },
            label = { Text("URL do relay (https)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                error = null
                container.appScope.launch {
                    runCatching { container.relay.login(relayUrl, email, password) }
                        .onSuccess { onLoggedIn() }
                        .onFailure { error = it.message }
                    password = ""
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Entrar")
        }
        val shown = error ?: lastError
        if (!shown.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(shown, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
        else -> DeviceList(container, onLink = { page = "link" }, onAddAccount = { page = "login" }, onLan = { page = "lan" })
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
        while (true) {
            runCatching { container.relay.refreshDeviceList() }
            delay(10_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("IDEs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(active?.email, active?.relayUrl?.let(::hostOf)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
        )
        if (conn is ConnState.Connecting || conn is ConnState.Reconnecting) {
            Text(
                if (conn is ConnState.Reconnecting) "Reconectando…" else "Conectando…",
                color = ApprovalAmber,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLink) { Text("Vincular IDE") }
            TextButton(onClick = { showAccounts = true }) { Text("Contas") }
        }
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Text(
                "Nenhuma IDE vinculada. Gere um código na IDE (Acesso remoto) ou crie um aqui.",
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
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
        val shown = localError ?: error
        if (!shown.isNullOrBlank()) {
            Text(shown, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onLan) { Text("Pareamento na rede local") }
    }

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
                }) { Text("Revogar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { revoke = null }) { Text("Cancelar") } },
        )
    }

    if (showAccounts) {
        AlertDialog(
            onDismissRequest = { showAccounts = false },
            title = { Text("Contas") },
            text = {
                Column {
                    accounts.forEach { account ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                account.email + if (account.id == active?.id) " · atual" else "",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
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
                            }) { Text("Apagar") }
                        }
                    }
                    TextButton(onClick = { showAccounts = false; onAddAccount() }) { Text("Adicionar conta") }
                    TextButton(onClick = { showAccounts = false; showPassword = true }) { Text("Trocar senha") }
                    if (active?.role == "owner") {
                        TextButton(onClick = { showAccounts = false; showCreate = true }) { Text("Criar conta de membro") }
                    }
                    TextButton(onClick = {
                        showAccounts = false
                        container.appScope.launch { container.relay.logout() }
                    }) { Text("Sair", color = MaterialTheme.colorScheme.error) }
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
private fun DeviceRow(device: DeviceInfo, busy: Boolean, onOpen: () -> Unit, onRevoke: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (device.online) SuccessGreen else TextDim, CircleShape),
            )
            Spacer(Modifier.size(8.dp))
            Text(device.name.ifBlank { device.id }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (device.online) "online" else "offline", color = if (device.online) SuccessGreen else TextDim, style = MaterialTheme.typography.labelSmall)
        }
        if (!device.e2eReady) {
            Text("criptografia ainda incompleta", color = ApprovalAmber, style = MaterialTheme.typography.labelSmall)
        }
        Row {
            TextButton(onClick = onOpen, enabled = !busy) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Controlar")
            }
            TextButton(onClick = onRevoke) { Text("Revogar") }
        }
    }
}

@Composable
private fun LinkIdeScreen(container: AppContainer, onBack: () -> Unit) {
    val link by container.relay.link.collectAsState()
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
                    error = if (PairingParser.parse(raw) != null) {
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

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = {
            container.relay.clearLink()
            onBack()
        }) { Text("Voltar") }
        Text("Vincular IDE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Os dois lados precisam estar na mesma conta. O código vale 10 minutos e é de uso único.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) { Text("Escanear QR da IDE") }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Código (ABCD-EFGH-JK)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
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
            enabled = !busy,
        ) { Text("Confirmar código") }
        Spacer(Modifier.height(16.dp))
        Text("Ou gere um código para digitar na IDE", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome da IDE") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
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
        ) { Text("Gerar código") }
        if (link != null) {
            Spacer(Modifier.height(8.dp))
            Text(LinkCodes.format(link!!.code), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(link!!.uri, style = MaterialTheme.typography.bodySmall, color = TextDim)
            val status = link!!.status.ifBlank { "pending" }
            Text(
                when (status) {
                    "ready" -> "IDE vinculada"
                    "expired" -> "código expirado"
                    else -> "aguardando a IDE"
                },
                color = if (status == "ready") SuccessGreen else TextDim,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            Column {
                OutlinedTextField(value = current, onValueChange = { current = it }, label = { Text("Senha atual") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = next, onValueChange = { next = it }, label = { Text("Senha nova") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
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
            Column {
                Text("Só o owner cria contas. A senha não fica gravada neste aparelho.", color = TextDim, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
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
