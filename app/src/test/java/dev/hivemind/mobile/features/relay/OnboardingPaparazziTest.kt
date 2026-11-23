package dev.hivemind.mobile.features.relay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.hivemind.mobile.features.pairing.LanPairingPreview
import dev.hivemind.mobile.theme.HiveMindTheme
import dev.hivemind.mobile.theme.TextDim
import org.junit.Rule
import org.junit.Test

class OnboardingPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    @Test
    fun depoisPortao() {
        paparazzi.snapshot(name = "depois-portao") { HiveMindTheme { GateHomePreview() } }
    }

    @Test
    fun depoisRelay() {
        paparazzi.snapshot(name = "depois-relay") { HiveMindTheme { RelayLoginPreview() } }
    }

    @Test
    fun depoisRelayEmail() {
        paparazzi.snapshot(name = "depois-relay-email") { HiveMindTheme { RelayLoginEmailPreview() } }
    }

    @Test
    fun depoisPareamento() {
        paparazzi.snapshot(name = "depois-pareamento") { HiveMindTheme { LanPairingPreview() } }
    }

    @Test
    fun antesPortao() {
        paparazzi.snapshot(name = "antes-portao") {
            HiveMindTheme {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        "HiveMind Mobile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Text(
                        "Comande sua equipe de agentes pela rede local ou por um relay próprio.",
                        color = TextDim,
                    )
                }
            }
        }
    }
}
