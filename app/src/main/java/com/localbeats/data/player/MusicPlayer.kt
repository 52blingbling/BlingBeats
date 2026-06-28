package com.localbeats.data.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.localbeats.data.model.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {

    private val exoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    // 绑定系统 MediaSession，让通知栏、锁屏、蓝牙耳机都能控制播放器。加 try-catch 防止部分定制系统（如 HyperOS）因权限或 Intent 解析闪退
    private val mediaSession: MediaSession? = try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            android.app.PendingIntent.getActivity(
                context,
                0,
                it,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val builder = MediaSession.Builder(context, exoPlayer)
        if (pendingIntent != null) {
            builder.setSessionActivity(pendingIntent)
        }
        builder.build()
    } catch (e: Exception) {
        e.printStackTrace()
        null
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

    @OptIn(UnstableApi::class)
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    // 成功进入 READY 状态，重置错误计数
                    consecutiveErrorCount = 0
                    if (shouldAutoPlay) {
                        exoPlayer.play()
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
                exoPlayer.stop()
            }
        }
    }

    init {
        MusicPlaybackService.playerInstance = exoPlayer
        try {
            val intent = Intent(context, MusicPlaybackService::class.java)
            context.startService(intent)
        } catch (_: Exception) {
            // Android 8+ background start restriction.
            // Usually this is called in foreground, but safely catch just in case.
        }
        exoPlayer.addListener(listener)
    }

    fun setPlaylist(tracks: List<MusicTrack>) {
        // 重置播放列表时先停止当前播放
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
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

    @OptIn(UnstableApi::class)
    private fun prepareTrack(track: MusicTrack) {
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
            
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
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
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            shouldAutoPlay = true
            exoPlayer.play()
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
        exoPlayer.seekTo(position)
    }

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun getDuration(): Long = exoPlayer.duration

    fun release() {
        exoPlayer.removeListener(listener)
        mediaSession?.release()
        exoPlayer.release()
    }
}
