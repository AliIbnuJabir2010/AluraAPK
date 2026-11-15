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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.alura.R
import com.alura.data.Resource
import com.alura.data.Track
import com.alura.player.PS

private val BOTTOM = 190.dp

@Composable
fun HomeScreen(vm: AppViewModel, ps: PS) {
    val tr by vm.trending.collectAsStateWithLifecycle()
    val pls by vm.playlists.collectAsStateWithLifecycle()
    val loc by vm.local.collectAsStateWithLifecycle()
    val trList = (tr as? Resource.Success)?.data.orEmpty()
    val all = loc + trList
    val cur = ps.track?.id
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, BOTTOM)) {
        item {
            Text("Halo,", color = Mut, fontSize = 12.sp)
            Text("Pendengar", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1C2B4D), Color(0xFF0D1424)))).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Nikmati musik favoritmu di Alura", fontWeight = FontWeight.Bold)
                    Text("Play. Feel. Repeat.", color = Mut, fontSize = 12.sp)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Acc).clickable { if (all.isNotEmpty()) vm.play(all, 0) },
                    Alignment.Center) { Ico(Icons.PLAY, Color.White, 18.dp) }
            }
        }
        item { SectionTitle("Playlist Pilihan") }
        item {
            when (val r = pls) {
                is Resource.Success -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(r.data, key = { it.id }) { p ->
                        Box(
                            Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF1D3B6E), Color(0xFF0B1222))))
                                .clickable { vm.playPlaylist(p) }
                        ) {
                            p.artworkUrl?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.7f)))))
                            Text(p.name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                        }
                    }
                }
                Resource.Loading -> CircularProgressIndicator(color = Acc, modifier = Modifier.size(24.dp))
                else -> Text("Playlist tidak tersedia", color = Mut, fontSize = 13.sp)
            }
        }
        item { SectionTitle("Lagu Terbaru") }
        itemsIndexed(loc, key = { _, t -> t.id }) { i, t -> TrackRow(t, t.id == cur) { vm.play(all, i) } }
        trackSection(tr, all, loc.size, cur, "Belum ada lagu.", { vm.refresh() }) { q, i -> vm.play(q, i) }
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
        trackSection(shown, queue, 0, ps.track?.id,
            "Tidak ada hasil. Tidak semua lagu populer tersedia di Audius.", { vm.setQuery(q) }) { qq, i -> vm.play(qq, i) }
    }
}

@Composable
fun ProfileScreen(onAbout: () -> Unit) {
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
            MenuRow("Playlist Saya", "12 Playlist")
            MenuRow("Favorit", "42 Lagu")
            MenuRow("Download", "8 Lagu (offline)")
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
            InfoRow("Sumber Musik", "Audius \u00B7 Lokal")
            InfoRow("Developed by", "Ali Ibnu Jabir")
        }
    }
}

@Composable
fun PlayerScreen(ps: PS, p: Player, onBack: () -> Unit) {
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
        Text(ps.track?.title.orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(ps.track?.artist.orEmpty(), color = Mut, fontSize = 13.sp, maxLines = 1)
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
