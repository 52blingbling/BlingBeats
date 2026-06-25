package com.localbeats.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localbeats.data.model.MusicTrack
import com.localbeats.data.player.MusicPlayer
import com.localbeats.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val player = MusicPlayer(application)

    private val _tracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val tracks: StateFlow<List<MusicTrack>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    init {
        // 每 500ms 轮询播放进度，更新进度条
        viewModelScope.launch {
            while (true) {
                delay(500)
                _currentPosition.value = player.getCurrentPosition()
                _duration.value = player.getDuration().coerceAtLeast(0L)
            }
        }
    }

    fun loadMusicFromFolder(folderUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val musicTracks = try {
                repository.loadMusicTracksFromFolder(folderUri)
            } catch (_: Exception) {
                emptyList()
            }
            _tracks.value = musicTracks
            // ExoPlayer 必须在主线程操作
            withContext(Dispatchers.Main) {
                player.setPlaylist(musicTracks)
            }
            _isLoading.value = false
        }
    }

    fun clearTracks() {
        _tracks.value = emptyList()
        player.setPlaylist(emptyList())
        _currentPosition.value = 0L
        _duration.value = 0L
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

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
