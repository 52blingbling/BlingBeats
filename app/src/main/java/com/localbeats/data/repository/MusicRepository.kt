package com.localbeats.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.localbeats.data.lyrics.LyricsExtractor
import com.localbeats.data.model.MusicTrack
import java.util.ArrayDeque

class MusicRepository(private val context: Context) {

    companion object {
        private const val TAG = "MusicRepository"
        var cachedTracks: List<MusicTrack>? = null
    }

    fun scanAudioFolders(): List<String> {
        val folders = mutableSetOf<String>()
        val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
        val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol)
                    java.io.File(path).parent?.let { folders.add(it) }
                }
            }
        } catch (_: Throwable) {}
        return folders.toList().sorted()
    }

    fun loadMusicTracksFromDevice(ignoredFolders: Set<String>, filterShortAudio: Boolean, forceReload: Boolean = false): List<MusicTrack> {
        if (!forceReload && cachedTracks != null) {
            Log.i(TAG, "从内存缓存中直接恢复了 ${cachedTracks!!.size} 首歌曲，跳过磁盘扫描")
            return cachedTracks!!
        }
        
        val tracks = mutableListOf<MusicTrack>()
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.TITLE,
            android.provider.MediaStore.Audio.Media.ARTIST,
            android.provider.MediaStore.Audio.Media.DURATION
        )
        val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        var lyricsFound = 0
        try {
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                android.provider.MediaStore.Audio.Media.TITLE + " ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol)
                    val parent = java.io.File(path).parent ?: continue
                    if (ignoredFolders.contains(parent)) continue

                    val id = cursor.getLong(idCol)
                    val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown"
                    val duration = cursor.getLong(durationCol)
                    
                    if (filterShortAudio && duration < 60_000) {
                        continue
                    }
                    
                    try {
                        val track = buildTrackFromMediaStore(id, uri, path, title, artist, duration)
                        if (track.lyrics != null) lyricsFound++
                        tracks.add(track)
                    } catch (_: Throwable) {
                        // 跳过无法解析的文件
                    }
                }
            }
        } catch (_: Throwable) {}

        Log.i(TAG, "扫描完成：共 ${tracks.size} 首歌曲，其中 $lyricsFound 首提取到歌词")
        cachedTracks = tracks
        return tracks
    }

    private fun buildTrackFromMediaStore(
        id: Long,
        uri: Uri,
        filePath: String,
        title: String,
        artist: String,
        duration: Long
    ): MusicTrack {
        val retriever = MediaMetadataRetriever()
        var albumArtUri: Uri? = null
        var lyrics: String? = null

        try {
            retriever.setDataSource(context, uri)
            // Artist 和 Duration 我们已经从 MediaStore 拿到了，所以这里主要是拿封面和歌词
            
            val embeddedPicture = retriever.embeddedPicture
            if (embeddedPicture != null) {
                albumArtUri = saveCoverArt(id.toString(), embeddedPicture)
            }

            // 提取嵌入式歌词：先尝试 MediaMetadataRetriever（快，但覆盖有限），
            // 失败再 fallback 到 jaudiotagger（需要复制文件，但支持 ID3 USLT 等所有标签）
            lyrics = extractLyrics(retriever) ?: LyricsExtractor.extract(
                context = context,
                uri = uri,
                cacheKey = id.toString()
            )
        } catch (_: Throwable) {
            // 捕获 Throwable 包括原生崩溃引发的 Error
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // release 失败时忽略
            }
        }

        val finalLyrics = lyrics?.takeIf { it.isNotBlank() }
        if (finalLyrics == null) {
            Log.d(TAG, "无歌词: $title")
        }

        return MusicTrack(
            id = id,
            title = title.ifBlank { "Unknown" },
            artist = artist.ifBlank { "Unknown" },
            duration = duration,
            uri = uri,
            coverUri = albumArtUri,
            filePath = filePath,
            lyrics = finalLyrics
        )
    }

    /**
     * 提取嵌入式歌词标签。
     * Android MediaMetadataRetriever 中 METADATA_KEY_LYRICS (值 29) 被标记 @hide，
     * 不同设备/解码器对 key 的支持有差异，按优先级尝试多个候选。
     */
    private fun extractLyrics(retriever: MediaMetadataRetriever): String? {
        // 候选 key：29 是 Android 源码中 METADATA_KEY_LYRICS 的实际值
        val candidates = intArrayOf(29)
        for (key in candidates) {
            try {
                val value = retriever.extractMetadata(key)
                if (!value.isNullOrBlank()) {
                    Log.i(TAG, "MediaMetadataRetriever 提取到歌词(key=$key)，长度=${value.length}")
                    return value
                }
            } catch (_: Throwable) {
                // 该 key 不支持，继续尝试下一个
            }
        }
        return null
    }

    private fun saveCoverArt(trackId: String, data: ByteArray): Uri? {
        return try {
            val cacheDir = java.io.File(context.cacheDir, "covers").also { it.mkdirs() }
            val file = java.io.File(cacheDir, "$trackId.jpg")
            if (!file.exists()) {
                file.writeBytes(data)
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    private fun isSupportedAudioFile(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return lowerName.endsWith(".mp3") ||
            lowerName.endsWith(".m4a") ||
            lowerName.endsWith(".wav") ||
            lowerName.endsWith(".flac") ||
            lowerName.endsWith(".ogg") ||
            lowerName.endsWith(".aac") ||
            lowerName.endsWith(".wma")
    }

    private fun getFileExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0 && dotIndex < fileName.length - 1) {
            fileName.substring(dotIndex)
        } else {
            ""
        }
    }
}
