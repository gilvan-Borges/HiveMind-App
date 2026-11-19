package dev.hivemind.mobile.features.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.api.ImageInput
import dev.hivemind.mobile.core.models.ApprovalInfo
import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.RestoredMessage
import dev.hivemind.mobile.core.models.SessionDetail
import dev.hivemind.mobile.core.models.SessionSummary
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.SteelBlue
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import kotlinx.coroutines.launch

private val RunModes = listOf("auto" to "Auto", "direct" to "Direto", "plan" to "Planejar", "squad" to "Squad")

@Composable
fun ChatScreen(container: AppContainer, padding: PaddingValues) {
    val workspace by container.hubState.selectedWorkspace.collectAsState()
    val lanesState by container.hubState.lanes.collectAsState()
    val pairing by container.hubState.pairing.collectAsState()

    if (workspace == null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Selecione um projeto na aba Projetos.", color = TextDim)
        }
        return
    }
    val ws: String = workspace ?: return

    val lanes = lanesState.values.filter { it.workspace == ws }
    val lane = lanes.firstOrNull { it.laneId == container.hubState.selectedLaneId.value }
        ?: lanes.firstOrNull()

    LaunchedEffect(ws) {
        container.hubState.selectedLaneId.value = lanes.firstOrNull()?.laneId
        container.refreshLanes(ws)
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pairing?.host?.ifBlank { ws.substringAfterLast('\\').substringAfterLast('/') } ?: "Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(ws, style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 1)
            }
            LaneSelector(container, ws, lanes, lane)
        }

        ApprovalCards(container, ws, lanes, lane)

        if (lane != null) {
            ChatLog(lane, Modifier.weight(1f))
            Composer(container, ws, lane)
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nenhuma lane ainda — envie a primeira instrução abaixo.", color = TextDim)
            }
            Composer(container, ws, null)
        }
    }
}

@Composable
private fun LaneSelector(
    container: AppContainer,
    workspace: String,
    lanes: List<LaneSnapshot>,
    lane: LaneSnapshot?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { open = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lane?.teamName?.ifBlank { lane?.laneId } ?: "lanes",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lane?.running == true) SuccessGreen else TextDim,
                )
                Spacer(Modifier.width(6.dp))
                Text("▾", style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lanes.forEach { l ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${l.teamName.ifBlank { l.laneId }} ${if (l.running) "• rodando" else ""}",
                            color = if (l.running) SuccessGreen else TextDim,
                        )
                    },
                    onClick = {
                        container.hubState.selectedLaneId.value = l.laneId
                        open = false
                    },
                )
            }
            if (lanes.isEmpty()) {
                DropdownMenuItem(text = { Text("(nenhuma lane ainda)") }, onClick = { open = false })
            }
        }
    }
}

@Composable
private fun ApprovalCards(
    container: AppContainer,
    workspace: String,
    lanes: List<LaneSnapshot>,
    lane: LaneSnapshot?,
) {
    val approvals = if (lane != null) lane.pendingApprovals
    else lanes.flatMap { it.pendingApprovals }
    if (approvals.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        approvals.forEach { approval ->
            ApprovalCard(container, workspace, approval)
        }
    }
}

@Composable
private fun ApprovalCard(container: AppContainer, workspace: String, approval: ApprovalInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = ApprovalAmber.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏸", color = ApprovalAmber)
                Spacer(Modifier.width(6.dp))
                Text(
                    approval.label.ifBlank { "Permissão Necessária" },
                    style = MaterialTheme.typography.labelLarge,
                    color = ApprovalAmber,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                approval.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 6,
            )
            Spacer(Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        container.appScope.launch {
                            runCatching { container.api.resolveApproval(approval.approvalId, false) }
                                .onSuccess { container.hubState.onApprovalResolved(approval.approvalId) }
                        }
                    },
                ) { Text("Recusar", color = ErrorRed) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        container.appScope.launch {
                            runCatching { container.api.resolveApproval(approval.approvalId, true) }
                                .onSuccess { container.hubState.onApprovalResolved(approval.approvalId) }
                        }
                    },
                ) { Text("Aprovar") }
            }
        }
    }
}

