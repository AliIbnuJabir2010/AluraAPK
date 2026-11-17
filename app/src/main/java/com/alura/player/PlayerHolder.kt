package com.alura.player

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alura.data.Track
import com.alura.data.YouTubeMusic
import com.alura.data.audiusHttpClient

data class PS(val track: Track?, val playing: Boolean, val pos: Long, val dur: Long)

/** Satu player untuk stream Audius dan salinan hasil download, dipakai UI dan service. */
object PlayerHolder {
    private var exo: ExoPlayer? = null
    private val _queueFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Track>>(emptyList())
    val queueFlow: kotlinx.coroutines.flow.StateFlow<List<Track>> = _queueFlow
    var queue: List<Track> = emptyList()
        private set(v) { field = v; _queueFlow.value = v }

    fun peek(): ExoPlayer? = exo

    fun player(ctx: Context): ExoPlayer = exo ?: ExoPlayer.Builder(ctx.applicationContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                ResolvingDataSource.Factory(
                    DefaultDataSource.Factory(ctx.applicationContext, OkHttpDataSource.Factory(audiusHttpClient))
                ) { spec ->
                    // ytm://<videoId> -> alamat audio YouTube asli (diambil saat mulai diputar)
                    if (spec.uri.scheme == "ytm") spec.withUri(YouTubeMusic.audioUrl(spec.uri.host.orEmpty()).toUri()) else spec
                }
            )
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 2_000, 5_000).build()
        )
        .build().also { p ->
            exo = p
            com.alura.data.AppSettings.init(ctx)
            val app = ctx.applicationContext
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { PlayerWidget.refresh(app); IslandOverlay.refresh(app) }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { PlayerWidget.refresh(app); IslandOverlay.refresh(app, expand = true) }
            })
        }

    fun play(ctx: Context, tracks: List<Track>, index: Int) {
        val p = player(ctx)
        queue = tracks
        p.setMediaItems(tracks.map { it.toItem() }, index, 0L)
        p.prepare()
        p.play()
        runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) }
    }

    /** Ganti lagu di antrean (mis. YouTube gagal -> versi Audius) lalu putar lagi. */
    fun replace(ctx: Context, index: Int, t: Track) {
        val p = player(ctx)
        if (index !in queue.indices) return
        queue = queue.toMutableList().also { it[index] = t }
        p.replaceMediaItem(index, t.toItem())
        p.prepare()
        p.play()
    }

    /** Sisipkan tepat setelah lagu yang sedang diputar. */
    fun addNext(ctx: Context, t: Track) {
        val p = player(ctx)
        if (queue.isEmpty() || p.mediaItemCount == 0) { play(ctx, listOf(t), 0); return }
        val at = (p.currentMediaItemIndex + 1).coerceIn(0, queue.size)
        queue = queue.toMutableList().also { it.add(at, t) }
        p.addMediaItem(at, t.toItem())
    }

    fun addLast(ctx: Context, t: Track) {
        val p = player(ctx)
        if (queue.isEmpty() || p.mediaItemCount == 0) { play(ctx, listOf(t), 0); return }
        queue = queue + t
        p.addMediaItem(t.toItem())
    }

    fun move(ctx: Context, from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        player(ctx).moveMediaItem(from, to)
        queue = queue.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun removeAt(ctx: Context, i: Int) {
        if (i !in queue.indices) return
        player(ctx).removeMediaItem(i)
        queue = queue.toMutableList().also { it.removeAt(i) }
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
