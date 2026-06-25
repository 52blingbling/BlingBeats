package com.localbeats.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.localbeats.data.model.MusicTrack
import java.util.ArrayDeque

class MusicRepository(private val context: Context) {

    fun loadMusicTracksFromFolder(folderUri: Uri?): List<MusicTrack> {
        if (folderUri == null) {
            return emptyList()
        }

        val root = try {
            DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        } catch (_: Throwable) {
            return emptyList()
        }
        val tracks = mutableListOf<MusicTrack>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            try {
                if (current.isDirectory) {
                    current.listFiles().forEach { child -> stack.add(child) }
                } else if (isSupportedAudioFile(current.name.orEmpty())) {
                    try {
                        tracks.add(buildTrack(current))
                    } catch (_: Throwable) {
                        // 跳过无法解析的文件
                    }
                }
            } catch (_: Throwable) {
                // 跳过无法访问的目录/文件
            }
        }

        return tracks.sortedBy { it.title.lowercase() }
    }

    private fun buildTrack(documentFile: DocumentFile): MusicTrack {
        val uri = documentFile.uri
        val rawName = documentFile.name ?: "Unknown"
        val title = rawName.removeSuffix(getFileExtension(rawName))
        val retriever = MediaMetadataRetriever()
        var duration = 0L
        var artist = "Unknown"
        var albumArtUri: Uri? = null
        var lyrics: String? = null

        try {
            retriever.setDataSource(context, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown"

            val embeddedPicture = retriever.embeddedPicture
            if (embeddedPicture != null) {
                albumArtUri = saveCoverArt(uri.toString().hashCode().toString(), embeddedPicture)
            }

            // 提取嵌入式歌词：MediaMetadataRetriever.METADATA_KEY_LYRICS 未在公开 API 中
            // 暴露，但实际常量值为 29。部分设备也支持字符串形式，按优先级尝试。
            lyrics = extractLyrics(retriever)
        } catch (_: Throwable) {
            // 捕获 Throwable 包括原生崩溃引发的 Error
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // release 失败时忽略
            }
        }

        return MusicTrack(
            id = uri.toString().hashCode().toLong(),
            title = title.ifBlank { "Unknown" },
            artist = artist.ifBlank { "Unknown" },
            duration = duration,
            uri = uri,
            coverUri = albumArtUri,
            filePath = uri.toString(),
            lyrics = lyrics?.takeIf { it.isNotBlank() }
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
