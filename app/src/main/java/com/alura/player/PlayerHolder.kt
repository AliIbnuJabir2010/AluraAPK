package com.alura.player

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alura.data.Track
import com.alura.data.audiusHttpClient

data class PS(val track: Track?, val playing: Boolean, val pos: Long, val dur: Long)

/** Satu player untuk Audius (online) dan file lokal (offline), dipakai UI dan service. */
object PlayerHolder {
    private var exo: ExoPlayer? = null
    var queue: List<Track> = emptyList()
        private set

    fun player(ctx: Context): ExoPlayer = exo ?: ExoPlayer.Builder(ctx.applicationContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(ctx.applicationContext, OkHttpDataSource.Factory(audiusHttpClient))
            )
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
        )
        .setHandleAudioBecomingNoisy(true)
        .build().also { exo = it }

    fun play(ctx: Context, tracks: List<Track>, index: Int) {
        val p = player(ctx)
        queue = tracks
        p.setMediaItems(tracks.map { it.toItem() }, index, 0L)
        p.prepare()
        p.play()
        runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) }
    }

    private fun Track.toItem() = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUri.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist)
                .setArtworkUri(artworkUrl?.toUri()).build()
        ).build()
}

/** Notifikasi media + kontrol di lock screen. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, PlayerHolder.player(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
