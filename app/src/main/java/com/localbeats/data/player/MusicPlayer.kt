package com.localbeats.data.player

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.localbeats.data.model.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var player: Player? = null

    private val pendingActions = mutableListOf<() -> Unit>()

    private fun executeOrQueue(action: () -> Unit) {
        if (player != null) {
            action()
        } else {
            pendingActions.add(action)
        }
    }

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _playlist = MutableStateFlow<List<MusicTrack>>(emptyList())
    val playlist: StateFlow<List<MusicTrack>> = _playlist.asStateFlow()

    private var currentIndex = -1
    // 标记当前是否应该自动播放（setPlaylist 时不自动播放，play(track) 时才播）
    private var shouldAutoPlay = false
    // 连续错误计数，防止 onPlayerError → playNext → onPlayerError 无限循环
    private var consecutiveErrorCount = 0
    private val maxConsecutiveErrors = 3

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    // 成功进入 READY 状态，重置错误计数
                    consecutiveErrorCount = 0
                    if (shouldAutoPlay) {
                        player?.play()
                    }
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                }
                else -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            val trackId = mediaItem?.mediaId?.toLongOrNull()
            if (trackId != null) {
                _currentTrack.value = _playlist.value.find { it.id == trackId }
            } else {
                _currentTrack.value = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            consecutiveErrorCount++
            if (consecutiveErrorCount <= maxConsecutiveErrors) {
                // 出错时跳过当前曲目
                player?.seekToNext()
            } else {
                // 连续错误次数过多，停止播放，避免无限循环
                consecutiveErrorCount = 0
                shouldAutoPlay = false
                player?.stop()
            }
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            player = controllerFuture?.get()
            player?.addListener(listener)
            val actions = pendingActions.toList()
            pendingActions.clear()
            actions.forEach { it() }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setPlaylist(tracks: List<MusicTrack>, forceReload: Boolean = false) {
        _playlist.value = tracks
        shouldAutoPlay = false
        consecutiveErrorCount = 0

        executeOrQueue {
            if (!forceReload && (player?.mediaItemCount ?: 0) > 0) {
                // 后台可能正在播放，不要打断，仅同步 UI 的当前曲目状态
                val currentMediaId = player?.currentMediaItem?.mediaId?.toLongOrNull()
                if (currentMediaId != null) {
                    _currentTrack.value = tracks.find { it.id == currentMediaId }
                } else if (tracks.isNotEmpty()) {
                    _currentTrack.value = tracks[0]
                }
                return@executeOrQueue
            }

            // 重置播放列表时先停止当前播放
            player?.stop()
            player?.clearMediaItems()
            val mediaItems = tracks.map { track ->
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.coverUri)
                    .build()
                MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaId(track.id.toString())
                    .setMediaMetadata(mediaMetadata)
                    .build()
            }
            player?.setMediaItems(mediaItems)
            player?.repeatMode = Player.REPEAT_MODE_ALL
            player?.prepare()
            
            if (tracks.isNotEmpty()) {
                _currentTrack.value = tracks[0]
            } else {
                _currentTrack.value = null
                _isPlaying.value = false
            }
        }
    }

    fun play(track: MusicTrack) {
        val tracks = _playlist.value
        var idx = tracks.indexOf(track)
        if (idx < 0) {
            setPlaylist(listOf(track), forceReload = true)
            idx = 0
        }
        shouldAutoPlay = true
        consecutiveErrorCount = 0
        executeOrQueue {
            player?.seekTo(idx, 0L)
            player?.play()
        }
        _currentTrack.value = track
    }

    fun togglePlayPause() {
        executeOrQueue {
            if (player?.isPlaying == true) {
                player?.pause()
            } else {
                shouldAutoPlay = true
                player?.play()
            }
        }
    }

    fun playNext() {
        shouldAutoPlay = true
        executeOrQueue {
            player?.seekToNext()
        }
    }

    fun playPrevious() {
        shouldAutoPlay = true
        executeOrQueue {
            player?.seekToPrevious()
        }
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        executeOrQueue {
            player?.shuffleModeEnabled = enabled
        }
    }

    fun seekTo(position: Long) {
        executeOrQueue {
            player?.seekTo(position)
        }
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    fun release() {
        player?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
