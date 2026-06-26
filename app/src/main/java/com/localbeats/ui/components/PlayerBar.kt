package com.localbeats.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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

/**
 * 苹果风格长胶囊液态玻璃播放栏：
 * - 胶囊形状（圆角等于高度一半，形成 pill）
 * - 液态玻璃质感（半透明渐变 + 顶部高光 + 阴影）
 * - 左侧：封面缩略图 + 歌曲标题 + 歌词行（LRC 同步显示当前行；纯文本滚动；无歌词回退到艺术家）
 * - 右侧：仅播放/暂停按钮
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    title: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onSeek: (Long) -> Unit = {},
    coverUri: android.net.Uri? = null,
    artist: String? = null,
    lyrics: String? = null,
    compact: Boolean = false,
    onOrientationToggleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 胶囊形状：圆角设为很大的值，配合高度形成 pill
    val pillShape = RoundedCornerShape(50)
    // compact 模式：横屏下让胶囊变短（宽度收窄），更精致
    val horizontalPadding = if (compact) 24.dp else 16.dp
    // compact 模式：横屏下不占满宽度，收窄居中显示
    val widthModifier = if (compact) Modifier.fillMaxWidth(0.42f) else Modifier.fillMaxWidth()

    // 解析歌词：LRC 格式可按播放进度同步显示当前行；纯文本则整段滚动
    val parsedLyrics = remember(lyrics) { LyricsParser.parse(lyrics) }
    val isSynced = LyricsParser.isSyncedLyrics(parsedLyrics)
    // LRC 同步模式：定位当前行；若处于第一行之前的前奏，回退显示第一行（避免空白）
    val currentLyricIndex = if (isSynced) {
        val idx = LyricsParser.currentLineIndex(parsedLyrics, currentPosition)
        if (idx < 0) 0 else idx
    } else -1
    val currentLyricText = when {
        isSynced && currentLyricIndex in parsedLyrics.indices -> parsedLyrics[currentLyricIndex].text
        !isSynced && parsedLyrics.isNotEmpty() ->
            // 纯文本歌词：拼接为一行整段滚动
            parsedLyrics.joinToString("  ·  ") { it.text }
        else -> null
    }

    Box(
        modifier = modifier
            .then(widthModifier)
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 12.dp)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(pillShape)
                .shadow(
                    elevation = 20.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .background(
                    // 模拟 iOS 深色毛玻璃底色：深灰微透，带有极弱的上下渐变
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2C2C2E).copy(alpha = 0.85f),
                            Color(0xFF1C1C1E).copy(alpha = 0.85f)
                        )
                    )
                )
                // 模拟 iOS 玻璃高光描边：左上角偏白，右下角偏暗/透明
                .border(
                    width = 0.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.01f),
                            Color.White.copy(alpha = 0.1f) // 右下角轻微反光
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = pillShape
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：封面缩略图
                CoverThumbnail(coverUri = coverUri, size = 48.dp)

                Spacer(modifier = Modifier.width(12.dp))

                // 左侧：歌曲信息（标题 + 歌词/艺术家）
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 标题超长时 marquee 滚动
                        modifier = Modifier.basicMarquee(
                            velocity = 40.dp,
                            delayMillis = 800
                        )
                    )

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
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // LRC 同步歌词因切换频繁不需 marquee；纯文本整段滚动
                                modifier = if (isSynced) Modifier else Modifier.basicMarquee(
                                    velocity = 35.dp,
                                    delayMillis = 600
                                )
                            )
                        }
                    } else if (!artist.isNullOrBlank()) {
                        Text(
                            text = artist,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 右侧：播放/暂停按钮 及 旋转按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onOrientationToggleClick != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(onClick = onOrientationToggleClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ScreenRotation,
                                contentDescription = "Toggle Orientation",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.95f),
                                        Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPlayPauseClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                (scaleIn(tween(120)) + fadeIn(tween(120)))
                                    .togetherWith(scaleOut(tween(120)) + fadeOut(tween(120)))
                            },
                            label = "play_pause"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 圆形封面缩略图，无封面时显示音符占位 */
@Composable
private fun CoverThumbnail(coverUri: android.net.Uri?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
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
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
