package com.example.utils

import android.content.Context
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

object DeviceUtils {
    private const val PREFS_NAME = "groupdrop_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceName = prefs.getString(KEY_DEVICE_NAME, null)
        if (deviceName == null) {
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            if (deviceName.length > 20) {
                deviceName = deviceName.take(20)
            }
            prefs.edit().putString(KEY_DEVICE_NAME, deviceName).apply()
        }
        return deviceName
    }

    fun setDeviceName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEVICE_NAME, name.trim()).apply()
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // First pass: Prioritize Wi-Fi and hotspot/tethering/bridge/AP interfaces
            for (networkInterface in interfaces) {
                val name = networkInterface.name?.lowercase() ?: ""
                if (name.contains("wlan") || name.contains("ap") || name.contains("p2p") || name.contains("br") || name.contains("rndis") || name.contains("eth")) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val ip = address.hostAddress ?: ""
                            if (ip.isNotEmpty() && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            }
            // Second pass: fallback to any working non-loopback IPv4 interface
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress ?: ""
                        if (ip.isNotEmpty() && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
