package dev.hivemind.mobile.features.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.SteelBlue
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.ui.ErrorBanner
import dev.hivemind.mobile.ui.HiveCard
import dev.hivemind.mobile.ui.StatusPill
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

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
                if (dev.hivemind.mobile.core.relay.LinkCodes.parse(raw) != null) {
                    error = "Esse QR é de acesso remoto (hivemind://link). Entre na conta do relay e vincule a IDE por lá."
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onBack != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
                Text("Pareamento LAN", style = MaterialTheme.typography.titleMedium, color = TextDim)
            }
            Spacer(Modifier.height(8.dp))
        }
        StatusPill(text = "Rede local · LAN / Tailscale", color = SteelBlue)
        Spacer(Modifier.height(12.dp))
        Text(
            "HiveMind Mobile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Continue e comande os trabalhos do HiveMind pelo celular.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        HiveCard(contentPadding = PaddingValues(18.dp)) {
            Button(
                onClick = { scanning = true },
                colors = ButtonDefaults.buttonColors(containerColor = SteelBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Escanear QR Code da IDE", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showManual = !showManual },
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
            ) {
                Text(if (showManual) "Ocultar código colado" else "Colar código da IDE")
            }

            if (showManual) {
                Spacer(Modifier.height(14.dp))
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SteelBlue,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface,
                )
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("Código hivemind://pair da IDE") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        when (val parsed = dev.hivemind.mobile.core.api.PairingParser.parse(pasted)) {
                            is PairQr.Offer -> {
                                error = null
                                finishPairing(container, parsed, onState = { connecting = it }, onError = { error = it })
                            }
                            PairQr.RejectedToken -> error = LanMessages.TOKEN_QR
                            PairQr.Invalid -> error = LanMessages.BAD_QR
                        }
                    },
                    enabled = !connecting,
                    colors = ButtonDefaults.buttonColors(containerColor = SteelBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Conectar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        val banner = error ?: notice
        if (banner != null) {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(message = banner)
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

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (!hasCamera) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Permissão de câmera necessária para escanear o QR.", color = androidx.compose.ui.graphics.Color.White)
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
                .padding(top = 12.dp)
                .fillMaxWidth(),
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
        Text(
            hint,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .background(androidx.compose.ui.graphics.Color(0x99000000)),
        )
    }
}
