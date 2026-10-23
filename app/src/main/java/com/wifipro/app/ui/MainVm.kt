package com.wifipro.app.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifipro.app.data.ConnInfo
import com.wifipro.app.data.ScanItem
import com.wifipro.app.data.Store
import com.wifipro.app.data.Tools
import com.wifipro.app.data.WifiEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainVm(app: Application) : AndroidViewModel(app) {
    val engine = WifiEngine(app)
    val store = Store(app)
    val scans = MutableStateFlow<List<ScanItem>>(emptyList())
    val status = MutableStateFlow("Idle")
    val info = MutableStateFlow<ConnInfo?>(null)
    val history = MutableStateFlow<List<Int>>(emptyList())
    val toolOut = MutableStateFlow("")
    val saved = MutableStateFlow<List<String>>(emptyList())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            scans.value = engine.results()
            status.value = "Scan complete"
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_EXPORTED
        )
        saved.value = store.names()
        viewModelScope.launch {
            while (true) {
                val c = engine.current()
                info.value = c
                if (c != null) history.value = (history.value + c.rssi).takeLast(60)
                delay(1000)
            }
        }
    }

    fun scan() {
        status.value = if (engine.startScan()) "Scanning..." else "Scan throttled by Android - showing last results"
        scans.value = engine.results()
    }

    fun connect(ssid: String, pass: String, security: String, hidden: Boolean) {
        if (pass.isNotBlank()) { store.save(ssid, pass); saved.value = store.names() }
        engine.connect(ssid, pass, security, hidden) { status.value = it }
    }

    fun forget(ssid: String) { store.forget(ssid); saved.value = store.names() }
    fun ping(h: String) { viewModelScope.launch { toolOut.value = "Pinging..."; toolOut.value = Tools.ping(h) } }
    fun dns(h: String) { viewModelScope.launch { toolOut.value = "Looking up..."; toolOut.value = Tools.dns(h) } }
    fun publicIp() { viewModelScope.launch { toolOut.value = "Fetching..."; toolOut.value = Tools.publicIp() } }

    override fun onCleared() {
        try { getApplication<Application>().unregisterReceiver(receiver) } catch (e: Exception) { }
        engine.release()
    }
}
