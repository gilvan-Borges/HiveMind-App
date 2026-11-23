package dev.hivemind.mobile.features.pairing

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.core.api.PairQr
import dev.hivemind.mobile.core.lan.LanMessages
import dev.hivemind.mobile.theme.TextPrimary
import dev.hivemind.mobile.ui.EntryBackHeader
import dev.hivemind.mobile.ui.EntryScaffold
import dev.hivemind.mobile.ui.EntryStep
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.IconField
import dev.hivemind.mobile.ui.PillButton
import dev.hivemind.mobile.ui.QrHero
import dev.hivemind.mobile.ui.QuietLink
import dev.hivemind.mobile.ui.StaticEntry
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun LanPairingPreview() {
    StaticEntry {
        LanPairingContent(
            onBack = {},
            showManual = false,
            pasted = "",
            connecting = false,
            error = null,
            onScan = {},
            onToggleManual = {},
            onPasted = {},
            onConnect = {},
        )
    }
}

@Composable
fun LanPairingPastePreview() {
    StaticEntry {
        LanPairingContent(
            onBack = {},
            showManual = true,
            pasted = "hivemind://pair?v=2&h=lab.local",
            connecting = false,
            error = null,
            onScan = {},
            onToggleManual = {},
            onPasted = {},
            onConnect = {},
        )
    }
}

@Composable
fun QrScannerPreview() {
    Box(Modifier.fillMaxSize().background(dev.hivemind.mobile.theme.ObsidianBg)) {
        Box(Modifier.align(Alignment.Center)) {
            dev.hivemind.mobile.ui.QrHero()
        }
        Row(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, end = 8.dp).fillMaxWidth(),
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = TextPrimary)
            }
        }
        Text(
            "Aponte para o QR exibido na IDE (modal App Mobile)",
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        )
    }
}

@Composable
fun QrPermissionPreview() {
    Box(Modifier.fillMaxSize().background(dev.hivemind.mobile.theme.ObsidianBg)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Permissão de câmera necessária para escanear o QR.", color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {}) { Text("Permitir câmera") }
        }
    }
}

/** Tela de pareamento LAN: QR `hivemind://pair?v=2` ou colar o mesmo código. */
@Composable
fun PairingScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val notice by container.lanNotice.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (scanning) {
        QrScannerOverlay(
            onResult = { raw ->
                scanning = false
                val qrLogin = dev.hivemind.mobile.core.relay.LinkCodes.parseQrLogin(raw)
                if (qrLogin != null) {
                    connecting = true
                    error = null
                    container.appScope.launch {
                        runCatching { container.relay.redeemQrLogin(qrLogin.first, qrLogin.second) }
                            .onFailure { error = it.message ?: "Falha ao entrar pelo QR Code remoto." }
                        connecting = false
                    }
                } else if (dev.hivemind.mobile.core.relay.LinkCodes.parse(raw) != null) {
                    error = "Esse QR é de vínculo (hivemind://link). Na IDE clique em 'Gerar QR Code para entrar no celular' para fazer login direto."
                } else {
                    when (val parsed = dev.hivemind.mobile.core.api.PairingParser.parse(raw)) {
                        is PairQr.Offer -> {
                            error = null
                            finishPairing(container, parsed, onState = { connecting = it }, onError = { error = it })
                        }
                        PairQr.RejectedToken -> error = LanMessages.TOKEN_QR
                        PairQr.Invalid -> error = LanMessages.BAD_QR
                    }
                }
            },
            onClose = { scanning = false },
        )
        return
    }

    LanPairingContent(
        onBack = onBack,
        showManual = showManual,
        pasted = pasted,
        connecting = connecting,
        error = error ?: notice,
        onScan = { scanning = true },
        onToggleManual = { showManual = !showManual },
        onPasted = { pasted = it },
        onConnect = {
            when (val parsed = dev.hivemind.mobile.core.api.PairingParser.parse(pasted)) {
                is PairQr.Offer -> {
                    error = null
                    finishPairing(container, parsed, onState = { connecting = it }, onError = { error = it })
                }
                PairQr.RejectedToken -> error = LanMessages.TOKEN_QR
                PairQr.Invalid -> error = LanMessages.BAD_QR
            }
        },
    )
}

@Composable
private fun LanPairingContent(
    onBack: (() -> Unit)?,
    showManual: Boolean,
    pasted: String,
    connecting: Boolean,
    error: String?,
    onScan: () -> Unit,
    onToggleManual: () -> Unit,
    onPasted: (String) -> Unit,
    onConnect: () -> Unit,
) {
    EntryScaffold(
        footer = {
            if (!error.isNullOrBlank()) {
                ErrorBanner(message = error)
            }
            if (showManual) {
                PillButton(text = "Conectar", onClick = onConnect, enabled = !connecting, busy = connecting)
                QuietLink(text = "Escanear QR da IDE", onClick = onScan, enabled = !connecting)
            } else {
                PillButton(text = "Escanear QR da IDE", onClick = onScan, enabled = !connecting, busy = connecting)
                QuietLink(text = "Colar código", onClick = onToggleManual, enabled = !connecting)
            }
        },
    ) {
        if (onBack != null) {
            EntryBackHeader(title = "LAN", onBack = onBack)
        }
        QrHero()
        Text(
            "Parear na rede local",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "O celular lê o QR que a IDE mostra. Os dois ficam na mesma rede.",
            style = MaterialTheme.typography.bodyMedium,
            color = dev.hivemind.mobile.theme.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        EntryStep(index = 1, title = "Abra a IDE", body = "No computador, com o HiveMind aberto.")
        EntryStep(index = 2, title = "Config > Celular", body = "Gere o QR de pareamento da rede local.")
        EntryStep(index = 3, title = "Aponte a câmera", body = "O app conecta assim que ler o código.")
        if (showManual) {
            Spacer(Modifier.height(12.dp))
            IconField(
                value = pasted,
                onValueChange = onPasted,
                label = "Código hivemind://pair",
                icon = Icons.Outlined.ContentPaste,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
    }
}

/** Resgata a oferta v2 na primeira URL que responder. */
private fun finishPairing(
    container: AppContainer,
    offer: PairQr.Offer,
    onState: (Boolean) -> Unit,
    onError: (String?) -> Unit,
) {
    container.appScope.launch {
        onState(true)
        runCatching { container.pair(offer) }
            .onFailure { onError(it.message ?: LanMessages.BAD_OFFER) }
        onState(false)
    }
}

/** Overlay de câmera com detecção de QR Code (CameraX + ML Kit). */
// @OptIn cobre o compilador; o UnsafeOptInUsageError do lint não rastreia
// @OptIn Kotlin através das lambdas aninhadas do CameraX, então o checker
// é silenciado especificamente para o ImageProxy.image.
@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrScannerOverlay(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    hint: String = "Aponte para o QR exibido na IDE (modal App Mobile)",
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasCamera = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(dev.hivemind.mobile.theme.ObsidianBg)) {
        if (!hasCamera) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Permissão de câmera necessária para escanear o QR.", color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir câmera")
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        runCatching {
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val input = InputImage.fromMediaImage(
                                        mediaImage, imageProxy.imageInfo.rotationDegrees,
                                    )
                                    scanner.process(input)
                                        .addOnSuccessListener { barcodes ->
                                            val value = barcodes.firstOrNull()?.rawValue
                                            if (value != null && !handled) {
                                                handled = true
                                                onResult(value)
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp)
                .fillMaxWidth(),
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = TextPrimary)
            }
        }
        Text(
            hint,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .background(dev.hivemind.mobile.theme.ObsidianBg.copy(alpha = 0.72f)),
        )
    }
}
