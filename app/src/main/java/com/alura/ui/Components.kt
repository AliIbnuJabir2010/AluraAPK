package com.alura.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.alura.data.MusicSource
import com.alura.data.Resource
import com.alura.data.Track
import com.alura.player.PS
import com.alura.player.PlayerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue

private fun snap(p: Player) = PS(
    PlayerHolder.queue.getOrNull(p.currentMediaItemIndex),
    p.isPlaying, p.currentPosition, p.duration.coerceAtLeast(0L)
)

@Composable
fun rememberPlayerState(p: Player): PS {
    var s by remember { mutableStateOf(snap(p)) }
    DisposableEffect(p) {
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { s = snap(player) }
        }
        p.addListener(l)
        onDispose { p.removeListener(l) }
    }
    // Jangan polling posisi saat player berhenti. Polling lebih jarang juga
    // mengurangi recomposition seluruh UI pada perangkat dengan RAM/CPU rendah.
    return s
}

/** Posisi lagu diperbarui hanya di layar pemutar, bukan di seluruh pohon UI. */
@Composable
fun rememberPlaybackProgress(player: Player, playing: Boolean): Pair<Long, Long> {
    var position by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                duration = player.duration.coerceAtLeast(0L)
                if (!player.isPlaying) position = player.currentPosition.coerceAtLeast(0L)
            }
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                position = newPosition.positionMs.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, playing) {
        if (playing) {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
                delay(750L)
            }
        } else {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
        }
    }
    return position to duration
}

private val PALETTE = listOf(0xFF2B4A85, 0xFF2F3A7A, 0xFF1F5560, 0xFF4A2F6E, 0xFF6E4A2F)

internal fun artTint(t: Track?): Color = Color(PALETTE[(t?.id?.hashCode() ?: 0).absoluteValue % PALETTE.size])

@Composable
fun Art(t: Track?, size: Dp?, radius: Dp, modifier: Modifier = Modifier) {
    val c = Color(PALETTE[(t?.id?.hashCode() ?: 0).absoluteValue % PALETTE.size])
    val url = t?.artworkUrl
    Box(
        (if (size != null) modifier.size(size) else modifier)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(c, Color(0xFF121212)))),
        Alignment.Center
    ) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("\u266A", color = Txt.copy(alpha = .6f), fontSize = 20.sp)
    }
}

@Composable
fun TrackRow(
    t: Track, current: Boolean, vm: AppViewModel,
    extra: List<Pair<String, () -> Unit>> = emptyList(),
    onClick: () -> Unit
) {
    // Setiap baris hanya mengamati flag miliknya sendiri. Perubahan statistik atau
    // progres download lagu lain tidak memicu semua baris untuk recomposition.
    val flagsFlow = remember(t.id) {
        vm.lib.map { it.isFav(t.id) to it.isDownloaded(t.id) }.distinctUntilChanged()
    }
    val flags by flagsFlow.collectAsStateWithLifecycle(false to false)
    val fav = flags.first
    val saved = flags.second
    val progressFlow = remember(t.id) {
        vm.downloading.map { it[t.id] }.distinctUntilChanged()
    }
    val pct by progressFlow.collectAsStateWithLifecycle<Int?>(null)
    // Local snapshot of delegated state; Kotlin can smart-cast this value.
    val progressPercent = pct
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    val sub = if (progressPercent != null) {
        t.artist + if (progressPercent >= 0) " \u00B7 Mengunduh $progressPercent%" else " \u00B7 Mengunduh\u2026"
    } else t.artist
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(t, 48.dp, 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 16.sp, color = if (current) Acc else Txt,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 14.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (fav) {
            Box(Modifier.size(38.dp).clip(CircleShape).clickable { vm.toggleFavorite(t) }, Alignment.Center) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Acc), Alignment.Center) {
                    Ico(Icons.CHECK, Color(0xFF121212), 14.dp)
                }
            }
        }
        Box(Modifier.size(38.dp).clip(CircleShape).clickable { menu = true }, Alignment.Center) {
            Text("\u22EE", color = Mut, fontSize = 20.sp)
            DropdownMenu(menu, { menu = false }, modifier = Modifier.background(Card2)) {
                DropdownMenuItem(
                    text = { Text(if (fav) "Hapus dari Favorit" else "Tambah ke Favorit", color = Txt) },
                    onClick = { menu = false; vm.toggleFavorite(t) }
                )
                DropdownMenuItem(
                    text = { Text("Putar berikutnya", color = Txt) },
                    onClick = { menu = false; vm.playNext(t) }
                )
                DropdownMenuItem(
                    text = { Text("Tambah ke antrean", color = Txt) },
                    onClick = { menu = false; vm.addToQueue(t) }
                )
                DropdownMenuItem(
                    text = { Text("+ Daftar putar", color = Txt) },
                    onClick = { menu = false; pick = true }
                )
                if (t.source != MusicSource.LOCAL) DropdownMenuItem(
                    text = { Text(if (saved) "Hapus download" else "Download", color = Txt) },
                    onClick = { menu = false; if (saved) vm.deleteDownload(t.id) else vm.download(t) }
                )
                extra.forEach { (label, act) ->
                    DropdownMenuItem(text = { Text(label, color = Txt) }, onClick = { menu = false; act() })
                }
            }
        }
    }
    if (pick) AddToPlaylistDialog(vm, t) { pick = false }
}

