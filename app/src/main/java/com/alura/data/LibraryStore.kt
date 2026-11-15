package com.alura.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class UserPlaylist(val id: String, val name: String, val tracks: List<Track> = emptyList())

@Serializable
data class TrackStats(
    val track: Track,
    val playCount: Int = 0,
    val totalListenMs: Long = 0L,
    val skipCount: Int = 0,
    val lastPlayedAt: Long = 0L,
)

@Serializable
data class LibraryData(
    val favorites: List<Track> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val downloads: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val stats: List<TrackStats> = emptyList(),
) {
    fun isFav(id: String) = favorites.any { it.id == id }
    fun isDownloaded(id: String) = downloads.any { it.id == id }
}

/**
 * Penyimpanan Favorit, Playlist Saya, dan daftar Download (metadata di library.json).
 * File audio hasil download diambil dari API Audius dan disimpan di filesDir/downloads/<id>.mp3.
 */
class LibraryStore(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val file = File(dir, "library.json")
    private val downloadDir = File(dir, "downloads").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    fun fileFor(id: String) = File(downloadDir, "$id.mp3")
    fun partFor(id: String) = File(downloadDir, "$id.part")

    private fun load(): LibraryData {
        val d = runCatching { json.decodeFromString(LibraryData.serializer(), file.readText()) }
            .getOrDefault(LibraryData())
        // Buang entri download yang file-nya sudah tidak ada
        return d.copy(downloads = d.downloads.filter { fileFor(it.id).exists() })
    }

    @Synchronized
    private fun edit(f: (LibraryData) -> LibraryData) {
        val n = f(_data.value)
        _data.value = n
        runCatching {
            val tmp = File(dir, "library.json.tmp")
            tmp.writeText(json.encodeToString(LibraryData.serializer(), n))
            tmp.renameTo(file)
        }
    }

    /** Selalu simpan URL Audius (bukan file://) supaya data tetap valid walau download dihapus. */
    private fun Track.clean() =
        if (streamUri.startsWith("file:")) copy(streamUri = audiusStreamUrl(id)) else this

    /** Kembalikan true bila lagu jadi favorit, false bila dihapus dari favorit. */
    fun toggleFavorite(t: Track): Boolean {
        var added = false
        edit { d ->
            if (d.isFav(t.id)) d.copy(favorites = d.favorites.filterNot { it.id == t.id })
            else { added = true; d.copy(favorites = listOf(t.clean()) + d.favorites) }
        }
        return added
    }

    fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        edit { it.copy(playlists = it.playlists + UserPlaylist(id, name)) }
        return id
    }

    fun deletePlaylist(id: String) = edit { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    /** false bila lagu sudah ada di playlist itu. */
    fun addToPlaylist(pid: String, t: Track): Boolean {
        var added = false
        edit { d ->
            d.copy(playlists = d.playlists.map { p ->
                if (p.id == pid && p.tracks.none { it.id == t.id }) { added = true; p.copy(tracks = p.tracks + t.clean()) } else p
            })
        }
        return added
    }

    fun removeFromPlaylist(pid: String, tid: String) = edit { d ->
        d.copy(playlists = d.playlists.map { p -> if (p.id == pid) p.copy(tracks = p.tracks.filterNot { it.id == tid }) else p })
    }

    fun addRecent(t: Track) = edit { d ->
        val clean = t.clean()
        val old = d.stats.firstOrNull { it.track.id == t.id }
        val stat = (old ?: TrackStats(clean)).copy(track = clean, lastPlayedAt = System.currentTimeMillis())
        d.copy(
            recent = (listOf(clean) + d.recent.filterNot { it.id == t.id }).take(30),
            stats = listOf(stat) + d.stats.filterNot { it.track.id == t.id },
        )
    }

    /** Akumulasi waktu dengar aktual. Dipanggil berkala hanya ketika player berjalan. */
    fun addListenTime(t: Track, deltaMs: Long) {
        if (deltaMs <= 0L) return
        edit { d ->
            val old = d.stats.firstOrNull { it.track.id == t.id }
            val next = (old ?: TrackStats(t.clean())).copy(
                track = t.clean(),
                totalListenMs = (old?.totalListenMs ?: 0L) + deltaMs,
                lastPlayedAt = System.currentTimeMillis(),
            )
            d.copy(stats = listOf(next) + d.stats.filterNot { it.track.id == t.id })
        }
    }

    /** Satu play baru dihitung setelah sesi mendengar mencapai ambang 30 detik. */
    fun recordQualifiedPlay(t: Track) = edit { d ->
        val old = d.stats.firstOrNull { it.track.id == t.id }
        val next = (old ?: TrackStats(t.clean())).copy(
            track = t.clean(),
            playCount = (old?.playCount ?: 0) + 1,
            lastPlayedAt = System.currentTimeMillis(),
        )
        d.copy(stats = listOf(next) + d.stats.filterNot { it.track.id == t.id })
    }

    fun recordSkip(t: Track) = edit { d ->
        val old = d.stats.firstOrNull { it.track.id == t.id }
        val next = (old ?: TrackStats(t.clean())).copy(
            track = t.clean(),
            skipCount = (old?.skipCount ?: 0) + 1,
            lastPlayedAt = old?.lastPlayedAt ?: System.currentTimeMillis(),
        )
        d.copy(stats = listOf(next) + d.stats.filterNot { it.track.id == t.id })
    }

    fun addDownload(t: Track) = edit { d ->
        if (d.isDownloaded(t.id)) d else d.copy(downloads = listOf(t.clean()) + d.downloads)
    }

    fun removeDownload(id: String) {
        fileFor(id).delete()
        edit { d -> d.copy(downloads = d.downloads.filterNot { it.id == id }) }
    }

    /** Bila lagu sudah di-download, putar dari file hasil download; selain itu stream dari API. */
    fun resolve(t: Track): Track {
        val f = fileFor(t.id)
        return if (_data.value.isDownloaded(t.id) && f.exists()) t.copy(streamUri = Uri.fromFile(f).toString()) else t.clean()
    }
}
