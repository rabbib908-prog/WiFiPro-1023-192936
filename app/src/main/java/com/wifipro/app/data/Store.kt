package com.wifipro.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Store(context: Context) {
    private val sp: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "wifipro_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("wifipro_fallback_empty", Context.MODE_PRIVATE)
    }

    fun save(ssid: String, pass: String) { sp.edit().putString(ssid, pass).apply() }
    fun get(ssid: String): String = sp.getString(ssid, "") ?: ""
    fun forget(ssid: String) { sp.edit().remove(ssid).apply() }
    fun names(): List<String> = sp.all.keys.sorted()
}
