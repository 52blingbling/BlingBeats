package com.localbeats.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 液态玻璃交互按钮
 */
@Composable
fun LiquidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(20.dp),
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .liquidGlass(
                shape = shape,
                style = LiquidGlassStyle.Button,
                tint = tint,
                interactive = true,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 圆形液态玻璃图标按钮（适合顶栏与播放控制）
 */
@Composable
fun LiquidIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(
                shape = CircleShape,
                style = LiquidGlassStyle.Button,
                tint = tint,
                elevation = 6.dp,
                interactive = true,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 悬浮液态玻璃胶囊容器（用于状态标签、微型工具栏等）
 */
@Composable
fun LiquidPill(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    elevation: Dp = 8.dp,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .liquidGlass(
                shape = shape,
                style = LiquidGlassStyle.Pill,
                tint = tint,
                elevation = elevation
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 液态玻璃卡片容器
 */
@Composable
fun LiquidCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .liquidGlass(
                shape = shape,
                style = LiquidGlassStyle.Card,
                tint = tint,
                elevation = elevation,
                interactive = interactive,
                onClick = onClick
            )
    ) {
        content()
    }
}

/**
 * 液态玻璃播放滑块与发光珠（带液态透镜边缘）
 */
@Composable
fun LiquidSlider(
    value: Float, // 0f .. 1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    fillColor: Color = Color(0xFFBB86FC),
    height: Dp = 8.dp,
    thumbSize: Dp = 16.dp
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedThumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "slider_thumb"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize.coerceAtLeast(height * 2))
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    onValueChange(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onValueChange(fraction)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 底层轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape)
                .background(trackColor)
        )

        // 填充进度条（液态微光渐变）
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            fillColor.copy(alpha = 0.8f),
                            fillColor,
                            Color.White.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // 液态玻璃发光滑块小珠
        val density = LocalDensity.current
        val thumbOffsetDp = with(density) {
            val maxTravelPx = (widthPx - thumbSize.toPx()).coerceAtLeast(0f)
            (maxTravelPx * value.coerceIn(0f, 1f)).toDp()
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .size(thumbSize * animatedThumbScale)
                    .shadow(elevation = 6.dp, shape = CircleShape, spotColor = fillColor)
                    .liquidGlass(
                        shape = CircleShape,
                        style = LiquidGlassStyle.Button,
                        tint = Color.White
                    )
            )
        }
    }
}
