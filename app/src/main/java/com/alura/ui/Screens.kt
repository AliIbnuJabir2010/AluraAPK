package com.alura.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.alura.R
import com.alura.data.MusicSource
import com.alura.data.Playlist
import com.alura.data.Resource
import com.alura.data.Track
import com.alura.player.PS

internal val BOTTOM = 190.dp

@Composable
private fun QuickTile(title: String, p: Playlist?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(8.dp)).background(Card2).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).background(
                Brush.linearGradient(
                    if (p == null) listOf(Color(0xFF5B3FE0), Color(0xFF8FE3D0)) else listOf(Color(0xFF1D3B6E), Color(0xFF0B1222))
                )
            ),
            Alignment.Center
        ) {
            if (title == "Lagu yang Disukai") Ico(Icons.HEART_FILL, Color.White, 24.dp)
            else if (p?.artworkUrl != null) AsyncImage(p.artworkUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Ico(Icons.PLAY, Color.White, 24.dp)
        }
        Text(
            title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun AutomaticTile(title: String, subtitle: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(CardC)
            .border(1.dp, Line, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(symbol, fontSize = 22.sp)
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 10.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, ps: PS, onOpen: (LibPage) -> Unit) {
    val tr by vm.trending.collectAsStateWithLifecycle()
    val pls by vm.playlists.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val trList = (tr as? Resource.Success)?.data.orEmpty()
    val plList = (pls as? Resource.Success)?.data.orEmpty().take(5)
    // Hasil sorting/statistik tidak perlu dihitung ulang saat posisi lagu berubah.
    val recent = remember(lib) { vm.recentlyPlayed.take(3).ifEmpty { lib.recent.take(3) } }
    val popular = remember(lib) { vm.mostPlayed.take(3) }
    val mix = remember(lib, tr) { vm.yourMix }
    val cur = ps.track?.id
    val tiles = remember(plList) {
        buildList<Pair<String, Playlist?>> {
            add("Lagu yang Disukai" to null)
            plList.forEach { add(it.name to it) }
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, BOTTOM)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (name, playlist) ->
                            QuickTile(name, playlist, Modifier.weight(1f)) {
                                if (playlist == null) onOpen(LibPage.FAVORITES) else vm.playPlaylist(playlist)
                            }
                        }
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionTitle("Playlist Otomatis") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomaticTile("Sering Diputar", "${vm.mostPlayed.size} lagu", "🔥", Modifier.weight(1f)) { onOpen(LibPage.MOST_PLAYED) }
                AutomaticTile("Baru Didengarkan", "${vm.recentlyPlayed.size} lagu", "🕘", Modifier.weight(1f)) { onOpen(LibPage.RECENTLY_PLAYED) }
                AutomaticTile("Mix Kamu", "${mix.size} lagu", "✨", Modifier.weight(1f)) { onOpen(LibPage.YOUR_MIX) }
            }
        }
        if (popular.isNotEmpty()) {
            item { SectionTitle("Lagi sering kamu putar") }
            itemsIndexed(popular, key = { _, t -> "popular_${t.id}" }) { i, t ->
                TrackRow(t, t.id == cur, vm) { vm.play(popular, i) }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Baru Didengarkan") }
            itemsIndexed(recent, key = { _, t -> "recent_${t.id}" }) { i, t ->
                TrackRow(t, t.id == cur, vm) { vm.play(recent, i) }
            }
        }
        item { SectionTitle("Lagu Indonesia") }
        item {
            when {
                trList.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(trList.take(10), key = { _, t -> t.id }) { i, t ->
                        Column(Modifier.width(140.dp).clickable { vm.play(trList, i) }) {
                            Art(t, 140.dp, 8.dp)
                            Text(t.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            Text(t.artist, fontSize = 11.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                tr is Resource.Loading -> CircularProgressIndicator(color = Acc, modifier = Modifier.size(24.dp))
                tr is Resource.Error -> Column {
                    Text((tr as Resource.Error).message, color = Mut, fontSize = 13.sp)
                    TextButton(onClick = { vm.refresh() }) { Text("Coba lagi", color = Acc) }
                }
                else -> Text("Belum ada lagu.", color = Mut, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, ps: PS) {
    val q by vm.query.collectAsStateWithLifecycle()
    val cat by vm.category.collectAsStateWithLifecycle()
    val res by vm.results.collectAsStateWithLifecycle()
    val tr by vm.trending.collectAsStateWithLifecycle()
    val shown = if (q.isBlank() && cat == null) tr else res
    val queue = (shown as? Resource.Success)?.data.orEmpty()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, BOTTOM)) {
        item {
            BasicTextField(
                value = q, onValueChange = vm::setQuery, singleLine = true,
                textStyle = TextStyle(color = Txt, fontSize = 14.sp), cursorBrush = SolidColor(Acc),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardC)
                        .border(1.dp, Line, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        if (q.isEmpty()) Text("Cari lagu, artis, atau album\u2026", color = Mut, fontSize = 14.sp)
                        inner()
                    }
                }
            )
        }
        item { SectionTitle("Kategori") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CATEGORIES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            val sel = cat == c
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(CardC)
                                    .border(1.dp, if (sel) Acc else Line, RoundedCornerShape(14.dp))
                                    .clickable { vm.setCategory(c) }.padding(vertical = 16.dp),
                                Alignment.Center
                            ) { Text(c.label, fontSize = 12.sp, color = if (sel) Acc else Txt) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hasil", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (shown is Resource.Success) Text("${shown.data.size} lagu", color = Mut, fontSize = 11.sp)
            }
        }
        trackSection(vm, shown, queue, 0, ps.track?.id,
            "Tidak ada hasil. Tidak semua lagu populer tersedia di Audius.", { vm.setQuery(q) }) { qq, i -> vm.play(qq, i) }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, onOpen: (LibPage) -> Unit, onAbout: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(18.dp, 18.dp, 18.dp, BOTTOM),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painterResource(R.drawable.ali_avatar), null,
            Modifier.padding(top = 8.dp).size(90.dp).clip(CircleShape).border(2.dp, Color.White.copy(.4f), CircleShape),
            contentScale = ContentScale.Crop
        )
        Text("Ali Ibnu Jabir", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text("@alibnujabir", color = Mut, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth()) {
            MenuRow("Playlist Saya", "${lib.playlists.size} Playlist") { onOpen(LibPage.PLAYLISTS) }
            MenuRow("Favorit", "${lib.favorites.size} Lagu") { onOpen(LibPage.FAVORITES) }
            MenuRow("Download", "${lib.downloads.size} Lagu (offline)") { onOpen(LibPage.DOWNLOADS) }
            MenuRow("Tentang Alura", "Versi, developer, dan dukungan", onAbout)
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(14.dp, 16.dp, 14.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), Alignment.Center) { Ico(Icons.BACK, Txt) }
            Text("Tentang Alura", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp, 8.dp, 18.dp, 120.dp)) {
            Box(
                Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(22.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(.14f)), RoundedCornerShape(22.dp))
            ) {
                Image(painterResource(R.drawable.ali_banner), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070B14).copy(.05f), Color(0xFF070B14).copy(.92f)))))
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Acc.copy(.28f), Color.Transparent))))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text("ALI IBNU JABIR", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text("DEVELOPER", fontSize = 10.sp, color = Color(0xFF9FB6E6), letterSpacing = 1.4.sp)
                }
                Image(
                    painterResource(R.drawable.ali_avatar), null,
                    Modifier.align(Alignment.BottomEnd).padding(14.dp).size(62.dp).clip(CircleShape)
                        .border(2.dp, Color.White.copy(.55f), CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoRow("Versi Aplikasi", "v1.0.0")
            InfoRow("Sumber Musik", "Audius")
            InfoRow("Developed by", "Ali Ibnu Jabir")
        }
    }
}

