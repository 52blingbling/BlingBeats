package com.localbeats.data.model

import android.net.Uri

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri,
    val coverUri: Uri?,
    val filePath: String,
    /** 嵌入式歌词（可能为纯文本或带 LRC 时间戳格式） */
    val lyrics: String? = null
) {
    val durationText: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
