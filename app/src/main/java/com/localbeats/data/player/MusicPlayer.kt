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
                    consecutiveErrorCount = 0
                    playNext()
                }
                Player.STATE_BUFFERING -> {}
                Player.STATE_IDLE -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            consecutiveErrorCount++
            if (consecutiveErrorCount <= maxConsecutiveErrors) {
                // 出错时跳过当前曲目
                playNext()
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

    fun setPlaylist(tracks: List<MusicTrack>) {
        executeOrQueue {
            // 重置播放列表时先停止当前播放
            player?.stop()
            player?.clearMediaItems()
        }
        _playlist.value = tracks
        currentIndex = -1
        shouldAutoPlay = false
        consecutiveErrorCount = 0
        if (tracks.isNotEmpty()) {
            currentIndex = 0
            prepareTrack(tracks[0])
        } else {
            _currentTrack.value = null
            _isPlaying.value = false
        }
    }

    private fun prepareTrack(track: MusicTrack) {
        executeOrQueue {
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setArtworkUri(track.coverUri)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(mediaMetadata)
                .build()
                
            player?.setMediaItem(mediaItem)
            player?.prepare()
        }
        _currentTrack.value = track
    }

    fun play(track: MusicTrack) {
        val tracks = _playlist.value
        val idx = tracks.indexOf(track)
        currentIndex = if (idx >= 0) idx else {
            _playlist.value = listOf(track)
            0
        }
        shouldAutoPlay = true
        consecutiveErrorCount = 0
        prepareTrack(track)
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
        val tracks = _playlist.value
        if (tracks.isEmpty()) return
        currentIndex = (currentIndex + 1) % tracks.size
        shouldAutoPlay = true
        prepareTrack(tracks[currentIndex])
    }

    fun playPrevious() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else tracks.size - 1
        shouldAutoPlay = true
        prepareTrack(tracks[currentIndex])
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
