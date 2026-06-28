package com.localbeats.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localbeats.data.model.MusicTrack
import com.localbeats.data.player.MusicPlayer
import com.localbeats.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineExceptionHandler
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

    private var isAppInForeground = true

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground) {
            // 返回前台时立即主动获取一次最新进度，保证界面刷新没有时间延迟
            viewModelScope.launch {
                updatePlaybackProgress()
            }
        }
    }

    private fun updatePlaybackProgress() {
        if (_tracks.value.isNotEmpty()) {
            try {
                val pos = player.getCurrentPosition()
                val dur = player.getDuration().coerceAtLeast(0L)
                _currentPosition.value = pos
                _duration.value = dur
            } catch (_: Exception) {
                // ExoPlayer 可能处于异常状态，忽略
            }
        }
    }

    init {
        // 动态频率轮询播放进度：前台播放 100ms（极省 CPU 且兼顾高精度），后台播放 1000ms，暂停状态 2000ms
        viewModelScope.launch {
            while (true) {
                val isPlaying = try { player.isPlaying.value } catch (_: Exception) { false }
                val delayTime = when {
                    isAppInForeground && isPlaying -> 100L
                    isPlaying -> 1000L
                    else -> 2000L
                }
                delay(delayTime)
                updatePlaybackProgress()
            }
        }
    }

    private val crashHandler = CoroutineExceptionHandler { _, _ -> }

    var isInitialLoadDone = false
        private set

    fun loadMusicFromDevice(ignoredFolders: Set<String>, filterShortAudio: Boolean, forceReload: Boolean = false) {
        if (!forceReload && isInitialLoadDone) return
        isInitialLoadDone = true

        // 在主线程同步设置崩溃标记，确保在任何崩溃前写入
        prefs.edit().putBoolean("loading_crashed", true).commit()

        viewModelScope.launch(Dispatchers.IO + crashHandler) {
            try {
                _isLoading.value = true
                val musicTracks = try {
                    repository.loadMusicTracksFromDevice(ignoredFolders, filterShortAudio, forceReload)
                } catch (_: Throwable) {
                    // 捕获 Throwable 而非 Exception，包括 OutOfMemoryError 等
                    emptyList()
                }
                _tracks.value = musicTracks
                // ExoPlayer 必须在主线程操作
                withContext(Dispatchers.Main) {
                    try {
                        player.setPlaylist(musicTracks, forceReload)
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

    /**
     * 重新扫描音乐：强制重新加载音乐文件并提取歌词等元数据。
     * 用于歌词提取逻辑更新后，刷新已扫描曲目的歌词字段。
     * 会重置播放列表，但保留当前播放曲目的位置（如果仍在列表中）。
     */
    fun rescanDevice() {
        val ignoredFolders = prefs.getStringSet("ignored_folders", emptySet()) ?: emptySet()
        val filterShortAudio = prefs.getBoolean("filter_short_audio", true)
        loadMusicFromDevice(ignoredFolders, filterShortAudio, forceReload = true)
    }

    fun getAudioFolders(): List<String> {
        return repository.scanAudioFolders()
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

    fun setShuffleModeEnabled(enabled: Boolean) {
        player.setShuffleModeEnabled(enabled)
    }

    /**
     * 重排磁贴墙顺序：将 from 位置的曲目移动到 to 位置。
     * 仅更新 UI 显示顺序，不影响当前播放列表（避免重置播放进度）。
     */
    fun reorderTracks(from: Int, to: Int) {
        val list = _tracks.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        // 交换两个磁贴的位置（swap），与磁贴墙的视觉交换一致，
        // 避免移动插入导致整体 reflow
        val tmp = list[from]
        list[from] = list[to]
        list[to] = tmp
        _tracks.value = list
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
