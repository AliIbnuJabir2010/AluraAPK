package com.alura.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alura.player.PS

enum class LibPage(val title: String) {
    PLAYLISTS("Playlist Manual"), FAVORITES("Favorit"), DOWNLOADS("Download"),
    MOST_PLAYED("Sering Diputar"), RECENTLY_PLAYED("Baru Didengarkan"), YOUR_MIX("Mix Kamu")
}

@Composable
fun LibraryScreen(vm: AppViewModel, ps: PS, page: LibPage, onBack: () -> Unit) {
    val lib by vm.lib.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val open = if (page == LibPage.PLAYLISTS) lib.playlists.firstOrNull { it.id == openId } else null
    val cur = ps.track?.id

    BackHandler(open != null) { openId = null }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp, 16.dp, 14.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clickable { if (open != null) openId = null else onBack() }, Alignment.Center) {
                Ico(Icons.BACK, Txt)
            }
            Text(
                open?.name ?: page.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            when {
                open != null -> TextButton(onClick = { vm.deletePlaylist(open.id); openId = null }) {
                    Text("Hapus", color = Mut, fontSize = 13.sp)
                }
                page == LibPage.PLAYLISTS -> Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { creating = true }, Alignment.Center
                ) { Ico(Icons.PLUS, Acc) }
                else -> Spacer(Modifier.size(40.dp))
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, BOTTOM)) {
            when {
                page == LibPage.PLAYLISTS && open == null -> {
                    if (lib.playlists.isEmpty()) item {
                        Text("Belum ada playlist. Ketuk + untuk membuat.", color = Mut, fontSize = 13.sp)
                    }
                    items(lib.playlists, key = { it.id }) { p ->
                        MenuRow(p.name, "${p.tracks.size} lagu") { openId = p.id }
                    }
                }
                open != null -> {
                    if (open.tracks.isEmpty()) item {
                        Text("Playlist kosong. Tambahkan lagu lewat menu \u22EE di lagu mana pun.", color = Mut, fontSize = 13.sp)
                    } else item {
                        Box(
                            Modifier.padding(bottom = 6.dp).clip(CircleShape).background(Acc)
                                .clickable { vm.play(open.tracks, 0) }.padding(horizontal = 20.dp, vertical = 10.dp)
                        ) { Text("Putar semua", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    itemsIndexed(open.tracks, key = { _, t -> t.id }) { i, t ->
                        TrackRow(
                            t, t.id == cur, vm,
                            extra = listOf("Hapus dari playlist" to { vm.removeFromPlaylist(open.id, t.id) })
                        ) { vm.play(open.tracks, i) }
                    }
                }
                page == LibPage.MOST_PLAYED || page == LibPage.RECENTLY_PLAYED || page == LibPage.YOUR_MIX -> {
                    val tracks = vm.automaticPlaylistTracks(page)
                    if (tracks.isEmpty()) item {
                        Text(
                            if (page == LibPage.MOST_PLAYED) "Putar lagu minimal 30 detik agar Alura bisa mempelajari seleramu."
                            else if (page == LibPage.RECENTLY_PLAYED) "Lagu yang kamu dengarkan akan muncul di sini."
                            else "Dengarkan beberapa lagu dan tambahkan favorit untuk membangun Mix Kamu.",
                            color = Mut, fontSize = 13.sp
                        )
                    } else item {
                        Box(
                            Modifier.padding(bottom = 6.dp).clip(CircleShape).background(Acc)
                                .clickable { vm.play(tracks, 0) }.padding(horizontal = 20.dp, vertical = 10.dp)
                        ) { Text("Putar semua", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    itemsIndexed(tracks, key = { _, t -> "${page.name}_${t.id}" }) { i, t ->
                        TrackRow(t, t.id == cur, vm) { vm.play(tracks, i) }
                    }
                }
                page == LibPage.FAVORITES -> {
                    if (lib.favorites.isEmpty()) item {
                        Text("Belum ada favorit. Ketuk ikon hati di lagu.", color = Mut, fontSize = 13.sp)
                    }
                    itemsIndexed(lib.favorites, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, t.id == cur, vm) { vm.play(lib.favorites, i) }
                    }
                }
                else -> {
                    if (lib.downloads.isEmpty()) item {
                        Text("Belum ada lagu yang di-download. Pakai menu \u22EE > Download.", color = Mut, fontSize = 13.sp)
                    }
                    itemsIndexed(lib.downloads, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, t.id == cur, vm) { vm.play(lib.downloads, i) }
                    }
                }
            }
        }
    }

    if (creating) NameDialog("Playlist baru", "Buat", { creating = false }) { vm.createPlaylist(it) }
}
