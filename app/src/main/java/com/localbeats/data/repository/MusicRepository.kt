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

        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val tracks = mutableListOf<MusicTrack>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (current.isDirectory) {
                current.listFiles().forEach { child -> stack.add(child) }
            } else if (isSupportedAudioFile(current.name.orEmpty())) {
                tracks.add(buildTrack(current))
            }
        }

        return tracks.sortedBy { it.title.lowercase() }
    }

    private fun buildTrack(documentFile: DocumentFile): MusicTrack {
        val uri = documentFile.uri
        val title = documentFile.name?.removeSuffix(getFileExtension(documentFile.name.orEmpty())) ?: "Unknown"
        val retriever = MediaMetadataRetriever()
        var duration = 0L
        var artist = "Unknown"

        try {
            retriever.setDataSource(context, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown"
        } catch (_: Exception) {
            // fall back to defaults when metadata cannot be read
        } finally {
            retriever.release()
        }

        return MusicTrack(
            id = uri.toString().hashCode().toLong(),
            title = title.ifBlank { "Unknown" },
            artist = artist.ifBlank { "Unknown" },
            duration = duration,
            uri = uri,
            coverUri = null,
            filePath = uri.toString()
        )
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
