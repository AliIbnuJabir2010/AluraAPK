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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
    private val localRepo = LocalRepository(app)

    val trending = MutableStateFlow<Resource<List<Track>>>(Resource.Loading)
    val playlists = MutableStateFlow<Resource<List<Playlist>>>(Resource.Loading)
    val results = MutableStateFlow<Resource<List<Track>>>(Resource.Empty)
    val query = MutableStateFlow("")
    val category = MutableStateFlow<Category?>(null)
    val local = MutableStateFlow<List<Track>>(emptyList())
    val localReady = MutableStateFlow(false)

    /** Favorit, Playlist Saya, dan Download. */
    val lib: StateFlow<LibraryData> = store.data
    val libraryReady: StateFlow<Boolean> = store.ready
    /** id lagu -> persen download (0..100; -1 bila ukuran tidak diketahui). */
    val downloading = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Pesan singkat untuk Toast. */
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var job: Job? = null
    private var localJob: Job? = null
    private var trendingJob: Job? = null
    private var playlistJob: Job? = null
    private var statsJob: Job? = null
    private var sessionTrack: Track? = null
    private var sessionListenMs = 0L
    private var sessionQualified = false

    val mostPlayed: List<Track> get() = lib.value.stats
        .filter { it.playCount > 0 }
        .sortedByDescending { stat -> popularityScore(stat) }
        .map { it.track }.distinctBy { it.id }.take(30)

    val recentlyPlayed: List<Track> get() = lib.value.stats
        .filter { it.lastPlayedAt > 0L }
        .sortedByDescending { it.lastPlayedAt }
        .map { it.track }.distinctBy { it.id }.take(30)

    /** Mix lokal: 40% populer, 30% favorit, 20% baru didengar, 10% penemuan dari trending. */
    val yourMix: List<Track> get() {
        val popular = mostPlayed
        val favorites = lib.value.favorites
        val recent = recentlyPlayed
        val discovery = (trending.value as? Resource.Success)?.data.orEmpty()
            .filter { candidate -> lib.value.stats.none { it.track.id == candidate.id } }
        val target = 30
        val result = LinkedHashMap<String, Track>()
        fun addFrom(list: List<Track>, count: Int) {
            list.asSequence().filter { it.id !in result }.take(count).forEach { result[it.id] = it }
        }
        addFrom(popular, (target * 0.40).toInt())
        addFrom(favorites, (target * 0.30).toInt())
        addFrom(recent, (target * 0.20).toInt())
        addFrom(discovery, target - result.size)
        // Isi slot yang tersisa dari kandidat lain tanpa duplikat.
        addFrom(popular + favorites + recent + discovery, target - result.size)
        return result.values.toList()
    }

    private fun popularityScore(stat: TrackStats): Double {
        val ageHours = ((System.currentTimeMillis() - stat.lastPlayedAt).coerceAtLeast(0L) / 3_600_000.0)
        val recency = if (stat.lastPlayedAt == 0L) 0.0 else 10.0 * kotlin.math.exp(-ageHours / (24.0 * 7.0))
        return stat.playCount * 3.0 + recency - stat.skipCount * 2.0
    }

    private fun say(s: String) { toasts.tryEmit(s) }

    fun refresh() {
        if (trendingJob?.isActive != true) {
            trendingJob = viewModelScope.launch {
                if (trending.value !is Resource.Success) trending.value = Resource.Loading
                trending.value = audius.indonesian()
            }
        }
        if (playlistJob?.isActive != true) {
            playlistJob = viewModelScope.launch {
                if (playlists.value !is Resource.Success) playlists.value = Resource.Loading
                playlists.value = audius.indoPlaylists()
            }
        }
    }

    /** Dipanggil setelah izin audio perangkat diberikan; query MediaStore berlangsung di IO. */
    fun loadLocal() {
        if (localJob?.isActive == true) return
        localJob = viewModelScope.launch {
            localReady.value = false
            val result = localRepo.trending()
            local.value = (result as? Resource.Success)?.data.orEmpty()
            localReady.value = true
        }
    }

    fun setLocalUnavailable() {
        localReady.value = true
        local.value = emptyList()
    }

    /** 0 = Semua, 1 = Artis Asli, 2 = Cover */
    val searchFilter = MutableStateFlow(0)
    fun setSearchFilter(i: Int) { searchFilter.value = i }
    fun isOriginal(t: Track) = isOriginalTrack(t, query.value, artistAffinity(lib.value))

    fun setQuery(q: String) { query.value = q; search(450) }
    fun setCategory(c: Category) { category.value = if (category.value == c) null else c; search(0) }

    private fun search(delayMs: Long) {
        job?.cancel()
        val q = query.value.trim()
        val c = category.value
        if (q.isEmpty() && c == null) {
            results.value = Resource.Empty
            return
        }
        // Ganti hasil lama dengan skeleton sebelum debounce, bukan setelah menunggu.
        results.value = Resource.Loading
        job = viewModelScope.launch {
            delay(delayMs)
            if (q.isNotEmpty()) {
                val online = searchAuto(q)
                val onlineTracks = (online as? Resource.Success)?.data.orEmpty()
                val combined = onlineTracks.distinctBy { it.id }
                val ranked = rankTracks(combined, q, artistAffinity(lib.value))
                results.value = if (ranked.isNotEmpty()) Resource.Success(ranked) else online
            } else {
                results.value = Resource.Loading
                results.value = categoryAuto(c!!)
            }
        }
    }

    /**
     * Otomatis memilih sumber: Audius dulu; kalau lagunya tidak ada di Audius pindah ke YouTube Music;
     * kalau YouTube gagal/limit/kosong kembali ke Audius.
     */
    private suspend fun searchAuto(q: String): Resource<List<Track>> {
        val audiusResult = audius.searchSmart(q, strict = true)
        if (audiusResult is Resource.Success) return audiusResult

        // Jangan mengulang permintaan Audius yang sama setelah YouTube gagal.
        // Pengulangan itu hanya menambah waktu tunggu dan beban jaringan.
        val youtubeResult = yt.search(q)
        return when {
            youtubeResult is Resource.Success -> youtubeResult
            audiusResult is Resource.Error -> audiusResult
            else -> youtubeResult
        }
    }

    private suspend fun categoryAuto(c: Category): Resource<List<Track>> {
        val a = audius.category(c.label, c.query)
        if (a is Resource.Success) return a
        val y = yt.category(c.label, c.query)
        return if (y is Resource.Success) y else a
    }

    /** Bila lagu YouTube gagal diputar (limit/diblokir), cari versi yang sama di Audius dan putar itu. */
    private fun beginStatsSession(track: Track?) {
        sessionTrack = track
        sessionListenMs = 0L
        sessionQualified = false
        track?.let { store.addRecent(it) }
    }

    private fun finishStatsSession(wasSkipped: Boolean) {
        val old = sessionTrack ?: return
        if (wasSkipped && !sessionQualified && sessionListenMs < 30_000L) store.recordSkip(old)
        sessionTrack = null
        sessionListenMs = 0L
        sessionQualified = false
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val old = sessionTrack
            if (old != null && old.id != PlayerHolder.queue.getOrNull(PlayerHolder.player(getApplication()).currentMediaItemIndex)?.id) {
                finishStatsSession(wasSkipped = reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
            val index = PlayerHolder.player(getApplication()).currentMediaItemIndex
            val next = PlayerHolder.queue.getOrNull(index)
            if (next != null && sessionTrack?.id != next.id) beginStatsSession(next)
        }

        private var retryCount = 0

        override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) retryCount = 0 }

        override fun onPlayerError(error: PlaybackException) {
            val p = PlayerHolder.player(getApplication())
            val i = p.currentMediaItemIndex
            val t = PlayerHolder.queue.getOrNull(i) ?: return
            // Putus sambungan / alamat kedaluwarsa: coba lagi dari posisi terakhir (maks 2x) sebelum menyerah.
            if (retryCount < 2) {
                retryCount++
                if (t.source == MusicSource.YOUTUBE) com.alura.data.YouTubeMusic.invalidate(t.streamUri.removePrefix("ytm://"))
                val pos = p.currentPosition
                viewModelScope.launch {
                    delay(800L * retryCount)
                    p.seekTo(i, pos)
                    p.prepare()
                    p.play()
                }
                return
            }
            if (t.source != MusicSource.YOUTUBE) { say("Lagu tidak bisa diputar"); return }
            viewModelScope.launch {
                val alt = (audius.searchSmart("${t.title} ${t.artist}", strict = true) as? Resource.Success)?.data?.firstOrNull()
                if (alt != null) {
                    PlayerHolder.replace(getApplication(), i, store.resolve(alt))
                    say("YouTube gagal, diputar dari Audius")
                } else say("Lagu tidak bisa diputar")
            }
        }
    }

    init {
        refresh()
        val player = PlayerHolder.player(app)
        player.addListener(playerListener)
        statsJob = viewModelScope.launch {
            var lastPosition = player.currentPosition
            var lastTrackId: String? = null
            var pendingTrack: Track? = null
            var pendingListenMs = 0L
            while (isActive) {
                delay(5_000)
                val current = sessionTrack
                if (current?.id != lastTrackId) {
                    // Simpan sisa sesi sebelumnya hanya saat lagu berganti.
                    if (pendingTrack != null && pendingListenMs > 0L) {
                        store.addListenTime(pendingTrack!!, pendingListenMs)
                    }
                    pendingTrack = current
                    pendingListenMs = 0L
                    lastTrackId = current?.id
                    lastPosition = player.currentPosition
                }
                if (current != null && player.isPlaying) {
                    val now = player.currentPosition
                    val delta = (now - lastPosition).coerceIn(0L, 7_000L)
                    if (delta > 0L) {
                        sessionListenMs += delta
                        pendingTrack = current
                        pendingListenMs += delta
                        // Hindari update StateFlow + serialisasi JSON setiap 5 detik.
                        // Satu batch per sekitar 30 detik cukup untuk statistik dengar.
                        if (pendingListenMs >= 30_000L) {
                            store.addListenTime(current, pendingListenMs)
                            pendingListenMs = 0L
                        }
                        if (!sessionQualified && sessionListenMs >= 30_000L) {
                            store.recordQualifiedPlay(current)
                            sessionQualified = true
                        }
                    }
                    lastPosition = now
                } else {
                    lastPosition = player.currentPosition
                }
            }
        }
    }

    override fun onCleared() {
        statsJob?.cancel()
        PlayerHolder.player(getApplication()).removeListener(playerListener)
    }

    /** Lagu yang sudah di-download diputar dari file hasil download, sisanya stream dari API. */
    fun play(queue: List<Track>, index: Int) {
        val nextId = queue.getOrNull(index)?.id
        finishStatsSession(wasSkipped = sessionTrack?.id != null && sessionTrack?.id != nextId)
        PlayerHolder.play(getApplication(), queue.map { store.resolve(it) }, index)
        beginStatsSession(queue.getOrNull(index))
    }

    fun automaticPlaylistTracks(page: LibPage): List<Track> = when (page) {
        LibPage.MOST_PLAYED -> mostPlayed
        LibPage.RECENTLY_PLAYED -> recentlyPlayed
        LibPage.YOUR_MIX -> yourMix
        else -> emptyList()
    }

    fun playAutomatic(page: LibPage) {
        val tracks = automaticPlaylistTracks(page)
        if (tracks.isNotEmpty()) play(tracks, 0) else say("Belum cukup data untuk playlist ini")
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
        if (track.source == MusicSource.LOCAL) { say("Lagu ini sudah ada di perangkat"); return }
        if (AppSettings.wifiOnly && !onWifi()) { say("Download hanya lewat WiFi (lihat Pengaturan)"); return }
        val id = track.id
        if (lib.value.isDownloaded(id)) { say("Sudah ada di Download"); return }
        if (downloading.value.containsKey(id)) return
        val t = if (track.source == MusicSource.AUDIUS) track.copy(streamUri = audiusStreamUrl(id)) else track
        downloading.update { it + (id to 0) }
        viewModelScope.launch(Dispatchers.IO) {
            val part = store.partFor(id)
            try {
                val url = if (track.source == MusicSource.YOUTUBE) YouTubeMusic.audioUrl(track.streamUri.removePrefix("ytm://")) else t.streamUri
                audiusHttpClient.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 13)").build()).execute().use { r ->
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
                                if (pct >= 0 && (lastPct < 0 || pct >= lastPct + 5 || pct == 100)) {
                                    lastPct = pct
                                    downloading.update { m -> m + (id to pct) }
                                } else if (pct < 0 && lastPct != -1) {
                                    lastPct = -1
                                    downloading.update { m -> m + (id to -1) }
                                }
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

    fun resetRecap() = store.resetStats()
    fun deleteAllDownloads() { lib.value.downloads.toList().forEach { store.removeDownload(it.id) } }

    private fun onWifi(): Boolean {
        val cm = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    // ---- Impor playlist Spotify ----
    val importStatus = MutableStateFlow<String?>(null)
    private var importing = false

    private suspend fun matchTrack(title: String, artist: String, aff: Map<String, Int>): Track? {
        val q = "$artist $title".trim()
        val pool = (yt.search(q) as? Resource.Success)?.data.orEmpty()
            .ifEmpty { (audius.searchSmart(q) as? Resource.Success)?.data.orEmpty() }
        return rankTracks(pool, q, aff).firstOrNull()
    }

    fun importSpotify(link: String) {
        if (importing) { say("Impor sedang berjalan"); return }
        importing = true
        viewModelScope.launch {
            try {
                importStatus.value = "Mengambil playlist\u2026"
                val (name, items) = SpotifyImport.fetch(link)
                val pid = store.createPlaylist(name)
                val aff = artistAffinity(lib.value)
                var ok = 0
                items.chunked(4).forEachIndexed { ci, chunk ->
                    val found = coroutineScope { chunk.map { (title, artist) -> async { runCatching { matchTrack(title, artist, aff) }.getOrNull() } }.awaitAll() }
                    found.forEach { t -> if (t != null && store.addToPlaylist(pid, t)) ok++ }
                    importStatus.value = "Mengimpor ${minOf((ci + 1) * 4, items.size)}/${items.size}\u2026"
                }
                importStatus.value = "Selesai: $ok dari ${items.size} lagu masuk ke \"$name\""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importStatus.value = e.message ?: "Gagal mengimpor"
            } finally {
                importing = false
            }
        }
    }

    fun playNext(t: Track) { PlayerHolder.addNext(getApplication(), store.resolve(t)); say("Diputar berikutnya") }
    fun addToQueue(t: Track) { PlayerHolder.addLast(getApplication(), store.resolve(t)); say("Ditambahkan ke antrean") }
    fun moveInQueue(from: Int, to: Int) = PlayerHolder.move(getApplication(), from, to)
    fun removeFromQueue(i: Int) = PlayerHolder.removeAt(getApplication(), i)

    fun deleteDownload(id: String) { store.removeDownload(id); say("Download dihapus") }
}
