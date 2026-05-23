package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.FileMetadata
import com.example.data.model.Peer
import com.example.data.model.TransferRecord
import com.example.data.repository.TransferRepository
import com.example.utils.DeviceUtils
import com.example.utils.StorageHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

class GroupDropManager private constructor(private val context: Context) {

    private val tag = "GroupDropManager"
    private val scope = CoroutineScope(Dispatchers.Default)

    // Database & Repository
    private val database = AppDatabase.getDatabase(context)
    val repository = TransferRepository(database.transferRecordDao())

    // State flows
    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<List<Peer>> = _peers
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Active transfers progress monitoring: Map<fileId, progressRatio>
    private val _activeTransfers = MutableStateFlow<Map<String, ActiveTransfer>>(emptyMap())
    val activeTransfers: StateFlow<List<ActiveTransfer>> = _activeTransfers
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Observe active transfers as live list
    val activeTransfersList: StateFlow<List<ActiveTransfer>> = activeTransfers

    // Settings State
    private val prefs = context.getSharedPreferences("groupdrop_settings_prefs", Context.MODE_PRIVATE)

    private val _autoSendPhotos = MutableStateFlow(prefs.getBoolean("auto_send_photos", true))
    val autoSendPhotos = _autoSendPhotos.asStateFlow()

    private val _autoSendVideos = MutableStateFlow(prefs.getBoolean("auto_send_videos", true))
    val autoSendVideos = _autoSendVideos.asStateFlow()

    private val _autoAcceptFiles = MutableStateFlow(prefs.getBoolean("auto_accept_files", true))
    val autoAcceptFiles = _autoAcceptFiles.asStateFlow()

    private val _saveFilesAutomatically = MutableStateFlow(prefs.getBoolean("save_files_auto", true))
    val saveFilesAutomatically = _saveFilesAutomatically.asStateFlow()

    private val _lightMode = MutableStateFlow(prefs.getBoolean("light_mode", false))
    val lightMode = _lightMode.asStateFlow()

    private val _customFolder = MutableStateFlow(prefs.getString("custom_folder", "GroupDrop") ?: "GroupDrop")
    val customFolder = _customFolder.asStateFlow()

    private val _useBase64Mode = MutableStateFlow(prefs.getBoolean("use_base64_mode", false))
    val useBase64Mode = _useBase64Mode.asStateFlow()

    // Network & Server instances
    private var nsdHelper: NsdHelper? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val serverPort = 8989

    // Moshi JSON Adaptation
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val metadataAdapter = moshi.adapter(FileMetadata::class.java)

    data class ActiveTransfer(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val progress: Float,
        val status: String, // "SENDING", "RECEIVING", "COMPLETED", "FAILED"
        val isOutgoing: Boolean,
        val peerName: String,
        val speedBps: Long = 0L,
        val etaSeconds: Long = -1L
    )

    init {
        nsdHelper = NsdHelper(
            context = context,
            onPeerDiscovered = { peer ->
                _peers.update { current -> current + (peer.id to peer) }
                Log.d(tag, "Peer discovered/updated: ${peer.name} (${peer.ip}:${peer.port})")
            },
            onPeerLost = { peerId ->
                _peers.update { current -> current - peerId }
                Log.d(tag, "Peer lost: $peerId")
            }
        )

        // Periodically verify liveness of peers
        scope.launch {
            while (true) {
                delay(5000)
                val now = System.currentTimeMillis()
                _peers.update { current ->
                    current.filter { entry -> now - entry.value.lastSeen < 15000 }
                }
            }
        }
    }

    fun addManualPeer(peer: Peer) {
        _peers.update { current -> current + (peer.id to peer) }
    }

