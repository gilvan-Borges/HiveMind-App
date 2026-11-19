package dev.hivemind.mobile.features.diffs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.models.FileDiff
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.SteelBlue
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import kotlinx.coroutines.launch

@Composable
fun DiffsScreen(container: AppContainer, padding: PaddingValues, workspace: String?) {
    if (workspace == null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Selecione um projeto na aba Projetos.", color = TextDim)
        }
        return
    }

    var files by remember { mutableStateOf<List<FileDiff>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FileDiff?>(null) }

    suspend fun load() {
        loading = true
        runCatching { container.api.diffs(workspace) }
            .onSuccess { files = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val open = selected
    if (open != null) {
        DiffViewer(open) { selected = null }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Diffs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { container.appScope.launch { load() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
            }
        }
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = ErrorRed) }
            files.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma alteração no workspace.", color = TextDim)
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(files) { f ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selected = f },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(statusMark(f.status), style = MaterialTheme.typography.labelSmall, color = TextDim)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                f.path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(8.dp))
                            if (f.added > 0) {
                                Text("+${f.added}", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                            }
                            if (f.removed > 0) {
                                Spacer(Modifier.width(6.dp))
                                Text("-${f.removed}", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffViewer(file: FileDiff, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                file.path,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (file.added > 0) {
                Text("+${file.added}", style = MaterialTheme.typography.labelMedium, color = SuccessGreen)
            }
            if (file.removed > 0) {
                Spacer(Modifier.width(6.dp))
                Text("-${file.removed}", style = MaterialTheme.typography.labelMedium, color = ErrorRed)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = fg,
                    )
                }
            }
        }
    }
}

private fun statusMark(status: String): String = when (status) {
    "A" -> "A"
    "D" -> "D"
    "R" -> "R"
    "U" -> "U"
    "M" -> "M"
    else -> "•"
}
