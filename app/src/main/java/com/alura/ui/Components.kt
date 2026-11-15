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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    LaunchedEffect(p) { while (true) { delay(400); s = snap(p) } }
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
fun TrackRow(t: Track, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(t, 46.dp, 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (current) Acc else Txt,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, fontSize = 12.sp, color = Mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(CircleShape).background(Card2).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text(if (t.source == MusicSource.AUDIUS) "AUDIUS" else "LOKAL", fontSize = 10.sp, color = Mut)
        }
    }
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
    res: Resource<List<Track>>, queue: List<Track>, offset: Int, curId: String?,
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
            TrackRow(t, t.id == curId) { onPlay(queue, offset + i) }
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
