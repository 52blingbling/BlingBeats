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
    private val prefs = application.getSharedPreferences("localbeats_prefs", android.content.Context.MODE_PRIVATE)

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
                // 仅在有当前曲目时才访问 ExoPlayer，避免不必要的访问
                if (_tracks.value.isNotEmpty()) {
                    try {
                        _currentPosition.value = player.getCurrentPosition()
                        _duration.value = player.getDuration().coerceAtLeast(0L)
                    } catch (_: Exception) {
                        // ExoPlayer 可能处于异常状态，忽略
                    }
                }
            }
        }
    }

    fun loadMusicFromFolder(folderUri: Uri?) {
        // 在主线程同步设置崩溃标记，确保在任何崩溃前写入
        prefs.edit().putBoolean("loading_crashed", true).commit()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                val musicTracks = try {
                    repository.loadMusicTracksFromFolder(folderUri)
                } catch (_: Throwable) {
                    // 捕获 Throwable 而非 Exception，包括 OutOfMemoryError 等
                    emptyList()
                }
                _tracks.value = musicTracks
                // ExoPlayer 必须在主线程操作
                withContext(Dispatchers.Main) {
                    try {
                        player.setPlaylist(musicTracks)
                    } catch (_: Throwable) {
                        // ExoPlayer 设置播放列表失败时忽略
                    }
                }
                _isLoading.value = false
            } finally {
                // 无论成功、失败还是取消，都清除崩溃标记
                // （仅原生崩溃导致的进程被杀才会保留标记）
                prefs.edit().putBoolean("loading_crashed", false).commit()
            }
        }
    }

    fun clearTracks() {
        _tracks.value = emptyList()
        try {
            player.setPlaylist(emptyList())
        } catch (_: Throwable) {
            // ExoPlayer 可能处于异常状态，忽略
        }
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
