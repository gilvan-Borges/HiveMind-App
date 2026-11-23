package dev.hivemind.mobile.features.diffs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.models.DiffLine
import dev.hivemind.mobile.core.models.FileDiff
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.DiffAddedBg
import dev.hivemind.mobile.theme.DiffAddedFg
import dev.hivemind.mobile.theme.DiffHeaderBg
import dev.hivemind.mobile.theme.DiffHunkBg
import dev.hivemind.mobile.theme.DiffRemovedBg
import dev.hivemind.mobile.theme.DiffRemovedFg
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.MonoFonts
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.ui.EmptyStatePanel
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.StatusPill
import kotlinx.coroutines.launch

@Composable
fun DiffsScreen(
    container: AppContainer,
    padding: PaddingValues,
    workspace: String?,
    onBack: (() -> Unit)? = null,
    onAskInChat: (String) -> Unit = {},
) {
    if (workspace == null) {
        Column(Modifier.fillMaxSize().padding(padding)) {
            DiffsStudio(
                files = emptyList(),
                loading = false,
                error = null,
                notice = null,
                busy = false,
                emptyTitle = "Nenhum projeto selecionado",
                emptyBody = "Volte e escolha um projeto para ver o que mudou.",
                onFile = {},
                onCommit = {},
                onPr = {},
                onRefresh = {},
                onBack = onBack,
            )
        }
        return
    }

    var files by remember { mutableStateOf<List<FileDiff>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FileDiff?>(null) }
    var commitOpen by remember { mutableStateOf(false) }
    var prOpen by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }
    var prTitle by remember { mutableStateOf("") }
    var prBody by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        runCatching { container.loadDiffs(workspace) }
            .onSuccess { files = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(workspace) { load() }

    val open = selected
    if (open != null) {
        DiffViewer(
            file = open,
            padding = padding,
            onBack = { selected = null },
            onAskInChat = { onAskInChat("Revise e ajuste as alterações em @${open.path}: ") },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        DiffsStudio(
            files = files,
            loading = loading,
            error = error,
            notice = notice,
            busy = busy,
            onFile = { selected = it },
            onCommit = { commitOpen = true },
            onPr = { prOpen = true },
            onRefresh = { container.appScope.launch { load() } },
            onBack = onBack,
        )
    }
        if (commitOpen) {
            AlertDialog(
                onDismissRequest = { if (!busy) commitOpen = false },
                title = { Text("Commit") },
                text = {
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Mensagem") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = commitMessage.isNotBlank() && !busy,
                        onClick = {
                            busy = true
                            container.appScope.launch {
                                runCatching { container.api.commit(workspace, commitMessage.trim(), false) }
                                    .onSuccess {
                                        commitMessage = ""
                                        commitOpen = false
                                        notice = "Commit registrado."
                                        load()
                                    }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        },
                    ) { Text("Commit") }
                },
                dismissButton = { TextButton(onClick = { commitOpen = false }) { Text("Cancelar") } },
            )
        }
        if (prOpen) {
            AlertDialog(
                onDismissRequest = { if (!busy) prOpen = false },
                title = { Text("Abrir pull request") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = prTitle, onValueChange = { prTitle = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = prBody, onValueChange = { prBody = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = prTitle.isNotBlank() && !busy,
                        onClick = {
                            busy = true
                            container.appScope.launch {
                                runCatching { container.api.openPullRequest(workspace, prTitle.trim(), prBody.trim(), "main") }
                                    .onSuccess { response ->
                                        prTitle = ""
                                        prBody = ""
                                        prOpen = false
                                        notice = response.url.ifBlank { "Pull request aberto." }
                                    }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        },
                    ) { Text("Abrir") }
                },
                dismissButton = { TextButton(onClick = { prOpen = false }) { Text("Cancelar") } },
            )
        }
}

@Composable
private fun DiffViewer(
    file: FileDiff,
    padding: PaddingValues,
    onBack: () -> Unit,
    onAskInChat: () -> Unit,
) {
    val hScroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dev.hivemind.mobile.ui.CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
                tint = dev.hivemind.mobile.theme.TextPrimary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                file.path.substringAfterLast('/'),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = MonoFonts,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (file.added > 0) Text("+${file.added}", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
            if (file.removed > 0) {
                Spacer(Modifier.width(8.dp))
                Text("-${file.removed}", color = ErrorRed, fontWeight = FontWeight.SemiBold)
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(file.lines) { line ->
                val (bg, fg) = when (line.kind) {
                    "added" -> DiffAddedBg to DiffAddedFg
                    "removed" -> DiffRemovedBg to DiffRemovedFg
                    "hunk" -> DiffHunkBg to BrandGold
                    "header" -> DiffHeaderBg to TextDim
                    else -> Color.Transparent to MaterialTheme.colorScheme.onBackground
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .horizontalScroll(hScroll)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFonts,
                        color = fg,
                        softWrap = false,
                    )
                }
            }
        }

        Surface(color = dev.hivemind.mobile.theme.ObsidianBg) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                dev.hivemind.mobile.ui.PillButton("Ajustar este arquivo no Chat", onAskInChat)
            }
        }
    }
}

