package com.localbeats.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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
    modifier: Modifier = Modifier
) {
    val glassShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    // 拖动进度条时用本地 state，避免和播放器 state 冲突
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val progress = if (isDragging) {
        dragProgress
    } else {
        if (duration > 0L) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(glassShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E).copy(alpha = 0.95f),
                        Color(0xFF0D0D0D).copy(alpha = 0.98f)
                    )
                )
            )
    ) {
        // 顶部高光线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFBB86FC).copy(alpha = 0.6f),
                            Color(0xFF03DAC6).copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 歌曲标题行 + 播放控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 动态音波 + 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isPlaying) {
                        WaveformBars(
                            isPlaying = true,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn())
                                .togetherWith(slideOutVertically { -it } + fadeOut())
                        },
                        label = "title_anim",
                        modifier = Modifier.weight(1f)
                    ) { t ->
                        Text(
                            text = t,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 播放控制按钮组
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(size = 32.dp, onClick = onPreviousClick) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 主播放/暂停按钮（更大）
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC6))
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
                                (slideInVertically { it } + fadeIn())
                                    .togetherWith(slideOutVertically { -it } + fadeOut())
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
                    Spacer(modifier = Modifier.width(8.dp))
                    ControlButton(size = 32.dp, onClick = onNextClick) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            Slider(
                value = progress,
                onValueChange = { v ->
                    isDragging = true
                    dragProgress = v
                },
                onValueChangeFinished = {
                    if (duration > 0L) {
                        onSeek((dragProgress * duration).toLong())
                    }
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFBB86FC),
                    activeTrackColor = Color(0xFFBB86FC),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )

            // 时间显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayPosition = if (isDragging && duration > 0L) {
                    (dragProgress * duration).toLong()
                } else currentPosition
                Text(
                    text = formatTime(displayPosition),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    size: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 动态音波柱子动画，播放中显示 */
@Composable
fun WaveformBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    barColor: Color = Color(0xFFBB86FC)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val bars = List(barCount) { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + i * 120,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$i"
        )
    }

    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        bars.forEach { animValue ->
            val height by animValue
            val barHeight = if (isPlaying) (20.dp * height) else 4.dp
            val animatedHeight by animateFloatAsState(
                targetValue = if (isPlaying) height else 0.2f,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "bar_height"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp * animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
