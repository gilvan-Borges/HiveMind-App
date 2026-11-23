package dev.hivemind.mobile.features.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.api.ImageInput
import dev.hivemind.mobile.core.api.RunStartRequest
import dev.hivemind.mobile.core.media.ImagePrep
import dev.hivemind.mobile.core.media.prepareImage
import dev.hivemind.mobile.core.models.ApprovalInfo
import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.RestoredMessage
import dev.hivemind.mobile.core.models.SessionDetail
import dev.hivemind.mobile.core.models.SessionSummary
import dev.hivemind.mobile.core.outbox.QueuedRun
import dev.hivemind.mobile.core.outbox.RunQueueState
import dev.hivemind.mobile.core.state.ConnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dev.hivemind.mobile.theme.ActionPrimary
import dev.hivemind.mobile.theme.ActionPrimaryText
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.BrandGoldBorder
import dev.hivemind.mobile.theme.BrandGoldStrong
import dev.hivemind.mobile.theme.BrandGoldSubtle
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.OnPrimary
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.InfoBlue
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.SurfaceInput
import dev.hivemind.mobile.theme.SurfacePanel
import dev.hivemind.mobile.theme.TextBody
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.theme.TextPrimary
import dev.hivemind.mobile.features.inbox.InboxIndex
import dev.hivemind.mobile.ui.CircleIconButton
import dev.hivemind.mobile.ui.EmptyStatePanel
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveBlob
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.MicGlyph
import dev.hivemind.mobile.ui.StatusPill
import dev.hivemind.mobile.ui.WaveformGlyph
import dev.hivemind.mobile.ui.blobColor
import kotlinx.coroutines.launch

private val RunModes = listOf(
    "auto" to "Auto",
    "direct" to "Direto",
    "plan" to "Planejar",
    "plan:sdd" to "SDD",
    "plan:sdd-jira" to "SDD + Jira",
    "squad" to "Squad",
    "resident" to "Residente",
)

private val StarterPrompts = listOf(
    "Analisar arquitetura do projeto",
    "Resumir últimas alterações",
    "Encontrar bugs ou pendências",
)

sealed class ChatDisplayItem {
    data class TextBubble(val authorLabel: String, val text: String, val isUser: Boolean) : ChatDisplayItem()
    data class StageBadge(val stage: String) : ChatDisplayItem()
    data class ToolGroup(val toolName: String, val count: Int) : ChatDisplayItem()
    data class ErrorItem(val text: String) : ChatDisplayItem()
    data class HandoffItem(val label: String) : ChatDisplayItem()
    data class FinishItem(val text: String, val tokens: Long) : ChatDisplayItem()
    data class RouteBadge(val provider: String, val model: String, val tier: String, val estimatedMicroUsd: Long) : ChatDisplayItem()
    data class FailoverItem(
        val fromProvider: String,
        val fromModel: String,
        val provider: String,
        val model: String,
        val reasons: List<String>,
    ) : ChatDisplayItem()
    data class OtherItem(val text: String) : ChatDisplayItem()
}

/**
 * Agrupa chunks consecutivos de texto do mesmo autor e chamadas repetidas da mesma ferramenta
 * (Law of Chunking + Miller's Law) para leitura rápida no celular.
 */
