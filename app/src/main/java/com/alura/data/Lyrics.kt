package com.alura.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

@Serializable
data class LrcDto(
    val trackName: String? = null,
    val artistName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

interface LrclibApi {
    @GET("search") suspend fun search(
        @Query("track_name") title: String,
        @Query("artist_name") artist: String,
    ): List<LrcDto>

    @GET("search") suspend fun searchQ(@Query("q") q: String): List<LrcDto>
}

@Serializable
data class OvhDto(val lyrics: String? = null)

/** Sumber cadangan: hanya teks biasa, tanpa waktu. */
interface OvhApi {
    @GET("v1/{artist}/{title}") suspend fun get(@Path("artist") artist: String, @Path("title") title: String): OvhDto
}

private val ovh: OvhApi by lazy {
    Retrofit.Builder()
        .baseUrl("https://api.lyrics.ovh/")
        .client(audiusHttpClient)
        .addConverterFactory(Json { ignoreUnknownKeys = true; coerceInputValues = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OvhApi::class.java)
}

private val lrclib: LrclibApi by lazy {
    val client = audiusHttpClient.newBuilder()
        .addInterceptor { it.proceed(it.request().newBuilder().header("User-Agent", "Alura/1.0").build()) }
        .build()
    Retrofit.Builder()
        .baseUrl("https://lrclib.net/api/")
        .client(client)
        .addConverterFactory(Json { ignoreUnknownKeys = true; coerceInputValues = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LrclibApi::class.java)
}

object LyricsRepo {
    private val cache = ConcurrentHashMap<String, Lyrics>()
    fun clearCache() = cache.clear()
    private val lrcTime = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    private fun cleanTitle(s: String) = s
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("""(?i)\s*[-|]\s*(official|lyrics?|lirik|video|audio|mv).*$"""), "")
        .replace(Regex("""(?i)\s+(feat\.?|ft\.?)\s+.*$"""), "")
        .replace(Regex("""\s+"""), " ").trim()

    private fun cleanArtist(s: String) = s
        .replace(Regex("""(?i)\s*-\s*topic$"""), "")
        .replace(Regex("""(?i)vevo$"""), "")
        .trim()

    private fun parseLrc(lrc: String): List<LyricLine> = buildList {
        for (raw in lrc.lines()) {
            val stamps = lrcTime.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in stamps) {
                val ms = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()) * 1000).toLong()
                add(LyricLine(ms, text))
            }
        }
    }.sortedBy { it.timeMs }

    private fun pick(list: List<LrcDto>, durSec: Int): LrcDto? {
        val usable = list.filter { !it.instrumental && (!it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()) }
        val synced = usable.filter { !it.syncedLyrics.isNullOrBlank() }
        return synced.firstOrNull { durSec <= 0 || abs((it.duration ?: 0.0) - durSec) <= 6 }
            ?: synced.firstOrNull()
            ?: usable.firstOrNull()
    }

    suspend fun load(t: Track): Resource<Lyrics> {
        cache[t.id]?.let { return Resource.Success(it) }
        return withContext(Dispatchers.IO) {
            try {
                val title = cleanTitle(t.title)
                val artist = cleanArtist(t.artist)
                var hit = pick(runCatching { lrclib.search(title, artist) }.getOrDefault(emptyList()), t.durationSec)
                if (hit == null) hit = pick(runCatching { lrclib.searchQ("$artist $title") }.getOrDefault(emptyList()), t.durationSec)
                var res: Lyrics? = null
                if (hit != null) {
                    val synced = hit.syncedLyrics?.takeIf { it.isNotBlank() }?.let(::parseLrc).orEmpty()
                    res = if (synced.isNotEmpty()) Lyrics(synced, true)
                    else Lyrics(hit.plainLyrics.orEmpty().lines().map { LyricLine(0, it.trim()) }.filter { it.text.isNotEmpty() }, false)
                    if (res.lines.isEmpty()) res = null
                }
                if (res == null) {
                    val txt = runCatching { ovh.get(artist, title).lyrics }.getOrNull().orEmpty()
                    val lines = txt.replace("\r", "").lines()
                        .filterNot { it.startsWith("Paroles de la chanson") }
                        .map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isNotEmpty()) res = Lyrics(lines.map { LyricLine(0, it) }, false)
                }
                if (res == null) return@withContext Resource.Empty
                cache[t.id] = res
                Resource.Success(res)
            } catch (e: Exception) {
                Resource.Error(Resource.Kind.NETWORK, "Gagal memuat lirik. Periksa koneksi.")
            }
        }
    }
}
