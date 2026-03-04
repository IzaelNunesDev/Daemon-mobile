package com.example.daemonmobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.daemonmobile.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

enum class PairingMode {
    WELCOME, QR_SCAN, MANUAL_INPUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onPaired: (String, Int, String) -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(PairingMode.WELCOME) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var pairedHost by remember { mutableStateOf("") }

    // Manual input fields
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("3030") }
    var manualSecret by remember { mutableStateOf("") }

    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("✅ Pareamento Bem-Sucedido!", color = StatusSuccess) },
            text = {
                Text(
                    "Conectado ao Daemon em $pairedHost.\nVocê será redirecionado para o chat.",
                    color = TextBright
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                ) {
                    Text("Continuar", color = BgPrimary)
                }
            },
            containerColor = SurfaceLight
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SLED Remote", color = TextBright, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary),
                navigationIcon = {
                    if (mode != PairingMode.WELCOME) {
                        IconButton(onClick = { mode = PairingMode.WELCOME }) {
                            Text("←", color = TextBright, fontSize = 20.sp)
                        }
                    }
                }
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (mode) {
                PairingMode.WELCOME -> WelcomeContent(
                    onScanQR = { mode = PairingMode.QR_SCAN },
                    onManualInput = { mode = PairingMode.MANUAL_INPUT }
                )
                PairingMode.QR_SCAN -> QrScanContent(
                    onPaired = { host, port, secret ->
                        pairedHost = "$host:$port"
                        showSuccessDialog = true
                        Toast.makeText(context, "✅ Pareado com $host:$port", Toast.LENGTH_LONG).show()
                        onPaired(host, port, secret)
                    }
                )
                PairingMode.MANUAL_INPUT -> ManualInputContent(
                    host = manualHost,
                    onHostChange = { manualHost = it },
                    port = manualPort,
                    onPortChange = { manualPort = it },
                    secret = manualSecret,
                    onSecretChange = { manualSecret = it },
                    onConnect = {
                        val portInt = manualPort.toIntOrNull() ?: 3030
                        if (manualHost.isNotBlank() && manualSecret.isNotBlank()) {
                            pairedHost = "$manualHost:$portInt"
                            showSuccessDialog = true
                            Toast.makeText(context, "✅ Pareado com $manualHost:$portInt", Toast.LENGTH_LONG).show()
                            onPaired(manualHost, portInt, manualSecret)
                        } else {
                            Toast.makeText(context, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeContent(
    onScanQR: () -> Unit,
    onManualInput: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(AccentPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🔌", fontSize = 48.sp)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Conectar ao Daemon",
            color = TextBright,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Escolha como deseja parear com o SLED Daemon no seu PC",
            color = TextDim,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // QR Code Button
        Button(
            onClick = onScanQR,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("📷 Escanear QR Code", color = TextBright, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Manual input button
        OutlinedButton(
            onClick = onManualInput,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextBright)
        ) {
            Text("⌨️ Inserir Manualmente", color = TextBright, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Abra a aba Segurança no Desktop\npara obter o QR Code",
            color = TextDim,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ManualInputContent(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    secret: String,
    onSecretChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Conexão Manual",
            color = TextBright,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Insira os dados de conexão do Daemon",
            color = TextDim,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("IP do Daemon", color = TextDim) },
            placeholder = { Text("192.168.1.100", color = TextDim.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceLight,
                unfocusedContainerColor = SurfaceLight,
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextBright,
                unfocusedTextColor = TextBright
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("Porta", color = TextDim) },
            placeholder = { Text("3030", color = TextDim.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceLight,
                unfocusedContainerColor = SurfaceLight,
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextBright,
                unfocusedTextColor = TextBright
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = secret,
            onValueChange = onSecretChange,
            label = { Text("Token Secreto", color = TextDim) },
            placeholder = { Text("uuid-do-pareamento", color = TextDim.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceLight,
                unfocusedContainerColor = SurfaceLight,
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextBright,
                unfocusedTextColor = TextBright
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("🔗 Conectar", color = BgPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QrScanContent(onPaired: (String, Int, String) -> Unit) {
    val context = LocalContext.current
    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCamPermission) {
            Box(modifier = Modifier.weight(1f)) {
                CameraPreview(onBarcodeScanned = { uri ->
                    parseSledUri(uri)?.let { (host, port, secret) ->
                        onPaired(host, port, secret)
                    }
                })
                
                // Overlay instruction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        colors = CardDefaults.cardColors(containerColor = BgSecondary.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "📷 Aponte para o QR Code no Desktop",
                            color = TextBright,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Permissão de câmera necessária.", color = TextDim)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Text("Conceder Permissão", color = TextBright)
                    }
                }
            }
        }
    }
}

private fun parseSledUri(uri: String): Triple<String, Int, String>? {
    if (!uri.startsWith("sled://")) return null
    try {
        val withoutScheme = uri.removePrefix("sled://")
        val parts = withoutScheme.split("?")
        if (parts.size != 2) return null
        val hostPort = parts[0].split(":")
        if (hostPort.size != 2) return null
        val host = hostPort[0]
        val port = hostPort[1].toInt()
        
        val queryParams = parts[1].split("&")
        var secret = ""
        for (param in queryParams) {
            val kv = param.split("=")
            if (kv.size == 2 && kv[0] == "secret") {
                secret = kv[1]
                break
            }
        }
        if (secret.isEmpty()) return null
        
        return Triple(host, port, secret)
    } catch (e: Exception) {
        Log.e("PairingScreen", "Failed to parse URI: $uri", e)
        return null
    }
}

@Composable
fun CameraPreview(onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isProcessing by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val executor = Executors.newSingleThreadExecutor()
                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    if (isProcessing) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    processImageProxy(imageProxy) { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                if (value.startsWith("sled://")) {
                                    isProcessing = true
                                    onBarcodeScanned(value)
                                }
                            }
                        }
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    onBarcodesDetected: (List<Barcode>) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                onBarcodesDetected(barcodes)
            }
            .addOnFailureListener {
                Log.e("CameraPreview", "Barcode scanning failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
