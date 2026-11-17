package com.alura.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Baca daftar lagu playlist Spotify publik dari halaman embed (tanpa login, maksimal sekitar 100 lagu). */
object SpotifyImport {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val idRe = Regex("""playlist[/:]([A-Za-z0-9]{22})""")
    private val dataRe = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /** Mengembalikan nama playlist dan pasangan (judul, artis). */
    suspend fun fetch(link: String): Pair<String, List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        val id = idRe.find(link)?.groupValues?.get(1) ?: throw IOException("Link playlist Spotify tidak dikenali")
        val req = Request.Builder()
            .url("https://open.spotify.com/embed/playlist/$id")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept-Language", "id,en;q=0.8")
            .build()
        val html = client.newCall(req).execute().use {
            if (!it.isSuccessful) throw IOException("Spotify menolak (kode ${it.code}). Pastikan playlist publik.")
            it.body?.string().orEmpty()
        }
        val json = dataRe.find(html)?.groupValues?.get(1) ?: throw IOException("Format halaman Spotify berubah, impor belum bisa.")
        var name: String? = null
        val items = mutableListOf<Pair<String, String>>()
        fun str(e: JsonElement?) = (e as? JsonPrimitive)?.contentOrNull?.replace('\u00A0', ' ')?.trim()
        fun walk(e: JsonElement) {
            if (items.isNotEmpty()) return
            when (e) {
                is JsonObject -> {
                    val tl = e["trackList"]
                    if (tl is JsonArray) {
                        name = str(e["name"]) ?: str(e["title"])
                        tl.forEach { t ->
                            val o = t as? JsonObject ?: return@forEach
                            val title = str(o["title"])
                            if (!title.isNullOrBlank()) items += title to (str(o["subtitle"]) ?: "")
                        }
                    } else e.values.forEach(::walk)
                }
                is JsonArray -> e.forEach(::walk)
                else -> {}
            }
        }
        walk(Json.parseToJsonElement(json))
        if (items.isEmpty()) throw IOException("Tidak ada lagu ditemukan. Pastikan playlist publik.")
        (name ?: "Playlist Spotify") to items
    }
}
