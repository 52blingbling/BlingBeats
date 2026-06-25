package com.localbeats.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.localbeats.data.model.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {

    private val exoPlayer = ExoPlayer.Builder(context).build()

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _playlist = MutableStateFlow<List<MusicTrack>>(emptyList())
    val playlist: StateFlow<List<MusicTrack>> = _playlist.asStateFlow()

    private var currentIndex = -1

    @OptIn(UnstableApi::class)
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _isPlaying.value = true
                }
                Player.STATE_ENDED -> {
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
            playNext()
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    fun setPlaylist(tracks: List<MusicTrack>) {
        _playlist.value = tracks
        if (tracks.isNotEmpty() && currentIndex < 0) {
            currentIndex = 0
            prepareTrack(tracks[0])
        }
    }

    @OptIn(UnstableApi::class)
    private fun prepareTrack(track: MusicTrack) {
        val mediaItem = MediaItem.fromUri(track.uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        _currentTrack.value = track
    }

    fun play(track: MusicTrack) {
        val tracks = _playlist.value
        currentIndex = tracks.indexOf(track)
        if (currentIndex < 0) {
            currentIndex = 0
            _playlist.value = listOf(track)
        }
        prepareTrack(track)
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun playNext() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return
        currentIndex = (currentIndex + 1) % tracks.size
        prepareTrack(tracks[currentIndex])
        exoPlayer.play()
    }

    fun playPrevious() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else tracks.size - 1
        prepareTrack(tracks[currentIndex])
        exoPlayer.play()
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun getDuration(): Long = exoPlayer.duration

    fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }
}
