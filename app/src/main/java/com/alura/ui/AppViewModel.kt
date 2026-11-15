package com.alura.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alura.data.*
import com.alura.player.PlayerHolder
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request

data class Category(val label: String, val genre: String? = null, val query: String? = null)

val CATEGORIES = listOf(
    Category("Pop", genre = "Pop"),
    Category("Rock", genre = "Rock"),
    Category("Indie", genre = "Alternative"),
    Category("Hip-Hop", genre = "Hip-Hop/Rap"),
    Category("Dangdut", query = "dangdut"),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val audius = AudiusRepository()
    private val yt = YouTubeRepository()
    private val store = LibraryStore(app)

    val trending = MutableStateFlow<Resource<List<Track>>>(Resource.Loading)
    val playlists = MutableStateFlow<Resource<List<Playlist>>>(Resource.Loading)
    val results = MutableStateFlow<Resource<List<Track>>>(Resource.Empty)
    val query = MutableStateFlow("")
    val category = MutableStateFlow<Category?>(null)

    /** Favorit, Playlist Saya, dan Download. */
    val lib: StateFlow<LibraryData> = store.data
    /** id lagu -> persen download (0..100; -1 bila ukuran tidak diketahui). */
    val downloading = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Pesan singkat untuk Toast. */
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var job: Job? = null

    init {
        refresh()
        PlayerHolder.player(app).addListener(playerListener)
    }

    private fun say(s: String) { toasts.tryEmit(s) }

    fun refresh() {
        viewModelScope.launch { trending.value = Resource.Loading; trending.value = audius.indonesian() }
        viewModelScope.launch { playlists.value = Resource.Loading; playlists.value = audius.indoPlaylists() }
    }

    fun setQuery(q: String) { query.value = q; search(450) }
    fun setCategory(c: Category) { category.value = if (category.value == c) null else c; search(0) }

    private fun search(delayMs: Long) {
        job?.cancel()
        job = viewModelScope.launch {
            delay(delayMs)
            val q = query.value.trim()
            val c = category.value
            if (q.isEmpty() && c == null) { results.value = Resource.Empty; return@launch }
            results.value = Resource.Loading
            results.value = if (q.isNotEmpty()) searchAuto(q) else categoryAuto(c!!)
        }
    }

    /**
     * Otomatis memilih sumber: Audius dulu; kalau lagunya tidak ada di Audius pindah ke YouTube Music;
     * kalau YouTube gagal/limit/kosong kembali ke Audius.
     */
    private suspend fun searchAuto(q: String): Resource<List<Track>> {
        val a = audius.searchSmart(q, strict = true)
        if (a is Resource.Success) return a
        val y = yt.search(q)
        if (y is Resource.Success) return y
        val a2 = audius.searchSmart(q)
        return if (a2 is Resource.Success || y !is Resource.Error) a2 else y
    }

    private suspend fun categoryAuto(c: Category): Resource<List<Track>> {
        val a = audius.category(c.label, c.query)
        if (a is Resource.Success) return a
        val y = yt.category(c.label, c.query)
        return if (y is Resource.Success) y else a
    }

    /** Bila lagu YouTube gagal diputar (limit/diblokir), cari versi yang sama di Audius dan putar itu. */
    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val p = PlayerHolder.player(getApplication())
            val i = p.currentMediaItemIndex
            val t = PlayerHolder.queue.getOrNull(i) ?: return
            if (t.source != MusicSource.YOUTUBE) return
            viewModelScope.launch {
                val alt = (audius.searchSmart("${t.title} ${t.artist}", strict = true) as? Resource.Success)?.data?.firstOrNull()
                if (alt != null) {
                    PlayerHolder.replace(getApplication(), i, store.resolve(alt))
                    say("YouTube gagal, diputar dari Audius")
                } else say("Lagu tidak bisa diputar")
            }
        }
    }

    override fun onCleared() { PlayerHolder.player(getApplication()).removeListener(playerListener) }

    /** Lagu yang sudah di-download diputar dari file hasil download, sisanya stream dari API. */
    fun play(queue: List<Track>, index: Int) {
        queue.getOrNull(index)?.let { store.addRecent(it) }
        PlayerHolder.play(getApplication(), queue.map { store.resolve(it) }, index)
    }

    fun playPlaylist(p: Playlist) {
        viewModelScope.launch { (audius.playlistTracks(p.id) as? Resource.Success)?.let { play(it.data, 0) } }
    }

    // ---- Favorit ----
    fun toggleFavorite(t: Track) {
        say(if (store.toggleFavorite(t)) "Ditambahkan ke Favorit" else "Dihapus dari Favorit")
    }

    // ---- Playlist Saya ----
    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        store.createPlaylist(n)
        say("Playlist \"$n\" dibuat")
    }

    fun createPlaylistWith(name: String, t: Track) {
        val n = name.trim()
        if (n.isEmpty()) return
        store.addToPlaylist(store.createPlaylist(n), t)
        say("Ditambahkan ke \"$n\"")
    }

    fun addToPlaylist(pid: String, t: Track) {
        say(if (store.addToPlaylist(pid, t)) "Ditambahkan ke playlist" else "Lagu sudah ada di playlist")
    }

    fun removeFromPlaylist(pid: String, tid: String) = store.removeFromPlaylist(pid, tid)
    fun deletePlaylist(pid: String) { store.deletePlaylist(pid); say("Playlist dihapus") }

    // ---- Download (audio diambil dari API Audius, disimpan untuk diputar offline) ----
    fun download(track: Track) {
        if (track.source == MusicSource.YOUTUBE) { say("Download tidak tersedia untuk lagu YouTube Music"); return }
        val id = track.id
        if (lib.value.isDownloaded(id)) { say("Sudah ada di Download"); return }
        if (downloading.value.containsKey(id)) return
        val t = track.copy(streamUri = audiusStreamUrl(id))
        downloading.update { it + (id to 0) }
        viewModelScope.launch(Dispatchers.IO) {
            val part = store.partFor(id)
            try {
                audiusHttpClient.newCall(Request.Builder().url(t.streamUri).build()).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                    val body = r.body ?: throw java.io.IOException("Respons kosong")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(32 * 1024)
                            var done = 0L
                            var lastPct = -2
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                if (pct != lastPct) { lastPct = pct; downloading.update { m -> m + (id to pct) } }
                            }
                        }
                    }
                }
                if (!part.renameTo(store.fileFor(id))) throw java.io.IOException("Gagal menyimpan file")
                store.addDownload(t)
                say("Download selesai")
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (e: Exception) {
                part.delete()
                say("Download gagal, periksa koneksi")
            } finally {
                downloading.update { it - id }
            }
        }
    }

    fun deleteDownload(id: String) { store.removeDownload(id); say("Download dihapus") }
}