@Composable
fun NameField(value: String, onChange: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = Txt, fontSize = 14.sp), cursorBrush = SolidColor(Acc),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardC)
                .border(1.dp, Line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (value.isEmpty()) Text(hint, color = Mut, fontSize = 14.sp)
                inner()
            }
        }
    )
}

@Composable
fun NameDialog(title: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card2, titleContentColor = Txt, textContentColor = Txt,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = { NameField(name, { name = it }, "Nama playlist") },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) { onOk(name); onDismiss() } }) { Text(confirm, color = Acc) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Mut) } }
    )
}

@Composable
fun AddToPlaylistDialog(vm: AppViewModel, t: Track, onDismiss: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    val none = lib.playlists.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card2, titleContentColor = Txt, textContentColor = Txt,
        title = { Text(if (none) "Buat playlist dulu" else "Pilih playlist", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            if (none) {
                Column {
                    Text("Kamu belum punya playlist.", color = Mut, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    NameField(name, { name = it }, "Nama playlist")
                }
            } else {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    lib.playlists.forEach { p ->
                        Text(
                            "${p.name} \u00B7 ${p.tracks.size}", fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id, t); onDismiss() }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        // Setelah playlist dibuat, dialog otomatis berganti ke daftar pilihan playlist
        confirmButton = {
            if (none) TextButton(onClick = { if (name.isNotBlank()) { vm.createPlaylist(name); name = "" } }) {
                Text("Buat", color = Acc)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Mut) } }
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
}

@Composable
fun MenuRow(title: String, sub: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp)).background(CardC)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Mut)
        }
        if (onClick != null) Ico(Icons.CHEV, Mut, 18.dp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(CardC).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 12.sp, color = Mut)
    }
}

/** Skeleton shimmer dibuat dengan satu animasi untuk seluruh placeholder agar tetap ringan. */
@Composable
fun TrackSkeletonList(count: Int = 5) {
    val transition = rememberInfiniteTransition(label = "trackSkeleton")
    val offset by transition.animateFloat(
        initialValue = -260f,
        targetValue = 520f,
        animationSpec = infiniteRepeatable(animation = tween(1250, easing = LinearEasing)),
        label = "skeletonOffset"
    )
    val shimmer = remember(offset, CardC, Card2) {
        Brush.linearGradient(
            colors = listOf(Card2, CardC.copy(alpha = 0.92f), Card2),
            start = Offset(offset, 0f),
            end = Offset(offset + 220f, 70f)
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        repeat(count) { index ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(shimmer))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(if (index % 2 == 0) 0.72f else 0.58f).height(11.dp)
                        .clip(RoundedCornerShape(6.dp)).background(shimmer))
                    Box(Modifier.fillMaxWidth(0.42f).height(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(shimmer))
                }
                Box(Modifier.size(26.dp).clip(CircleShape).background(shimmer))
            }
        }
    }
}

@Composable
fun PlaylistSkeletonRow() {
    val transition = rememberInfiniteTransition(label = "playlistSkeleton")
    val offset by transition.animateFloat(
        initialValue = -180f,
        targetValue = 420f,
        animationSpec = infiniteRepeatable(animation = tween(1250, easing = LinearEasing)),
        label = "playlistSkeletonOffset"
    )
    val shimmer = remember(offset, CardC, Card2) {
        Brush.linearGradient(listOf(Card2, CardC, Card2), Offset(offset, 0f), Offset(offset + 160f, 70f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) { Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(shimmer)) }
    }
}

/** Menampilkan Loading / Empty / Error / daftar lagu. onPlay menerima antrean + indeks lagu yang diketuk. */
fun LazyListScope.trackSection(
    vm: AppViewModel, res: Resource<List<Track>>, queue: List<Track>, offset: Int, curId: String?,
    emptyMsg: String, onRetry: () -> Unit, onPlay: (List<Track>, Int) -> Unit
) {
    when (res) {
        Resource.Loading -> item(key = "track_skeleton") { TrackSkeletonList() }
        Resource.Empty -> item { Text(emptyMsg, color = Mut, fontSize = 13.sp) }
        is Resource.Error -> item {
            Column {
                Text(res.message, color = Mut, fontSize = 13.sp)
                TextButton(onClick = onRetry) { Text("Coba lagi", color = Acc) }
            }
        }
        is Resource.Success -> itemsIndexed(res.data, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, t.id == curId, vm) { onPlay(queue, offset + i) }
        }
    }
}

@Composable
fun MiniPlayer(ps: PS, p: Player, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = ps.track ?: return
    val (position, duration) = rememberPlaybackProgress(p, ps.playing)
    val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(androidx.compose.ui.graphics.lerp(artTint(t), Color.Black, .35f))
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(t, 40.dp, 4.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, fontSize = 12.sp, color = Color.White.copy(.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(40.dp).clickable { if (p.isPlaying) p.pause() else p.play() }, Alignment.Center) {
                Ico(if (ps.playing) Icons.PAUSE else Icons.PLAY, Color.White, 22.dp)
            }
            Box(Modifier.size(40.dp).clickable { p.seekToNextMediaItem() }, Alignment.Center) {
                Ico(Icons.NEXT, Color.White, 22.dp)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(.25f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
        }
    }
}
