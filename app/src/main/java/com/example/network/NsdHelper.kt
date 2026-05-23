package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.data.model.Peer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress

class NsdHelper(
    private val context: Context,
    private val onPeerDiscovered: (Peer) -> Unit,
    private val onPeerLost: (String) -> Unit
) {
    private val tag = "NsdHelper"
    private val serviceType = "_groupdrop._tcp"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    private var registeredServiceName: String? = null
    private val resolveMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)

    fun registerService(port: Int, deviceId: String, deviceName: String) {
        try {
            unregisterService()
            
            // Format name safely
            val sanitizedName = deviceName.replace("_", "-").replace(":", "-").trim()
            val serviceName = "GroupDrop__${deviceId}__$sanitizedName"
            
            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = serviceName
                this.serviceType = this@NsdHelper.serviceType
                this.port = port
                // Set custom attributes (TXT records) as a fallback for IPv4
                val localIp = com.example.utils.DeviceUtils.getLocalIpAddress()
                if (localIp.isNotEmpty() && localIp != "127.0.0.1") {
                    setAttribute("ipAddress", localIp)
                }
            }
            
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    registeredServiceName = serviceInfo.serviceName
                    Log.d(tag, "Service registered successfully: ${serviceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(tag, "Service registration failed: $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.d(tag, "Service unregistered successfully: ${serviceInfo.serviceName}")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(tag, "Service unregistration failed: $errorCode")
                }
            }
            
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Error in registerService", e)
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering service", e)
            }
            registrationListener = null
        }
        registeredServiceName = null
    }

    fun discoverServices() {
        try {
            stopDiscovery()
            
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(tag, "Discovery failed to start: $errorCode")
                    try {
                        nsdManager.stopServiceDiscovery(this)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(tag, "Discovery failed to stop: $errorCode")
                    try {
                        nsdManager.stopServiceDiscovery(this)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                override fun onDiscoveryStarted(regType: String) {
                    Log.d(tag, "Service discovery started successfully. regType=$regType")
                }

                override fun onDiscoveryStopped(regType: String) {
                    Log.d(tag, "Service discovery stopped.")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(tag, "Service found raw name: ${serviceInfo.serviceName}, type: ${serviceInfo.serviceType}")
                    
                    // Don't discover our own registered service
                    val myRegName = registeredServiceName ?: ""
                    if (myRegName.isNotEmpty() && serviceInfo.serviceName.contains(myRegName)) {
                        Log.d(tag, "Skipping our own service: ${serviceInfo.serviceName}")
                        return
                    }
                    
                    if (serviceInfo.serviceName.contains("GroupDrop__")) {
                        scope.launch {
                            resolveServiceSequentially(serviceInfo)
                        }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(tag, "Service lost: ${serviceInfo.serviceName}")
                    val infoName = serviceInfo.serviceName
                    val index = infoName.indexOf("GroupDrop__")
                    if (index >= 0) {
                        val cleanName = infoName.substring(index)
                        val parts = cleanName.split("__")
                        if (parts.size >= 2) {
                            val id = parts[1]
                            onPeerLost(id)
                        }
                    }
                }
            }
            
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error in discoverServices", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping discovery", e)
            }
            discoveryListener = null
        }
    }

    private suspend fun resolveService(serviceInfo: NsdServiceInfo): NsdServiceInfo? = 
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(tag, "Resolve failed for ${serviceInfo.serviceName} with error code $errorCode")
                    if (continuation.isActive) {
                        continuation.resume(null, onCancellation = null)
                    }
                }

                override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                    Log.d(tag, "Service resolved successfully: ${resolvedServiceInfo.serviceName}")
                    if (continuation.isActive) {
                        continuation.resume(resolvedServiceInfo, onCancellation = null)
                    }
                }
            }
            
            try {
                // Ensure standard serviceType without extra trailing dots/local domains is passed if supported
                val serviceToResolve = NsdServiceInfo().apply {
                    this.serviceName = serviceInfo.serviceName
                    this.serviceType = serviceInfo.serviceType
                }
                nsdManager.resolveService(serviceToResolve, resolveListener)
            } catch (e: Exception) {
                Log.e(tag, "Exception during resolveService invocation", e)
                if (continuation.isActive) {
                    continuation.resume(null, onCancellation = null)
                }
            }
        }

    private suspend fun resolveServiceSequentially(serviceInfo: NsdServiceInfo) {
        resolveMutex.withLock {
            try {
                Log.d(tag, "Resolving service sequentially: ${serviceInfo.serviceName}...")
                
                // Wrap in a tight timeout of 3 seconds so if resolve hangs, we do not lock the queue
                val resolvedServiceInfo = kotlinx.coroutines.withTimeoutOrNull(3000) {
                    resolveService(serviceInfo)
                }
                
                if (resolvedServiceInfo != null) {
                    val infoName = resolvedServiceInfo.serviceName
                    val index = infoName.indexOf("GroupDrop__")
                    if (index >= 0) {
                        val cleanName = infoName.substring(index)
                        val parts = cleanName.split("__")
                        if (parts.size >= 3) {
                            val id = parts[1]
                            val name = parts[2].replace("-", " ")
                            
                            // Parse host/IP address
                            var hostAddress = resolvedServiceInfo.host?.hostAddress ?: ""
                            
                            // Check attributes map for TXT fallback "ipAddress" (bypasses IPv6 routing issues on some routers/devices)
                            try {
                                val ipBytes = resolvedServiceInfo.attributes?.get("ipAddress")
                                if (ipBytes != null) {
                                    val txtIp = String(ipBytes, Charsets.UTF_8).trim()
                                    if (txtIp.isNotEmpty() && txtIp != "127.0.0.1") {
                                        Log.d(tag, "Using TXT attribute fallback IPAddress: $txtIp (resolved host was $hostAddress)")
                                        hostAddress = txtIp
                                    }
                                }
                            } catch (ae: Exception) {
                                Log.e(tag, "Failed to read attributes from resolved service", ae)
                            }
                            
                            if (hostAddress.isNotEmpty()) {
                                val peer = Peer(
                                    id = id,
                                    name = name,
                                    ip = hostAddress,
                                    port = resolvedServiceInfo.port,
                                    lastSeen = System.currentTimeMillis()
                                )
                                Log.d(tag, "New Peer fully discovered and updated: $peer")
                                onPeerDiscovered(peer)
                            }
                        }
                    }
                } else {
                    Log.w(tag, "Could not resolve service info within timeout: ${serviceInfo.serviceName}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception while processing resolveServiceSequentially", e)
            }
        }
    }
}
