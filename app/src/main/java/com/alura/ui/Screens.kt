package com.alura.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.alura.data.AppSettings
import com.alura.data.MusicSource
import com.alura.data.Playlist
import com.alura.data.Resource
import com.alura.data.Track
import com.alura.data.TrackStats
import com.alura.player.PS

internal val BOTTOM = 190.dp

@Composable
private fun QuickTile(title: String, p: Playlist?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(4.dp)).background(Card2).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).background(
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
            title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun CoverCard(t: Track, onClick: () -> Unit) {
    Column(Modifier.width(164.dp).clickable(onClick = onClick)) {
        Art(t, 164.dp, 4.dp)
        Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(t.artist, fontSize = 12.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, ps: PS, onOpen: (LibPage) -> Unit) {
    val tr by vm.trending.collectAsStateWithLifecycle()
    val pls by vm.playlists.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val trList = (tr as? Resource.Success)?.data.orEmpty()
    val plList = (pls as? Resource.Success)?.data.orEmpty().take(5)
    val recent = remember(lib) { vm.recentlyPlayed.take(10).ifEmpty { lib.recent.take(10) } }
    val popular = remember(lib) { vm.mostPlayed.take(3) }
    val cur = ps.track?.id
    val tiles = remember(plList) {
        buildList<Pair<String, Playlist?>> {
            add("Lagu yang Disukai" to null)
            plList.forEach { add(it.name to it) }
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, BOTTOM)) {
        if (pls is Resource.Loading) item { PlaylistSkeletonRow() }
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
        if (popular.isNotEmpty()) {
            item { SectionTitle("Sering kamu putar baru-baru ini") }
            itemsIndexed(popular, key = { _, t -> "popular_${t.id}" }) { i, t ->
                TrackRow(t, t.id == cur, vm) { vm.play(popular, i) }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Baru saja didengar") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(recent, key = { _, t -> "recent_${t.id}" }) { i, t ->
                        CoverCard(t) { vm.play(recent, i) }
                    }
                }
            }
        }
        item { SectionTitle("Lagu Indonesia") }
        item {
            when {
                trList.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(trList.take(10), key = { _, t -> t.id }) { i, t ->
                        CoverCard(t) { vm.play(trList, i) }
                    }
                }
                tr is Resource.Loading -> TrackSkeletonList(count = 3)
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
    val base = if (q.isBlank() && cat == null) tr else res
    val filter by vm.searchFilter.collectAsStateWithLifecycle()
    val shown = if (base is Resource.Success && q.isNotBlank() && filter != 0) {
        val l = base.data.filter { vm.isOriginal(it) == (filter == 1) }
        if (l.isEmpty()) Resource.Empty else Resource.Success(l)
    } else base
    val queue = (shown as? Resource.Success)?.data.orEmpty()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, BOTTOM)) {
        item {
            BasicTextField(
                value = q, onValueChange = vm::setQuery, singleLine = true,
                textStyle = TextStyle(color = Color(0xFF121212), fontSize = 16.sp), cursorBrush = SolidColor(Color(0xFF121212)),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ico(Icons.SEARCH, Color(0xFF121212), 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text("Cari lagu, artis, atau album", color = Color(0xFF6A6A6A), fontSize = 16.sp)
                            inner()
                        }
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
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (sel) Color.White else Card2)
                                    .clickable { vm.setCategory(c) }.padding(vertical = 16.dp),
                                Alignment.Center
                            ) { Text(c.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color(0xFF121212) else Txt) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (q.isNotBlank()) item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Semua", "Artis Asli", "Cover").forEachIndexed { i, label ->
                    val sel = filter == i
                    Box(
                        Modifier.clip(CircleShape).background(if (sel) Color.White else Card2)
                            .clickable { vm.setSearchFilter(i) }.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color(0xFF121212) else Txt) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Hasil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (shown is Resource.Success) Text("${shown.data.size} lagu", color = Mut, fontSize = 11.sp)
            }
        }
        trackSection(vm, shown, queue, 0, ps.track?.id,
            "Tidak ada hasil. Tidak semua lagu populer tersedia di Audius.", { vm.setQuery(q) }) { qq, i -> vm.play(qq, i) }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, onOpen: (LibPage) -> Unit, onAbout: () -> Unit, onSettings: () -> Unit, onRecap: () -> Unit = {}) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val localReady by vm.localReady.collectAsStateWithLifecycle()
    val ready by vm.libraryReady.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(18.dp, 18.dp, 18.dp, BOTTOM),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painterResource(R.drawable.ali_avatar), null,
            Modifier.padding(top = 8.dp).size(90.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Text("Ali Ibnu Jabir", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text("@alibnujabir", color = Mut, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth()) {
            if (!ready) {
                TrackSkeletonList(count = 3)
            } else {
                MenuRow("Playlist Saya", "${lib.playlists.size} Playlist") { onOpen(LibPage.PLAYLISTS) }
                MenuRow("Favorit", "${lib.favorites.size} Lagu") { onOpen(LibPage.FAVORITES) }
                MenuRow("Download", "${lib.downloads.size} Lagu (offline)") { onOpen(LibPage.DOWNLOADS) }
            }
            MenuRow("Rekap Dengar", "Artis dan lagu favoritmu", onRecap)
            MenuRow("Tentang Alura", "Versi, developer, dan dukungan", onAbout)
            MenuRow("Pengaturan", "Tampilan dan preferensi aplikasi", onSettings)
        }
    }
}


@Composable
private fun SettingToggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp)).background(CardC)
            .clickable { onChange(!checked) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(sub, fontSize = 12.sp, color = Mut)
        }
        androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = Acc, checkedThumbColor = Color.White,
                uncheckedTrackColor = Card2, uncheckedThumbColor = Mut, uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingGroup(label: String) {
    Text(label, color = Mut, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
fun ExtraSettings(vm: AppViewModel) {
    val ctx = LocalContext.current
    var island by remember { mutableStateOf(AppSettings.island) }
    var autoLyr by remember { mutableStateOf(AppSettings.autoLyrics) }
    var blur by remember { mutableStateOf(AppSettings.blurCover) }
    var low by remember { mutableStateOf(AppSettings.lowData) }
    var wifi by remember { mutableStateOf(AppSettings.wifiOnly) }
    fun toast(s: String) = android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show()

    SettingGroup("PEMUTAR")
    SettingToggle("Pulau melayang", "Pil kecil di atas layar saat musik diputar", island) {
        island = it; AppSettings.island = it
        if (it && !android.provider.Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            toast("Izinkan \"Tampil di atas aplikasi lain\" untuk Alura")
        }
        com.alura.player.IslandOverlay.refresh(ctx, expand = true)
    }
    SettingToggle("Buka lirik otomatis", "Panel lirik langsung terbuka di pemutar", autoLyr) { autoLyr = it; AppSettings.autoLyrics = it }
    SettingToggle("Latar sampul buram", "Latar pemutar mengikuti sampul lagu", blur) { blur = it; AppSettings.blurCover = it }

    SettingGroup("PEMUTARAN")
    SettingToggle("Hemat data", "Audio YouTube memakai kualitas lebih rendah", low) { low = it; AppSettings.lowData = it }

    SettingGroup("DOWNLOAD")
    SettingToggle("Hanya lewat WiFi", "Download diblokir saat memakai data seluler", wifi) { wifi = it; AppSettings.wifiOnly = it }
    MenuRow("Hapus semua download", "Lagu yang diunduh dihapus dari HP") { vm.deleteAllDownloads(); toast("Semua download dihapus") }

    SettingGroup("DATA")
    MenuRow("Reset Rekap Dengar", "Hitungan putar dan waktu dengar dikosongkan") { vm.resetRecap(); toast("Rekap direset") }
    MenuRow("Bersihkan cache lirik", "Lirik diambil ulang saat dibuka") { com.alura.data.LyricsRepo.clearCache(); toast("Cache lirik dibersihkan") }
}

@Composable
fun SettingsScreen(currentStyle: AluraStyle, onStyleChange: (AluraStyle) -> Unit, extra: @Composable () -> Unit = {}, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(14.dp, 16.dp, 14.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), Alignment.Center) { Ico(Icons.BACK, Txt) }
            Text("Pengaturan", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp, 8.dp, 18.dp, 120.dp)) {
            Text("TAMPILAN", color = Mut, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text("Pilih gaya Alura", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            StyleChoice(
                title = "Liquid Glass", subtitle = "Nuansa kaca transparan, biru, dan modern",
                selected = currentStyle == AluraStyle.LIQUID_GLASS,
                preview = Color(0xFF14243D), accent = Color(0xFF4F8DFF)
            ) { onStyleChange(AluraStyle.LIQUID_GLASS) }
            Spacer(Modifier.height(10.dp))
            StyleChoice(
                title = "Paper", subtitle = "Terang seperti kertas, bersih, dan nyaman dibaca",
                selected = currentStyle == AluraStyle.PAPER,
                preview = Color(0xFFF5F1E8), accent = Color(0xFF315E9B)
            ) { onStyleChange(AluraStyle.PAPER) }
            Spacer(Modifier.height(22.dp))
            Text("TENTANG TAMPILAN", color = Mut, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text("Pilihan tema disimpan otomatis dan tetap digunakan saat Alura dibuka kembali.", color = Mut, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(18.dp))
            InfoRow("Tema aktif", if (currentStyle == AluraStyle.LIQUID_GLASS) "Liquid Glass" else "Paper")
            extra()
        }
    }
}

@Composable
private fun StyleChoice(
    title: String, subtitle: String, selected: Boolean,
    preview: Color, accent: Color, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(CardC).border(1.dp, if (selected) Acc else Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(76.dp, 62.dp).clip(RoundedCornerShape(12.dp)).background(preview).padding(8.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(.62f).height(7.dp).clip(CircleShape).background(accent))
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(.12f)))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.weight(1f).height(7.dp).clip(CircleShape).background(Color.White.copy(.2f))) }
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 11.sp, color = Mut, modifier = Modifier.padding(top = 3.dp))
        }
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Acc, unselectedColor = Mut))
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
            InfoRow("Sumber Musik", "Audius · YouTube Music · Lokal")
            InfoRow("Developed by", "Ali Ibnu Jabir")
        }
    }
}

