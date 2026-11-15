package com.alura.data

import com.alura.BuildConfig
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

const val AUDIUS_BASE = "https://api.audius.co/v1/"

interface AudiusApi {
    @GET("tracks/search") suspend fun searchTracks(@Query("query") q: String, @Query("limit") limit: Int = 30, @Query("sort_method") sort: String? = null): Envelope<List<TrackDto>>
    @GET("users/search") suspend fun searchUsers(@Query("query") q: String, @Query("limit") limit: Int = 20): Envelope<List<UserDto>>
    @GET("playlists/search") suspend fun searchPlaylists(@Query("query") q: String, @Query("limit") limit: Int = 20): Envelope<List<PlaylistDto>>
    @GET("tracks/trending") suspend fun trending(@Query("genre") genre: String? = null, @Query("time") time: String = "week", @Query("limit") limit: Int = 30): Envelope<List<TrackDto>>
    @GET("playlists/trending") suspend fun trendingPlaylists(@Query("time") time: String = "week"): Envelope<List<PlaylistDto>>
    @GET("tracks/{id}") suspend fun track(@Path("id") id: String): Envelope<TrackDto>
    @GET("users/{id}") suspend fun user(@Path("id") id: String): Envelope<UserDto>
    @GET("users/{id}/tracks") suspend fun userTracks(@Path("id") id: String, @Query("limit") limit: Int = 20, @Query("sort_method") sort: String? = "plays", @Query("sort_direction") dir: String? = "desc"): Envelope<List<TrackDto>>
    @GET("playlists/{id}/tracks") suspend fun playlistTracks(@Path("id") id: String): Envelope<List<TrackDto>>
}

/** Client dipakai bersama oleh Retrofit dan ExoPlayer agar header x-api-key ikut terkirim saat streaming. */
val audiusHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
        if (req.url.host.endsWith("audius.co")) {
            chain.proceed(req.newBuilder().addHeader("x-api-key", BuildConfig.AUDIUS_API_KEY).build())
        } else chain.proceed(req)
        }
        .build()
}

val audiusApi: AudiusApi by lazy {
    Retrofit.Builder()
        .baseUrl(AUDIUS_BASE)
        .client(audiusHttpClient)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AudiusApi::class.java)
}
