package dev.hivemind.mobile.features.chat

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.api.ApiException
import dev.hivemind.mobile.core.api.ImageInput
import dev.hivemind.mobile.core.api.RunStartRequest
import dev.hivemind.mobile.core.media.ImagePrep
import dev.hivemind.mobile.core.media.prepareImage
import dev.hivemind.mobile.core.models.ApprovalInfo
import dev.hivemind.mobile.core.models.LaneEventItem
import dev.hivemind.mobile.core.models.LaneSnapshot
import dev.hivemind.mobile.core.models.ProviderHealthView
import dev.hivemind.mobile.core.models.RestoredMessage
import dev.hivemind.mobile.core.models.SessionDetail
import dev.hivemind.mobile.core.models.SessionSummary
import dev.hivemind.mobile.core.outbox.QueuedRun
import dev.hivemind.mobile.core.outbox.RunQueueState
import dev.hivemind.mobile.core.state.ConnState
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.MonoFonts
import dev.hivemind.mobile.theme.InfoBlue
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.ui.EmptyStatePanel
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.StatusPill
import kotlinx.coroutines.launch

// Modos aceitos pelo bridge (ide/internal/remote/server.go isAllowedRunMode).
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
    "Resumir Ãºltimas alteraÃ§Ãµes",
    "Encontrar bugs ou pendÃªncias",
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
 * (Law of Chunking + Miller's Law) para leitura rÃ¡pida no celular.
 */
fun coalesceChatEvents(events: List<LaneEventItem>): List<ChatDisplayItem> {
    val out = mutableListOf<ChatDisplayItem>()
    for (e in events) {
        when (e.kind) {
            "approval_required" -> Unit
            "text" -> {
                if (e.text.isEmpty()) continue
                val authorLabel = e.label.ifBlank { e.author }.ifBlank { "Equipe" }
                val isUser = e.author.equals("user", ignoreCase = true) ||
                    e.label.equals("VocÃª", ignoreCase = true)
                val prev = out.lastOrNull() as? ChatDisplayItem.TextBubble
                if (prev != null && prev.authorLabel == authorLabel && prev.isUser == isUser) {
                    out[out.lastIndex] = prev.copy(text = prev.text + e.text)
                } else {
                    out += ChatDisplayItem.TextBubble(authorLabel, e.text, isUser)
                }
            }
            "stage" -> {
                val stage = e.stage.ifBlank { "â€”" }
                val prev = out.lastOrNull() as? ChatDisplayItem.StageBadge
                if (prev?.stage != stage) {
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
            "error" -> out += ChatDisplayItem.ErrorItem(e.text)
            "handoff" -> out += ChatDisplayItem.HandoffItem(e.label.ifBlank { "Handoff" })
            "finish" -> out += ChatDisplayItem.FinishItem(e.text.ifBlank { "ExecuÃ§Ã£o concluÃ­da." }, e.tokens)
            "model_route" -> out += ChatDisplayItem.RouteBadge(e.provider, e.model, e.tier, e.estimatedMicroUsd)
            "provider_failover" -> out += ChatDisplayItem.FailoverItem(
                fromProvider = e.fromProvider,
                fromModel = e.fromModel,
                provider = e.provider,
                model = e.model,
                reasons = e.reasons,
            )
            "provider_exhausted" -> out += ChatDisplayItem.ErrorItem(
                e.text.ifBlank { "Todos os provedores de IA estÃ£o esgotados ou sem credencial." },
            )
            else -> if (e.text.isNotBlank()) out += ChatDisplayItem.OtherItem(e.text)
        }
    }
    return out
}

@Composable
fun ChatScreen(
    container: AppContainer,
    padding: PaddingValues,
    draftSeed: String? = null,
    onConsumeDraftSeed: () -> Unit = {},
) {
    val workspace by container.hubState.selectedWorkspace.collectAsState()
    val lanesState by container.hubState.lanes.collectAsState()
    val pairing by container.hubState.pairing.collectAsState()
    val selectedLaneId by container.hubState.selectedLaneId.collectAsState()
    val providerHealth by container.hubState.providerHealth.collectAsState()
    val relayGap by container.hubState.relayGap.collectAsState()

    if (workspace == null) {
        EmptyStatePanel(
            title = "Nenhum projeto ativo",
            subtitle = "Escolha um repositÃ³rio na aba Projetos para acompanhar o chat da equipe ou enviar instruÃ§Ãµes.",
            modifier = Modifier.padding(padding),
        )
        return
    }
    val ws: String = workspace ?: return

    val lanes = lanesState.values.filter { it.workspace == ws }
    val lane = lanes.firstOrNull { it.laneId == selectedLaneId } ?: lanes.firstOrNull()

    var historyOpen by remember { mutableStateOf(false) }
    var sessionDetail by remember { mutableStateOf<SessionDetail?>(null) }
    var continueSessionId by remember { mutableStateOf<String?>(null) }
    var quickSeed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ws) {
        if (container.hubState.selectedLaneId.value == null) {
            container.hubState.selectedLaneId.value = lanes.firstOrNull()?.laneId
        }
        container.refreshLanes(ws)
    }

    LaunchedEffect(draftSeed) {
        if (!draftSeed.isNullOrBlank()) {
            quickSeed = draftSeed
            onConsumeDraftSeed()
        }
    }

    val projectLeaf = ws.substringAfterLast('\\').substringAfterLast('/').ifBlank { "Chat" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding(),
    ) {
        // CabeÃ§alho compacto com contexto claro (Jakob's Law + Selective Attention)
        Surface(color = ObsidianCard) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            projectLeaf,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pairing?.host?.takeIf { it.isNotBlank() }?.let { "$it Â· $ws" } ?: ws,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if ((lane?.turnTokens ?: 0L) > 0L) {
                        StatusPill(
                            text = "${formatTokens(lane?.turnTokens ?: 0L)} tok",
                            color = BrandGold,
                        )
                    }
                    LaneSelector(container, lanes, lane)
                    IconButton(
                        onClick = { historyOpen = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = "HistÃ³rico de sessÃµes",
                            tint = TextDim,
                        )
                    }
                }
                if (lane != null && (lane.running || lane.stage.isNotBlank())) {
                    PipelineProgressStrip(stage = lane.stage, running = lane.running)
                }
                ProviderHealthStrip(health = providerHealth)
                if (relayGap != null) {
                    Surface(
                        onClick = { container.hubState.clearRelayGap() },
                        color = ApprovalAmber.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ApprovalAmber.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "Relay perdeu eventos â€” histÃ³rico pode ter furos (${relayGap!!.reason}). Toque para dispensar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ApprovalAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                HorizontalDivider(color = ObsidianBorder)
            }
        }

        ApprovalCards(container, lanes, lane)

        if (lane != null) {
            ChatLog(
                lane = lane,
                onPickStarter = { quickSeed = it },
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
            lane = lane,
            continueSessionId = continueSessionId,
            onClearContinueSession = { continueSessionId = null },
            seedText = quickSeed,
            onConsumeSeed = { quickSeed = null },
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
                current -> BrandGold
                passed -> BrandGold.copy(alpha = 0.65f)
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

/**
 * Badges de saÃºde dos provedores (evento `provider_health` do Jev): circuitos
 * abertos/meio-abertos e gasto diÃ¡rio acima de 80% do teto. Sem estados, nada
 * Ã© exibido (SDD D4: destaque sem competiÃ§Ã£o visual).
 */
@Composable
private fun ProviderHealthStrip(health: List<ProviderHealthView>) {
    if (health.isEmpty()) return
    val alerts = health.mapNotNull { p ->
        when {
            p.state.equals("open", true) -> p to ErrorRed
            p.state.equals("half_open", true) -> p to ApprovalAmber
            p.dailyCapMicroUsd > 0 && p.dailySpentMicroUsd >= (p.dailyCapMicroUsd * 4) / 5 -> p to ApprovalAmber
            else -> null
        }
    }
    if (alerts.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        alerts.take(3).forEach { (p, color) ->
            val label = when {
                p.state.equals("open", true) -> "${p.provider} Â· circuito aberto"
                p.state.equals("half_open", true) -> "${p.provider} Â· meio-aberto"
                else -> "${p.provider} Â· gasto diÃ¡rio ${formatMicroUsd(p.dailySpentMicroUsd)}"
            }
            StatusPill(text = label, color = color, showDot = true)
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
                Text("â–¾", style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lanes.forEach { l ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${l.teamName.ifBlank { l.laneId }} ${if (l.running) "â€¢ rodando" else ""}",
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
            StatusPill(text = "AprovaÃ§Ã£o pendente", color = ApprovalAmber, showDot = true)
            Text(
                approval.label.ifBlank { "PermissÃ£o necessÃ¡ria" },
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
                    containerColor = BrandGold,
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
    lane: LaneSnapshot,
    onPickStarter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItems = remember(lane.recentEvents) { coalesceChatEvents(lane.recentEvents) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(displayItems.size) {
        if (displayItems.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(displayItems.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (lane.lastError.isNotBlank()) {
                ErrorBanner(
                    message = lane.lastError,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (displayItems.isEmpty()) {
                StarterEmptyChat(
                    onPickStarter = onPickStarter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayItems) { item -> DisplayItemRow(item) }
                }
            }
        }

        AnimatedVisibility(
            visible = !isNearBottom && displayItems.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        if (displayItems.isNotEmpty()) listState.animateScrollToItem(displayItems.lastIndex)
                    }
                },
                containerColor = ObsidianSurface,
                contentColor = BrandGold,
            ) {
                Text("â†“", fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Pronto para comeÃ§ar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Envie uma instruÃ§Ã£o abaixo ou toque em uma sugestÃ£o para iniciar a equipe:",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
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
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(prompt, style = MaterialTheme.typography.labelMedium, color = BrandGold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayItemRow(item: ChatDisplayItem) {
    when (item) {
        is ChatDisplayItem.TextBubble -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (item.isUser) Arrangement.End else Arrangement.Start,
            ) {
                Surface(
                    color = if (item.isUser) BrandGold.copy(alpha = 0.16f) else ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (item.isUser) BrandGold.copy(alpha = 0.40f) else ObsidianBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            item.authorLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandGold,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(item.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        is ChatDisplayItem.StageBadge -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                StatusPill(text = "EstÃ¡gio: ${item.stage}", color = TextDim)
            }
        }
        is ChatDisplayItem.ToolGroup -> {
            val label = if (item.count > 1) "${item.toolName} Ã—${item.count}" else item.toolName
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
            ) {
                Text(
                    "âš™ $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    fontFamily = MonoFonts,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        is ChatDisplayItem.ErrorItem -> {
            ErrorBanner(message = item.text)
        }
        is ChatDisplayItem.HandoffItem -> {
            Text(
                "â‡„ ${item.label}",
                style = MaterialTheme.typography.labelSmall,
                color = BrandGold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        is ChatDisplayItem.RouteBadge -> {
            val route = listOfNotNull(
                item.provider.takeIf { it.isNotBlank() },
                item.model.takeIf { it.isNotBlank() },
                item.tier.takeIf { it.isNotBlank() },
            ).joinToString(" Â· ")
            if (route.isNotBlank()) {
                Text(
                    buildString {
                        append("â—† IA: ")
                        append(route)
                        if (item.estimatedMicroUsd > 0) append(" Â· ~").append(formatMicroUsd(item.estimatedMicroUsd))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = InfoBlue,
                    fontFamily = MonoFonts,
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
                color = ApprovalAmber.copy(alpha = 0.10f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ApprovalAmber.copy(alpha = 0.35f)),
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        "â‡„ Failover${if (from.isNotBlank() && to.isNotBlank()) ": $from â†’ $to" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ApprovalAmber,
                        fontFamily = MonoFonts,
                    )
                    item.reasons.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        is ChatDisplayItem.FinishItem -> {
            Surface(
                color = SuccessGreen.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "âœ“ ${item.text} Â· ${formatTokens(item.tokens)} tok",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        is ChatDisplayItem.OtherItem -> {
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

/**
 * Fila de envio do workspace (Spec 002): objetivos gravados com o bridge
 * inacessível, entregues sozinhos quando a conexão volta e a lane está livre.
 * Estados visíveis — Na fila / Enviando / Falhou — com ação humana apenas no
 * FAILED (SDD D4–D5: destaque sem competir com o chat vivo).
 */
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
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
    lane: LaneSnapshot?,
    continueSessionId: String?,
    onClearContinueSession: () -> Unit,
    seedText: String?,
    onConsumeSeed: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var runMode by remember { mutableStateOf("auto") }
    var images by remember { mutableStateOf<List<ImageInput>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val conn by container.hubState.conn.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(seedText) {
        if (!seedText.isNullOrBlank()) {
            text = if (text.isBlank()) seedText else "$text\n$seedText"
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
                    ImagePrep.TooLarge -> error = "A imagem nÃ£o cabe no relay (limite de 12 MiB)."
                    ImagePrep.Unreadable -> error = "NÃ£o foi possÃ­vel ler a imagem."
                    null -> error = "NÃ£o foi possÃ­vel ler a imagem."
                }
            }
        }
    }

    fun submitRun() {
        if ((text.isBlank() && images.isEmpty()) || sending) return
        sending = true
        error = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        container.appScope.launch {
            val laneId = lane?.laneId ?: "lane-mobile"
            val activeSessionId = continueSessionId?.takeIf { it.isNotBlank() }
                ?: lane?.sessionId?.takeIf { it.isNotBlank() }
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
                }.onFailure { error = it.message }
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
                    container.refreshLanes(workspace)
                } catch (e: ApiException) {
                    if (e.status == null) {
                        // Transporte caiu no meio do caminho: o hub nunca recebeu o
                        // comando — o rascunho vai para a fila durável (Spec 002).
                        enqueueQueued()
                    } else {
                        error = e.message
                    }
                } catch (e: Exception) {
                    error = e.message
                }
            } else {
                // Bridge inacessível: fila durável — envia sozinho quando voltar.
                enqueueQueued()
            }
            sending = false
        }
    }

    Surface(color = ObsidianCard) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = ObsidianBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    ErrorBanner(message = error.orEmpty())
                }

                if (continueSessionId != null) {
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
                                "Continuando sessÃ£o ${continueSessionId.take(8)}â€¦",
                                style = MaterialTheme.typography.labelSmall,
                                color = ApprovalAmber,
                            )
                            IconButton(
                                onClick = onClearContinueSession,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Cancelar continuaÃ§Ã£o",
                                    tint = ApprovalAmber,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    RunModes.forEach { (value, label) ->
                        FilterChip(
                            selected = runMode == value,
                            onClick = { runMode = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandGold.copy(alpha = 0.20f),
                                selectedLabelColor = BrandGold,
                            ),
                        )
                    }
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
                                    Text(
                                        img.name.ifBlank { "imagem" },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    IconButton(
                                        onClick = { images = images.filterIndexed { j, _ -> j != i } },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remover",
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = "Anexar imagem",
                            tint = TextDim,
                        )
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("InstruÃ§Ã£o para a equipeâ€¦", color = TextDim) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandGold,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = {
                            if (lane?.running != true) submitRun()
                        }),
                        maxLines = 4,
                    )
                    if (lane?.running != true) {
                        FilledIconButton(
                            onClick = { submitRun() },
                            enabled = !sending && (text.isNotBlank() || images.isNotEmpty()),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = BrandGold,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = ObsidianSurface,
                                disabledContentColor = TextDim,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            if (sending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                            }
                        }
                    }
                }

                if (lane?.running == true) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                container.appScope.launch {
                                    runCatching { container.api.cancel(lane.laneId) }
                                }
                            },
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.50f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                        ) {
                            Text("â–  Parar", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                if (text.isBlank() && images.isEmpty()) return@Button
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                container.appScope.launch {
                                    val laneId = lane.laneId
                                    runCatching {
                                        container.api.redirect(
                                            RunStartRequest(
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
                            enabled = text.isNotBlank() || images.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGold,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                        ) {
                            Text("Redirecionar", fontWeight = FontWeight.SemiBold)
                        }
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
        title = { Text("SessÃµes do projeto", fontWeight = FontWeight.SemiBold) },
        text = {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> ErrorBanner(message = error.orEmpty())
                sessions.isEmpty() -> Text("Nenhuma sessÃ£o salva ainda.", color = TextDim)
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
                                "${s.updatedAt.take(16).replace('T', ' ')} Â· ${formatTokens(s.totalTokens)} tok",
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
        title = { Text(session.title.ifBlank { "SessÃ£o ${session.id.take(8)}" }) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Entrada ${formatTokens(session.inputTokens)} Â· SaÃ­da ${formatTokens(session.outputTokens)} Â· Total ${formatTokens(session.totalTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandGold,
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
                "user" -> BrandGold
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

/** Micro-USD do Jev em formato legÃ­vel: 12_300 â†’ "$0.0123". */
fun formatMicroUsd(microUsd: Long): String {
    val usd = microUsd / 1_000_000.0
    return when {
        usd >= 1.0 -> java.util.Locale.US.let { String.format(it, "$%.2f", usd) }
        usd >= 0.01 -> java.util.Locale.US.let { String.format(it, "$%.4f", usd) }
        else -> java.util.Locale.US.let { String.format(it, "$%.6f", usd) }
    }
}