@Composable
private fun ChatLog(lane: LaneSnapshot, modifier: Modifier = Modifier) {
    val events = lane.recentEvents
    Column(modifier = modifier.fillMaxWidth()) {
        if (lane.lastError.isNotBlank()) {
            Text(
                "Erro: ${lane.lastError}",
                color = ErrorRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("O streaming da equipe aparece aqui em tempo real.", color = TextDim, style = MaterialTheme.typography.bodySmall)
            }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(events) { e -> EventRow(e) }
        }
    }
}

@Composable
private fun EventRow(e: LaneEventItem) {
    when (e.kind) {
        "text" -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        e.label.ifBlank { e.author }.ifBlank { "Equipe" },
                        style = MaterialTheme.typography.labelSmall,
                        color = SteelBlue,
                    )
                    Text(e.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        "stage" -> {
            Text(
                "▸ estágio: ${e.stage.ifBlank { "—" }}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        "tool_call" -> {
            Text(
                "⚙ ${e.toolName.ifBlank { "ferramenta" }}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                fontFamily = FontFamily.Monospace,
            )
        }
        "error" -> {
            Text(
                "✕ ${e.text}",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
            )
        }
        "approval_required" -> Unit // exibido nos cards acima
        "handoff" -> {
            Text(
                "⇄ ${e.label.ifBlank { "Handoff" }}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
        }
        "finish" -> {
            Text(
                "✓ ${e.text.ifBlank { "Execução concluída." }} · ${formatTokens(e.tokens)}",
                style = MaterialTheme.typography.bodySmall,
                color = SuccessGreen,
            )
        }
        else -> {
            if (e.text.isNotBlank()) {
                Text(e.text, style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
    }
}

@Composable
private fun Composer(container: AppContainer, workspace: String, lane: LaneSnapshot?) {
    var text by remember { mutableStateOf("") }
    var runMode by remember { mutableStateOf("auto") }
    var images by remember { mutableStateOf<List<ImageInput>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var sessionDetail by remember { mutableStateOf<SessionDetail?>(null) }
    var continueSessionId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            container.appScope.launch {
                val img = uriToImageInput(context, uri)
                if (img != null) images = images + img
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        if (continueSessionId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Continuando sessão ${continueSessionId?.take(8)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApprovalAmber,
                )
                IconButton(onClick = { continueSessionId = null }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancelar continuação", modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            RunModes.forEach { (value, label) ->
                FilterChip(
                    selected = runMode == value,
                    onClick = { runMode = value },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { historyOpen = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = "Histórico de sessões")
            }
        }
        if (images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                images.forEachIndexed { i, img ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(img.name.ifBlank { "imagem" }, style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { images = images.filterIndexed { j, _ -> j != i } }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remover", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Instrução para a equipe…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "Anexar imagem")
                }
                IconButton(
                    onClick = {
                        if (text.isBlank() && images.isEmpty()) return@IconButton
                        sending = true
                        error = null
                        container.appScope.launch {
                            val laneId = lane?.laneId ?: "lane-mobile"
                            runCatching {
                                container.startRun(
                                    laneId = laneId,
                                    workspace = workspace,
                                    objective = text.ifBlank { "Analise a(s) imagem(ns) anexada(s)." },
                                    sessionId = continueSessionId,
                                    runMode = runMode,
                                    images = images,
                                )
                            }.onSuccess {
                                text = ""
                                images = emptyList()
                                continueSessionId = null
                                container.hubState.selectedLaneId.value = laneId
                                container.refreshLanes(workspace)
                            }.onFailure { error = it.message }
                            sending = false
                        }
                    },
                    enabled = !sending,
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Send, contentDescription = "Enviar")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (error != null) {
                Text(error.orEmpty(), color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
            }
            if (lane?.running == true) {
                Button(
                    onClick = {
                        container.appScope.launch {
                            runCatching { container.api.cancel(lane.laneId) }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ErrorRed.copy(alpha = 0.2f),
                        contentColor = ErrorRed,
                    ),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("■ Parar", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (text.isBlank() && images.isEmpty()) return@TextButton
                        container.appScope.launch {
                            val laneId = lane.laneId
                            runCatching {
                                container.api.redirect(
                                    dev.hivemind.mobile.core.api.RunStartRequest(
                                        laneId = laneId,
                                        workspace = workspace,
                                        objective = text.ifBlank { "Analise a(s) imagem(ns) anexada(s)." },
                                        sessionId = lane.sessionId.ifBlank { null },
                                        runMode = runMode,
                                        images = images,
                                    ),
                                )
                            }.onSuccess {
                                text = ""
                                images = emptyList()
                            }.onFailure { error = it.message }
                        }
                    },
                ) { Text("Redirecionar") }
            }
        }
    }

    if (historyOpen) {
        SessionHistoryDialog(
            container = container,
            workspace = workspace,
            onLoad = { session -> sessionDetail = session },
            onContinue = { session ->
                continueSessionId = session.id
                historyOpen = false
            },
            onDismiss = { historyOpen = false },
        )
    }

    if (sessionDetail != null) {
        SessionDetailDialog(session = sessionDetail!!, onDismiss = { sessionDetail = null })
    }
}

@Composable
private fun SessionHistoryDialog(
    container: AppContainer,
    workspace: String,
    onLoad: (SessionDetail) -> Unit,
    onContinue: (SessionSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { container.api.sessions(workspace) }
            .onSuccess { sessions = it.sessions.reversed() }
            .onFailure { error = it.message }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sessões do projeto") },
        text = {
            when {
                loading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Text(error.orEmpty(), color = ErrorRed)
                sessions.isEmpty() -> Text("Nenhuma sessão salva ainda.", color = TextDim)
                else -> Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                ) {
                    sessions.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    s.title.ifBlank { s.id.take(8) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    "${s.updatedAt.take(16).replace('T', ' ')} · ${formatTokens(s.totalTokens)} tok",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDim,
                                )
                            }
                            TextButton(onClick = {
                                container.appScope.launch {
                                    runCatching { container.api.session(workspace, s.id) }
                                        .onSuccess { onLoad(it) }
                                }
                            }) { Text("Ver") }
                            TextButton(onClick = { onContinue(s) }) { Text("Continuar") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun SessionDetailDialog(session: SessionDetail, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.title.ifBlank { "Sessão ${session.id.take(8)}" }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Entrada ${formatTokens(session.inputTokens)} · Saída ${formatTokens(session.outputTokens)} · Total ${formatTokens(session.totalTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
                Spacer(Modifier.padding(top = 8.dp))
                session.messages.forEach { m -> RestoredRow(m) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun RestoredRow(m: RestoredMessage) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            m.label.ifBlank { m.kind },
            style = MaterialTheme.typography.labelSmall,
            color = when (m.kind) {
                "user" -> SteelBlue
                "chief" -> SuccessGreen
                "tool" -> TextDim
                else -> TextDim
            },
        )
        if (m.text.isNotBlank()) {
            Text(m.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private suspend fun uriToImageInput(context: Context, uri: Uri): ImageInput? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/*"
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        val (bytes, outMime) = shrinkImage(raw, mime)
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imagem"
        ImageInput(
            name = name.substringBeforeLast('.') + if (outMime == "image/jpeg") ".jpg" else "",
            mimeType = outMime,
            dataBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }.getOrNull()
}

/** Reduz a foto para caber no quadro de 12 MiB do relay. */
private fun shrinkImage(bytes: ByteArray, mime: String): Pair<ByteArray, String> {
    if (bytes.size <= 500_000) return bytes to mime
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return if (bytes.size <= 1_200_000) bytes to mime else bytes to mime
    }
    var sample = 1
    while (bounds.outWidth / sample > 3200 || bounds.outHeight / sample > 3200) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return bytes to mime
    val longest = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
    val bmp = if (longest > 1600) {
        val ratio = 1600f / longest
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        decoded
    }
    if (bmp !== decoded) decoded.recycle()
    var quality = 75
    var out = ByteArray(0)
    while (quality >= 40) {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        out = bos.toByteArray()
        if (out.size <= 800_000) break
        quality -= 10
    }
    bmp.recycle()
    return out to "image/jpeg"
}

fun formatTokens(tokens: Long): String {
    if (tokens >= 1_000_000) return "%.1fM".format(tokens / 1_000_000.0)
    if (tokens >= 1_000) return "%.1fk".format(tokens / 1_000.0)
    return tokens.toString()
}
