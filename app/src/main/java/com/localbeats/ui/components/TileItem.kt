package com.localbeats.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localbeats.data.model.MusicTrack

// 为每首歌生成一个基于 id 的独特渐变色，作为封面占位符
private val placeholderPalettes = listOf(
    listOf(Color(0xFF6A1B9A), Color(0xFF1565C0)),
    listOf(Color(0xFF00695C), Color(0xFF1565C0)),
    listOf(Color(0xFF4A148C), Color(0xFFAD1457)),
    listOf(Color(0xFF0D47A1), Color(0xFF00838F)),
    listOf(Color(0xFF37474F), Color(0xFF6A1B9A)),
    listOf(Color(0xFF880E4F), Color(0xFF4A148C)),
    listOf(Color(0xFF1B5E20), Color(0xFF006064)),
    listOf(Color(0xFFBF360C), Color(0xFF4E342E)),
)

@Composable
fun TileItem(
    track: MusicTrack,
    isLarge: Boolean,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "tile_scale"
    )

    val tileAlpha = if (isLarge) 1f else 0.85f
    val aspectRatio = if (isLarge) 1f else 0.8f
    val shape = RoundedCornerShape(16.dp)
    val palette = placeholderPalettes[(track.id % placeholderPalettes.size).toInt().coerceAtLeast(0)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .scale(scale)
            .alpha(tileAlpha)
            .clip(shape)
            .then(
                if (isPlaying) {
                    Modifier.border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC6))
                        ),
                        shape = shape
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // 封面图 or 占位符
        if (track.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
        } else {
            // 无封面时：渐变色背景 + 音符图标
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(colors = palette),
                        shape = shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(if (isLarge) 40.dp else 28.dp)
                )
            }
        }

        // 底部渐变遮罩（保证文字可读）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = if (track.coverUri != null) 0.65f else 0.45f)
                        )
                    )
                )
        )

        // 正在播放时：顶部发光点
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF03DAC6))
                    .align(Alignment.TopEnd)
            )
        }

        Text(
            text = track.title,
            color = Color.White,
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.BottomStart)
        )
    }
}