@Composable
fun DiffsStudio(
    files: List<FileDiff>,
    loading: Boolean,
    error: String?,
    notice: String?,
    busy: Boolean,
    onFile: (FileDiff) -> Unit,
    onCommit: () -> Unit,
    onPr: () -> Unit,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
    emptyTitle: String = "Workspace limpo",
    emptyBody: String = "Nenhuma alteração pendente de commit neste projeto.",
) {
    val added = files.sumOf { it.added }
    val removed = files.sumOf { it.removed }
    val kinds = files.groupBy { extOf(it.path) }.entries.sortedByDescending { it.value.size }.take(4)
    dev.hivemind.mobile.ui.EntryScaffold(
        applyInsets = false,
        footer = {
            if (!notice.isNullOrBlank()) {
                Text(notice, color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
            }
            when {
                loading -> Unit
                error != null -> dev.hivemind.mobile.ui.PillButton("Tentar de novo", onRefresh)
                files.isEmpty() -> dev.hivemind.mobile.ui.PillButton("Atualizar", onRefresh)
                else -> {
                    dev.hivemind.mobile.ui.PillButton("Aprovar commit", onCommit, enabled = !busy && files.isNotEmpty())
                    dev.hivemind.mobile.ui.PillButton("Aprovar PR", onPr, primary = false, enabled = !busy)
                }
            }
        },
    ) {
        if (onBack != null) {
            dev.hivemind.mobile.ui.EntryBackHeader("Diffs", onBack)
            Spacer(Modifier.height(8.dp))
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGold)
            }
            error != null -> dev.hivemind.mobile.ui.StudioEmpty(
                icon = Icons.Outlined.ErrorOutline,
                title = "Não deu para ler o diff",
                body = error,
            )
            files.isEmpty() -> dev.hivemind.mobile.ui.StudioEmpty(
                icon = Icons.Outlined.CheckCircle,
                title = emptyTitle,
                body = emptyBody,
            )
            else -> {
                Text(
                    files.size.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (files.size == 1) "arquivo alterado" else "arquivos alterados",
                    color = TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                dev.hivemind.mobile.ui.ChangeBar(added, removed)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("+$added", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                    Text("-$removed", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
                if (kinds.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        kinds.forEach { (ext, group) ->
                            StatusPill(text = "$ext ${group.size}", color = BrandGold)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                files.forEach { file ->
                    val (mark, markColor) = statusStyle(file.status)
                    HiveCard(
                        onClick = { onFile(file) },
                        modifier = Modifier.padding(bottom = 10.dp),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(text = mark, color = markColor)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    file.path.substringAfterLast('/'),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    file.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDim,
                                    fontFamily = MonoFonts,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("+${file.added}", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Text("-${file.removed}", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        dev.hivemind.mobile.ui.ChangeBar(file.added, file.removed)
                    }
                }
            }
        }
    }
}

private fun extOf(path: String): String = path.substringAfterLast('.', "arq").lowercase().ifBlank { "arq" }

private fun statusStyle(status: String): Pair<String, Color> = when (status) {
    "A" -> "A" to SuccessGreen
    "D" -> "D" to ErrorRed
    "R" -> "R" to BrandGold
    "U" -> "U" to ApprovalAmber
    "M" -> "M" to BrandGold
    else -> "•" to TextDim
}

@Composable
fun DiffsPreview() {
    DiffsStudio(
        files = sampleDiffs(),
        loading = false,
        error = null,
        notice = null,
        busy = false,
        onFile = {},
        onCommit = {},
        onPr = {},
        onRefresh = {},
        onBack = {},
    )
}

@Composable
fun DiffFilePreview() {
    DiffViewer(file = sampleDiffs().first(), padding = PaddingValues(0.dp), onBack = {}, onAskInChat = {})
}

@Composable
fun DiffsEmptyPreview() {
    DiffsStudio(
        files = emptyList(),
        loading = false,
        error = null,
        notice = null,
        busy = false,
        onFile = {},
        onCommit = {},
        onPr = {},
        onRefresh = {},
        onBack = {},
    )
}

@Composable
fun DiffsErrorPreview() {
    DiffsStudio(
        files = emptyList(),
        loading = false,
        error = "O host respondeu 501: a IDE não está ligada.",
        notice = null,
        busy = false,
        onFile = {},
        onCommit = {},
        onPr = {},
        onRefresh = {},
        onBack = {},
    )
}

@Composable
fun CommitDialogPreview() {
    Box(Modifier.fillMaxSize()) {
        DiffsPreview()
        AlertDialog(
            onDismissRequest = {},
            containerColor = ObsidianCard,
            title = { Text("Commit") },
            text = {
                OutlinedTextField(
                    value = "Ajusta o contraste da pílula principal",
                    onValueChange = {},
                    label = { Text("Mensagem") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = {}) { Text("Commit", color = BrandGold) } },
            dismissButton = { TextButton(onClick = {}) { Text("Cancelar") } },
        )
    }
}

@Composable
fun PrDialogPreview() {
    Box(Modifier.fillMaxSize()) {
        DiffsPreview()
        AlertDialog(
            onDismissRequest = {},
            containerColor = ObsidianCard,
            title = { Text("Abrir pull request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = "Contraste do onboarding", onValueChange = {}, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = "Texto escuro na pílula clara.", onValueChange = {}, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {}) { Text("Abrir", color = BrandGold) } },
            dismissButton = { TextButton(onClick = {}) { Text("Cancelar") } },
        )
    }
}

private fun sampleDiffs() = listOf(
    FileDiff(
        path = "ui/EntryChrome.kt",
        status = "M",
        added = 12,
        removed = 3,
        lines = listOf(
            DiffLine("hunk", "@@ função PillButton @@"),
            DiffLine("context", "        ) {"),
            DiffLine("removed", "-            Text(text)"),
            DiffLine("added", "+            Text(text, color = foreground)"),
            DiffLine("context", "        }"),
        ),
    ),
    FileDiff(path = "README.md", status = "M", added = 2, removed = 1),
)
