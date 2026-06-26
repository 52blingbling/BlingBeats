package com.localbeats.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.localbeats.data.lyrics.LyricsParser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOrientationToggleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 循环切换：用较大的虚拟页数实现近似无限循环，page 取模映射到真实曲目。
    // 不能用 Int.MAX_VALUE：HorizontalPager 的滚动范围 = (pageCount-1)*pageWidth 会溢出，
    // 导致无法滑动。100k 页足够（约 5 万次循环），且 pageCount*pageWidth 远小于 Int.MAX_VALUE。
    val virtualCount = if (tracks.isEmpty()) 0 else if (tracks.size == 1) 1 else 100_000
    val startIndex = tracks.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
    // 初始页位于中间，左右各约 5 万页可滑，足够循环
    val startVirtual = if (virtualCount <= 1) 0 else virtualCount / 2 + startIndex
    val pagerState = rememberPagerState(
        initialPage = startVirtual,
        pageCount = { virtualCount }
    )

    // 虚拟页 → 真实曲目（取模，兼容负数）
    fun trackAt(virtualPage: Int): MusicTrack? {
        if (tracks.isEmpty()) return null
        val realIndex = ((virtualPage % tracks.size) + tracks.size) % tracks.size
        return tracks.getOrNull(realIndex)
    }

    // 用户滑动停止后（settledPage）切换到对应曲目
    // 用 tracks 作为 key 之一，避免 tracks 变化后 trackAt 闭包捕获旧值
    LaunchedEffect(pagerState, tracks) {
        snapshotFlow { pagerState.settledPage }
            .collectLatest { page ->
                val track = trackAt(page) ?: return@collectLatest
                if (track.id != currentTrack?.id) {
                    onTrackClick(track)
                }
            }
    }

    // 外部切歌（如播放下一首、点磁贴）时，把轮播平滑滚到对应曲目
    // 选择离当前页最近的虚拟页，保证最短路径滚动
    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(currentTrack?.id, tracks) {
        val targetTrack = currentTrack ?: return@LaunchedEffect
        if (tracks.isEmpty()) return@LaunchedEffect
        val targetReal = tracks.indexOfFirst { it.id == targetTrack.id }
        if (targetReal < 0) return@LaunchedEffect
        val currentVirtual = pagerState.currentPage
        val currentReal = ((currentVirtual % tracks.size) + tracks.size) % tracks.size
        // 取离 currentReal 最近的等价虚拟页
        val targetVirtual = currentVirtual + (targetReal - currentReal)

        if (isFirstLoad) {
            pagerState.scrollToPage(targetVirtual)
            isFirstLoad = false
        } else if (!pagerState.isScrollInProgress && pagerState.settledPage != targetVirtual) {
            pagerState.animateScrollToPage(targetVirtual)
        }
    }

    // 自定义 fling：基于速度的快→慢减速动画，支持跨多页甩动，最终自然吸附
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(20),
        snapPositionalThreshold = 0.5f
    )

    // 解析歌词
    val parsedLyrics = remember(currentTrack?.lyrics) { LyricsParser.parse(currentTrack?.lyrics) }
    val isSynced = LyricsParser.isSyncedLyrics(parsedLyrics)
    val currentLyricIndex = if (isSynced) {
        val idx = LyricsParser.currentLineIndex(parsedLyrics, currentPosition)
        if (idx < 0) 0 else idx
    } else -1
    val currentLyricText = when {
        isSynced && currentLyricIndex in parsedLyrics.indices -> parsedLyrics[currentLyricIndex].text
        !isSynced && parsedLyrics.isNotEmpty() -> parsedLyrics.joinToString("  ·  ") { it.text }
        else -> null
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    // 动态计算封面大小，为顶部标题和底部歌词留出更紧凑的空间（现在播放条在角落，可以把封面放得更大）
    val coverSize = (screenHeightDp - 100.dp).coerceAtMost(360.dp).coerceAtLeast(200.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        if (tracks.isEmpty()) {
            Text(
                text = "No music found",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 不使用 contentPadding peek，改用 translationX 实现真正的堆叠
                contentPadding = PaddingValues(horizontal = 0.dp),
                // 自定义 fling：基于速度的快→慢减速吸附
                flingBehavior = flingBehavior,
                // 预渲染左右各 2 页，保证堆叠邻居可见
                beyondBoundsPageCount = 2
            ) { page ->
                val track = trackAt(page) ?: return@HorizontalPager
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
                        size = coverSize,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // 顶部渐变遮罩与标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 32.dp)
            ) {
                Text(
                    text = currentTrack?.title ?: "未选择歌曲",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 32.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 底部歌词
            if (currentLyricText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp) // 悬浮在 PlayerBar 上方
                        .padding(horizontal = 24.dp)
                ) {
                    AnimatedContent(
                        targetState = currentLyricText,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(200)))
                        },
                        label = "lyric_line",
                        modifier = Modifier.align(Alignment.Center)
                    ) { line ->
                        Text(
                            text = line,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(velocity = 35.dp, delayMillis = 600)
                        )
                    }
                }
            }
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
            onOrientationToggleClick = onOrientationToggleClick,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 4.dp)
        )
    }
}
