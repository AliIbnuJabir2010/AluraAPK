package com.alura.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.IOException

/** Kontrak sama untuk kedua sumber, sehingga UI/player tidak peduli asal lagu. */
interface MusicRepository {
    suspend fun trending(genre: String? = null): Resource<List<Track>>
    suspend fun searchTracks(query: String): Resource<List<Track>>
}

private suspend fun <T> safe(block: suspend () -> List<T>): Resource<List<T>> = try {
    val r = withTimeout(20_000) { block() }
    if (r.isEmpty()) Resource.Empty else Resource.Success(r)
} catch (e: TimeoutCancellationException) { Resource.Error(Resource.Kind.TIMEOUT, "Koneksi terlalu lama")
} catch (e: IOException) { Resource.Error(Resource.Kind.NETWORK, "Tidak ada koneksi internet")
} catch (e: HttpException) { Resource.Error(Resource.Kind.SERVER, "Server error (${e.code()})")
} catch (e: Exception) { Resource.Error(Resource.Kind.UNKNOWN, e.message ?: "Terjadi kesalahan") }

class AudiusRepository(private val api: AudiusApi = audiusApi) : MusicRepository {
    private data class Cached(val at: Long, val tracks: List<Track>)
    private val cache = HashMap<String, Cached>()
    private val ttlMs = 5 * 60_000L

    private fun TrackDto.toDomain() = Track(
        id = id, title = title, artist = user.name,
        artworkUrl = artwork?.medium ?: artwork?.small,
        durationSec = duration, source = MusicSource.AUDIUS,
        streamUri = "${AUDIUS_BASE}tracks/$id/stream", genre = genre,
    )

    override suspend fun trending(genre: String?): Resource<List<Track>> {
        val key = "trending:${genre.orEmpty()}"
        cache[key]?.takeIf { System.currentTimeMillis() - it.at < ttlMs }?.let { return Resource.Success(it.tracks) }
        return safe { api.trending(genre).data.filter { it.isStreamable }.map { it.toDomain() } }
            .also { if (it is Resource.Success) cache[key] = Cached(System.currentTimeMillis(), it.data) }
    }

    override suspend fun searchTracks(query: String) =
        safe { api.searchTracks(query).data.filter { it.isStreamable }.map { it.toDomain() } }

    suspend fun searchArtists(query: String): Resource<List<Artist>> = safe {
        api.searchUsers(query).data.map { Artist(it.id, it.name, it.handle, it.profilePicture?.medium, it.followerCount) }
    }
    suspend fun trendingPlaylists(): Resource<List<Playlist>> = safe {
        api.trendingPlaylists().data.map { Playlist(it.id, it.name, it.artwork?.medium, it.trackCount) }
    }
    suspend fun playlistTracks(id: String) = safe { api.playlistTracks(id).data.map { it.toDomain() } }
    suspend fun artistTracks(id: String) = safe { api.userTracks(id).data.map { it.toDomain() } }
}

/** Offline: baca audio perangkat via MediaStore. Butuh izin READ_MEDIA_AUDIO (API 33+) / READ_EXTERNAL_STORAGE. */
class LocalRepository(private val context: Context) : MusicRepository {
    private suspend fun all(): List<Track> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        val out = mutableListOf<Track>()
        context.contentResolver.query(uri, cols, "${MediaStore.Audio.Media.IS_MUSIC}=1", null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Track(id = "local:$id", title = c.getString(1) ?: "Tanpa judul",
                    artist = c.getString(2) ?: "Unknown", artworkUrl = null,
                    durationSec = (c.getLong(3) / 1000).toInt(), source = MusicSource.LOCAL,
                    streamUri = ContentUris.withAppendedId(uri, id).toString())
            }
        }
        out
    }
    override suspend fun trending(genre: String?) = safe { all().take(50) }   // "terbaru" untuk lokal
    override suspend fun searchTracks(query: String) =
        safe { all().filter { it.title.contains(query, true) || it.artist.contains(query, true) } }
}
