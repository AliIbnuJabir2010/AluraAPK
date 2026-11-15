package com.alura.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

/** Jembatan HTTP untuk NewPipeExtractor (tanpa header API key Audius). */
private class YtDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NpRequest): NpResponse {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val b = okhttp3.Request.Builder().url(request.url()).method(method, body)
        b.header("User-Agent", UA)
        request.headers().forEach { (k, vs) ->
            b.removeHeader(k)
            vs.forEach { b.addHeader(k, it) }
        }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            return NpResponse(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}

/**
 * YouTube Music lewat NewPipeExtractor (TIDAK RESMI): cari lagu dan ambil alamat audio saat diputar.
 * Bisa rusak sewaktu-waktu bila YouTube mengubah sistemnya; update versi NewPipeExtractor bila itu terjadi.
 */
object YouTubeMusic {
    @Volatile private var ready = false

    @Synchronized
    private fun ensureInit() {
        if (!ready) {
            NewPipe.init(YtDownloader(), Localization("id", "ID"), ContentCountry("ID"))
            ready = true
        }
    }

    fun search(query: String): List<Track> {
        ensureInit()
        val ex = ServiceList.YouTube.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""
        )
        ex.fetchPage()
        return ex.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }
    }

    private fun StreamInfoItem.toTrack(): Track? {
        val vid = url.substringAfter("v=", "").substringBefore("&")
        if (vid.isEmpty()) return null
        return Track(
            id = "yt_$vid", title = name,
            artist = uploaderName.orEmpty().removeSuffix(" - Topic"),
            artworkUrl = thumbnails.lastOrNull()?.url,
            durationSec = duration.coerceAtLeast(0).toInt(),
            streamUri = "ytm://$vid", source = MusicSource.YOUTUBE,
        )
    }

    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** Dipanggil pemutar (thread pemuat) saat mulai memutar; alamat audio YouTube kedaluwarsa, jadi di-cache 3 jam. */
    fun audioUrl(videoId: String): String {
        urlCache[videoId]?.takeIf { System.currentTimeMillis() - it.second < 3 * 3600_000L }?.let { return it.first }
        ensureInit()
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: Exception) {
            throw IOException("Gagal memuat audio YouTube: ${e.message}", e)
        }
        val audios = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val best = audios.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: audios.maxByOrNull { it.averageBitrate }
            ?: throw IOException("Audio tidak ditemukan")
        urlCache[videoId] = best.content to System.currentTimeMillis()
        return best.content
    }
}
