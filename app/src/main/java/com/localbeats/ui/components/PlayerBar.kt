package com.localbeats.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localbeats.data.lyrics.LyricsParser
import com.localbeats.ui.glass.LiquidGlassStyle
import com.localbeats.ui.glass.LiquidIconButton
import com.localbeats.ui.glass.liquidGlass

/**
 * 苹果 / AndroidLiquidGlass 风格悬浮长胶囊液态玻璃播放栏：
 * - 纯正液态透镜折射（SDF 凹凸透镜 + 色散）
 * - 高透磨砂质感 + 镜面边缘渐变高光 + 悬浮环境软阴影
 * - 左侧：封面缩略图 + 标题跑马灯 + 实时歌词/艺术家
 * - 右侧：液态玻璃旋转与播放/暂停控制按钮
 * - 底部：嵌入式光纤微光播放进度条
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    title: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    currentPositionProvider: () -> Long = { 0L },
    duration: Long = 0L,
    onSeek: (Long) -> Unit = {},
    coverUri: android.net.Uri? = null,
    artist: String? = null,
    lyrics: String? = null,
    compact: Boolean = false,
    onOrientationToggleClick: (() -> Unit)? = null,
    glassTint: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = if (compact) 24.dp else 16.dp
    val widthModifier = if (compact) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()
    val barHeight = if (compact) 58.dp else 66.dp
    val thumbSize = if (compact) 42.dp else 50.dp

    // 歌词解析
    val parsedLyrics = remember(lyrics) { LyricsParser.parse(lyrics) }
    val isSynced = LyricsParser.isSyncedLyrics(parsedLyrics)
    val currentPosition = currentPositionProvider()
    val currentLyricIndex = if (isSynced) {
        val idx = LyricsParser.currentLineIndex(parsedLyrics, currentPosition + 300L)
        if (idx < 0) 0 else idx
    } else -1
    val currentLyricText = when {
        isSynced && currentLyricIndex in parsedLyrics.indices -> parsedLyrics[currentLyricIndex].text
        !isSynced && parsedLyrics.isNotEmpty() ->
            parsedLyrics.joinToString("  ·  ") { it.text }
        else -> null
    }

    // 进度比例
    val progressFraction = if (duration > 0L) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .then(widthModifier)
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 12.dp)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .then(if (compact) Modifier.wrapContentWidth() else Modifier.fillMaxWidth())
                .height(barHeight)
                .liquidGlass(
                    shape = CircleShape,
                    style = LiquidGlassStyle.Pill,
                    tint = glassTint,
                    elevation = 20.dp,
                    borderWidth = 1.dp
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // 拦截点击穿透
                )
        ) {
            // 胶囊底部嵌入式光纤微光进度条
            if (duration > 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(2.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.primary,
                                        Color.White.copy(alpha = 0.9f)
                                    )
                                )
                            )
                    )
                }
            }

            Row(
                modifier = (if (compact) Modifier.wrapContentWidth() else Modifier.fillMaxWidth())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!compact) {
                    // 左侧：封面缩略图（带液态玻璃边框）
                    AnimatedContent(
                        targetState = coverUri,
                        transitionSpec = {
                            fadeIn(tween(300)).togetherWith(fadeOut(tween(300)))
                        },
                        label = "cover_change"
                    ) { animatedCover ->
                        CoverThumbnail(coverUri = animatedCover, size = thumbSize)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 左侧：歌曲信息（标题 + 歌词/艺术家）
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        val displayTitle = if (!artist.isNullOrBlank() && artist != "<unknown>") "$title - $artist" else title
                        AnimatedContent(
                            targetState = displayTitle,
                            transitionSpec = {
                                (slideInHorizontally { it / 2 } + fadeIn(tween(300)))
                                    .togetherWith(slideOutHorizontally { -it / 2 } + fadeOut(tween(300)))
                            },
                            label = "title_change"
                        ) { animatedTitle ->
                            Text(
                                text = animatedTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(
                                    velocity = 40.dp,
                                    delayMillis = 800
                                )
                            )
                        }

                        // 第二行：优先显示歌词；无歌词时回退到艺术家
                        if (currentLyricText != null) {
                            AnimatedContent(
                                targetState = currentLyricText,
                                transitionSpec = {
                                    (slideInVertically { it / 2 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(200)))
                                },
                                label = "lyric_line"
                            ) { line ->
                                Text(
                                    text = line,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(
                                        velocity = 35.dp,
                                        delayMillis = 600
                                    )
                                )
                            }
                        } else if (!artist.isNullOrBlank()) {
                            Text(
                                text = artist,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // 右侧：播放/暂停按钮及旋转按钮
                Row(
                    horizontalArrangement = if (compact) Arrangement.Center else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOrientationToggleClick != null) {
                        LiquidIconButton(
                            icon = Icons.Filled.ScreenRotation,
                            onClick = onOrientationToggleClick,
                            size = 38.dp,
                            iconSize = 18.dp,
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                            contentDescription = "Toggle Orientation"
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    // 播放/暂停按钮：液态透镜图标按钮
                    LiquidIconButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        onClick = onPlayPauseClick,
                        size = 46.dp,
                        iconSize = 26.dp,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}

/** 圆形封面缩略图，外加液态玻璃光环边缘 */
@Composable
private fun CoverThumbnail(coverUri: android.net.Uri?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .liquidGlass(
                shape = CircleShape,
                style = LiquidGlassStyle.Button,
                elevation = 4.dp,
                borderWidth = 1.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