private fun brandName(): String {
    val m = android.os.Build.MANUFACTURER.orEmpty().trim()
    return if (m.isNotEmpty() && m == m.lowercase()) m.replaceFirstChar { it.uppercase() } else m
}

/** Bar tipis: tanpa thumb untuk progres lagu, dengan thumb bulat untuk volume. */
@Composable
private fun SeekBar(value: Float, thumb: Boolean, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val v = value.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun frac(x: Float) = (x / size.width).coerceIn(0f, 1f)
                onChange(frac(down.position.x))
                drag(down.id) { onChange(frac(it.position.x)); it.consume() }
                onDone()
            }
        },
        Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(.3f)))
        Box(Modifier.fillMaxWidth(v).height(4.dp).clip(CircleShape).background(Color.White))
        if (thumb) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(maxOf(v, 0.001f)))
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.weight(maxOf(1f - v, 0.001f)))
        }
    }
}

@Composable
private fun Speaker(waves: Boolean) {
    Box(Modifier.size(22.dp), Alignment.Center) {
        Ico(Icons.SPEAKER, Color.White.copy(.7f), 22.dp)
        if (waves) Ico(Icons.WAVES, Color.White.copy(.7f), 22.dp)
    }
}

@Composable
private fun LyricsPanel(res: Resource<com.alura.data.Lyrics>, position: Long, onSeek: (Long) -> Unit) {
    Box(Modifier.fillMaxWidth().height(230.dp), Alignment.Center) {
        when (res) {
            Resource.Loading -> Text("Memuat lirik\u2026", color = Color.White.copy(.6f), fontSize = 14.sp)
            Resource.Empty -> Text("Lirik tidak tersedia", color = Color.White.copy(.6f), fontSize = 14.sp)
            is Resource.Error -> Text(res.message, color = Color.White.copy(.6f), fontSize = 14.sp, textAlign = TextAlign.Center)
            is Resource.Success -> {
                val ly = res.data
                val idx = if (ly.synced) ly.lines.indexOfLast { it.timeMs <= position + 250 } else -1
                val st = rememberLazyListState()
                LaunchedEffect(idx) { if (idx >= 0) st.animateScrollToItem(maxOf(idx - 1, 0)) }
                LazyColumn(Modifier.fillMaxSize(), st) {
                    itemsIndexed(ly.lines) { i, l ->
                        val cur = i == idx
                        Text(
                            l.text,
                            fontSize = if (ly.synced) 20.sp else 16.sp,
                            fontWeight = if (ly.synced) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White.copy(if (!ly.synced || cur) 1f else .4f),
                            modifier = Modifier.fillMaxWidth()
                                .then(if (ly.synced) Modifier.clickable { onSeek(l.timeMs) } else Modifier)
                                .padding(vertical = if (ly.synced) 8.dp else 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueDialog(vm: AppViewModel, ps: PS, onDismiss: () -> Unit) {
    val q by com.alura.player.PlayerHolder.queueFlow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val curIdx = com.alura.player.PlayerHolder.player(ctx).currentMediaItemIndex
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(CardC).padding(16.dp)) {
            Text("Antrean", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (q.isEmpty()) Text("Antrean kosong", color = Mut, fontSize = 14.sp)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(q) { i, t ->
                    val cur = i == curIdx && t.id == ps.track?.id
                    Row(
                        Modifier.fillMaxWidth().clickable { com.alura.player.PlayerHolder.player(ctx).seekTo(i, 0L) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(t, 40.dp, 4.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontSize = 14.sp, color = if (cur) Acc else Txt, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, fontSize = 12.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(32.dp).clickable { vm.moveInQueue(i, i - 1) }, Alignment.Center) { Ico(Icons.UP, Mut, 18.dp) }
                        Box(Modifier.size(32.dp).clickable { vm.moveInQueue(i, i + 1) }, Alignment.Center) { Ico(Icons.DOWN, Mut, 18.dp) }
                        Box(Modifier.size(32.dp).clickable { vm.removeFromQueue(i) }, Alignment.Center) { Ico(Icons.CLOSE, Mut, 18.dp) }
                    }
                }
            }
        }
    }
}

private fun fmtDuration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60} jam ${m % 60} menit" else "$m menit"
}

private data class ArtistRec(val name: String, val plays: Int, val ms: Long, val track: Track)

@Composable
fun RecapScreen(vm: AppViewModel, onBack: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val stats = lib.stats.filter { it.playCount > 0 }
    val totalMs = stats.sumOf { it.totalListenMs }
    val totalPlays = stats.sumOf { it.playCount }
    val topTracks = stats.sortedWith(compareByDescending<TrackStats> { it.playCount }.thenByDescending { it.totalListenMs }).take(10)
    val artists = stats.groupBy { it.track.artist }.map { (name, l) ->
        ArtistRec(name, l.sumOf { it.playCount }, l.sumOf { it.totalListenMs }, l.maxByOrNull { it.playCount }!!.track)
    }.sortedWith(compareByDescending<ArtistRec> { it.plays }.thenByDescending { it.ms }).take(10)

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(14.dp, 16.dp, 14.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), Alignment.Center) { Ico(Icons.BACK, Txt) }
            Text("Rekap Dengar", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }
        if (stats.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Putar beberapa lagu dulu, rekapnya muncul di sini.", color = Mut, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp)) }
            return@Column
        }
        val top = artists.first()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 120.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(artTint(top.track), Color.Black, .2f), Card2)))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Artis teratasmu", fontSize = 13.sp, color = Color.White.copy(.75f))
                Spacer(Modifier.height(12.dp))
                Art(top.track, 140.dp, 70.dp)
                Spacer(Modifier.height(12.dp))
                Text(top.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                Text("${top.plays} kali didengar \u00B7 ${fmtDuration(top.ms)}", fontSize = 14.sp, color = Color.White.copy(.75f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(fmtDuration(totalMs) to "Waktu dengar", "$totalPlays" to "Kali putar", "${stats.size}" to "Lagu berbeda").forEach { (v, l) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(CardC).padding(12.dp)) {
                        Text(v, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(l, fontSize = 12.sp, color = Mut)
                    }
                }
            }
            SectionTitle("Peringkat artis")
            artists.forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (i == 0) Acc else Mut, modifier = Modifier.width(28.dp))
                    Art(a.track, 48.dp, 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(.12f))) {
                            Box(Modifier.fillMaxWidth(a.plays.toFloat() / top.plays).fillMaxHeight().background(if (i == 0) Acc else Color.White.copy(.55f)))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${a.plays}x", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(fmtDuration(a.ms), fontSize = 11.sp, color = Mut)
                    }
                }
            }
            SectionTitle("Lagu teratas")
            topTracks.forEachIndexed { i, st ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (i == 0) Acc else Mut, modifier = Modifier.width(28.dp))
                    Art(st.track, 48.dp, 4.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(st.track.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(st.track.artist, fontSize = 14.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${st.playCount}x", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(vm: AppViewModel, ps: PS, p: Player, onBack: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    val dl by vm.downloading.collectAsStateWithLifecycle()
    val (position, duration) = rememberPlaybackProgress(p, ps.playing)
    var pick by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var lyr by remember { mutableStateOf(AppSettings.autoLyrics) }
    var showQueue by remember { mutableStateOf(false) }
    val lyrics by produceState<Resource<com.alura.data.Lyrics>>(Resource.Loading, ps.track?.id, lyr) {
        val t = ps.track
        if (lyr && t != null) { value = Resource.Loading; value = com.alura.data.LyricsRepo.load(t) }
    }
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableStateOf(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    val brand = remember { brandName() }
    fun fmt(ms: Long): String { val s = (ms.coerceAtLeast(0) / 1000).toInt(); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

    Box(Modifier.fillMaxSize().background(artTint(ps.track).copy(alpha = .9f).compositeOver(Color.Black))) {
        val url = ps.track?.artworkUrl
        if (url != null && AppSettings.blurCover && android.os.Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                url, null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(48.dp),
                contentScale = ContentScale.Crop
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.35f)))
        Box(
            Modifier.statusBarsPadding().padding(8.dp).size(40.dp).clickable(onClick = onBack),
            Alignment.Center
        ) { Ico(Icons.BACK, Color.White) }

        Column(
            Modifier.align(Alignment.Center).navigationBarsPadding().padding(horizontal = 24.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(.3f)).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(ps.track, 56.dp, 6.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(brand, fontSize = 13.sp, color = Color.White.copy(.6f), maxLines = 1)
                    Text(ps.track?.title.orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ps.track?.artist.orEmpty(), fontSize = 15.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (lyr) {
                Spacer(Modifier.height(16.dp))
                LyricsPanel(lyrics, position) { p.seekTo(it) }
            }
            Spacer(Modifier.height(24.dp))
            SeekBar(
                value = drag ?: if (duration > 0) position.toFloat() / duration else 0f,
                thumb = false,
                onChange = { drag = it },
                onDone = { drag?.let { f -> p.seekTo((f * duration).toLong()) }; drag = null }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(position), color = Color.White.copy(.6f), fontSize = 12.sp)
                Text("-" + fmt(duration - position), color = Color.White.copy(.6f), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clickable { p.seekToPreviousMediaItem() }, Alignment.Center) { Ico(Icons.PREV, Color.White.copy(.5f), 34.dp) }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.size(64.dp).clickable { if (p.isPlaying) p.pause() else p.play() }, Alignment.Center) {
                    Ico(if (ps.playing) Icons.PAUSE else Icons.PLAY, Color.White, 38.dp)
                }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.size(56.dp).clickable { p.seekToNextMediaItem() }, Alignment.Center) { Ico(Icons.NEXT, Color.White.copy(.5f), 34.dp) }
            }
            if (!lyr) Row(verticalAlignment = Alignment.CenterVertically) {
                Speaker(false)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    SeekBar(
                        value = vol, thumb = true,
                        onChange = {
                            vol = it
                            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (it * maxVol).roundToInt(), 0)
                        },
                        onDone = {}
                    )
                }
                Spacer(Modifier.width(10.dp))
                Speaker(true)
            }
            ps.track?.let { t ->
                val fav = lib.isFav(t.id)
                val saved = lib.isDownloaded(t.id)
                val pct = dl[t.id]
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.toggleFavorite(t) }, Alignment.Center) {
                        Ico(if (fav) Icons.HEART_FILL else Icons.HEART, if (fav) Acc else Color.White.copy(.7f), 24.dp)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable { pick = true }, Alignment.Center) {
                        Ico(Icons.PLUS, Color.White.copy(.7f), 24.dp)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable { lyr = !lyr }, Alignment.Center) {
                        Ico(Icons.LYRICS, if (lyr) Acc else Color.White.copy(.7f), 24.dp)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable { showQueue = true }, Alignment.Center) {
                        Ico(Icons.QUEUE, Color.White.copy(.7f), 24.dp)
                    }
                    if (t.source != MusicSource.LOCAL) Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.download(t) }, Alignment.Center) {
                        if (pct != null) Text(if (pct >= 0) "$pct%" else "\u2026", color = Acc, fontSize = 12.sp)
                        else Ico(Icons.DOWNLOAD, if (saved) Acc else Color.White.copy(.7f), 24.dp)
                    }
                }
                if (pick) AddToPlaylistDialog(vm, t) { pick = false }
                if (showQueue) QueueDialog(vm, ps) { showQueue = false }
            }
        }
    }
}
