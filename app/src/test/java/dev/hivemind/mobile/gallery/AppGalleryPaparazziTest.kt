package dev.hivemind.mobile.gallery

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.hivemind.mobile.features.chat.ChatApprovalPreview
import dev.hivemind.mobile.features.chat.ChatEmptyPreview
import dev.hivemind.mobile.features.chat.ChatErrorPreview
import dev.hivemind.mobile.features.chat.ChatThreadPreview
import dev.hivemind.mobile.features.diffs.CommitDialogPreview
import dev.hivemind.mobile.features.diffs.DiffFilePreview
import dev.hivemind.mobile.features.diffs.DiffsEmptyPreview
import dev.hivemind.mobile.features.diffs.DiffsErrorPreview
import dev.hivemind.mobile.features.diffs.DiffsPreview
import dev.hivemind.mobile.features.diffs.PrDialogPreview
import dev.hivemind.mobile.features.flow.FlowEmptyPreview
import dev.hivemind.mobile.features.flow.FlowErrorPreview
import dev.hivemind.mobile.features.flow.FlowPrsPreview
import dev.hivemind.mobile.features.flow.FlowTasksPreview
import dev.hivemind.mobile.features.inbox.ConversationsEmptyPreview
import dev.hivemind.mobile.features.inbox.ConversationsInboxPreview
import dev.hivemind.mobile.features.inbox.KindDialogPreview
import dev.hivemind.mobile.features.inbox.ProjectsEmptyPreview
import dev.hivemind.mobile.features.inbox.ProjectsErrorPreview
import dev.hivemind.mobile.features.inbox.ProjectsInboxPreview
import dev.hivemind.mobile.features.inbox.ProjectsLoadingPreview
import dev.hivemind.mobile.features.inbox.ProjectsOfflinePreview
import dev.hivemind.mobile.features.inbox.ProjectsSearchPreview
import dev.hivemind.mobile.features.pairing.LanPairingPastePreview
import dev.hivemind.mobile.features.pairing.LanPairingPreview
import dev.hivemind.mobile.features.pairing.QrPermissionPreview
import dev.hivemind.mobile.features.pairing.QrScannerPreview
import dev.hivemind.mobile.features.projects.ManageProjectsEmptyPreview
import dev.hivemind.mobile.features.projects.ManageProjectsPreview
import dev.hivemind.mobile.features.projects.UnpairDialogPreview
import dev.hivemind.mobile.features.relay.DevicesEmptyPreview
import dev.hivemind.mobile.features.relay.DevicesErrorPreview
import dev.hivemind.mobile.features.relay.DevicesPreview
import dev.hivemind.mobile.features.relay.GateAccountsPreview
import dev.hivemind.mobile.features.relay.GateHomePreview
import dev.hivemind.mobile.features.relay.LinkIdePreview
import dev.hivemind.mobile.features.relay.RelayErrorPreview
import dev.hivemind.mobile.features.relay.RelayLoginEmailPreview
import dev.hivemind.mobile.features.relay.RelayLoginPreview
import dev.hivemind.mobile.features.settings.SettingsPermissionDialogPreview
import dev.hivemind.mobile.features.settings.SettingsPreview
import dev.hivemind.mobile.features.settings.SettingsSwitchDialogPreview
import dev.hivemind.mobile.theme.HiveMindTheme
import org.junit.Rule
import org.junit.Test

class AppGalleryPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    @Test
    fun todas() {
        snap("tela-portao") { GateHomePreview() }
        snap("tela-portao-contas") { GateAccountsPreview() }
        snap("tela-relay") { RelayLoginPreview() }
        snap("tela-relay-email") { RelayLoginEmailPreview() }
        snap("tela-relay-erro") { RelayErrorPreview() }
        snap("tela-pareamento") { LanPairingPreview() }
        snap("tela-pareamento-colar") { LanPairingPastePreview() }
        snap("tela-scanner") { QrScannerPreview() }
        snap("tela-scanner-permissao") { QrPermissionPreview() }
        snap("tela-projetos") { ProjectsInboxPreview() }
        snap("tela-projetos-vazio") { ProjectsEmptyPreview() }
        snap("tela-projetos-erro") { ProjectsErrorPreview() }
        snap("tela-projetos-busca") { ProjectsSearchPreview() }
        snap("tela-projetos-offline") { ProjectsOfflinePreview() }
        snap("tela-projetos-carregando") { ProjectsLoadingPreview() }
        snap("tela-conversas") { ConversationsInboxPreview() }
        snap("tela-conversas-vazio") { ConversationsEmptyPreview() }
        snap("tela-nova-conversa") { KindDialogPreview() }
        snap("tela-chat") { ChatThreadPreview() }
        snap("tela-chat-vazio") { ChatEmptyPreview() }
        snap("tela-chat-erro") { ChatErrorPreview() }
        snap("tela-chat-aprovacao") { ChatApprovalPreview() }
        snap("tela-config") { SettingsPreview() }
        snap("tela-trocar-ide") { SettingsSwitchDialogPreview() }
        snap("tela-permissao") { SettingsPermissionDialogPreview() }
        snap("tela-diffs") { DiffsPreview() }
        snap("tela-diff-arquivo") { DiffFilePreview() }
        snap("tela-diffs-vazio") { DiffsEmptyPreview() }
        snap("tela-diffs-erro") { DiffsErrorPreview() }
        snap("tela-commit") { CommitDialogPreview() }
        snap("tela-pr") { PrDialogPreview() }
        snap("tela-fluxo") { FlowTasksPreview() }
        snap("tela-fluxo-vazio") { FlowEmptyPreview() }
        snap("tela-fluxo-erro") { FlowErrorPreview() }
        snap("tela-fluxo-prs") { FlowPrsPreview() }
        snap("tela-ides") { DevicesPreview() }
        snap("tela-ides-vazio") { DevicesEmptyPreview() }
        snap("tela-ides-erro") { DevicesErrorPreview() }
        snap("tela-vincular") { LinkIdePreview() }
        snap("tela-gerenciar") { ManageProjectsPreview() }
        snap("tela-gerenciar-vazio") { ManageProjectsEmptyPreview() }
        snap("tela-desconectar") { UnpairDialogPreview() }
    }

    private fun snap(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) { HiveMindTheme { content() } }
    }
}
