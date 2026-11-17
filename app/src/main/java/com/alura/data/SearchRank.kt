package com.alura.data

import kotlin.math.log10
import kotlin.math.min

private val COVER_RE = Regex(
    """\b(cover|covered|karaoke|tribute|remix|koplo|dj|nightcore|slowed|speed up|sped up|8d|instrumental|reggae|ukulele)\b"""
)

/** Judul/artis bertanda cover, remix, karaoke, dll. */
fun Track.isCoverish() = COVER_RE.containsMatchIn("$title $artist".lowercase())

private fun isKnownArtist(an: String) = INDO_ARTISTS.any { an == it || an.startsWith("$it ") }

/** Seberapa sering user memutar/menyukai tiap artis (kunci: nama artis ternormalisasi). */
fun artistAffinity(d: LibraryData): Map<String, Int> {
    val m = HashMap<String, Int>()
    fun add(t: Track, n: Int) { val k = t.artist.norm(); if (k.isNotEmpty()) m[k] = (m[k] ?: 0) + n }
    d.stats.forEach { add(it.track, it.playCount) }
    d.favorites.forEach { add(it, 2) }
    d.downloads.forEach { add(it, 2) }
    d.recent.forEach { add(it, 1) }
    return m
}

/**
 * Urutan hasil: cocok dengan kueri, artis disebut di kueri, populer (penonton/pemutaran),
 * artis yang sering didengar user, dan cover diturunkan.
 */
fun rankTracks(tracks: List<Track>, query: String, aff: Map<String, Int>): List<Track> {
    val qt = query.norm().split(' ').filter { it.isNotEmpty() }
    if (qt.isEmpty()) return tracks
    val q = " ${qt.joinToString(" ")} "
    val wantsCover = "cover" in qt
    fun score(t: Track): Double {
        val hay = "${t.title} ${t.artist}".norm().split(' ').toSet()
        var s = 100.0 * qt.count { it in hay } / qt.size
        val an = t.artist.norm()
        if (an.isNotEmpty() && q.contains(" $an ")) s += 60
        s += log10(t.popularity + 1.0) * 8
        s += min(30, (aff[an] ?: 0) * 6)
        if (isKnownArtist(an)) s += 15
        if (!wantsCover && t.isCoverish()) s -= 40
        return s
    }
    return tracks.sortedByDescending(::score)
}

/** Lagu dari artis aslinya (bukan cover): artis disebut di kueri, artis dikenal/sering didengar, atau sangat populer. */
fun isOriginalTrack(t: Track, query: String, aff: Map<String, Int>): Boolean {
    if (t.isCoverish()) return false
    val an = t.artist.norm()
    if (an.isEmpty()) return false
    if (" ${query.norm()} ".contains(" $an ")) return true
    return isKnownArtist(an) || (aff[an] ?: 0) > 0 || t.popularity >= 5_000_000L
}
