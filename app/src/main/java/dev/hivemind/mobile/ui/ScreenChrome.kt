package dev.hivemind.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.R
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.SurfaceInput
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.theme.TextPrimary

/** Conteúdo abaixo da status bar e acima da barra de navegação, com teclado. */
fun Modifier.edgeInsets(): Modifier = this
    .statusBarsPadding()
    .navigationBarsPadding()
    .imePadding()

@Composable
fun OnboardingHero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.hivemind_mark),
            contentDescription = null,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun hiveFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandGold,
    unfocusedBorderColor = ObsidianBorder,
    focusedContainerColor = SurfaceInput,
    unfocusedContainerColor = SurfaceInput,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = BrandGold,
    focusedLabelColor = BrandGold,
    unfocusedLabelColor = TextDim,
    focusedPlaceholderColor = TextDim,
    unfocusedPlaceholderColor = TextDim,
)

@Composable
fun HelpDisclosure(body: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(
            onClick = { open = !open },
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text(if (open) "Ocultar ajuda" else "Ajuda", color = TextDim)
        }
        if (open) {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
        }
    }
}