    fun updateCustomFolder(name: String) {
        val sanitized = name.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == ' ' }
        val finalFolder = if (sanitized.isEmpty()) "GroupDrop" else sanitized
        prefs.edit().putString("custom_folder", finalFolder).apply()
        _customFolder.value = finalFolder
    }

    fun updateSetting(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        when (key) {
            "auto_send_photos" -> _autoSendPhotos.value = value
            "auto_send_videos" -> _autoSendVideos.value = value
            "auto_accept_files" -> _autoAcceptFiles.value = value
            "save_files_auto" -> _saveFilesAutomatically.value = value
            "light_mode" -> _lightMode.value = value
            "use_base64_mode" -> _useBase64Mode.value = value
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            multicastLock = wifiManager.createMulticastLock("GroupDropMulticastLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(tag, "MulticastLock acquired specifically for local group discovery.")
        } catch (e: Exception) {
            Log.e(tag, "Could not acquire MulticastLock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            multicastLock = null
            Log.d(tag, "MulticastLock released.")
        } catch (e: Exception) {
            Log.e(tag, "Could not release MulticastLock", e)
        }
    }

    fun startSharing() {
        if (_isSharing.value) return
        _isSharing.value = true
        acquireMulticastLock()
        startLocalServer()
        val devId = DeviceUtils.getDeviceId(context)
        val devName = DeviceUtils.getDeviceName(context)
        nsdHelper?.registerService(serverPort, devId, devName)
        nsdHelper?.discoverServices()
        Log.d(tag, "Sharing services and NSD discovery initiated.")
    }

    fun stopSharing() {
        if (!_isSharing.value) return
        _isSharing.value = false
        nsdHelper?.unregisterService()
        nsdHelper?.stopDiscovery()
        releaseMulticastLock()
        stopLocalServer()
        _peers.value = emptyMap()
        Log.d(tag, "Sharing services stopped.")
    }

    private fun startLocalServer() {
        serverJob?.cancel()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(serverPort)
                Log.d(tag, "Local File Server started on port $serverPort")
                while (_isSharing.value) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch(Dispatchers.IO) {
                        handleIncomingConnection(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception in Local Server loop", e)
            }
        }
    }

    private fun stopLocalServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing server socket", e)
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleIncomingConnection(socket: Socket) {
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            var fileId = "unknown"
            try {
                val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val metadataLen = dis.readInt()
                if (metadataLen <= 0 || metadataLen > 10 * 1024 * 1024) {
                    socket.close()
                    return@withContext
                }

                val metadataBytes = ByteArray(metadataLen)
                dis.readFully(metadataBytes)
                val metadataJson = String(metadataBytes, Charsets.UTF_8)
                val metadata = metadataAdapter.fromJson(metadataJson) ?: run {
                    socket.close()
                    return@withContext
                }

                fileId = metadata.fileId
                Log.d(tag, "Incoming metadata: $metadata")

                // Ensure auto-accept is enabled
                if (!autoAcceptFiles.value) {
                    socket.close()
                    return@withContext
                }

                // Check for duplicate
                val existing = repository.getRecordById(fileId)
                if (existing != null && existing.status == "RECEIVED") {
                    Log.d(tag, "Duplicate file already received and stored: ${metadata.fileName}")
                    // Simply acknowledge immediately and skip
                    val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    dos.writeUTF("SUCCESS_DUPLICATE")
                    dos.flush()
                    socket.close()
                    return@withContext
                }

                // Setup active transfer progress
                _activeTransfers.update { current ->
                    current + (fileId to ActiveTransfer(
                        fileId = fileId,
                        fileName = metadata.fileName,
                        fileSize = metadata.fileSize,
                        progress = 0.0f,
                        status = "RECEIVING",
                        isOutgoing = false,
                        peerName = metadata.senderDeviceName,
                        speedBps = 0L,
                        etaSeconds = -1L
                    ))
                }

                tempFile = File.createTempFile("gd_receive_", ".tmp", context.cacheDir)
                val fos = tempFile.outputStream()
                
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var totalBytesReceived = 0L
                val fileSize = metadata.fileSize

                // Insert pending transfer record in DB
                repository.insertRecord(
                    TransferRecord(
                        fileId = fileId,
                        fileName = metadata.fileName,
                        fileType = metadata.fileType,
                        fileSize = metadata.fileSize,
                        senderReceiverName = metadata.senderDeviceName,
                        status = "RECEIVING",
                        timestamp = System.currentTimeMillis(),
                        savedPath = "",
                        isOutgoing = false,
                        progress = 0.0f
                    )
                )

                var lastUpdateTime = System.currentTimeMillis()
                var lastBytesTransferred = 0L

                while (totalBytesReceived < fileSize) {
                    val decodedBytes = if (metadata.isBase64) {
                        val packetLength = dis.readInt()
                        if (packetLength == -1) break
                        val encodedBuffer = ByteArray(packetLength)
                        dis.readFully(encodedBuffer)
                        android.util.Base64.decode(encodedBuffer, android.util.Base64.NO_WRAP)
                    } else {
                        bytesRead = dis.read(buffer)
                        if (bytesRead == -1) break
                        buffer.copyOf(bytesRead)
                    }

                    fos.write(decodedBytes)
                    totalBytesReceived += decodedBytes.size
                    val progress = totalBytesReceived.toFloat() / fileSize.coerceAtLeast(1)
                    
                    val now = System.currentTimeMillis()
                    val interval = now - lastUpdateTime
                    if (interval >= 300 || totalBytesReceived == fileSize) {
                        val speed = if (interval > 0) ((totalBytesReceived - lastBytesTransferred) * 1000) / interval else 0L
                        val remainingBytes = fileSize - totalBytesReceived
                        val eta = if (speed > 0) remainingBytes / speed else -1L

                        // Update live UI progress
                        _activeTransfers.update { current ->
                            val item = current[fileId]
                            if (item != null) {
                                current + (fileId to item.copy(
                                    progress = progress,
                                    speedBps = speed,
                                    etaSeconds = eta
                                ))
                            } else current
                        }
                        
                        lastUpdateTime = now
                        lastBytesTransferred = totalBytesReceived
                    }
                }
                fos.flush()
                fos.close()

                // Save received file to public folder
                val savedDestinationPath = if (saveFilesAutomatically.value) {
                    StorageHelper.saveFileToPublicDirectory(
                        context = context,
                        tempFile = tempFile,
                        fileName = metadata.fileName,
                        mimeType = metadata.fileType,
                        customFolder = customFolder.value
                    )
                } else {
                    ""
                }

                if (savedDestinationPath.isNotEmpty() || !saveFilesAutomatically.value) {
                    // Update Database progress
                    repository.updateTransferProgress(fileId, "RECEIVED", savedDestinationPath, 1.0f)

                    // Update live UI progress
                    _activeTransfers.update { current ->
                        val item = current[fileId]
                        if (item != null) {
                            current + (fileId to item.copy(progress = 1.0f, status = "RECEIVED"))
                        } else current
                    }

                    // Send acknowledgement back to peer
                    val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    dos.writeUTF("SUCCESS")
                    dos.flush()
                } else {
                    throw Exception("Storage saving failure")
                }

            } catch (e: Exception) {
                Log.e(tag, "Incoming transfer failed", e)
                repository.updateTransferProgress(fileId, "FAILED", "", 0.0f)
                _activeTransfers.update { current ->
                    val item = current[fileId]
                    if (item != null) {
                        current + (fileId to item.copy(status = "FAILED"))
                    } else current
                }
            } finally {
                try {
                    tempFile?.delete()
                } catch (e: Exception) {
                    // Ignore
                }
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // High level method to send selected local file URI to all discovered devices
    fun sendFileToAllPeers(tempFile: File, fileName: String, fileType: String, fileSize: Long) {
        val activePeers = peers.value
        if (activePeers.isEmpty()) {
            Log.w(tag, "Attempted to share file, but no peers are discovered yet.")
            return
        }

        activePeers.forEach { peer ->
            scope.launch {
                sendFileToPeer(peer, tempFile, fileName, fileType, fileSize)
            }
        }
    }

    // High level method to send selected local file to a specific peer
    fun sendFileToSpecificPeer(peer: Peer, tempFile: File, fileName: String, fileType: String, fileSize: Long) {
        scope.launch {
            sendFileToPeer(peer, tempFile, fileName, fileType, fileSize)
        }
    }

    private suspend fun sendFileToPeer(
        peer: Peer,
        tempFile: File,
        fileName: String,
        fileType: String,
        fileSize: Long
    ) {
        val uniqueFileId = "${fileName}_${fileSize}_${DeviceUtils.getDeviceId(context)}_${System.currentTimeMillis()}"

        // Update database with outgoing transfer record
        val initialRecord = TransferRecord(
            fileId = uniqueFileId,
            fileName = fileName,
            fileType = fileType,
            fileSize = fileSize,
            senderReceiverName = peer.name,
            status = "SENDING",
            timestamp = System.currentTimeMillis(),
            savedPath = tempFile.absolutePath,
            isOutgoing = true,
            progress = 0.0f
        )
        repository.insertRecord(initialRecord)

        // Update live state flow UI
        _activeTransfers.update { current ->
            current + (uniqueFileId to ActiveTransfer(
                fileId = uniqueFileId,
                fileName = fileName,
                fileSize = fileSize,
                progress = 0.0f,
                status = "SENDING",
                isOutgoing = true,
                peerName = peer.name,
                speedBps = 0L,
                etaSeconds = -1L
            ))
        }

        var socket: Socket? = null
        try {
            // Attempt socket connect
            socket = Socket(peer.ip, peer.port)
            socket.soTimeout = 30000 // 30s timeout

            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            
            // Build metadata
            val metadata = FileMetadata(
                fileId = uniqueFileId,
                fileName = fileName,
                fileType = fileType,
                fileSize = fileSize,
                senderDeviceName = DeviceUtils.getDeviceName(context),
                senderDeviceId = DeviceUtils.getDeviceId(context),
                timestamp = System.currentTimeMillis(),
                isBase64 = useBase64Mode.value
            )

            // 1. Send metadata JSON
            val metadataJson = metadataAdapter.toJson(metadata)
            val metadataBytes = metadataJson.toByteArray(Charsets.UTF_8)
            dos.writeInt(metadataBytes.size)
            dos.write(metadataBytes)
            dos.flush()

            // 2. Stream file content
            val fileInputStream = tempFile.inputStream()
            val buffer = ByteArray(12 * 1024)
            var bytesRead: Int
            var totalBytesSent = 0L

            var lastUpdateTime = System.currentTimeMillis()
            var lastBytesTransferred = 0L

            while (true) {
                bytesRead = fileInputStream.read(buffer)
                if (bytesRead == -1) break
                
                if (useBase64Mode.value) {
                    val encodedBytes = android.util.Base64.encode(buffer, 0, bytesRead, android.util.Base64.NO_WRAP)
                    dos.writeInt(encodedBytes.size)
                    dos.write(encodedBytes)
                } else {
                    dos.write(buffer, 0, bytesRead)
                }

                totalBytesSent += bytesRead
                val progress = totalBytesSent.toFloat() / fileSize.coerceAtLeast(1)

                val now = System.currentTimeMillis()
                val interval = now - lastUpdateTime
                if (interval >= 300 || totalBytesSent == fileSize) {
                    val speed = if (interval > 0) ((totalBytesSent - lastBytesTransferred) * 1000) / interval else 0L
                    val remainingBytes = fileSize - totalBytesSent
                    val eta = if (speed > 0) remainingBytes / speed else -1L

                    // Update live progress
                    _activeTransfers.update { current ->
                        val item = current[uniqueFileId]
                        if (item != null) {
                            current + (uniqueFileId to item.copy(
                                progress = progress,
                                speedBps = speed,
                                etaSeconds = eta
                            ))
                        } else current
                    }

                    lastUpdateTime = now
                    lastBytesTransferred = totalBytesSent
                }
            }
            dos.flush()

            // 3. Hear Acknowledge
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val ack = dis.readUTF()
            if (ack == "SUCCESS" || ack == "SUCCESS_DUPLICATE") {
                repository.updateTransferProgress(uniqueFileId, "SENT", tempFile.absolutePath, 1.0f)
                _activeTransfers.update { current ->
                    val item = current[uniqueFileId]
                    if (item != null) {
                        current + (uniqueFileId to item.copy(progress = 1.0f, status = "SENT"))
                    } else current
                }
            } else {
                throw Exception("Peer failed to save: $ack")
            }

        } catch (e: Exception) {
            Log.e(tag, "Sending failure details to peer ${peer.name}", e)
            repository.updateTransferProgress(uniqueFileId, "FAILED", "", 0.0f)
            _activeTransfers.update { current ->
                val item = current[uniqueFileId]
                if (item != null) {
                    current + (uniqueFileId to item.copy(status = "FAILED"))
                } else current
            }
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: GroupDropManager? = null

        fun getInstance(context: Context): GroupDropManager {
            return INSTANCE ?: synchronized(this) {
                val instance = GroupDropManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
