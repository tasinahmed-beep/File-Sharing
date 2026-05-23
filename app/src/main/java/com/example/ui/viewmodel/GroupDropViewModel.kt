package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Peer
import com.example.data.model.TransferRecord
import com.example.network.GroupDropManager
import com.example.network.GroupDropService
import com.example.utils.DeviceUtils
import com.example.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GroupDropViewModel(private val context: Context) : ViewModel() {

    private val tag = "GroupDropViewModel"
    private val manager = GroupDropManager.getInstance(context)

    val isSharing: StateFlow<Boolean> = manager.isSharing
    val peers: StateFlow<List<Peer>> = manager.peers
    val activeTransfers: StateFlow<List<GroupDropManager.ActiveTransfer>> = manager.activeTransfersList

    // Settings
    val autoSendPhotos: StateFlow<Boolean> = manager.autoSendPhotos
    val autoSendVideos: StateFlow<Boolean> = manager.autoSendVideos
    val autoAcceptFiles: StateFlow<Boolean> = manager.autoAcceptFiles
    val saveFilesAutomatically: StateFlow<Boolean> = manager.saveFilesAutomatically
    val lightMode: StateFlow<Boolean> = manager.lightMode
    val customFolder: StateFlow<String> = manager.customFolder
    val useBase64Mode: StateFlow<Boolean> = manager.useBase64Mode

    // Device Info
    private val _deviceName = MutableStateFlow(DeviceUtils.getDeviceName(context))
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _localIp = MutableStateFlow(DeviceUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    // Database Transfer History
    val transferHistory: StateFlow<List<TransferRecord>> = manager.repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateDeviceName(name: String) {
        if (name.isBlank()) return
        DeviceUtils.setDeviceName(context, name)
        _deviceName.value = name
        // If already sharing, restart to advertise new name
        if (isSharing.value) {
            viewModelScope.launch {
                manager.stopSharing()
                manager.startSharing()
            }
        }
    }

    fun refreshIp() {
        _localIp.value = DeviceUtils.getLocalIpAddress()
    }

    fun startSharingService() {
        try {
            val serviceIntent = Intent(context, GroupDropService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(tag, "GroupDrop Service started.")
        } catch (e: Exception) {
            Log.e(tag, "Error starting foreground service", e)
        }
    }

    fun stopSharingService() {
        try {
            val serviceIntent = Intent(context, GroupDropService::class.java)
            context.stopService(serviceIntent)
            Log.d(tag, "GroupDrop Service stopped.")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping service", e)
        }
    }

    fun updateSetting(key: String, value: Boolean) {
        manager.updateSetting(key, value)
    }

    fun updateCustomFolder(name: String) {
        manager.updateCustomFolder(name)
    }

    fun sendPickedFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempFile = StorageHelper.createTempFileFromUri(context, uri) ?: return@launch
            val fileName = StorageHelper.getFileNameFromUri(context, uri)
            val fileType = StorageHelper.getMimeType(fileName)
            val fileSize = tempFile.length()

            manager.sendFileToAllPeers(tempFile, fileName, fileType, fileSize)
        }
    }

    fun sendFileToSpecificPeer(peer: com.example.data.model.Peer, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempFile = StorageHelper.createTempFileFromUri(context, uri) ?: return@launch
            val fileName = StorageHelper.getFileNameFromUri(context, uri)
            val fileType = StorageHelper.getMimeType(fileName)
            val fileSize = tempFile.length()

            manager.sendFileToSpecificPeer(peer, tempFile, fileName, fileType, fileSize)
        }
    }

    fun connectToPairingUrl(urlString: String): Boolean {
        try {
            if (!urlString.startsWith("groupdrop://pair")) return false
            val uri = Uri.parse(urlString)
            val ip = uri.getQueryParameter("ip") ?: return false
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8989
            val name = uri.getQueryParameter("name") ?: "Paired Node"
            val id = uri.getQueryParameter("id") ?: "paired_${System.currentTimeMillis()}"
            
            val peer = com.example.data.model.Peer(
                id = id,
                name = name,
                ip = ip,
                port = port,
                lastSeen = System.currentTimeMillis()
            )
            manager.addManualPeer(peer)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun handleCapturedMedia(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempFile = StorageHelper.createTempFileFromUri(context, uri) ?: return@launch
            val fileName = if (isVideo) "Captured_Video_${System.currentTimeMillis()}.mp4" else "Captured_Photo_${System.currentTimeMillis()}.jpg"
            val fileType = if (isVideo) "video/mp4" else "image/jpeg"
            val fileSize = tempFile.length()

            // Save captured copy locally to public gallery
            if (saveFilesAutomatically.value) {
                StorageHelper.saveFileToPublicDirectory(context, tempFile, fileName, fileType)
            }

            // Auto share with peers if settings are ON
            val shouldAutoSend = (isVideo && autoSendVideos.value) || (!isVideo && autoSendPhotos.value)
            if (shouldAutoSend && peers.value.isNotEmpty()) {
                manager.sendFileToAllPeers(tempFile, fileName, fileType, fileSize)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            manager.repository.clearHistory()
        }
    }
}

class GroupDropViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupDropViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GroupDropViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
