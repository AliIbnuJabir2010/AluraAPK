package com.alura.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alura.data.*
import com.alura.player.PlayerHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class Category(val label: String, val genre: String? = null, val query: String? = null)

val CATEGORIES = listOf(
    Category("Pop", genre = "Pop"),
    Category("Rock", genre = "Rock"),
    Category("Indie", genre = "Alternative"),
    Category("Hip-Hop", genre = "Hip-Hop/Rap"),
    Category("Dangdut", query = "dangdut"),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val audius = AudiusRepository()
    private val localRepo = LocalRepository(app)

    val trending = MutableStateFlow<Resource<List<Track>>>(Resource.Loading)
    val playlists = MutableStateFlow<Resource<List<Playlist>>>(Resource.Loading)
    val local = MutableStateFlow<List<Track>>(emptyList())
    val results = MutableStateFlow<Resource<List<Track>>>(Resource.Empty)
    val query = MutableStateFlow("")
    val category = MutableStateFlow<Category?>(null)
    private var job: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { trending.value = Resource.Loading; trending.value = audius.trending() }
        viewModelScope.launch { playlists.value = Resource.Loading; playlists.value = audius.trendingPlaylists() }
    }

    fun loadLocal() {
        viewModelScope.launch { (localRepo.trending() as? Resource.Success)?.let { local.value = it.data } }
    }

    fun setQuery(q: String) { query.value = q; search(450) }
    fun setCategory(c: Category) { category.value = if (category.value == c) null else c; search(0) }

    private fun search(delayMs: Long) {
        job?.cancel()
        job = viewModelScope.launch {
            delay(delayMs)
            val q = query.value.trim()
            val c = category.value
            if (q.isEmpty() && c == null) { results.value = Resource.Empty; return@launch }
            results.value = Resource.Loading
            results.value = when {
                q.isNotEmpty() -> audius.searchTracks(q)
                c?.genre != null -> audius.trending(c.genre)
                else -> audius.searchTracks(c?.query.orEmpty())
            }
        }
    }

    fun play(queue: List<Track>, index: Int) = PlayerHolder.play(getApplication(), queue, index)

    fun playPlaylist(p: Playlist) {
        viewModelScope.launch { (audius.playlistTracks(p.id) as? Resource.Success)?.let { play(it.data, 0) } }
    }
}
