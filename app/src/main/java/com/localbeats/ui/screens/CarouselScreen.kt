package com.localbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbeats.data.model.MusicTrack
import com.localbeats.ui.components.CarouselItem
import com.localbeats.ui.components.PlayerBar
import kotlinx.coroutines.flow.collectLatest

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CarouselScreen(
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
    modifier: Modifier = Modifier
) {
    val startIndex = tracks.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { tracks.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            if (page in tracks.indices) {
                onTrackClick(tracks[page])
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        if (tracks.isEmpty()) {
            Text(
                text = "No music found",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp)
            ) { page ->
                val track = tracks[page]
                val pageOffset = (
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                )

                CarouselItem(
                    track = track,
                    pageOffset = pageOffset,
                    isPlaying = isPlaying && track.id == currentTrack?.id,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 80.dp)
                )
            }

            // 顶部标题区域（带渐变遮罩）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D0D0D).copy(alpha = 0.7f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 200f
                        )
                    )
            )

            // 曲名与艺术家
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentTrack?.title ?: "",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Text(
                        text = currentTrack?.artist ?: "",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
                    )
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
