package com.localbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbeats.data.model.MusicTrack
import com.localbeats.ui.components.PlayerBar
import com.localbeats.ui.components.TileItem

@Composable
fun TileGridScreen(
    tracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    onTrackClick: (MusicTrack) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onImportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 72.dp,   // 给顶部标题栏留空间
                bottom = 160.dp  // 给新版 PlayerBar（更高）留空间
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(tracks) { index, track ->
                val isLarge = index % 5 == 0 || index % 5 == 3
                TileItem(
                    track = track,
                    isLarge = isLarge,
                    isPlaying = isPlaying && track.id == currentTrack?.id,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 顶部标题栏（带渐变遮罩）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D0D0D),
                            Color(0xFF0D0D0D).copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LocalBeats",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${tracks.size} 首",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                    IconButton(onClick = onImportClick) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Import Folder",
                            tint = Color(0xFFBB86FC)
                        )
                    }
                }
            }
        }

        PlayerBar(
            title = currentTrack?.title ?: "未选择歌曲",
            isPlaying = isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            currentPosition = currentPosition,
            duration = duration,
            onSeek = onSeek,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
