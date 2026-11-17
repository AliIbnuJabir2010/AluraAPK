package com.alura.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import retrofit2.HttpException
import java.io.IOException

private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

private suspend fun <T> safe(block: suspend () -> List<T>): Resource<List<T>> = try {
    val r = withTimeout(20_000) { block() }
    if (r.isEmpty()) Resource.Empty else Resource.Success(r)
} catch (e: TimeoutCancellationException) { Resource.Error(Resource.Kind.TIMEOUT, "Koneksi terlalu lama")
} catch (e: CancellationException) { throw e
} catch (e: IOException) { Resource.Error(Resource.Kind.NETWORK, "Tidak ada koneksi internet")
} catch (e: HttpException) { Resource.Error(Resource.Kind.SERVER, "Server error (${e.code()})")
} catch (e: Exception) { Resource.Error(Resource.Kind.UNKNOWN, e.message ?: "Terjadi kesalahan") }

class AudiusRepository(private val api: AudiusApi = audiusApi) {
    private data class Cached(val at: Long, val tracks: List<Track>)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    private val ttlMs = 5 * 60_000L

    private fun TrackDto.toDomain() = Track(
        id = id, title = title, artist = user.name,
        artworkUrl = artwork?.medium ?: artwork?.small,
        durationSec = duration, streamUri = audiusStreamUrl(id), genre = genre, artistId = user.id,
        popularity = playCount.toLong(),
    )

    private fun fresh(key: String) =
        cache[key]?.takeIf { System.currentTimeMillis() - it.at < ttlMs }?.tracks

    /** Jalankan beberapa pencarian paralel. Gagal sebagian diabaikan; gagal semua dilempar. */
    private suspend fun gather(queries: List<String>, limit: Int, sort: String?): List<TrackDto> = coroutineScope {
        val rs = queries.map { q -> async { attempt { api.searchTracks(q, limit, sort).data } } }.awaitAll()
        val ok = rs.filter { it.isSuccess }
        if (ok.isEmpty()) throw (rs.first().exceptionOrNull() ?: IOException("Gagal memuat"))
        ok.flatMap { it.getOrThrow() }
    }

