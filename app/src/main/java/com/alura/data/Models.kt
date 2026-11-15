package com.alura.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MusicSource { AUDIUS, LOCAL }

/** Model domain tunggal: dipakai UI & player untuk online maupun offline. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSec: Int,
    val source: MusicSource,
    val streamUri: String,          // URL Audius atau content:// URI lokal
    val genre: String? = null,
)

data class Artist(val id: String, val name: String, val handle: String, val avatarUrl: String?, val followers: Int)
data class Playlist(val id: String, val name: String, val artworkUrl: String?, val trackCount: Int)

/** State seragam untuk loading/error/timeout/empty. */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data object Empty : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val kind: Kind, val message: String) : Resource<Nothing>
    enum class Kind { NETWORK, TIMEOUT, SERVER, UNKNOWN }
}

// ---- DTO Audius (semua respons dibungkus {"data": ...}) ----
@Serializable data class Envelope<T>(val data: T)
@Serializable data class ArtworkDto(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null,
)
@Serializable data class UserDto(
    val id: String, val name: String, val handle: String,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("profile_picture") val profilePicture: ArtworkDto? = null,
)
@Serializable data class TrackDto(
    val id: String, val title: String, val duration: Int = 0,
    val genre: String? = null, val artwork: ArtworkDto? = null, val user: UserDto,
    @SerialName("is_streamable") val isStreamable: Boolean = true,
)
@Serializable data class PlaylistDto(
    val id: String,
    @SerialName("playlist_name") val name: String,
    val artwork: ArtworkDto? = null,
    @SerialName("track_count") val trackCount: Int = 0,
)
