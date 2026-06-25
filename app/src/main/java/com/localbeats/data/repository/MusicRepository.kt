package com.localbeats.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.localbeats.Dbg
import com.localbeats.data.model.MusicTrack
import java.util.ArrayDeque

class MusicRepository(private val context: Context) {

    fun loadMusicTracksFromFolder(folderUri: Uri?): List<MusicTrack> {
        Dbg.log("[REPO] loadMusicTracksFromFolder START uri=$folderUri")
        if (folderUri == null) {
            Dbg.log("[REPO] folderUri is null, return empty")
            return emptyList()
        }

        val root = try {
            val r = DocumentFile.fromTreeUri(context, folderUri)
            Dbg.log("[REPO] root=${r?.uri} exists=${r?.exists()} isDir=${r?.isDirectory}")
            r ?: return emptyList()
        } catch (t: Throwable) {
            Dbg.err("[REPO] fromTreeUri threw", t)
            return emptyList()
        }
        val tracks = mutableListOf<MusicTrack>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)
        var scanCount = 0

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            try {
                if (current.isDirectory) {
                    val children = current.listFiles()
                    Dbg.log("[REPO] dir=${current.name} children=${children.size}")
                    children.forEach { child -> stack.add(child) }
                } else if (isSupportedAudioFile(current.name.orEmpty())) {
                    scanCount++
                    Dbg.log("[REPO] file#${scanCount} name=${current.name}")
                    try {
                        tracks.add(buildTrack(current))
                    } catch (t: Throwable) {
                        Dbg.err("[REPO] buildTrack failed name=${current.name}", t)
                    }
                }
            } catch (t: Throwable) {
                Dbg.err("[REPO] iter failed name=${current.name}", t)
            }
        }

        Dbg.log("[REPO] DONE scanned=$scanCount tracks=${tracks.size}")
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
            Dbg.log("[REPO] setDataSource uri=$uri")
            retriever.setDataSource(context, uri)
            Dbg.log("[REPO] setDataSource OK")
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown"

            val embeddedPicture = retriever.embeddedPicture
            if (embeddedPicture != null) {
                Dbg.log("[REPO] cover bytes=${embeddedPicture.size}")
                albumArtUri = saveCoverArt(uri.toString().hashCode().toString(), embeddedPicture)
            }
        } catch (t: Throwable) {
            Dbg.err("[REPO] buildTrack setDataSource failed name=$rawName", t)
        } finally {
            try {
                retriever.release()
            } catch (t: Throwable) {
                Dbg.err("[REPO] retriever.release failed", t)
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
