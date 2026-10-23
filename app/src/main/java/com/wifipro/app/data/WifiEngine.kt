package com.wifipro.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi

data class ScanItem(
    val ssid: String, val bssid: String, val level: Int, val freq: Int, val channel: Int,
    val band: String, val security: String, val width: String, val standard: String
)

data class ConnInfo(
    val ssid: String, val bssid: String, val rssi: Int, val linkMbps: Int, val freq: Int,
    val ip: String, val gateway: String, val mask: String, val dns: String
)

fun channelOf(f: Int): Int = when {
    f == 2484 -> 14
    f in 2400..2483 -> (f - 2407) / 5
    f in 5000..5900 -> (f - 5000) / 5
    f >= 5950 -> (f - 5950) / 5
    else -> 0
}

fun bandOf(f: Int): String = when {
    f < 3000 -> "2.4 GHz"
    f < 5900 -> "5 GHz"
    else -> "6 GHz"
}

fun securityOf(cap: String): String = when {
    cap.contains("SAE") -> "WPA3"
    cap.contains("WPA") || cap.contains("PSK") -> "WPA2"
    cap.contains("WEP") -> "WEP"
    else -> "Open"
}

private fun ip(i: Int): String =
    "${i and 255}.${(i shr 8) and 255}.${(i shr 16) and 255}.${(i shr 24) and 255}"

@Suppress("DEPRECATION")
class WifiEngine(context: Context) {
    private val app = context.applicationContext
    private val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var cb: ConnectivityManager.NetworkCallback? = null

    fun startScan(): Boolean = try { wm.startScan() } catch (e: SecurityException) { false }

    fun results(): List<ScanItem> = try {
        wm.scanResults.map { r -> toItem(r) }.sortedByDescending { it.level }
    } catch (e: SecurityException) { emptyList() }

    private fun toItem(r: ScanResult): ScanItem {
        val width = when (r.channelWidth) {
            0 -> "20 MHz"
            1 -> "40 MHz"
            2 -> "80 MHz"
            3 -> "160 MHz"
            4 -> "80+80 MHz"
            else -> "?"
        }
        val std = if (Build.VERSION.SDK_INT >= 30) when (r.wifiStandard) {
            1 -> "legacy"
            4 -> "n (Wi-Fi 4)"
            5 -> "ac (Wi-Fi 5)"
            6 -> "ax (Wi-Fi 6)"
            8 -> "be (Wi-Fi 7)"
            else -> "?"
        } else "?"
        return ScanItem(
            r.SSID.ifBlank { "(hidden)" }, r.BSSID ?: "?", r.level, r.frequency,
            channelOf(r.frequency), bandOf(r.frequency), securityOf(r.capabilities), width, std
        )
    }

    fun current(): ConnInfo? = try {
        val i = wm.connectionInfo
        if (i == null || i.networkId == -1) null else {
            val d = wm.dhcpInfo
            ConnInfo(
                i.ssid.trim('"'), i.bssid ?: "?", i.rssi, i.linkSpeed, i.frequency,
                ip(d.ipAddress), ip(d.gateway), ip(d.netmask), ip(d.dns1)
            )
        }
    } catch (e: Exception) { null }

    fun connect(ssid: String, pass: String, security: String, hidden: Boolean, onState: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= 29) connectQ(ssid, pass, security, hidden, onState)
        else connectLegacy(ssid, pass, security, hidden, onState)
    }

    @RequiresApi(29)
    private fun connectQ(ssid: String, pass: String, security: String, hidden: Boolean, onState: (String) -> Unit) {
        cb?.let { try { cm.unregisterNetworkCallback(it) } catch (e: Exception) { } }
        val b = WifiNetworkSpecifier.Builder().setSsid(ssid).setIsHiddenSsid(hidden)
        when (security) {
            "Open" -> { }
            "WPA3" -> b.setWpa3Passphrase(pass)
            else -> b.setWpa2Passphrase(pass)
        }
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(b.build())
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                onState("Connected to $ssid")
            }
            override fun onUnavailable() {
                onState("Failed: network not found, wrong password, or request declined")
            }
            override fun onLost(network: Network) {
                cm.bindProcessToNetwork(null)
                onState("Disconnected from $ssid")
            }
        }
        cb = callback
        try {
            onState("Connecting to $ssid... (confirm the system dialog)")
            cm.requestNetwork(req, callback, 30000)
        } catch (e: Exception) {
            onState("Failed: ${e.message}")
        }
    }

    private fun connectLegacy(ssid: String, pass: String, security: String, hidden: Boolean, onState: (String) -> Unit) {
        try {
            val c = WifiConfiguration()
            c.SSID = "\"$ssid\""
            c.hiddenSSID = hidden
            if (security == "Open") c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            else c.preSharedKey = "\"$pass\""
            val id = wm.addNetwork(c)
            if (id == -1) { onState("Failed: could not add network"); return }
            wm.disconnect()
            val ok = wm.enableNetwork(id, true)
            wm.reconnect()
            onState(if (ok) "Connecting to $ssid..." else "Failed: could not enable network")
        } catch (e: Exception) {
            onState("Failed: ${e.message}")
        }
    }

    fun release() {
        cb?.let { try { cm.unregisterNetworkCallback(it) } catch (e: Exception) { } }
        cb = null
        if (Build.VERSION.SDK_INT >= 23) cm.bindProcessToNetwork(null)
    }
}
