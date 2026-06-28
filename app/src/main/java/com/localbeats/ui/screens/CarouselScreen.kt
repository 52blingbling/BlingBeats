package com.localbeats.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import com.localbeats.data.lyrics.LyricsParser
import com.localbeats.data.model.MusicTrack
import com.localbeats.ui.components.CarouselItem
import com.localbeats.ui.components.PlayerBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * 电影胶卷横屏播放器：
 * - 专辑封面横向连排，形成胶片带效果
 * - 顶部和底部有胶片穿孔装饰
 * - 背景颜色跟随当前封面主色动态变化
 * - 拖动时每经过一张封面触发一次震动
 * - 松手后弹性吸附到最近封面并切歌
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
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val screenHeightDp = configuration.screenHeightDp.dp
    val frameSize = (screenHeightDp * 0.82f).coerceAtMost(340.dp).coerceAtLeast(180.dp)
    val gapDp = 12.dp

    val framePx = with(density) { frameSize.toPx() }
    val gapPx = with(density) { gapDp.toPx() }
    val stridePx = framePx + gapPx
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val startIndex = tracks.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
    // scrollOffset = -index * stride means that track[index] is centered
    val scrollOffset = remember { Animatable(-startIndex * stridePx) }
    var lastHapticIdx by remember { mutableIntStateOf(startIndex) }
    val velocityTracker = remember { VelocityTracker() }

    var controlsVisible by remember { mutableStateOf(true) }

    // 播放状态下，3.5 秒无操作自动隐藏控制按钮和标题
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            kotlinx.coroutines.delay(3500)
            controlsVisible = false
        }
    }

    // Sync when external track selection changes (e.g., PlayerBar prev/next).
    // Skip if a snap animation is already running to avoid fighting with snapToNearest.
    LaunchedEffect(currentTrack?.id, tracks) {
        if (scrollOffset.isRunning) return@LaunchedEffect  // snapToNearest is animating, don't interfere
        if (tracks.isEmpty()) return@LaunchedEffect
        val targetIdx = tracks.indexOfFirst { it.id == currentTrack?.id }
        if (targetIdx < 0) return@LaunchedEffect
        val n = tracks.size
        if (n <= 1) return@LaunchedEffect

        val currentV = ((-scrollOffset.value) / stridePx).roundToInt()
        val currentRealIdx = ((currentV % n) + n) % n
        val diff = targetIdx - currentRealIdx
        var shortestDiff = diff
        if (shortestDiff > n / 2) {
            shortestDiff -= n
        } else if (shortestDiff < -n / 2) {
            shortestDiff += n
        }
        val targetV = currentV + shortestDiff
        val targetOffset = -targetV * stridePx
        if (abs(scrollOffset.value - targetOffset) > 2f) {
            if (abs(shortestDiff) > 1) {
                // 如果是跳跃切歌（如随机播放），为了避免过快的物理滚动导致硬切感，
                // 我们在动画前，瞬间把坐标“瞬移”到目标的前一帧，然后再平滑滑动最后 1 帧。
                // 这样看起来就像是人为轻轻划了一下，完美解决硬变问题。
                val direction = if (shortestDiff > 0) 1 else -1
                val fakeStartV = targetV - direction
                scrollOffset.snapTo(-fakeStartV * stridePx)
            }
            scrollOffset.animateTo(targetOffset, spring(dampingRatio = 0.8f, stiffness = 300f))
        }
    }

    // Dynamic background: extract dark vibrant color from album art
    var rawBgColor by remember { mutableStateOf(Color(0xFF0A0A0A)) }
    val bgColor by animateColorAsState(
        targetValue = rawBgColor,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "filmBg"
    )

    LaunchedEffect(currentTrack?.coverUri) {
        val uri = currentTrack?.coverUri
        if (uri == null) { rawBgColor = Color(0xFF0A0A0A); return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val raw = palette.getVibrantColor(
                        palette.getMutedColor(
                            palette.getDominantColor(0xFF1A1A1A.toInt())
                        )
                    )
                    val c = Color(raw)
                    // 提升亮度和饱和度限制，让背景颜色变化更明显、更有氛围感
                    rawBgColor = Color(
                        red   = (c.red   * 0.65f).coerceIn(0f, 0.60f),
                        green = (c.green * 0.65f).coerceIn(0f, 0.60f),
                        blue  = (c.blue  * 0.65f).coerceIn(0f, 0.60f)
                    )
                }
            } catch (_: Exception) { rawBgColor = Color(0xFF0A0A0A) }
        }
    }

    // Lyrics
    val parsedLyrics = remember(currentTrack?.lyrics) { LyricsParser.parse(currentTrack?.lyrics) }
    val isSynced = LyricsParser.isSyncedLyrics(parsedLyrics)
    val currentLyricIndex = if (isSynced) {
        val idx = LyricsParser.currentLineIndex(parsedLyrics, currentPosition + 300L)
        if (idx < 0) 0 else idx
    } else -1
    val currentLyricText = when {
        isSynced && currentLyricIndex in parsedLyrics.indices -> parsedLyrics[currentLyricIndex].text
        !isSynced && parsedLyrics.isNotEmpty() -> parsedLyrics.joinToString("  ·  ") { it.text }
        else -> null
    }

    // Snap to nearest frame with fling support.
    // onTrackClick is called BEFORE animateTo so it is never lost if animation is interrupted.
    fun snapToNearest(velocityX: Float = 0f) {
        coroutineScope.launch {
            if (tracks.isEmpty()) return@launch
            val flingFrames = (velocityX / stridePx * 0.28f).toInt().coerceIn(-8, 8)
            val idx = ((-scrollOffset.value) / stridePx).roundToInt() - flingFrames
            val n = tracks.size
            val realIdx = ((idx % n) + n) % n
            // Trigger playback immediately — don’t wait for the animation to finish
            tracks.getOrNull(realIdx)?.let { track ->
                if (track.id != currentTrack?.id) onTrackClick(track)
            }
            // Animate the filmstrip to the snapped position
            scrollOffset.animateTo(
                targetValue = -idx * stridePx,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(bgColor, Color(0xFF060606)),
                    radius = screenWidthPx * 0.75f,
                    center = Offset(screenWidthPx / 2f, with(density) { configuration.screenHeightDp.dp.toPx() } / 2f)
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = {
                        onPlayPauseClick()
                    }
                )
            }
            .pointerInput(stridePx, tracks.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x
                        velocityTracker.resetTracking()
                        snapToNearest(velocity)
                    },
                    onDragCancel = { velocityTracker.resetTracking(); snapToNearest() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (tracks.isEmpty()) return@detectHorizontalDragGestures
                        coroutineScope.launch {
                            scrollOffset.snapTo(
                                scrollOffset.value + dragAmount
                            )
                        }
                        // Haptic: fire every time we cross into a new frame
                        val nowIdx = ((-scrollOffset.value) / stridePx).roundToInt()
                        if (nowIdx != lastHapticIdx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticIdx = nowIdx
                        }
                    }
                )
            }
    ) {
        if (tracks.isEmpty()) {
            Text(
                text = "No music found",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // ── Film strip sprocket holes (top & bottom) ──────────────────────
            val stripHeightDp = 20.dp
            val holeRadiusDp = 5.dp
            val holeSpacingDp = 22.dp

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // 开启离屏渲染，使 BlendMode.Clear 能够真正“挖空”胶片边缘，透出底部炫彩背景
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            ) {
                val stripH = stripHeightDp.toPx()
                val holeR = holeRadiusDp.toPx()
                val holeSpacing = holeSpacingDp.toPx()
                val filmEdgeColor = Color(0xFF0D0D0D) // 胶片黑色边缘
                val holeRimColor = Color(0xFF333333)

                // Top strip background
                drawRect(filmEdgeColor, topLeft = Offset(0f, 0f), size = Size(size.width, stripH))
                // Bottom strip background
                drawRect(filmEdgeColor, topLeft = Offset(0f, size.height - stripH), size = Size(size.width, stripH))

                // Sprocket holes – animated with scroll so holes move with the film
                val holePhase = (scrollOffset.value * 0.5f) % holeSpacing
                var x = holePhase - holeSpacing
                while (x < size.width + holeSpacing) {
                    val cx = x
                    val topCy = stripH / 2f
                    val botCy = size.height - stripH / 2f
                    
                    // Hole fill: 挖空胶片，透出后面的炫彩渐变背景！
                    drawCircle(Color.Black, radius = holeR, center = Offset(cx, topCy), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    drawCircle(Color.Black, radius = holeR, center = Offset(cx, botCy), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    
                    // Hole rim: 稍微画一点灰色的描边增加立体感
                    drawCircle(holeRimColor, radius = holeR, center = Offset(cx, topCy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    drawCircle(holeRimColor, radius = holeR, center = Offset(cx, botCy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    x += holeSpacing
                }

                // Sprocket holes only — center spotlight border removed
            }

            // ── Album cover frames (Infinite loop) ────────────────────────────
            val centerV = ((-scrollOffset.value) / stridePx).roundToInt()
            for (v in (centerV - 3)..(centerV + 3)) {
                val realIdx = ((v % tracks.size) + tracks.size) % tracks.size
                val track = tracks[realIdx]
                val itemCenterOffset = scrollOffset.value + v * stridePx
                val distFromCenter = abs(itemCenterOffset)
                val normalizedDist = (distFromCenter / stridePx).coerceIn(0f, 1f)

                val scale = lerp(1f, 0.62f, normalizedDist)
                val itemAlpha = lerp(1f, 0.35f, normalizedDist)

                androidx.compose.runtime.key(v) {
                    Box(
                        modifier = Modifier
                            .size(frameSize)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                translationX = itemCenterOffset
                                scaleX = scale
                                scaleY = scale
                                alpha = itemAlpha
                            }
                    ) {
                        CarouselItem(
                            track = track,
                            isPlaying = isPlaying && track.id == currentTrack?.id,
                            onClick = {
                                coroutineScope.launch {
                                    scrollOffset.animateTo(
                                        -v * stridePx,
                                        spring(dampingRatio = 0.70f, stiffness = 340f)
                                    )
                                    if (track.id != currentTrack?.id) onTrackClick(track)
                                }
                            },
                            size = frameSize
                        )
                    }
                }
            }

            // ── Song title (top, near screen edge) ────────────────────────────
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars)
                        // top padding bumped to 24dp so title clears the sprocket hole strip
                        .padding(top = 24.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = currentTrack?.title ?: "",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 32.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Synced lyrics (bottom, near screen edge) ──────────────────────
            AnimatedVisibility(
                visible = controlsVisible && currentLyricText != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                        .padding(bottom = 16.dp, top = 10.dp)
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
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(velocity = 35.dp, delayMillis = 600)
                        )
                    }
                }
            }

            // ── Compact PlayerBar (bottom-left corner) ────────────────────────
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, top = 28.dp)
            ) {
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
                    onOrientationToggleClick = onOrientationToggleClick
                )
            }
        }
    }
}

/** Linear interpolation helper */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
