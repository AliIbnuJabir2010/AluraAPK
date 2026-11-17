package com.alura.player

import android.content.Context
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.alura.R
import com.alura.data.AppSettings

/** Pil melayang di atas layar (mirip Dynamic Island): tampil saat musik diputar, ketuk untuk membuka kontrol. */
object IslandOverlay {
    private var root: LinearLayout? = null
    private var art: ImageView? = null
    private var title: TextView? = null
    private var artist: TextView? = null
    private var controls: LinearLayout? = null
    private var playBtn: ImageView? = null
    private var expanded = false
    private var curUrl: String? = null
    private val h = Handler(Looper.getMainLooper())
    private val collapse = Runnable { expanded = false; applyMode() }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun refresh(ctx: Context, expand: Boolean = false) {
        val app = ctx.applicationContext
        val p = PlayerHolder.peek()
        val t = p?.let { PlayerHolder.queue.getOrNull(it.currentMediaItemIndex) }
        if (!AppSettings.island || !Settings.canDrawOverlays(app) || p == null || t == null || !p.isPlaying) {
            hide(app); return
        }
        if (root == null) build(app)
        title?.text = t.title
        artist?.text = t.artist
        if (t.artworkUrl != curUrl) {
            curUrl = t.artworkUrl
            val url = t.artworkUrl
            Thread {
                val bmp = PlayerWidget.loadArt(url)
                h.post { if (bmp != null) art?.setImageBitmap(bmp) }
            }.start()
        }
        if (expand) { expanded = true; h.removeCallbacks(collapse); h.postDelayed(collapse, 4000) }
        applyMode()
    }

    fun hide(ctx: Context) {
        h.removeCallbacks(collapse)
        val r = root ?: return
        runCatching { (ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(r) }
        root = null; curUrl = null; expanded = false
    }

    private fun icon(ctx: Context, res: Int, onClick: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res)
        val pad = dp(ctx, 6)
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36))
        setOnClickListener { onClick(); h.removeCallbacks(collapse); h.postDelayed(collapse, 4000) }
    }

    private fun build(ctx: Context) {
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6))
            background = GradientDrawable().apply { setColor(0xF2000000.toInt()); cornerRadius = dp(ctx, 40).toFloat() }
            elevation = dp(ctx, 6).toFloat()
        }
        art = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF2A2A2A.toInt())
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, v.height / 2f) }
            }
            clipToOutline = true
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 10), 0, dp(ctx, 4), 0)
        }
        title = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        artist = TextView(ctx).apply {
            setTextColor(0xFFA7A7A7.toInt()); textSize = 11f
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(title); texts.addView(artist)
        playBtn = icon(ctx, R.drawable.ic_w_pause) {
            PlayerHolder.peek()?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        controls = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(icon(ctx, R.drawable.ic_w_prev) { PlayerHolder.peek()?.seekToPreviousMediaItem() })
            addView(playBtn)
            addView(icon(ctx, R.drawable.ic_w_next) { PlayerHolder.peek()?.seekToNextMediaItem() })
        }
        r.addView(art); r.addView(texts); r.addView(controls)
        r.setOnClickListener {
            expanded = !expanded
            h.removeCallbacks(collapse)
            if (expanded) h.postDelayed(collapse, 4000)
            applyMode()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(ctx, 6) }
        runCatching {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(r, lp)
            root = r
        }
    }

    private fun applyMode() {
        val r = root ?: return
        val ctx = r.context
        val size = dp(ctx, if (expanded) 44 else 28)
        art?.layoutParams = LinearLayout.LayoutParams(size, size)
        title?.maxWidth = dp(ctx, if (expanded) 170 else 110)
        artist?.visibility = if (expanded) View.VISIBLE else View.GONE
        controls?.visibility = if (expanded) View.VISIBLE else View.GONE
    }
}
