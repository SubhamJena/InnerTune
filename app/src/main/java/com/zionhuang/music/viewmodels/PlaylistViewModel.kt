package com.zionhuang.music.viewmodels

import android.content.Context
import androidx.lifecycle.*
import com.zionhuang.innertube.YouTube
import com.zionhuang.music.db.entities.AlbumWithSongs
import com.zionhuang.music.repos.SongRepository
import com.zionhuang.music.youtube.getAlbumWithSongs
import kotlinx.coroutines.launch

class PlaylistViewModel(
    context: Context,
    val albumId: String,
    val playlistId: String?,
) : ViewModel() {
    val albumWithSongs = MutableLiveData<AlbumWithSongs?>(null)

    init {
        viewModelScope.launch {
            albumWithSongs.value = SongRepository(context).getAlbumWithSongs(albumId)
                ?: YouTube.getAlbumWithSongs(context, albumId, playlistId)
        }
    }
}

@Suppress("UNCHECKED_CAST")
class PlaylistViewModelFactory(
    val context: Context,
    val albumId: String,
    val playlistId: String?,
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlaylistViewModel(context, albumId, playlistId) as T
}