package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

@Composable
fun QrPairingDialog(
    localIp: String,
    deviceName: String,
    onPairScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: Live Camera, 1: Show QR, 2: Manual
    var rawInputText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val localId = "gd_id_${deviceName.hashCode().coerceAtMost(9999)}"

    val pairingString = "groupdrop://pair?ip=$localIp&port=8989&name=${Uri.encode(deviceName)}&id=$localId"

    val qrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val decoded = com.example.utils.QrCodeGenerator.decodeQrFromBitmap(bitmap)
                    if (decoded != null) {
                        onPairScanned(decoded)
                        android.widget.Toast.makeText(context, "QR code scanned from image successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        android.widget.Toast.makeText(context, "Could not find a valid QR Code in selected image.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error reading image file", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(pairingString) {
        if (localIp != "127.0.0.1") {
            qrBitmap = com.example.utils.QrCodeGenerator.generateQrBitmap(pairingString, 450, 450)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("QR Instant Pairing Hub", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("🎥 Scan QR", "📱 My Pass", "✍️ Manual").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (activeTab == index) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { activeTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                when (activeTab) {
                    0 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Point this camera scanner lens at another device's pairing QR code to pair immediately.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                LiveCameraPreview(
                                    onQrCodeScanned = { result ->
                                        onPairScanned(result)
                                        onDismiss()
                                    }
                                )

                                val infiniteTransition = rememberInfiniteTransition(label = "LaserSwipe")
                                val animationProgress by infiniteTransition.animateFloat(
                                    initialValue = 0.05f,
                                    targetValue = 0.95f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "LaserSwipeProgress"
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val verticalY = size.height * animationProgress
                                    drawLine(
                                        color = Color(0xFF00FFCC),
                                        start = Offset(0f, verticalY),
                                        end = Offset(size.width, verticalY),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { qrImagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Collections,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick Image", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val randomIp = "192.168.1.${(10..220).random()}"
                                        val randomId = "paired_node_${(1000..9999).random()}"
                                        val names = listOf(
                                            "Pro Laptop 16",
                                            "S24 Ultra Hub",
                                            "iPad Pro Airspace",
                                            "Desktop Workstation",
                                            "Ubuntu Server"
                                        )
                                        val simUrl = "groupdrop://pair?ip=$randomIp&port=8989&name=${Uri.encode(names.random())}&id=$randomId"
                                        onPairScanned(simUrl)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Simulate", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    1 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Show this QR code to another device to let them scan and add you in 1 second.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (localIp == "127.0.0.1") {
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Connect to Wi-Fi\nto display QR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (qrBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "My Pairing QR Code",
                                    modifier = Modifier
                                        .size(180.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                )
                            }

                            Text(
                                text = "My Pair Key Address: $localIp",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    2 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Directly paste or input another device's GroupDrop pair key connection schema to register immediately.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = rawInputText,
                                onValueChange = { rawInputText = it },
                                placeholder = { Text("Paste connection URL here...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Pair Connection String") }
                            )

                            Button(
                                onClick = {
                                    if (rawInputText.isNotBlank()) {
                                        onPairScanned(rawInputText.trim())
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Bind Connection Key")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun LiveCameraPreview(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var cameraState by remember { mutableStateOf("loading") } // "loading", "active", "permission_denied", "error"
    var errorMessage by remember { mutableStateOf("") }
    var activeCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var scanSuccessful by remember { mutableStateOf(false) }

    DisposableEffect(activeCameraProvider) {
        onDispose {
            try {
                activeCameraProvider?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            cameraState = "permission_denied"
                            return@addListener
                        }

                        val provider = cameraProviderFuture.get()
                        activeCameraProvider = provider

                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor, QrCodeAnalyzer { result ->
                            if (!scanSuccessful) {
                                scanSuccessful = true
                                try {
                                    provider.unbindAll()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onQrCodeScanned(result)
                            }
                        })

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        if (provider.hasCamera(cameraSelector)) {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            cameraState = "active"
                        } else {
                            errorMessage = "No hardware back camera detected on this device."
                            cameraState = "error"
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        errorMessage = t.localizedMessage ?: "Failed to initialize Camera"
                        cameraState = "error"
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        if (cameraState != "active") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    val icon = when (cameraState) {
                        "permission_denied" -> Icons.Default.Cancel
                        "error" -> Icons.Default.Info
                        else -> Icons.Default.CameraAlt
                    }

                    val titleText = when (cameraState) {
                        "permission_denied" -> "Permission Denied"
                        "error" -> "Camera Error"
                        else -> "Initializing Camera..."
                    }

                    val descText = when (cameraState) {
                        "permission_denied" -> "Camera hardware access is required to scan QR codes. Please use Manual / Image tabs, or grant permissions."
                        "error" -> errorMessage
                        else -> "Powering up the lens and sensor module..."
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = "Camera Stream Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )

                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = descText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
class QrCodeAnalyzer(private val onQrCodeScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        setHints(hints)
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val source = PlanarYUVLuminanceSource(
                bytes,
                imageProxy.width,
                imageProxy.height,
                0,
                0,
                imageProxy.width,
                imageProxy.height,
                false
            )
            val binarizer = HybridBinarizer(source)
            val binaryBitmap = BinaryBitmap(binarizer)

            val result = reader.decode(binaryBitmap)
            val scannedText = result.text
            if (!scannedText.isNullOrBlank()) {
                onQrCodeScanned(scannedText)
            }
        } catch (e: Exception) {
            // Expected during scanning search frames
        } finally {
            imageProxy.close()
        }
    }
}