private fun simplifyErrorMessage(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Falha na execução."
    // Extrai mensagem legível quando o backend anexa payload JSON bruto do GitHub (ex.: 422 Unprocessable Entity: {"message":...})
    val jsonIdx = trimmed.indexOf(": {")
    if (jsonIdx > 0 && trimmed.endsWith("}")) {
        val prefix = trimmed.substring(0, jsonIdx).trim()
        val msgRegex = Regex(""""message"\s*:\s*"([^"]+)"""")
        val matches = msgRegex.findAll(trimmed.substring(jsonIdx)).map { it.groupValues[1] }.toList()
        val detail = matches.lastOrNull { !it.equals("Validation Failed", ignoreCase = true) } ?: matches.firstOrNull()
        if (!detail.isNullOrBlank()) {
            return "$prefix — $detail"
        }
    }
    return trimmed
}

fun coalesceChatEvents(events: List<LaneEventItem>): List<ChatDisplayItem> {
    val out = mutableListOf<ChatDisplayItem>()
    var lastStage = ""
    for (e in events) {
        when (e.kind) {
            "approval_required" -> Unit
            "text" -> {
                if (e.text.isEmpty()) continue
                val authorLabel = e.label.ifBlank { e.author }.ifBlank { "Equipe" }
                val isUser = e.author.equals("user", ignoreCase = true) ||
                    e.label.equals("Você", ignoreCase = true)
                val prev = out.lastOrNull() as? ChatDisplayItem.TextBubble
                if (prev != null && prev.authorLabel == authorLabel && prev.isUser == isUser) {
                    out[out.lastIndex] = prev.copy(text = prev.text + e.text)
                } else {
                    out += ChatDisplayItem.TextBubble(authorLabel, e.text, isUser)
                }
            }
            "stage" -> {
                val stage = e.stage.ifBlank { "—" }
                if (stage != lastStage) {
                    lastStage = stage
                    out += ChatDisplayItem.StageBadge(stage)
                }
            }
            "tool_call" -> {
                val tool = e.toolName.ifBlank { "ferramenta" }
                val prev = out.lastOrNull() as? ChatDisplayItem.ToolGroup
                if (prev != null && prev.toolName == tool) {
                    out[out.lastIndex] = prev.copy(count = prev.count + 1)
                } else {
                    out += ChatDisplayItem.ToolGroup(tool, 1)
                }
            }
            "error" -> {
                val clean = simplifyErrorMessage(e.text)
                val prev = out.lastOrNull() as? ChatDisplayItem.ErrorItem
                if (prev?.text != clean) {
                    out += ChatDisplayItem.ErrorItem(clean)
                }
            }
            "handoff" -> out += ChatDisplayItem.HandoffItem(e.label.ifBlank { "Handoff" })
            "finish" -> out += ChatDisplayItem.FinishItem(e.text.ifBlank { "Execução concluída." }, e.tokens)
            "model_route" -> out += ChatDisplayItem.RouteBadge(e.provider, e.model, e.tier, e.estimatedMicroUsd)
            "provider_failover" -> out += ChatDisplayItem.FailoverItem(
                fromProvider = e.fromProvider,
                fromModel = e.fromModel,
                provider = e.provider,
                model = e.model,
                reasons = e.reasons,
            )
            "provider_exhausted" -> out += ChatDisplayItem.ErrorItem(
                e.text.ifBlank { "Todos os provedores de IA estão esgotados ou sem credencial." },
            )
            else -> if (e.text.isNotBlank()) out += ChatDisplayItem.OtherItem(e.text)
        }
    }
    return out
}

@Composable
fun ChatThreadPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dev.hivemind.mobile.theme.ObsidianBg),
    ) {
        ConversationHeader(
            title = "Agent Hive",
            onBack = {},
            onHistory = {},
            onFlow = {},
            onDiffs = {},
            onClear = null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DisplayItemRow(ChatDisplayItem.TextBubble("Você", "Blz mais continua um agente hive flutuante ok", true))
            NovasDivider()
            DisplayItemRow(
                ChatDisplayItem.TextBubble(
                    "Equipe",
                    "Fechado, o Agent Hive continua flutuante. As conversas dele passam a ser as mesmas da IDE.",
                    false,
                ),
            )
            RunningAgentCard(
                lane = LaneSnapshot(laneId = "resident", teamName = "Agent Hive", objective = "Unificado com a IDE", running = true, stage = "em execução"),
                onOpenFlow = {},
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleIconButton(Icons.Filled.Add, "Anexar", {}, tint = TextDim)
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Mensagem para Agent Hive",
                        color = TextDim,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 16.sp),
                    )
                    MicGlyph(tint = TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
            }
            Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = SuccessGreen) {
                Box(contentAlignment = Alignment.Center) {
                    WaveformGlyph(tint = OnPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    container: AppContainer,
    padding: PaddingValues,
    draftSeed: String? = null,
    draftMode: String? = null,
    onConsumeDraftSeed: () -> Unit = {},
    onConsumeDraftMode: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenFlow: () -> Unit = {},
    onOpenDiffs: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    conversationTitle: String? = null,
    openedSessionId: String? = null,
    openedLaneId: String? = null,
    initialRunMode: String? = null,
    showUnreadDivider: Boolean = false,
) {
    val workspace by container.hubState.selectedWorkspace.collectAsState()
    val lanesState by container.hubState.lanes.collectAsState()
    val selectedLaneId by container.hubState.selectedLaneId.collectAsState()

    if (workspace == null) {
        EmptyStatePanel(
            title = "Nenhum projeto ativo",
            subtitle = if (onBack != null) "Volte e escolha um projeto." else "Abra o menu para escolher um repositório da IDE.",
            actionLabel = if (onBack != null) "Voltar" else "Escolher projeto",
            onAction = { if (onBack != null) onBack() else onOpenDrawer() },
            modifier = Modifier.padding(padding),
        )
        return
    }
    val ws: String = workspace ?: return

    val activity by container.hubState.activity.collectAsState()
    val lanes = lanesState.values.filter { it.workspace == ws }
    val lane = if (!openedLaneId.isNullOrBlank()) {
        lanes.firstOrNull { it.laneId == openedLaneId }
    } else {
        lanes.firstOrNull { it.laneId == selectedLaneId } ?: lanes.firstOrNull()
    }

    var historyOpen by remember { mutableStateOf(false) }
    var sessionDetail by remember { mutableStateOf<SessionDetail?>(null) }
    var continueSessionId by remember(openedSessionId) { mutableStateOf(openedSessionId) }
    var restored by remember(openedSessionId) { mutableStateOf<List<RestoredMessage>>(emptyList()) }
    var quickSeed by remember { mutableStateOf<String?>(null) }
    var quickMode by remember { mutableStateOf<String?>(null) }
    // Covers the gap between POST /runs/start Accepted and SSE running=true.
    var runPending by remember { mutableStateOf(false) }

    LaunchedEffect(ws, openedLaneId) {
        if (!openedLaneId.isNullOrBlank()) {
            container.hubState.selectedLaneId.value = openedLaneId
        } else if (container.hubState.selectedLaneId.value == null) {
            container.hubState.selectedLaneId.value = lanes.firstOrNull()?.laneId
        }
        container.refreshLanes(ws)
    }
    LaunchedEffect(ws, openedSessionId) {
        val id = openedSessionId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        runCatching { container.api.session(ws, id) }.onSuccess { restored = it.messages }
    }

    LaunchedEffect(draftSeed, draftMode) {
        if (!draftSeed.isNullOrBlank()) {
            quickSeed = draftSeed
            if (!draftMode.isNullOrBlank()) quickMode = draftMode
            onConsumeDraftSeed()
            onConsumeDraftMode()
        }
    }

    val projectLeaf = conversationTitle?.takeIf { it.isNotBlank() }
        ?: ws.substringAfterLast('\\').substringAfterLast('/').ifBlank { "Chat" }
    val composerLaneId = openedLaneId?.takeIf { it.isNotBlank() } ?: lane?.laneId ?: "lane-mobile"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding(),
    ) {
        if (onBack != null) {
            ConversationHeader(
                title = projectLeaf,
                onBack = onBack,
                onHistory = { historyOpen = true },
                onFlow = onOpenFlow,
                onDiffs = onOpenDiffs,
                onClear = if (lane != null && !lane.running && (lane.recentEvents.isNotEmpty() || lane.lastError.isNotBlank())) {
                    {
                        continueSessionId = null
                        container.clearConversation(ws, lane.laneId)
                    }
                } else {
                    null
                },
            )
        } else {
            Surface(color = SurfacePanel) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Menu, contentDescription = "Abrir projetos e menu", tint = TextPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                projectLeaf,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            LaneSelector(container, lanes, lane)
                        }
                    }
                    HorizontalDivider(color = ObsidianBorder)
                }
            }
        }

        ApprovalCards(container, lanes, lane)

        val showLog = lane != null || restored.isNotEmpty()
        if (showLog) {
            ChatLog(
                lane = lane,
                restored = restored,
                openedSessionId = openedSessionId,
                showUnreadDivider = showUnreadDivider,
                activity = activity?.takeIf { notice ->
                    notice.laneId == composerLaneId || (openedSessionId != null && notice.sessionId == openedSessionId)
                },
                onPickStarter = { quickSeed = it },
                onOpenFlow = onOpenFlow,
                modifier = Modifier.weight(1f),
            )
        } else {
            StarterEmptyChat(
                onPickStarter = { quickSeed = it },
                modifier = Modifier.weight(1f),
            )
        }

        PendingRunsPanel(container = container, workspace = ws, lane = lane)

        Composer(
            container = container,
            workspace = ws,
            laneId = composerLaneId,
            peerName = projectLeaf,
            initialRunMode = initialRunMode,
            quietSession = onBack != null,
            continueSessionId = continueSessionId,
            onClearContinueSession = { continueSessionId = null },
            seedText = quickSeed,
            seedMode = quickMode,
            onConsumeSeed = { quickSeed = null },
            onConsumeMode = { quickMode = null },
            onRunPendingChange = { runPending = it },
        )
    }

    if (historyOpen) {
        SessionHistoryDialog(
            container = container,
            workspace = ws,
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
private fun ConversationHeader(
    title: String,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onFlow: () -> Unit,
    onDiffs: () -> Unit,
    onClear: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Voltar",
            onClick = onBack,
            tint = TextPrimary,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HiveBlob(color = blobColor(InboxIndex.avatarSlot(title)), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box {
            CircleIconButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "Mais opções",
                onClick = { menu = true },
                tint = TextPrimary,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Histórico") }, onClick = { menu = false; onHistory() })
                DropdownMenuItem(text = { Text("Fluxo da equipe") }, onClick = { menu = false; onFlow() })
                DropdownMenuItem(text = { Text("Diffs") }, onClick = { menu = false; onDiffs() })
                if (onClear != null) {
                    DropdownMenuItem(text = { Text("Limpar tela") }, onClick = { menu = false; onClear() })
                }
            }
        }
    }
}

@Composable
private fun PipelineProgressStrip(stage: String, running: Boolean) {
    val activeIdx = when {
        stage.contains("fecho", true) || stage.contains("finish", true) || stage.contains("done", true) || stage.contains("closing", true) -> 3
        stage.contains("qa", true) || stage.contains("review", true) || stage.contains("test", true) -> 2
        stage.contains("dev", true) || stage.contains("cod", true) || stage.contains("exec", true) || stage.contains("impl", true) -> 1
        else -> 0
    }
    val steps = listOf("Chefe", "Dev", "QA", "Fecho")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { idx, name ->
            val passed = idx < activeIdx
            val current = idx == activeIdx
            val color = when {
                current && running -> SuccessGreen
                current -> InfoBlue
                passed -> InfoBlue.copy(alpha = 0.65f)
                else -> ObsidianBorder
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                color = if (current || passed) color.copy(alpha = 0.16f) else ObsidianSurface,
                border = BorderStroke(1.dp, color),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (current && running) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(SuccessGreen, CircleShape),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (current || passed) color else TextDim,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LaneSelector(
    container: AppContainer,
    lanes: List<LaneSnapshot>,
    lane: LaneSnapshot?,
) {
    var open by remember { mutableStateOf(false) }
    val running = lane?.running == true
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(10.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, if (running) SuccessGreen.copy(alpha = 0.45f) else ObsidianBorder),
            modifier = Modifier.heightIn(min = 38.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (running) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(SuccessGreen, CircleShape),
                    )
                }
                Text(
                    lane?.teamName?.ifBlank { lane.laneId } ?: "lanes",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (running) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text("▾", style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lanes.forEach { l ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${l.teamName.ifBlank { l.laneId }} ${if (l.running) "• rodando" else ""}",
                            color = if (l.running) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        container.hubState.selectedLaneId.value = l.laneId
                        open = false
                    },
                )
            }
            if (lanes.isEmpty()) {
                DropdownMenuItem(text = { Text("(nenhuma lane ainda)", color = TextDim) }, onClick = { open = false })
            }
        }
    }
}

@Composable
private fun ApprovalCards(
    container: AppContainer,
    lanes: List<LaneSnapshot>,
    lane: LaneSnapshot?,
) {
    val approvals = if (lane != null) lane.pendingApprovals else lanes.flatMap { it.pendingApprovals }
    if (approvals.isEmpty()) return
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        approvals.forEach { approval ->
            ApprovalCard(container, approval)
        }
    }
}

