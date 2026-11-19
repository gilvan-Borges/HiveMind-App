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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.hivemind.mobile.AppContainer
import dev.hivemind.mobile.theme.TextDim
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

/** Tela de pareamento: QR Code (câmera) ou modo manual (URL + Token). */
@Composable
fun PairingScreen(container: AppContainer) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (scanning) {
        QrScannerOverlay(
            onResult = { raw ->
                scanning = false
                val parsed = dev.hivemind.mobile.core.api.PairingParser.parse(raw)
                if (parsed == null) {
                    error = "QR inválido — aponte para o QR exibido na IDE (App Mobile)."
                } else {
                    error = null
                    finishPairing(container, parsed.first, parsed.second, parsed.third, onState = { connecting = it }, onError = { error = it })
                }
            },
            onClose = { scanning = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("HiveMind Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Continue e comande os trabalhos do HiveMind pelo celular.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
        )
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { scanning = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Escanear QR Code da IDE")
        }

        Spacer(Modifier.height(16.dp))
        Text("— ou pareamento manual —", style = MaterialTheme.typography.labelSmall, color = TextDim)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL do PC (ex.: http://100.x.x.x:47890)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Nome do PC (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val urls = url.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (urls.isEmpty() || token.isBlank()) {
                    error = "Informe a URL e o token."
                } else {
                    finishPairing(container, urls, token.trim(), host.trim(), onState = { connecting = it }, onError = { error = it })
                }
            },
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Conectar")
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Testa URLs em fallback (Tailscale → LAN) e ativa o pareamento. */
private fun finishPairing(
    container: AppContainer,
    urls: List<String>,
    token: String,
    host: String,
    onState: (Boolean) -> Unit,
    onError: (String?) -> Unit,
) {
    container.appScope.launch {
        onState(true)
        val active = container.pickActiveUrl(urls, token)
        if (active == null) {
            onState(false)
            val detail = container.lastPickError?.let { " — $it" } ?: ""
            onError("Nenhuma URL respondeu (verifique Tailscale/Wi-Fi e o Bridge na IDE)$detail")
            return@launch
        }
        val pairing = dev.hivemind.mobile.core.store.Pairing(urls, token, host, active)
        container.pairingStore.save(urls, token, host, active)
        container.activate(pairing)
        onState(false)
        onError(null)
    }
}

/** Overlay de câmera com detecção de QR Code (CameraX + ML Kit). */
@Composable
fun QrScannerOverlay(onResult: (String) -> Unit, onClose: () -> Unit) {
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
            "Aponte para o QR exibido na IDE (modal App Mobile)",
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .background(androidx.compose.ui.graphics.Color(0x99000000)),
        )
    }
}
