package com.alura.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.alura.R
import com.alura.data.audiusHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request

/** Widget layar utama: sampul, judul, artis, dan kontrol putar. */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        val p = PlayerHolder.peek()
        if (p != null && p.mediaItemCount > 0) when (intent.action) {
            ACTION_TOGGLE -> if (p.isPlaying) p.pause() else p.play()
            ACTION_NEXT -> p.seekToNextMediaItem()
            ACTION_PREV -> p.seekToPreviousMediaItem()
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.alura.widget.TOGGLE"
        private const val ACTION_NEXT = "com.alura.widget.NEXT"
        private const val ACTION_PREV = "com.alura.widget.PREV"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var artUrl: String? = null
        private var artBitmap: Bitmap? = null

        private fun action(ctx: Context, a: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, a.hashCode(), Intent(ctx, PlayerWidget::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        internal fun loadArt(url: String?): Bitmap? {
            if (url == null) return null
            if (url == artUrl) return artBitmap
            return runCatching {
                audiusHttpClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val bytes = r.body?.bytes() ?: return@use null
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                    Bitmap.createScaledBitmap(bmp, 200, 200, true)
                }
            }.getOrNull().also { artUrl = url; artBitmap = it }
        }

        /** Gambar ulang semua widget sesuai lagu yang sedang diputar. */
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val p = PlayerHolder.peek()
            val track = p?.let { PlayerHolder.queue.getOrNull(it.currentMediaItemIndex) }
            val playing = p?.isPlaying == true
            scope.launch {
                val v = RemoteViews(app.packageName, R.layout.widget_player)
                v.setTextViewText(R.id.widget_title, track?.title ?: "Belum ada lagu")
                v.setTextViewText(R.id.widget_artist, track?.artist ?: "Ketuk untuk membuka Alura")
                v.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
                loadArt(track?.artworkUrl)?.let { v.setImageViewBitmap(R.id.widget_art, it) }
                    ?: v.setImageViewResource(R.id.widget_art, android.R.color.transparent)
                v.setOnClickPendingIntent(R.id.widget_play, action(app, ACTION_TOGGLE))
                v.setOnClickPendingIntent(R.id.widget_next, action(app, ACTION_NEXT))
                v.setOnClickPendingIntent(R.id.widget_prev, action(app, ACTION_PREV))
                val open = app.packageManager.getLaunchIntentForPackage(app.packageName)
                if (open != null) v.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
                mgr.updateAppWidget(ids, v)
            }
        }
    }
}
