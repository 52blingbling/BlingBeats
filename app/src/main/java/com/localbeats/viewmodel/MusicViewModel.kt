package com.localbeats.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localbeats.data.model.MusicTrack
import com.localbeats.data.player.MusicPlayer
import com.localbeats.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val player = MusicPlayer(application)

    private val _tracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val tracks: StateFlow<List<MusicTrack>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadMusicFromFolder(folderUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val musicTracks = try {
                repository.loadMusicTracksFromFolder(folderUri)
            } catch (_: Exception) {
                emptyList()
            }
            _tracks.value = musicTracks
            player.setPlaylist(musicTracks)
            _isLoading.value = false
        }
    }

    fun clearTracks() {
        _tracks.value = emptyList()
        player.setPlaylist(emptyList())
    }

    fun playTrack(track: MusicTrack) {
        player.play(track)
    }

    fun togglePlayPause() {
        player.togglePlayPause()
    }

    fun playNext() {
        player.playNext()
    }

    fun playPrevious() {
        player.playPrevious()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
