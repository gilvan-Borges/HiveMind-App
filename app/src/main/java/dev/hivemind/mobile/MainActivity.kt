package dev.hivemind.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.hivemind.mobile.features.chat.ChatScreen
import dev.hivemind.mobile.features.diffs.DiffsScreen
import dev.hivemind.mobile.features.flow.FlowScreen
import dev.hivemind.mobile.features.pairing.PairingScreen
import dev.hivemind.mobile.features.projects.ProjectsScreen
import dev.hivemind.mobile.theme.HiveMindTheme

enum class MainTab(val label: String, val icon: ImageVector) {
    Projects("Projetos", Icons.Filled.Home),
    Chat("Chat", Icons.Filled.Send),
    Flow("Fluxo", Icons.Filled.Build),
    Diffs("Diffs", Icons.Filled.List),
}

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as HiveMindApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HiveMindTheme {
                val pairing by container.hubState.pairing.collectAsState()
                if (pairing == null) {
                    PairingScreen(container)
                } else {
                    MainScaffold(container)
                }
            }
        }
    }
}

@Composable
fun MainScaffold(container: AppContainer) {
    var tab by remember { mutableStateOf(MainTab.Projects) }
    val workspace by container.hubState.selectedWorkspace.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.Projects -> ProjectsScreen(container, padding)
            MainTab.Chat -> ChatScreen(container, padding)
            MainTab.Flow -> FlowScreen(container, padding, workspace)
            MainTab.Diffs -> DiffsScreen(container, padding, workspace)
        }
    }
}
