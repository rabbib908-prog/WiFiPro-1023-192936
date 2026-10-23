package com.wifipro.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.math.abs

object Tools {
    suspend fun ping(host: String, n: Int = 6): String = withContext(Dispatchers.IO) {
        try {
            val a = InetAddress.getByName(host)
            val t = mutableListOf<Long>()
            repeat(n) {
                val s = System.nanoTime()
                if (a.isReachable(2000)) t.add((System.nanoTime() - s) / 1_000_000)
                delay(200)
            }
            if (t.isEmpty()) "Ping ${a.hostAddress}: 100% loss"
            else {
                val jit = if (t.size > 1) t.zipWithNext { x, y -> abs(x - y) }.average() else 0.0
                "Ping ${a.hostAddress}\nSent $n, received ${t.size}, loss ${100 * (n - t.size) / n}%\n" +
                    "min ${t.min()} ms / avg ${"%.1f".format(t.average())} ms / max ${t.max()} ms\n" +
                    "jitter ${"%.1f".format(jit)} ms"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    suspend fun dns(host: String): String = withContext(Dispatchers.IO) {
        try {
            val s = System.nanoTime()
            val all = InetAddress.getAllByName(host)
            val ms = (System.nanoTime() - s) / 1_000_000
            "DNS $host ($ms ms)\n" + all.joinToString("\n") { it.hostAddress ?: "?" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    suspend fun publicIp(): String = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://api.ipify.org").openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 5000
            "Public IP: " + c.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}