@Composable
fun PlayerScreen(vm: AppViewModel, ps: PS, p: Player, onBack: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val dl by vm.downloading.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf(false) }
    var lyr by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    fun fmt(ms: Long): String { val s = (ms / 1000).toInt(); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
    Column(Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}.statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), Alignment.Center) { Ico(Icons.BACK, Txt) }
            Text("Sekarang Diputar", fontWeight = FontWeight.SemiBold)
            Box(Modifier.size(40.dp).clickable { lyr = !lyr }, Alignment.Center) { Ico(Icons.LYRICS, Txt) }
        }
        Spacer(Modifier.height(18.dp))
        Art(ps.track, null, 22.dp, Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(.85f).aspectRatio(1f))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ps.track?.title.orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(ps.track?.artist.orEmpty(), color = Mut, fontSize = 13.sp, maxLines = 1)
            }
            ps.track?.let { t ->
                val fav = lib.isFav(t.id)
                val saved = lib.isDownloaded(t.id)
                val pct = dl[t.id]
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.toggleFavorite(t) }, Alignment.Center) {
                    Ico(if (fav) Icons.HEART_FILL else Icons.HEART, if (fav) Acc else Mut, 24.dp)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { pick = true }, Alignment.Center) {
                    Ico(Icons.PLUS, Mut, 24.dp)
                }
                if (t.source != MusicSource.YOUTUBE) Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.download(t) }, Alignment.Center) {
                    if (pct != null) Text(if (pct >= 0) "$pct%" else "\u2026", color = Acc, fontSize = 12.sp)
                    else Ico(Icons.DOWNLOAD, if (saved) Acc else Mut, 24.dp)
                }
            }
        }
        ps.track?.let { if (pick) AddToPlaylistDialog(vm, it) { pick = false } }
        if (lyr) Text("Lirik tidak tersedia untuk lagu ini.", color = Mut, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        Slider(
            value = drag ?: if (ps.dur > 0) (ps.pos.toFloat() / ps.dur).coerceIn(0f, 1f) else 0f,
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { p.seekTo((it * ps.dur).toLong()) }; drag = null },
            colors = SliderDefaults.colors(thumbColor = Acc, activeTrackColor = Acc, inactiveTrackColor = Line)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(ps.pos), color = Mut, fontSize = 12.sp)
            Text(fmt(ps.dur), color = Mut, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable { p.seekToPreviousMediaItem() }, Alignment.Center) { Ico(Icons.PREV, Txt, 26.dp) }
            Spacer(Modifier.width(18.dp))
            Box(Modifier.size(62.dp).clip(CircleShape).background(Acc).clickable { if (p.isPlaying) p.pause() else p.play() }, Alignment.Center) {
                Ico(if (ps.playing) Icons.PAUSE else Icons.PLAY, Color.White, 28.dp)
            }
            Spacer(Modifier.width(18.dp))
            Box(Modifier.size(48.dp).clickable { p.seekToNextMediaItem() }, Alignment.Center) { Ico(Icons.NEXT, Txt, 26.dp) }
        }
    }
}
