package com.localbeats.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 液态流光漫反射背景：
 * 为上层的液态玻璃（PlayerBar、磁贴、控制岛）提供生动丰富的色彩折射源。
 */
@Composable
fun LiquidAmbientBackground(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF6A1B9A),
    secondaryColor: Color = Color(0xFF1565C0),
    tertiaryColor: Color = Color(0xFF00838F)
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val animPrimary by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(1200),
        label = "ambient_c1"
    )
    val animSecondary by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(1200),
        label = "ambient_c2"
    )
    val animTertiary by animateColorAsState(
        targetValue = tertiaryColor,
        animationSpec = tween(1200),
        label = "ambient_c3"
    )

    val transition = rememberInfiniteTransition(label = "ambient_drift")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_phase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alphaMultiplier = if (isDark) 0.35f else 0.20f

            // 光晕球 1：左上方漂移
            val c1 = Offset(
                x = w * (0.25f + 0.15f * phase),
                y = h * (0.20f + 0.10f * phase)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animPrimary.copy(alpha = 0.8f * alphaMultiplier),
                        animPrimary.copy(alpha = 0.3f * alphaMultiplier),
                        Color.Transparent
                    ),
                    center = c1,
                    radius = w * 0.7f
                ),
                center = c1,
                radius = w * 0.7f
            )

            // 光晕球 2：右下方漂移
            val c2 = Offset(
                x = w * (0.75f - 0.20f * phase),
                y = h * (0.70f - 0.15f * phase)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animSecondary.copy(alpha = 0.7f * alphaMultiplier),
                        animSecondary.copy(alpha = 0.25f * alphaMultiplier),
                        Color.Transparent
                    ),
                    center = c2,
                    radius = w * 0.8f
                ),
                center = c2,
                radius = w * 0.8f
            )

            // 光晕球 3：中央点缀微光
            val c3 = Offset(
                x = w * (0.50f + 0.10f * (1f - phase)),
                y = h * (0.45f + 0.15f * (1f - phase))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animTertiary.copy(alpha = 0.5f * alphaMultiplier),
                        Color.Transparent
                    ),
                    center = c3,
                    radius = w * 0.55f
                ),
                center = c3,
                radius = w * 0.55f
            )
        }
    }
}
