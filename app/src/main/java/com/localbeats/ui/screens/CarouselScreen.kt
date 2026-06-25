package com.localbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbeats.data.model.MusicTrack
import com.localbeats.ui.components.CarouselItem
import com.localbeats.ui.components.PlayerBar
import kotlinx.coroutines.flow.collectLatest

@OptIn androidx.compose.foundation.ExperimentalFoundationApi::class
@Composable
fun CarouselScreen(
    tracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    onTrackClick: (MusicTrack) -> Unit,
    onPlayPauseClick: () -> Unit,
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
                    onClick = { onTrackClick(track) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 80.dp)
                )
            }

            Text(
                text = currentTrack?.title ?: "",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            )

            Text(
                text = currentTrack?.artist ?: "",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
            )
        }

        PlayerBar(
            title = currentTrack?.title ?: "No track selected",
            isPlaying = isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
