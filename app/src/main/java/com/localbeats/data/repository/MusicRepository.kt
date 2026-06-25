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
                    // 每个文件单独 try/catch，一个文件失败不影响其他文件
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

        try {
            retriever.setDataSource(context, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown"

            // 提取内嵌封面图并缓存到应用私有目录
            val embeddedPicture = retriever.embeddedPicture
            if (embeddedPicture != null) {
                albumArtUri = saveCoverArt(uri.toString().hashCode().toString(), embeddedPicture)
            }
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
            filePath = uri.toString()
        )
    }

    /**
     * 将封面图字节写入应用私有缓存目录，返回文件 Uri。
     * 文件名用曲目 id hash 确保唯一且不重复写入。
     */
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