    /** Beranda: hanya lagu Indonesia (lagu barat baru muncul kalau dicari). */
    suspend fun indonesian(): Resource<List<Track>> {
        fresh("indo")?.let { return Resource.Success(it) }
        return safe {
            gather(INDO_QUERIES, 15, "popular")
                .filter { it.isStreamable && it.isIndo() }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<TrackDto> { min3(it.indoScore()) }.thenByDescending { it.playCount })
                .limitPerArtist(3)
                .take(30)
                .map { it.toDomain() }
        }.also { if (it is Resource.Success) cache["indo"] = Cached(System.currentTimeMillis(), it.data) }
    }

    private fun min3(x: Int) = if (x > 3) 3 else x

    /** Kategori (Pop, Rock, ...): lagu Indonesia di urutan atas. */
    suspend fun category(label: String, query: String?): Resource<List<Track>> = safe {
        val qs = listOfNotNull(query, "$label indonesia", "$label indo").distinct()
        gather(qs, 30, "popular")
            .filter { it.isStreamable }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TrackDto> { if (it.isIndo()) 1 else 0 }.thenByDescending { it.playCount })
            .limitPerArtist(4)
            .take(40)
            .map { it.toDomain() }
    }

    /**
     * Pencarian bergaya Spotify: lagu yang judul/artisnya paling cocok di atas,
     * disusul lagu lain dari artis yang sama, lalu sisa hasil yang masih relevan.
     */
    suspend fun searchSmart(query: String, strict: Boolean = false): Resource<List<Track>> = safe {
        val q = query.trim()
        if (q.length < 2) return@safe emptyList()
        val raw = api.searchTracks(q, 40).data.filter { it.isStreamable }.distinctBy { it.id }
        val sorted = raw.sortedByDescending { it.rank(q) }
        val relevant = sorted.filter { it.textScore(q) > 0 }
        val pool = if (relevant.size >= 3) relevant else sorted

        val top = pool.firstOrNull()
        val hits = pool.filter { it.textScore(q) >= 50 }
        // strict: kalau tidak ada yang judul/artisnya cocok, anggap lagunya tidak ada di Audius
        if (strict && hits.isEmpty()) return@safe emptyList()
        val more = if (top != null && top.textScore(q) >= 50) {
            runCatching { api.userTracks(top.user.id, 20).data }.getOrDefault(emptyList())
                .filter { it.isStreamable }
        } else emptyList()

        (hits + more + pool).distinctBy { it.id }.take(50).map { it.toDomain() }
    }

    suspend fun searchArtists(query: String): Resource<List<Artist>> = safe {
        api.searchUsers(query).data.map { Artist(it.id, it.name, it.handle, it.profilePicture?.medium, it.followerCount) }
    }

    /** Playlist untuk Beranda: dicari dengan kata kunci Indonesia, cadangan trending global. */
    suspend fun indoPlaylists(): Resource<List<Playlist>> = safe {
        val rs = coroutineScope {
            listOf("indonesia", "lagu indonesia")
                .map { q -> async { attempt { api.searchPlaylists(q, 12).data } } }.awaitAll()
        }
        val found = rs.flatMap { it.getOrNull().orEmpty() }.filter { it.trackCount > 0 }.distinctBy { it.id }
        (if (found.isNotEmpty()) found else api.trendingPlaylists().data)
            .take(12).map { Playlist(it.id, it.name, it.artwork?.medium, it.trackCount) }
    }

    suspend fun playlistTracks(id: String) = safe { api.playlistTracks(id).data.map { it.toDomain() } }
    suspend fun artistTracks(id: String) = safe { api.userTracks(id).data.map { it.toDomain() } }
}

/** Sumber kedua: YouTube Music (tidak resmi, lewat NewPipeExtractor). Hanya putar, tanpa download. */
class YouTubeRepository {
    /** Lagu yang judulnya cocok di atas, disusul lagu lain dari artis yang sama, lalu sisa hasil. */
    suspend fun search(query: String): Resource<List<Track>> = safe {
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList<Track>()
            val nq = q.norm()
            val base = YouTubeMusic.search(q)
            val hits = base.filter { val t = it.title.norm(); t == nq || t.startsWith(nq) || " $nq " in " $t " }
            val anchor = hits.firstOrNull() ?: base.firstOrNull { it.artist.norm() == nq }
            val more = if (anchor != null && hits.size < 5) {
                val a = anchor.artist.norm()
                runCatching { YouTubeMusic.search(anchor.artist) }.getOrDefault(emptyList()).filter { it.artist.norm() == a }
            } else emptyList()
            (hits + more + base).distinctBy { it.id }.take(50)
        }
    }

    suspend fun category(label: String, query: String?) = search(query ?: "$label indonesia")
}


/** Musik lokal perangkat; pemindaian MediaStore selalu berjalan di thread IO. */
class LocalRepository(private val context: Context) {
    private suspend fun all(): List<Track> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
        )
        val tracks = ArrayList<Track>(128)
        context.contentResolver.query(
            uri, columns, "${MediaStore.Audio.Media.IS_MUSIC}=1", null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                tracks += Track(
                    id = "local:$id",
                    title = cursor.getString(titleCol) ?: "Tanpa judul",
                    artist = cursor.getString(artistCol) ?: "Unknown",
                    artworkUrl = null,
                    durationSec = (cursor.getLong(durationCol) / 1000L).toInt(),
                    streamUri = ContentUris.withAppendedId(uri, id).toString(),
                    source = MusicSource.LOCAL
                )
            }
        }
        tracks
    }

    suspend fun trending(genre: String? = null): Resource<List<Track>> =
        safe { all().take(80) }

    suspend fun searchTracks(query: String): Resource<List<Track>> = safe {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else all().filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
        }
    }
}
