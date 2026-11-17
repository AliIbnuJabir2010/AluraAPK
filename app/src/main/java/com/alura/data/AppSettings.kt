package com.alura.data

import android.content.Context
import android.content.SharedPreferences

/** Pengaturan aplikasi (disimpan di SharedPreferences). Panggil init(ctx) sekali di awal. */
object AppSettings {
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences("alura_settings", Context.MODE_PRIVATE)
    }

    private fun get(k: String, d: Boolean) = prefs?.getBoolean(k, d) ?: d
    private fun put(k: String, v: Boolean) { prefs?.edit()?.putBoolean(k, v)?.apply() }

    var island: Boolean get() = get("island", false); set(v) = put("island", v)
    var autoLyrics: Boolean get() = get("auto_lyrics", false); set(v) = put("auto_lyrics", v)
    var blurCover: Boolean get() = get("blur_cover", true); set(v) = put("blur_cover", v)
    var lowData: Boolean get() = get("low_data", false); set(v) = put("low_data", v)
    var wifiOnly: Boolean get() = get("wifi_only", false); set(v) = put("wifi_only", v)
}
