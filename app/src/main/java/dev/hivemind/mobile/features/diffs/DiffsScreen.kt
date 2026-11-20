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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import dev.hivemind.mobile.core.models.FileDiff
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.SteelBlue
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
    onAskInChat: (String) -> Unit = {},
) {
    if (workspace == null) {
        EmptyStatePanel(
            title = "Nenhum projeto selecionado",
            subtitle = "Selecione um projeto na aba Projetos para inspecionar os diffs do workspace.",
            modifier = Modifier.padding(padding),
        )
        return
    }

    var files by remember { mutableStateOf<List<FileDiff>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FileDiff?>(null) }

    suspend fun load() {
        loading = true
        error = null
        runCatching { container.api.diffs(workspace) }
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

    val totalAdded = files.sumOf { it.added }
    val totalRemoved = files.sumOf { it.removed }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Surface(color = ObsidianCard) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Diffs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!loading && error == null && files.isNotEmpty()) {
                            Text(
                                "${files.size} arquivo(s) · +$totalAdded -$totalRemoved",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim,
                            )
                        }
                    }
                    IconButton(
                        onClick = { container.appScope.launch { load() } },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = TextDim)
                    }
                }
                HorizontalDivider(color = ObsidianBorder)
            }
        }
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                ErrorBanner(
                    message = error.orEmpty(),
                    actionLabel = "Tentar",
                    onAction = { container.appScope.launch { load() } },
                )
            }
            files.isEmpty() -> EmptyStatePanel(
                title = "Workspace limpo",
                subtitle = "Nenhuma alteração pendente de commit neste projeto.",
                actionLabel = "Atualizar",
                onAction = { container.appScope.launch { load() } },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.path }) { f ->
                    val (mark, markColor) = statusStyle(f.status)
                    HiveCard(
                        onClick = { selected = f },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            StatusPill(text = mark, color = markColor)
                            Text(
                                f.path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (f.added > 0) {
                                Text(
                                    "+${f.added}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (f.removed > 0) {
                                Text(
                                    "-${f.removed}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text("›", style = MaterialTheme.typography.titleMedium, color = TextDim)
                        }
                    }
                }
            }
        }
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
        Surface(color = ObsidianCard) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                    Text(
                        file.path,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (file.added > 0) {
                        Text(
                            "+${file.added}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (file.removed > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "-${file.removed}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                HorizontalDivider(color = ObsidianBorder)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(file.lines) { line ->
                val (bg, fg) = when (line.kind) {
                    "added" -> Color(0xFF0E2A1E) to Color(0xFF8CE0B3)
                    "removed" -> Color(0xFF2A1214) to Color(0xFFF3A8A8)
                    "hunk" -> Color(0xFF141C2C) to SteelBlue
                    "header" -> Color(0xFF10151E) to TextDim
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
                        fontFamily = FontFamily.Monospace,
                        color = fg,
                        softWrap = false,
                    )
                }
            }
        }

        Surface(color = ObsidianCard) {
            Column {
                HorizontalDivider(color = ObsidianBorder)
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Button(
                        onClick = onAskInChat,
                        colors = ButtonDefaults.buttonColors(containerColor = SteelBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                    ) {
                        Text("Ajustar este arquivo no Chat", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun statusStyle(status: String): Pair<String, Color> = when (status) {
    "A" -> "A" to SuccessGreen
    "D" -> "D" to ErrorRed
    "R" -> "R" to SteelBlue
    "U" -> "U" to ApprovalAmber
    "M" -> "M" to SteelBlue
    else -> "•" to TextDim
}
