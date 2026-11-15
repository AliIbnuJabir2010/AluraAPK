package com.alura.ui

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.alura.data.MusicSource
import com.alura.data.Resource
import com.alura.data.Track
import com.alura.player.PS
import com.alura.player.PlayerHolder
import kotlinx.coroutines.delay
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
    LaunchedEffect(p) {
        while (true) {
            delay(1_500)
            if (p.isPlaying) s = snap(p)
        }
    }
    return s
}

private val PALETTE = listOf(0xFF2B4A85, 0xFF2F3A7A, 0xFF1F5560, 0xFF4A2F6E, 0xFF6E4A2F)

@Composable
fun Art(t: Track?, size: Dp?, radius: Dp, modifier: Modifier = Modifier) {
    val c = Color(PALETTE[(t?.id?.hashCode() ?: 0).absoluteValue % PALETTE.size])
    val url = t?.artworkUrl
    Box(
        (if (size != null) modifier.size(size) else modifier)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(c, Color(0xFF0B1222)))),
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
    val lib by vm.lib.collectAsStateWithLifecycle()
    val dl by vm.downloading.collectAsStateWithLifecycle()
    val fav = lib.isFav(t.id)
    val saved = lib.isDownloaded(t.id)
    val pct = dl[t.id]
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    val sub = t.artist + when {
        saved -> " \u00B7 Offline"
        pct != null -> if (pct >= 0) " \u00B7 Mengunduh $pct%" else " \u00B7 Mengunduh\u2026"
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(t, 46.dp, 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (current) Acc else Txt,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (fav) {
            Box(Modifier.size(38.dp).clip(CircleShape).clickable { vm.toggleFavorite(t) }, Alignment.Center) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Acc), Alignment.Center) {
                    Ico(Icons.CHECK, Color.White, 14.dp)
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
                    text = { Text("+ Daftar putar", color = Txt) },
                    onClick = { menu = false; pick = true }
                )
                if (t.source != MusicSource.YOUTUBE) DropdownMenuItem(
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
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}

@Composable
fun MenuRow(title: String, sub: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(CardC)
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

/** Menampilkan Loading / Empty / Error / daftar lagu. onPlay menerima antrean + indeks lagu yang diketuk. */
fun LazyListScope.trackSection(
    vm: AppViewModel, res: Resource<List<Track>>, queue: List<Track>, offset: Int, curId: String?,
    emptyMsg: String, onRetry: () -> Unit, onPlay: (List<Track>, Int) -> Unit
) {
    when (res) {
        Resource.Loading -> item {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Acc) }
        }
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
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card2)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(t, 40.dp, 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, fontSize = 11.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).clickable { if (p.isPlaying) p.pause() else p.play() }, Alignment.Center) {
            Ico(if (ps.playing) Icons.PAUSE else Icons.PLAY, Txt, 20.dp)
        }
        Box(Modifier.size(40.dp).clickable { p.seekToNextMediaItem() }, Alignment.Center) {
            Ico(Icons.NEXT, Txt, 20.dp)
        }
    }
}
