package com.localbeats.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.localbeats.data.model.MusicTrack
import com.localbeats.ui.components.CarouselItem
import com.localbeats.ui.components.PlayerBar
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * 横屏专辑轮播：
 * - 当前曲目专辑正对显示（旋转 0）
 * - 左侧专辑堆叠并 3D 向右倾斜（rotationY < 0，右侧朝向观察者）
 * - 右侧专辑堆叠并 3D 向左倾斜（rotationY > 0，左侧朝向观察者）
 * - 滑动切换居中专辑即播放对应曲目
 *
 * 关于旋转重播修复：监听 settledPage 而非 currentPage，且仅当目标曲目与当前
 * 播放曲目不同时才调用 onTrackClick。旋转时 initialPage 即当前曲目，
 * tracks[page].id == currentTrack?.id 成立，因此跳过调用，避免重置 MediaItem。
 */
@OptIn(ExperimentalFoundationApi::class)
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

    // 仅在 settledPage 改变（用户停止滑动后）且目标与当前播放曲目不同时切换。
    // 旋转重建时 settledPage = initialPage = 当前曲目，跳过调用避免重播。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collectLatest { page ->
                if (page in tracks.indices && tracks[page].id != currentTrack?.id) {
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
                // 不使用 contentPadding peek，改用 translationX 实现真正的堆叠
                contentPadding = PaddingValues(horizontal = 0.dp),
                // 预渲染左右各 2 页，保证堆叠邻居可见
                beyondBoundsPageCount = 2
            ) { page ->
                val track = tracks[page]
                // pageOffset：当前页相对此页的偏移。左侧页 pageOffset > 0，右侧页 < 0
                val pageOffset =
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = abs(pageOffset)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 当前页 zIndex 最高（绘制在最上层），邻居越远越靠后
                        .zIndex(10f - absOffset)
                        .graphicsLayer {
                            // 堆叠：将相邻页拉向中心（保留 ~15% 露出量）
                            translationX = pageOffset * size.width * 0.85f
                            // 中心放大，邻居缩小
                            val s = (1f - absOffset * 0.18f).coerceAtLeast(0.45f)
                            scaleX = s
                            scaleY = s
                            // 3D 旋转：左侧向右倾斜（rotationY < 0，右边缘朝向观察者），
                            // 右侧向左倾斜（rotationY > 0，左边缘朝向观察者）
                            rotationY = pageOffset * -35f
                            // cameraDistance 越小透视越强
                            cameraDistance = 8f * density
                            // 邻居略淡出
                            alpha = (1f - absOffset * 0.35f).coerceAtLeast(0.25f)
                        }
                ) {
                    CarouselItem(
                        track = track,
                        isPlaying = isPlaying && track.id == currentTrack?.id,
                        onClick = { onTrackClick(track) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // 顶部渐变遮罩（仅作视觉氛围，不再显示文字信息）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D0D0D).copy(alpha = 0.5f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 160f
                        )
                    )
            )
        }

        PlayerBar(
            title = currentTrack?.title ?: "未选择歌曲",
            artist = currentTrack?.artist,
            coverUri = currentTrack?.coverUri,
            lyrics = currentTrack?.lyrics,
            isPlaying = isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            currentPosition = currentPosition,
            duration = duration,
            onSeek = onSeek,
            compact = true,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
