package com.wifipro.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wifipro.app.data.ScanItem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { Surface(Modifier.fillMaxSize()) { App() } }
        }
    }
}

private fun bars(level: Int): String = when {
    level >= -55 -> "▂▄▆█"
    level >= -67 -> "▂▄▆"
    level >= -78 -> "▂▄"
    else -> "▂"
}

private data class Draft(val ssid: String, val security: String, val hidden: Boolean)

@Composable
fun App(vm: MainVm = viewModel()) {
    val tabs = listOf("Scan", "Signal", "Channels", "Info", "Tools", "Saved")
    var tab by remember { mutableStateOf(0) }
    val perms = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.scan() }
    LaunchedEffect(Unit) { launcher.launch(perms) }

    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        ScrollableTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> ScanScreen(vm)
            1 -> SignalScreen(vm)
            2 -> ChannelScreen(vm)
            3 -> InfoScreen(vm)
            4 -> ToolsScreen(vm)
            else -> SavedScreen(vm)
        }
    }
}

@Composable
fun ScanScreen(vm: MainVm) {
    val list by vm.scans.collectAsState()
    val status by vm.status.collectAsState()
    var draft by remember { mutableStateOf<Draft?>(null) }
    Column(Modifier.padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.scan() }) { Text("Scan") }
            OutlinedButton(onClick = { draft = Draft("", "WPA2", true) }) { Text("Hidden network") }
        }
        Text(status, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        if (list.isEmpty()) Text("No networks yet. Grant permissions, turn on Wi-Fi and Location, then tap Scan.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { n -> NetRow(n) { draft = Draft(n.ssid, n.security, false) } }
        }
    }
    draft?.let { d ->
        ConnectDialog(d, vm.store.get(d.ssid), { draft = null }) { s, p, h ->
            vm.connect(s, p, d.security, h)
            draft = null
        }
    }
}

@Composable
private fun NetRow(n: ScanItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(12.dp)) {
            Text(n.ssid, style = MaterialTheme.typography.titleMedium)
            Text("${bars(n.level)}  ${n.level} dBm  |  ${n.band}  ch ${n.channel}  |  ${n.security}")
            Text("${n.width}  |  ${n.standard}  |  ${n.bssid}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConnectDialog(d: Draft, savedPass: String, onDismiss: () -> Unit, onConnect: (String, String, Boolean) -> Unit) {
    var ssid by remember { mutableStateOf(d.ssid) }
    var pass by remember { mutableStateOf(savedPass) }
    var show by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(d.hidden) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect (${d.security})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text("SSID") }, singleLine = true, enabled = d.hidden)
                if (d.security != "Open") OutlinedTextField(
                    pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation()
                )
                Row { Text("Show password", Modifier.weight(1f)); Switch(show, { show = it }) }
                Row { Text("Hidden network", Modifier.weight(1f)); Switch(hidden, { hidden = it }) }
                Text("Android 10+ shows one system confirmation dialog.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConnect(ssid.trim(), pass, hidden) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SignalScreen(vm: MainVm) {
    val h by vm.history.collectAsState()
    val info by vm.info.collectAsState()
    val c = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(16.dp)) {
        Text(info?.let { "${it.ssid}: ${it.rssi} dBm ${bars(it.rssi)}" } ?: "Not connected", style = MaterialTheme.typography.headlineSmall)
        Text("Last 60 seconds (-100 to -30 dBm). Walk around to find the best spot.")
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp)) {
            if (h.size > 1) {
                val p = Path()
                h.forEachIndexed { i, v ->
                    val x = size.width * i / (h.size - 1).toFloat()
                    val y = size.height * (1f - (v.coerceIn(-100, -30) + 100) / 70f)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                drawPath(p, c, style = Stroke(width = 5f))
            }
        }
    }
}

@Composable
fun ChannelScreen(vm: MainVm) {
    val list by vm.scans.collectAsState()
    val g24 = list.filter { it.band == "2.4 GHz" }
    val score = listOf(1, 6, 11).associateWith { ch -> g24.count { kotlin.math.abs(it.channel - ch) <= 4 } }
    val best = score.minByOrNull { it.value }?.key
    val max = (1..13).maxOf { ch -> g24.count { it.channel == ch } }.coerceAtLeast(1)
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("2.4 GHz congestion", style = MaterialTheme.typography.titleMedium)
        Text(if (g24.isEmpty()) "Scan first." else "Recommended channel: $best")
        (1..13).forEach { ch ->
            val n = g24.count { it.channel == ch }
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("ch %2d".format(ch), Modifier.padding(end = 8.dp))
                LinearProgressIndicator(progress = { n / max.toFloat() }, modifier = Modifier.weight(1f).padding(top = 8.dp))
                Text("  $n")
            }
        }
        Text("5/6 GHz channels in use", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        val g5 = list.filter { it.band != "2.4 GHz" }.groupBy { it.channel }.toSortedMap()
        if (g5.isEmpty()) Text("None found.")
        g5.forEach { (ch, l) -> Text("ch $ch (${l.first().band}): ${l.size} network(s)") }
    }
}

@Composable
fun InfoScreen(vm: MainVm) {
    val i by vm.info.collectAsState()
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        val c = i
        if (c == null) Text("Not connected to Wi-Fi, or Android hides details for app-requested networks.")
        else {
            Text(
                "SSID: ${c.ssid}\nBSSID: ${c.bssid}\nSignal: ${c.rssi} dBm\nLink speed: ${c.linkMbps} Mbps\n" +
                    "Frequency: ${c.freq} MHz\nIP: ${c.ip}\nGateway: ${c.gateway}\nSubnet: ${c.mask}\nDNS: ${c.dns}"
            )
            Text(
                "Note: Android may use a randomized MAC address per network.", Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ToolsScreen(vm: MainVm) {
    var host by remember { mutableStateOf("1.1.1.1") }
    val out by vm.toolOut.collectAsState()
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(host, { host = it }, label = { Text("Host or IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.ping(host.trim()) }) { Text("Ping") }
            Button(onClick = { vm.dns(host.trim()) }) { Text("DNS") }
            Button(onClick = { vm.publicIp() }) { Text("Public IP") }
        }
        Text(out)
    }
}

@Composable
fun SavedScreen(vm: MainVm) {
    val names by vm.saved.collectAsState()
    Column(Modifier.padding(16.dp)) {
        if (names.isEmpty()) Text("No saved networks yet. Passwords are stored encrypted on this device.")
        LazyColumn {
            items(names) { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(n, Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = { vm.forget(n) }) { Text("Forget") }
                }
            }
        }
    }
}
