package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.Peer
import com.example.network.GroupDropManager
import com.example.ui.components.BrandHeader
import com.example.ui.components.QuickActionCard
import com.example.ui.components.QrPairingDialog
import com.example.ui.viewmodel.GroupDropViewModel
import java.io.File

@Composable
fun SharingTab(
    viewModel: GroupDropViewModel,
    onTransferClick: (GroupDropManager.ActiveTransfer) -> Unit
) {
    val context = LocalContext.current
    val isSharing by viewModel.isSharing.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val clipManager = LocalClipboardManager.current

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputVal by remember { mutableStateOf(deviceName) }
    var showQrPairingDialog by remember { mutableStateOf(false) }

    val tempPhotoUri = remember { mutableStateOf<Uri?>(null) }
    val tempVideoUri = remember { mutableStateOf<Uri?>(null) }

    fun generateCacheUri(isVideo: Boolean): Uri? {
        val extension = if (isVideo) "mp4" else "jpg"
        val prefix = if (isVideo) "vid_temp_" else "img_temp_"
        return try {
            val file = File.createTempFile(prefix, ".$extension", context.cacheDir)
            FileProvider.getUriForFile(
                context,
                "com.example.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri.value?.let { uri ->
                viewModel.handleCapturedMedia(uri, isVideo = false)
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            tempVideoUri.value?.let { uri ->
                viewModel.handleCapturedMedia(uri, isVideo = true)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.sendPickedFile(it)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandHeader(
                title = "GroupDrop",
                showAction = true,
                onActionClick = { viewModel.refreshIp() }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = deviceName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename device name",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            renameInputVal = deviceName
                                            showRenameDialog = true
                                        }
                                )
                            }
                            Text(
                                text = "Device Identity",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                letterSpacing = 0.5.sp
                            )
                        }

                        val signalTransition = rememberInfiniteTransition(label = "SignalTransition")
                        val signalPulseAlpha by signalTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "SignalPulse"
                        )

                        val statusColor = if (isSharing) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        val statusText = if (isSharing) "DISCOVERABLE" else "OFFLINE"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = signalPulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "Connection Status Indicator",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (localIp != "127.0.0.1") MaterialTheme.colorScheme.primary else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (localIp == "127.0.0.1") "Not Connected" else "Wi-Fi Hub Connected",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = if (localIp == "127.0.0.1") "Connect to same Wi-Fi/Hotspot" else "Local IP: $localIp",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.clickable {
                                    if (localIp != "127.0.0.1") {
                                        clipManager.setText(AnnotatedString(localIp))
                                    }
                                }
                            )
                        }

                        Text(
                            text = "REFRESH IP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.refreshIp() }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.startSharingService() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !isSharing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Sharing", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.stopSharingService() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = isSharing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Sharing", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showQrPairingDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeviceHub,
                                contentDescription = "QR Pairing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("QR Code Instant Pairing Hub", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("Scan or show device QR code to pair in 1s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
                        }
                    }
                    Text("OPEN HUB", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "QUICK ACTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        circleBg = Color(0xFFEFF6FF),
                        iconColor = Color(0xFF2563EB),
                        onClick = {
                            val uri = generateCacheUri(isVideo = false)
                            if (uri != null) {
                                tempPhotoUri.value = uri
                                cameraLauncher.launch(uri)
                            }
                        }
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Videocam,
                        label = "Media",
                        circleBg = Color(0xFFFFF7ED),
                        iconColor = Color(0xFFEA580C),
                        onClick = {
                            val uri = generateCacheUri(isVideo = true)
                            if (uri != null) {
                                tempVideoUri.value = uri
                                videoLauncher.launch(uri)
                            }
                        }
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FolderOpen,
                        label = "Files",
                        circleBg = Color(0xFFF0FDF4),
                        iconColor = Color(0xFF16A34A),
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
            }
        }

        if (activeTransfers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phonelink, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Active Transfers",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            items(activeTransfers) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onTransferClick(item) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val transferIcon = if (item.isOutgoing) Icons.Default.Share else Icons.Default.Save
                                Icon(
                                    imageVector = transferIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = item.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (item.isOutgoing) "Sending to: ${item.peerName}" else "Receiving from: ${item.peerName}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val pct = (item.progress * 100).toInt()
                            val progressColor = when (item.status) {
                                "COMPLETED", "SENT", "RECEIVED" -> Color(0xFF4CAF50)
                                "FAILED" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Text(
                                text = when (item.status) {
                                    "COMPLETED", "SENT", "RECEIVED" -> "DONE"
                                    "FAILED" -> "FAILED"
                                    else -> "$pct%"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = progressColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(progressColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (item.status == "FAILED") Color(0xFFF44336) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            NetworkDiagnosticsPanel(localIp = localIp, context = context)
        }

        item {
            var selectedPeerForSend by remember { mutableStateOf<Peer?>(null) }
            val dynamicFilePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    selectedPeerForSend?.let { p ->
                        viewModel.sendFileToSpecificPeer(p, it)
                    }
                }
            }

            RadarPeerTracker(
                peers = peers,
                isSharing = isSharing,
                onPeerClick = { peer ->
                    selectedPeerForSend = peer
                    dynamicFilePickerLauncher.launch("*/*")
                }
            )
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Update Device Name") },
            text = {
                Column {
                    Text("Change how this phone displays to other other GroupDrop users on this offline channel:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInputVal,
                        onValueChange = { renameInputVal = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. Pixel 8 Admin") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputVal.isNotBlank()) {
                            viewModel.updateDeviceName(renameInputVal.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQrPairingDialog) {
        QrPairingDialog(
            localIp = localIp,
            deviceName = deviceName,
            onPairScanned = { pairingUrl ->
                val ok = viewModel.connectToPairingUrl(pairingUrl)
                if (ok) {
                    android.widget.Toast.makeText(context, "Successfully paired devices via QR!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Invalid pairing format", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showQrPairingDialog = false }
        )
    }
}

@Composable
fun RadarPeerTracker(
    peers: List<Peer>,
    isSharing: Boolean,
    onPeerClick: (Peer) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = androidx.compose.animation.core.FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = androidx.compose.animation.core.FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PEER DISCOVERY RADAR",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radiusList = listOf(size.minDimension / 2 * 0.35f, size.minDimension / 2 * 0.65f, size.minDimension / 2 * 0.95f)
                    
                    if (isSharing) {
                        drawCircle(
                            color = primaryColor,
                            radius = size.minDimension / 2 * pulseScale,
                            style = Stroke(width = 2.dp.toPx()),
                            alpha = pulseAlpha
                        )
                    }

                    radiusList.forEach { r ->
                        drawCircle(
                            color = outlineColor,
                            radius = r,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = outlineColor,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 1.dp.toPx()
                    )

                    if (isSharing) {
                        rotate(rotationAngle) {
                            val sweepBrush = Brush.sweepGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.5f),
                                    primaryColor.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                center = center
                            )
                            drawCircle(
                                brush = sweepBrush,
                                radius = size.minDimension / 2 * 0.95f
                            )
                            drawLine(
                                color = primaryColor,
                                start = center,
                                end = Offset(size.width / 2, size.height / 2 - (size.minDimension / 2 * 0.95f)),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Me",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isSharing && peers.isNotEmpty()) {
                    peers.forEachIndexed { index, peer ->
                        val angleDegrees = (index * (360f / peers.size.coerceAtLeast(1)) + 15f) % 360f
                        val angleRadians = Math.toRadians(angleDegrees.toDouble())
                        val radiusFactor = if (index % 2 == 0) 0.65f else 0.85f
                        val radiusPx = (240.dp / 2 * radiusFactor).value
                        val xOffset = (radiusPx * Math.cos(angleRadians)).dp
                        val yOffset = (radiusPx * Math.sin(angleRadians)).dp

                        val peerIcon = when {
                            peer.name.contains("Laptop", ignoreCase = true) || peer.name.contains("Desktop", ignoreCase = true) -> Icons.Default.Laptop
                            peer.name.contains("Tablet", ignoreCase = true) || peer.name.contains("iPad", ignoreCase = true) -> Icons.Default.Tablet
                            else -> Icons.Default.Smartphone
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = xOffset, y = yOffset)
                                .clickable { onPeerClick(peer) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = peerIcon,
                                    contentDescription = peer.name,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = peer.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (!isSharing) {
                Text(
                    text = "Discovery Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else if (peers.isEmpty()) {
                Text(
                    text = "Airspace clear - waiting for nodes...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "Tap peripheral device node to target-send details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