@Composable
private fun ApprovalCard(container: AppContainer, approval: ApprovalInfo) {
    val haptic = LocalHapticFeedback.current
    var busy by remember { mutableStateOf(false) }

    HiveCard(
        highlighted = true,
        accentColor = ApprovalAmber,
        containerColor = ApprovalAmber.copy(alpha = 0.08f),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(text = "Aprovação pendente", color = ApprovalAmber, showDot = true)
            Text(
                approval.label.ifBlank { "Permissão necessária" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            approval.prompt,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = {
                    busy = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    container.appScope.launch {
                        runCatching { container.api.resolveApproval(approval.approvalId, false) }
                            .onSuccess { container.hubState.onApprovalResolved(approval.approvalId) }
                        busy = false
                    }
                },
                enabled = !busy,
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
            ) {
                Text("Recusar", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    busy = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    container.appScope.launch {
                        runCatching { container.api.resolveApproval(approval.approvalId, true) }
                            .onSuccess { container.hubState.onApprovalResolved(approval.approvalId) }
                        busy = false
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = InfoBlue,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
            ) {
                Text("Aprovar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChatLog(
    lane: LaneSnapshot?,
    restored: List<RestoredMessage>,
    openedSessionId: String?,
    showUnreadDivider: Boolean,
    activity: dev.hivemind.mobile.core.models.ActivityNotice?,
    onPickStarter: (String) -> Unit,
    onOpenFlow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItems = remember(lane?.recentEvents, lane?.sessionId, restored, openedSessionId) {
        transcriptFor(
            restored = restored,
            live = lane?.recentEvents.orEmpty(),
            laneSessionId = lane?.sessionId,
            openedSessionId = openedSessionId,
        )
    }
    var novasAt by remember(openedSessionId) { mutableStateOf(-1) }
    LaunchedEffect(displayItems.size, showUnreadDivider) {
        if (showUnreadDivider && novasAt < 0 && displayItems.isNotEmpty()) {
            novasAt = displayItems.indexOfLast { it is ChatDisplayItem.TextBubble }
        }
    }
    val entries = remember(displayItems, novasAt) { withNovasAt(displayItems, novasAt) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!lane?.lastError.isNullOrBlank()) {
                ErrorBanner(
                    message = lane?.lastError.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (entries.isEmpty() && lane?.running != true) {
                StarterEmptyChat(
                    onPickStarter = onPickStarter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(entries.size) { index ->
                        when (val entry = entries[index]) {
                            TranscriptEntry.Novas -> NovasDivider()
                            is TranscriptEntry.Item -> DisplayItemRow(entry.item)
                        }
                    }
                    if (activity != null && activity.phase.isNotBlank()) {
                        item { ActivityCard(activity) }
                    }
                    lane?.takeIf { it.running }?.let { runningLane ->
                        item { RunningAgentCard(lane = runningLane, onOpenFlow = onOpenFlow) }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isNearBottom && entries.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
                    }
                },
                containerColor = ObsidianSurface,
                contentColor = InfoBlue,
            ) {
                Text("↓", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StarterEmptyChat(
    onPickStarter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = BrandGoldSubtle,
                border = BorderStroke(1.dp, BrandGoldBorder),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = BrandGold,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                "No que vamos trabalhar?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                "Envie uma instrução abaixo ou escolha um atalho para começar:",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StarterPrompts.forEach { prompt ->
                    Surface(
                        onClick = { onPickStarter(prompt) },
                        shape = RoundedCornerShape(999.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(prompt, style = MaterialTheme.typography.labelMedium, color = TextBody)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovasDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = ObsidianBorder)
        Text(
            "NOVAS",
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = InfoBlue,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = ObsidianBorder)
    }
}

@Composable
private fun ActivityCard(activity: dev.hivemind.mobile.core.models.ActivityNotice) {
    val label = when (activity.phase) {
        "file" -> "Arquivo"
        "command" -> "Comando"
        "test" -> "Teste"
        else -> "Atividade"
    }
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusPill(text = label, color = InfoBlue, showDot = true)
            Text(
                activity.target.ifBlank { activity.tool.ifBlank { label } },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RunningAgentCard(lane: LaneSnapshot, onOpenFlow: () -> Unit) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HiveBlob(color = blobColor(InboxIndex.avatarSlot(lane.laneId)), modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Equipe", style = MaterialTheme.typography.labelSmall, color = TextDim)
                    Text(
                        lane.teamName.ifBlank { lane.objective.ifBlank { "Agentes em execução" } },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(text = "Em execução", color = SuccessGreen, showDot = true)
            }
            if (lane.stage.isNotBlank() || lane.objective.isNotBlank()) {
                Text(
                    lane.stage.ifBlank { lane.objective },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onOpenFlow,
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) { Text("Ver fluxo") }
        }
    }
}

@Composable
private fun DisplayItemRow(item: ChatDisplayItem) {
    when (item) {
        is ChatDisplayItem.TextBubble -> {
            if (item.isUser) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        color = SuccessGreen,
                        shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                            color = OnPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        is ChatDisplayItem.StageBadge -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                StatusPill(text = "Estágio · ${item.stage}", color = TextDim)
            }
        }
        is ChatDisplayItem.ToolGroup -> {
            var expanded by remember(item.toolName, item.count) { mutableStateOf(false) }
            val isEdit = item.toolName.contains("edit", true) || item.toolName.contains("write", true)
            Surface(
                onClick = { expanded = !expanded },
                color = ObsidianCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (isEdit) Icons.Filled.Edit else Icons.Filled.Build,
                            contentDescription = null,
                            tint = BrandGold,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            item.toolName,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextBody,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.count > 1) {
                            StatusPill(text = "×${item.count}", color = TextDim)
                        }
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "Recolher" else "Expandir",
                            tint = TextDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            "Ferramenta executada ${item.count} vez(es) nesta etapa (${item.toolName}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        is ChatDisplayItem.ErrorItem -> {
            ErrorBanner(message = item.text)
        }
        is ChatDisplayItem.HandoffItem -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = BrandGold,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandGold,
                )
            }
        }
        is ChatDisplayItem.FinishItem -> {
            Surface(
                color = SuccessGreen.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "${item.text} · ${formatTokens(item.tokens)} tok",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        is ChatDisplayItem.RouteBadge -> {
            val route = listOfNotNull(
                item.provider.takeIf { it.isNotBlank() },
                item.model.takeIf { it.isNotBlank() },
                item.tier.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (route.isNotBlank()) {
                Text(
                    buildString {
                        append("IA: ")
                        append(route)
                        if (item.estimatedMicroUsd > 0) append(" · ~").append(formatMicroUsd(item.estimatedMicroUsd))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = InfoBlue,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        is ChatDisplayItem.FailoverItem -> {
            val from = listOfNotNull(
                item.fromProvider.takeIf { it.isNotBlank() },
                item.fromModel.takeIf { it.isNotBlank() },
            ).joinToString("/")
            val to = listOfNotNull(
                item.provider.takeIf { it.isNotBlank() },
                item.model.takeIf { it.isNotBlank() },
            ).joinToString("/")
            Surface(
                color = BrandGold.copy(alpha = 0.10f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.35f)),
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        "Failover: ${from.ifBlank { "provedor" }} -> ${to.ifBlank { "próximo" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandGold,
                    )
                    if (item.reasons.isNotEmpty()) {
                        Text(
                            item.reasons.joinToString("; "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                        )
                    }
                }
            }
        }
        is ChatDisplayItem.OtherItem -> {
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

@Composable
private fun PendingRunsPanel(
    container: AppContainer,
    workspace: String,
    lane: LaneSnapshot?,
) {
    val queued by container.outbox.runs.collectAsState()
    val items = remember(queued, workspace) {
        queued.filter { it.workspace == workspace }.sortedBy { it.createdAt }
    }
    if (items.isEmpty()) return
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { run -> PendingRunRow(container, run, lane) }
    }
}

@Composable
private fun PendingRunRow(container: AppContainer, run: QueuedRun, lane: LaneSnapshot?) {
    val haptic = LocalHapticFeedback.current
    val foreignLane = lane != null && run.laneId != lane.laneId

    val (stateLabel, stateColor) = when (run.state) {
        RunQueueState.QUEUED -> "Na fila" to ApprovalAmber
        RunQueueState.SENDING -> "Enviando…" to InfoBlue
        RunQueueState.FAILED -> "Falhou no envio" to ErrorRed
    }

    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(text = stateLabel, color = stateColor, showDot = true)
                if (foreignLane) {
                    Text(
                        "lane ${run.laneId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                run.objective,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            when (run.state) {
                RunQueueState.QUEUED -> Text(
                    "Envia sozinho quando o PC voltar a responder e a lane estiver livre.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
                RunQueueState.SENDING -> Unit
                RunQueueState.FAILED -> {
                    if (!run.lastError.isNullOrBlank()) {
                        Text(
                            run.lastError,
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                container.appScope.launch { container.outbox.retry(run.id) }
                            },
                            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                        ) {
                            Text("Repetir", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                container.appScope.launch { container.outbox.discard(run.id) }
                            },
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                        ) {
                            Text("Descartar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    container: AppContainer,
    workspace: String,
    laneId: String,
    peerName: String,
    initialRunMode: String?,
    quietSession: Boolean,
    continueSessionId: String?,
    onClearContinueSession: () -> Unit,
    seedText: String?,
    seedMode: String?,
    onConsumeSeed: () -> Unit,
    onConsumeMode: () -> Unit,
    onRunPendingChange: (Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var runMode by remember(initialRunMode) { mutableStateOf(initialRunMode?.ifBlank { null } ?: "auto") }
    var images by remember { mutableStateOf<List<ImageInput>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stickySessionId by remember(laneId) { mutableStateOf<String?>(null) }
    val conn by container.hubState.conn.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val laneKey = "$workspace|$laneId"

    val running by produceState(initialValue = false, laneKey) {
        container.hubState.lanes
            .map { it[laneKey]?.running == true }
            .distinctUntilChanged()
            .collect { value = it }
    }
    val boundSessionId by produceState(initialValue = "", laneKey) {
        container.hubState.lanes
            .map { it[laneKey]?.sessionId.orEmpty() }
            .distinctUntilChanged()
            .collect { value = it }
    }

    LaunchedEffect(boundSessionId) {
        if (boundSessionId.isNotBlank()) stickySessionId = boundSessionId
    }
    LaunchedEffect(running) {
        if (running) {
            sending = false
            onRunPendingChange(false)
        }
    }
    LaunchedEffect(sending) {
        if (!sending) return@LaunchedEffect
        delay(12_000)
        if (!running) {
            sending = false
            onRunPendingChange(false)
        }
    }

    LaunchedEffect(seedText, seedMode) {
        val mode = seedMode
        if (!mode.isNullOrBlank()) {
            runMode = mode
            onConsumeMode()
        }
        if (!seedText.isNullOrBlank()) {
            text = if (text.isBlank() || !mode.isNullOrBlank()) seedText else "$text\n$seedText"
            onConsumeSeed()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            container.appScope.launch {
                val used = images.sumOf { it.dataBase64.length }
                when (val prep = uriToImageInput(context, uri, used)) {
                    is ImagePrep.Ok -> {
                        images = images + prep.toInput(uri)
                        error = null
                    }
                    ImagePrep.TooLarge -> error = "A imagem não cabe no relay (limite de 12 MiB)."
                    ImagePrep.Unreadable -> error = "Não foi possível ler a imagem."
                    null -> error = "Não foi possível ler a imagem."
                }
            }
        }
    }

    fun submitRun() {
        if ((text.isBlank() && images.isEmpty()) || sending || running) return
        sending = true
        error = null
        onRunPendingChange(true)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        container.appScope.launch {
            val activeSessionId = continueSessionId?.takeIf { it.isNotBlank() }
                ?: stickySessionId?.takeIf { it.isNotBlank() }
                ?: boundSessionId.takeIf { it.isNotBlank() }
            val objective = text.ifBlank { "Analise a(s) imagem(ns) anexada(s)." }
            val payload = images.toList()

            fun consumeDraft() {
                text = ""
                images = emptyList()
                onClearContinueSession()
            }

            suspend fun enqueueQueued() {
                runCatching {
                    container.enqueueRun(
                        laneId = laneId,
                        workspace = workspace,
                        objective = objective,
                        runMode = runMode,
                        sessionId = activeSessionId,
                        images = payload,
                    )
                }.onSuccess {
                    consumeDraft()
                    container.hubState.selectedLaneId.value = laneId
                    sending = false
                    onRunPendingChange(false)
                }.onFailure {
                    error = it.message
                    sending = false
                    onRunPendingChange(false)
                }
            }

            if (conn is ConnState.Connected) {
                try {
                    container.startRun(
                        laneId = laneId,
                        workspace = workspace,
                        objective = objective,
                        sessionId = activeSessionId,
                        runMode = runMode,
                        images = payload,
                    )
                    consumeDraft()
                    container.hubState.selectedLaneId.value = laneId
                    // Keep sending/runPending until SSE sets running (or timeout).
                } catch (e: ApiException) {
                    if (e.status == null) {
                        enqueueQueued()
                    } else {
                        error = e.message
                        sending = false
                        onRunPendingChange(false)
                    }
                } catch (e: Exception) {
                    error = e.message
                    sending = false
                    onRunPendingChange(false)
                }
            } else {
                enqueueQueued()
            }
        }
    }

    var modeMenuOpen by remember { mutableStateOf(false) }
    val currentModeLabel = RunModes.firstOrNull { it.first == runMode }?.second ?: "Auto"
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) text = if (text.isBlank()) spoken else "$text $spoken"
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Mensagem para $peerName")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { error = "Reconhecimento de voz indisponível neste aparelho." }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (error != null) {
            ErrorBanner(message = error.orEmpty())
        }

        if (continueSessionId != null && !quietSession) {
            Surface(
                color = ApprovalAmber.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, ApprovalAmber.copy(alpha = 0.35f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        "Continuando sessão ${continueSessionId.take(8)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = ApprovalAmber,
                    )
                    IconButton(
                        onClick = onClearContinueSession,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancelar continuação",
                            tint = ApprovalAmber,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (runMode != "auto") {
            Text(
                "Modo · $currentModeLabel",
                style = MaterialTheme.typography.labelSmall,
                color = BrandGold,
            )
        }
        if (images.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                images.forEachIndexed { i, img ->
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(img.name.ifBlank { "imagem" }, style = MaterialTheme.typography.labelSmall)
                            IconButton(
                                onClick = { images = images.filterIndexed { j, _ -> j != i } },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remover", modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                CircleIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Anexar imagem ou escolher modo",
                    onClick = { modeMenuOpen = true },
                    tint = TextDim,
                )
                DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Anexar imagem") },
                        onClick = {
                            modeMenuOpen = false
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                    RunModes.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = if (runMode == value) BrandGold else TextPrimary,
                                    fontWeight = if (runMode == value) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                runMode = value
                                modeMenuOpen = false
                            },
                        )
                    }
                    if (running) {
                        DropdownMenuItem(
                            text = { Text("Parar", color = ErrorRed) },
                            onClick = {
                                modeMenuOpen = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                container.appScope.launch {
                                    runCatching { container.api.cancel(laneId) }
                                }
                            },
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(24.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).heightIn(min = 24.dp, max = 96.dp),
                        textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp),
                        cursorBrush = SolidColor(BrandGold),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { if (!running) submitRun() }),
                        maxLines = 4,
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    Text("Mensagem para $peerName", color = TextDim, fontSize = 16.sp)
                                }
                                inner()
                            }
                        },
                    )
                    IconButton(onClick = { startVoice() }, modifier = Modifier.size(44.dp)) {
                        MicGlyph(tint = TextDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
            val canSend = text.isNotBlank() || images.isNotEmpty()
            Surface(
                onClick = click@{
                    if (sending) return@click
                    if (!canSend) {
                        startVoice()
                        return@click
                    }
                    if (running) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        container.appScope.launch {
                            val sid = stickySessionId?.takeIf { it.isNotBlank() }
                                ?: boundSessionId.takeIf { it.isNotBlank() }
                            val note = text.ifBlank { "Analise a(s) imagem(ns) anexada(s)." }
                            val restart = RunStartRequest(
                                laneId = laneId,
                                workspace = workspace,
                                objective = note,
                                sessionId = sid,
                                runMode = runMode,
                                images = images,
                            )
                            runCatching {
                                if (images.isEmpty()) {
                                    try {
                                        container.api.steer(laneId, note)
                                    } catch (e: ApiException) {
                                        if (e.status == 404 || e.status == 400 || e.status == 409) {
                                            container.api.redirect(restart)
                                        } else {
                                            throw e
                                        }
                                    }
                                } else {
                                    container.api.redirect(restart)
                                }
                            }.onSuccess {
                                text = ""
                                images = emptyList()
                            }.onFailure { error = it.message }
                        }
                    } else {
                        submitRun()
                    }
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = SuccessGreen,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = OnPrimary,
                        )
                    } else if (!canSend) {
                        WaveformGlyph(tint = OnPrimary, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (running) "Enviar nota" else "Enviar",
                            tint = OnPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
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
        title = { Text("Sessões do projeto", fontWeight = FontWeight.SemiBold) },
        text = {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> ErrorBanner(message = error.orEmpty())
                sessions.isEmpty() -> Text("Nenhuma sessão salva ainda.", color = TextDim)
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sessions.forEach { s ->
                        HiveCard(contentPadding = PaddingValues(10.dp)) {
                            Text(
                                s.title.ifBlank { s.id.take(8) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${s.updatedAt.take(16).replace('T', ' ')} · ${formatTokens(s.totalTokens)} tok",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        container.appScope.launch {
                                            runCatching { container.api.session(workspace, s.id) }
                                                .onSuccess { onLoad(it) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) { Text("Ver") }
                                Button(
                                    onClick = { onContinue(s) },
                                    modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) { Text("Continuar") }
                            }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Entrada ${formatTokens(session.inputTokens)} · Saída ${formatTokens(session.outputTokens)} · Total ${formatTokens(session.totalTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InfoBlue,
                )
                HorizontalDivider(color = ObsidianBorder)
                session.messages.forEach { m -> RestoredRow(m) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun RestoredRow(m: RestoredMessage) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            m.label.ifBlank { m.kind },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (m.kind) {
                "user" -> InfoBlue
                "chief" -> SuccessGreen
                else -> TextDim
            },
        )
        if (m.text.isNotBlank()) {
            Text(m.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private suspend fun uriToImageInput(context: Context, uri: Uri, alreadyBase64: Int): ImagePrep? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/*"
            val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching ImagePrep.Unreadable
            prepareImage(raw, mime, alreadyBase64)
        }.getOrNull()
    }

private fun ImagePrep.Ok.toInput(uri: Uri): ImageInput {
    val leaf = uri.lastPathSegment?.substringAfterLast('/') ?: "imagem"
    val base = leaf.substringBeforeLast('.')
    return ImageInput(
        name = base + if (mime == "image/jpeg") ".jpg" else "",
        mimeType = mime,
        dataBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

fun formatTokens(tokens: Long): String {
    if (tokens >= 1_000_000) return "%.1fM".format(java.util.Locale.US, tokens / 1_000_000.0)
    if (tokens >= 1_000) return "%.1fk".format(java.util.Locale.US, tokens / 1_000.0)
    return tokens.toString()
}

/** Micro-USD do Jev em formato legível: 12_300 -> "$0.0123". */
fun formatMicroUsd(microUsd: Long): String {
    val usd = microUsd / 1_000_000.0
    return when {
        usd >= 1.0 -> java.util.Locale.US.let { String.format(it, "$%.2f", usd) }
        usd >= 0.01 -> java.util.Locale.US.let { String.format(it, "$%.4f", usd) }
        else -> java.util.Locale.US.let { String.format(it, "$%.6f", usd) }
    }
}
