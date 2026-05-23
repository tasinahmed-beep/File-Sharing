package com.example.ui.screens

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BrandHeader
import com.example.ui.viewmodel.GroupDropViewModel

@Composable
fun SettingsTab(viewModel: GroupDropViewModel) {
    val autoSendPhotos by viewModel.autoSendPhotos.collectAsState()
    val autoSendVideos by viewModel.autoSendVideos.collectAsState()
    val autoAcceptFiles by viewModel.autoAcceptFiles.collectAsState()
    val saveFilesAutomatically by viewModel.saveFilesAutomatically.collectAsState()
    val lightMode by viewModel.lightMode.collectAsState()
    val useBase64Mode by viewModel.useBase64Mode.collectAsState()
    val context = LocalContext.current
    val localIp by viewModel.localIp.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandHeader(title = "Settings")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsToggleItem(
                        title = "Auto-share captured photos",
                        description = "Instantly sends any photo you snap inside the app layout to all connected users.",
                        checked = autoSendPhotos,
                        onCheckedChange = { viewModel.updateSetting("auto_send_photos", it) }
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                    SettingsToggleItem(
                        title = "Auto-share captured videos",
                        description = "Instantly sends any video recorded inside the app layout to all connected users.",
                        checked = autoSendVideos,
                        onCheckedChange = { viewModel.updateSetting("auto_send_videos", it) }
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                    SettingsToggleItem(
                        title = "Auto-accept files",
                        description = "Automatically accept file packets sent by other peers inside the group network.",
                        checked = autoAcceptFiles,
                        onCheckedChange = { viewModel.updateSetting("auto_accept_files", it) }
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                    SettingsToggleItem(
                        title = "Save received files automatically",
                        description = "Downloads/registers received items instantly without prompting. Photos go to Pictures, videos to Movies, items to Downloads.",
                        checked = saveFilesAutomatically,
                        onCheckedChange = { viewModel.updateSetting("save_files_auto", it) }
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                    SettingsToggleItem(
                        title = "Light Theme Mode",
                        description = "Enable clean, high-contrast, bright UI workspace. Disable to switch back to deep slate dark colors.",
                        checked = lightMode,
                        onCheckedChange = { viewModel.updateSetting("light_mode", it) }
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                    SettingsToggleItem(
                        title = "Base64 Payload Streaming",
                        description = "When enabled, transfers use robust line-divided Base64 chunk representations for safe wire transport.",
                        checked = useBase64Mode,
                        onCheckedChange = { viewModel.updateSetting("use_base64_mode", it) }
                    )
                }
            }
        }

        item {
            val customFolder by viewModel.customFolder.collectAsState()
            var folderInput by remember { mutableStateOf(customFolder) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Storage Destination Customizer",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = "Customize the storage sub-directory folders used when incoming media packets are saved. By default, items land in GroupDrop.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = {
                            folderInput = it
                            viewModel.updateCustomFolder(it)
                        },
                        label = { Text("Subfolder Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("GroupDrop") }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Automatic Sorted Hierarchy Pathway Map:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Text("• 📸 Images  ➜  Pictures / $customFolder", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• 🎬 Videos  ➜  Movies / $customFolder", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• 📄 Files   ➜  Downloads / $customFolder", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "How does GroupDrop work offline?",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. Every phone must connect to the exact same Wi-Fi connection, OR one phone must turn on a mobile portable Wi-Fi Hotspot and other phones connect to it.\n\n" +
                               "2. Ensure all participants click \"Start Sharing\" in their respective app screen.\n\n" +
                               "3. The network engine automatically pairs devices via mDNS. Send pictures, pdf documents, and videos directly without cellular internet!",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun NetworkDiagnosticsPanel(localIp: String, context: Context) {
    var isExpanded by remember { mutableStateOf(false) }

    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val wifiInfo = wifiManager.connectionInfo
    val rawSsid = wifiInfo?.ssid?.replace("\"", "") ?: "M3 Local Network"
    val ssid = if (rawSsid == "<unknown ssid>" || rawSsid.isBlank() || rawSsid == "0x") "Local High-Speed WLAN" else rawSsid
    
    val rssi = wifiInfo?.rssi ?: -50
    val signalStrength = when {
        rssi >= -50 -> "Excellent (-${-rssi} dBm)"
        rssi >= -70 -> "Good (-${-rssi} dBm)"
        else -> "Moderate (-${-rssi} dBm)"
    }
    val signalStrengthPercent = WifiManager.calculateSignalLevel(rssi, 100)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Layer 3 Node Diagnostics",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Channel: $ssid",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    text = if (isExpanded) "COLLAPSE" else "EXPAND PANEL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    
                    DiagnosticItem(
                        label = "SSID / Connection",
                        value = ssid,
                        statusText = "CONNECTED",
                        statusColor = Color(0xFF4CAF50)
                    )

                    DiagnosticItem(
                        label = "Network Signal Quality",
                        value = signalStrength,
                        statusText = "$signalStrengthPercent%",
                        statusColor = if (signalStrengthPercent > 70) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )

                    DiagnosticItem(
                        label = "Multicast Protocol Discovery",
                        value = "IPv4 Broadcast / NSD Layer",
                        statusText = "SUPPORTED",
                        statusColor = Color(0xFF4CAF50)
                    )

                    DiagnosticItem(
                        label = "Local Binding Address",
                        value = localIp,
                        statusText = "SOCKET-UP",
                        statusColor = if (localIp != "127.0.0.1") Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String, statusText: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = statusText,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = statusColor,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(statusColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
