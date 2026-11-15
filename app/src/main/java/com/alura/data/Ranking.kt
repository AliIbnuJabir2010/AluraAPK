package com.alura.data

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Aturan prioritas lagu Indonesia & urutan hasil pencarian.
 * API Audius tidak punya filter bahasa/negara, jadi lagu Indonesia dikenali dari
 * lokasi artis, nama artis (daftar di bawah), tag/deskripsi, dan kata umum di judul.
 */

/** Artis Indonesia untuk dicari di Beranda & dikenali di hasil. Tambah/ubah sesuai selera. */
val INDO_ARTISTS = listOf(
    "hindia", "sal priadi", "juicy luicy", "nadin amizah", "pamungkas", "fourtwnty", "tulus",
    "feast", "lomba sihir", "raisa", "isyana sarasvati", "kunto aji", "bernadya", "barasuara",
    "payung teduh", "efek rumah kaca", "mocca", "reality club", "the adams", "rendy pandugo",
    "denny caknan", "tiara andini", "mahalini", "lyodra", "dewa 19", "sheila on 7", "nidji",
)

/** Kueri umum + beberapa artis, dipakai untuk mengisi Beranda dengan lagu Indonesia. */
val INDO_QUERIES = listOf("lagu indonesia", "indo pop", "indie indonesia", "Hindia", "Nadin Amizah")

private val INDO_PLACES = setOf(
    "indonesia", "jakarta", "bandung", "surabaya", "yogyakarta", "jogja", "bali", "medan", "semarang",
    "malang", "makassar", "depok", "bekasi", "tangerang", "bogor", "palembang", "denpasar", "batam",
)

private val INDO_WORDS = setOf(
    "aku", "kamu", "kau", "kita", "cinta", "rindu", "hati", "yang", "dan", "untuk", "dengan", "dalam",
    "akan", "tanpa", "tidak", "bukan", "pulang", "hujan", "senja", "langit", "sendiri", "kenangan",
    "pergi", "kembali", "hanya", "sayang", "selalu", "rumah", "mimpi", "waktu", "maaf", "semesta",
    "doa", "janji", "palsu", "hidup", "berakhir", "usahakan", "ramai", "sepi", "bersama", "membasuh",
    "bahagia", "luka", "selamat", "terima", "kasih", "dunia", "malam", "pagi", "matahari", "bulan",
    "bintang", "laut", "angin", "jiwa", "harap", "asmara", "kisah", "cerita", "rasa", "belum", "sudah",
    "masih", "cuma", "hilang", "ingin", "semoga", "damai", "tanah", "nusantara", "galau",
)

fun String.norm(): String =
    lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

/** 0 = tidak terdeteksi sebagai lagu Indonesia; makin besar makin yakin. */
fun TrackDto.indoScore(): Int {
    var s = 0
    val loc = user.location.orEmpty().norm().split(' ')
    if (loc.any { it in INDO_PLACES }) s += 3
    val artist = user.name.norm()
    if (INDO_ARTISTS.any { artist == it || artist.startsWith("$it ") }) s += 3
    val meta = " ${(tags.orEmpty() + " " + description.orEmpty()).norm()} "
    if (" indonesia " in meta || " indo " in meta || " nusantara " in meta || " bahasa indonesia " in meta) s += 2
    val hits = title.norm().split(' ').count { it in INDO_WORDS }
    s += min(hits, 3)
    return s
}

fun TrackDto.isIndo() = indoScore() >= 1

private fun fieldScore(field: String, q: String, toks: List<String>, exact: Int, starts: Int, has: Int, all: Int): Int {
    if (field.isEmpty() || q.isEmpty()) return 0
    val ft = field.split(' ').toSet()
    return when {
        field == q -> exact
        field.startsWith(q) -> starts
        " $q " in " $field " -> has
        toks.all { it in ft } -> all
        else -> {
            val c = toks.count { it in ft }
            if (c > 0) 15 + 15 * c / toks.size else 0
        }
    }
}

/** Skor kecocokan teks (tanpa bonus): judul & nama artis dibandingkan dengan kueri. */
fun TrackDto.textScore(query: String): Int {
    val q = query.norm()
    if (q.isEmpty()) return 0
    val toks = q.split(' ')
    val t = title.norm()
    val a = user.name.norm()
    var best = max(fieldScore(t, q, toks, 100, 80, 60, 50), fieldScore(a, q, toks, 90, 70, 50, 40))
    // "membasuh hindia": judul + artis bersama-sama mencakup semua kata
    if (toks.size > 1) {
        val both = "$t $a".split(' ').toSet()
        if (toks.all { it in both }) best = max(best, 95)
    }
    return best
}

/** Skor akhir: kecocokan teks + bonus lagu Indonesia + sedikit bobot popularitas. */
fun TrackDto.rank(query: String): Double =
    textScore(query) + (if (isIndo()) 10 else 0) + min(5.0, log10(playCount + 1.0))

/** Maksimal [n] lagu per artis supaya Beranda tidak dikuasai satu orang. */
fun List<TrackDto>.limitPerArtist(n: Int): List<TrackDto> {
    val cnt = HashMap<String, Int>()
    return filter { val c = cnt[it.user.id] ?: 0; cnt[it.user.id] = c + 1; c < n }
}
