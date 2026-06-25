package com.localbeats.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localbeats.data.model.MusicTrack
import kotlin.math.abs

@Composable
fun CarouselItem(
    track: MusicTrack,
    pageOffset: Float,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val absOffset = abs(pageOffset)
    val scale by animateFloatAsState(
        targetValue = 1f - (absOffset * 0.15f).coerceIn(0f, 0.3f),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "carousel_scale"
    )
    val tilt = pageOffset * -25f
    val itemAlpha by animateFloatAsState(
        targetValue = 1f - (absOffset * 0.4f).coerceIn(0f, 0.5f),
        animationSpec = tween(200),
        label = "carousel_alpha"
    )

    val shape = RoundedCornerShape(20.dp)
    val palette = placeholderPalettes[(track.id % placeholderPalettes.size).toInt().coerceAtLeast(0)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationY = tilt
                cameraDistance = 12f * density
            }
            .alpha(itemAlpha)
            .clip(shape)
            .then(
                if (isPlaying) {
                    Modifier.border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC6))
                        ),
                        shape = shape
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (track.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(shape)
            )
        } else {
            // 无封面：渐变色背景 + 大号音符图标
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        brush = Brush.linearGradient(colors = palette),
                        shape = shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        // 镜面反射效果（仅有封面图时）
        if (track.coverUri != null) {
            val reflectionAlpha = (0.25f * (1f - absOffset)).coerceIn(0f, 0.25f)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.coverUri)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = reflectionAlpha,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .graphicsLayer { rotationX = 180f }
                    .clip(shape)
            )
        }

        // 底部渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                    )
                )
        )
    }
}
