package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.components.MediaPreviewDialog
import com.example.ui.components.PreviewDetail
import com.example.ui.viewmodel.GroupDropViewModel
import com.example.utils.StorageHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDropApp(viewModel: GroupDropViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("splash") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val requiredPermissions = remember {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissions.toTypedArray()
    }

    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    var activePreviewInfo by remember { mutableStateOf<PreviewDetail?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (permissionsGranted) {
            currentScreen = "dashboard"
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        currentScreen = if (permissionsGranted) {
            "dashboard"
        } else {
            "permissions"
        }
    }

    when (currentScreen) {
        "splash" -> SplashScreen()
        "permissions" -> PermissionScreen(
            permissions = requiredPermissions,
            onGrantClicked = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
        "dashboard" -> {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Share, contentDescription = "Sharing Tools") },
                            label = { Text("Sharing") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.History, contentDescription = "History Logs") },
                            label = { Text("History") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings Panel") },
                            label = { Text("Settings") }
                        )
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    val currentCustomFolder by viewModel.customFolder.collectAsState()
                    when (selectedTab) {
                        0 -> SharingTab(viewModel) { item ->
                            val finalFolder = if (currentCustomFolder.isBlank()) "GroupDrop" else currentCustomFolder
                            val dirType = when {
                                StorageHelper.isImage(StorageHelper.getMimeType(item.fileName)) -> android.os.Environment.DIRECTORY_PICTURES
                                StorageHelper.isVideo(StorageHelper.getMimeType(item.fileName)) -> android.os.Environment.DIRECTORY_MOVIES
                                else -> android.os.Environment.DIRECTORY_DOWNLOADS
                            }
                            val pathFile = File(android.os.Environment.getExternalStoragePublicDirectory(dirType), "$finalFolder/${item.fileName}")
                            activePreviewInfo = PreviewDetail(
                                fileName = item.fileName,
                                fileType = StorageHelper.getMimeType(item.fileName),
                                fileSize = item.fileSize,
                                savedPath = pathFile.absolutePath,
                                isOutgoing = item.isOutgoing
                            )
                        }
                        1 -> HistoryTab(viewModel) { record ->
                            activePreviewInfo = PreviewDetail(
                                fileName = record.fileName,
                                fileType = record.fileType,
                                fileSize = record.fileSize,
                                savedPath = record.savedPath,
                                isOutgoing = record.isOutgoing
                            )
                        }
                        2 -> SettingsTab(viewModel)
                    }
                }
            }
        }
    }

    activePreviewInfo?.let { detail ->
        MediaPreviewDialog(
            fileName = detail.fileName,
            fileType = detail.fileType,
            fileSize = detail.fileSize,
            savedPath = detail.savedPath,
            isOutgoing = detail.isOutgoing,
            onDismiss = { activePreviewInfo = null },
            context = context
        )
    }
}
