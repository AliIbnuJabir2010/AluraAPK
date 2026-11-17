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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
 * Metadata pustaka dibaca dan ditulis di satu worker IO khusus.
 * Operasi ke StateFlow tetap instan agar sentuhan UI tidak menunggu file.
 */
class LibraryStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = appContext.filesDir
    private val file = File(dir, "library.json")
    private val downloadDir = File(dir, "downloads")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ioExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "alura-library-io").apply { isDaemon = true }
    }

    private val _data = MutableStateFlow(LibraryData())
    val data: StateFlow<LibraryData> = _data.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val stateLock = Any()
    private var dataReady = false
    private val pendingEdits = ArrayList<(LibraryData) -> LibraryData>()
    private val revision = AtomicLong(0L)
    private val persistScheduled = AtomicBoolean(false)

    init {
        // Membaca dan mendekode JSON dilakukan setelah layar dapat mulai dirender.
        ioExecutor.execute {
            val loaded = loadFromDisk()
            val hadPendingEdits: Boolean
            synchronized(stateLock) {
                var merged = loaded
                hadPendingEdits = pendingEdits.isNotEmpty()
                pendingEdits.forEach { edit -> merged = edit(merged) }
                pendingEdits.clear()
                dataReady = true
                _data.value = merged
                _ready.value = true
            }
            if (hadPendingEdits) schedulePersist()
        }
    }

    fun fileFor(id: String) = File(downloadDir, "$id.mp3")

    fun partFor(id: String): File {
        downloadDir.mkdirs()
        return File(downloadDir, "$id.part")
    }

    private fun loadFromDisk(): LibraryData {
        downloadDir.mkdirs()
        val stored = runCatching {
            if (file.exists()) json.decodeFromString(LibraryData.serializer(), file.readText())
            else LibraryData()
        }.getOrDefault(LibraryData())
        return stored.copy(downloads = stored.downloads.filter { fileFor(it.id).exists() })
    }

    private fun edit(transform: (LibraryData) -> LibraryData) {
        synchronized(stateLock) {
            _data.value = transform(_data.value)
            if (dataReady) schedulePersist() else pendingEdits += transform
        }
    }

    /** Coalesce penulisan beruntun agar perubahan statistik tidak membuat antrean I/O panjang. */
    private fun schedulePersist() {
        revision.incrementAndGet()
        if (!persistScheduled.compareAndSet(false, true)) return
        ioExecutor.execute {
            while (true) {
                val version = revision.get()
                persist(_data.value)
                if (revision.get() == version) {
                    persistScheduled.set(false)
                    if (revision.get() == version || !persistScheduled.compareAndSet(false, true)) break
                }
            }
        }
    }

    private fun persist(snapshot: LibraryData) {
        runCatching {
            val encoded = json.encodeToString(LibraryData.serializer(), snapshot)
            val tmp = File(dir, "library.json.tmp")
            tmp.writeText(encoded)
            if (!tmp.renameTo(file)) {
                file.writeText(encoded)
                tmp.delete()
            }
        }
    }

    /** Simpan URL stream asli, bukan URI file hasil download. */
    private fun Track.clean(): Track =
        if (!streamUri.startsWith("file:")) this
        else if (source == MusicSource.YOUTUBE) copy(streamUri = "ytm://${id.removePrefix("yt_")}")
        else copy(streamUri = audiusStreamUrl(id))

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

    fun addToPlaylist(pid: String, t: Track): Boolean {
        var added = false
        edit { d ->
            d.copy(playlists = d.playlists.map { p ->
                if (p.id == pid && p.tracks.none { it.id == t.id }) {
                    added = true
                    p.copy(tracks = p.tracks + t.clean())
                } else p
            })
        }
        return added
    }

    fun removeFromPlaylist(pid: String, tid: String) = edit { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id == pid) p.copy(tracks = p.tracks.filterNot { it.id == tid }) else p
        })
    }

    fun addRecent(t: Track) = edit { d ->
        val clean = t.clean()
        val old = d.stats.firstOrNull { it.track.id == t.id }
        val stat = (old ?: TrackStats(clean)).copy(track = clean, lastPlayedAt = System.currentTimeMillis())
        d.copy(
            recent = (listOf(clean) + d.recent.filterNot { it.id == t.id }).take(30),
            stats = (listOf(stat) + d.stats.filterNot { it.track.id == t.id }).take(500),
        )
    }

    fun addListenTime(t: Track, deltaMs: Long) {
        if (deltaMs <= 0L) return
        edit { d ->
            val old = d.stats.firstOrNull { it.track.id == t.id }
            val clean = t.clean()
            val next = (old ?: TrackStats(clean)).copy(
                track = clean,
                totalListenMs = (old?.totalListenMs ?: 0L) + deltaMs,
                lastPlayedAt = System.currentTimeMillis(),
            )
            d.copy(stats = (listOf(next) + d.stats.filterNot { it.track.id == t.id }).take(500))
        }
    }

    fun recordQualifiedPlay(t: Track) = edit { d ->
        val old = d.stats.firstOrNull { it.track.id == t.id }
        val clean = t.clean()
        val next = (old ?: TrackStats(clean)).copy(
            track = clean,
            playCount = (old?.playCount ?: 0) + 1,
            lastPlayedAt = System.currentTimeMillis(),
        )
        d.copy(stats = (listOf(next) + d.stats.filterNot { it.track.id == t.id }).take(500))
    }

    fun recordSkip(t: Track) = edit { d ->
        val old = d.stats.firstOrNull { it.track.id == t.id }
        val next = (old ?: TrackStats(t.clean())).copy(
            track = t.clean(),
            skipCount = (old?.skipCount ?: 0) + 1,
            lastPlayedAt = old?.lastPlayedAt ?: System.currentTimeMillis(),
        )
        d.copy(stats = (listOf(next) + d.stats.filterNot { it.track.id == t.id }).take(500))
    }

    fun addDownload(t: Track) = edit { d ->
        if (d.isDownloaded(t.id)) d else d.copy(downloads = listOf(t.clean()) + d.downloads)
    }

    fun removeDownload(id: String) {
        edit { d -> d.copy(downloads = d.downloads.filterNot { it.id == id }) }
        ioExecutor.execute { fileFor(id).delete() }
    }

    fun resetStats() = edit { it.copy(stats = emptyList()) }

    fun resolve(t: Track): Track {
        if (t.source == MusicSource.LOCAL) return t
        val f = fileFor(t.id)
        return if (_data.value.isDownloaded(t.id) && f.exists()) {
            t.copy(streamUri = Uri.fromFile(f).toString())
        } else t.clean()
    }
}
