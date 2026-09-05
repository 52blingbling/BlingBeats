package com.localbeats.ui.glass

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃风格类型
 */
enum class LiquidGlassStyle {
    /** 悬浮药丸/长胶囊（播放栏、顶栏工具条） */
    Pill,
    /** 大卡片/面板（磁贴、引导页面板、设置项） */
    Card,
    /** 互动按钮/功能标签 */
    Button,
    /** 微薄半透晶体（角标、浮动计数、阅读器） */
    UltraThin
}

/**
 * 缓存 AGSL 着色器实例（在 Android 13+ / API 33+ 生效）
 */
private object LiquidGlassShaderCache {
    /**
     * Kyant0 官方标准圆角矩形透镜折射着色器 (RoundedRectRefractionShader)
     * 官方标准参数：
     * - refractionHeight: 12~16dp
     * - refractionAmount: 24dp
     * - depthEffect: 1.0 (开启)
     * - chromaticAberration: 0 (官方默认关闭色散，保持 iOS 纯净通透质感)
     */
    private const val AGSL_LENS_SHADER = """
        uniform shader content;
        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;

        float radiusAt(float2 coord, float4 radii) {
            if (coord.x >= 0.0) {
                if (coord.y <= 0.0) return radii.y;
                else return radii.z;
            } else {
                if (coord.y <= 0.0) return radii.x;
                else return radii.w;
            }
        }

        float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            float outside = length(max(cornerCoord, 0.0)) - radius;
            float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
            return outside + inside;
        }

        float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                return sign(coord) * normalize(max(cornerCoord, 0.0));
            } else {
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * float2(gradX, 1.0 - gradX);
            }
        }

        float circleMap(float x) {
            return 1.0 - sqrt(max(1.0 - x * x, 0.0));
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float radius = radiusAt(centeredCoord, cornerRadii);
            
            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            if (sd > 0.0 || -sd >= refractionHeight) {
                return content.eval(coord);
            }
            
            float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
            
            float2 refractedCoord = coord + d * grad;
            return content.eval(refractedCoord);
        }
    """

    val shader: android.graphics.RuntimeShader? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(AGSL_LENS_SHADER)
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }
}

/**
 * 核心液态玻璃 Modifier：
 * 采用 Kyant0 官方仓库及 iOS 原生磨砂玻璃（Frosted / Liquid Glass）标准设计：
 * - 纯净半透磨砂基底（无杂色死白）
 * - 顶部自然下沉的发丝级微光描边（iOS Hairline Specular Border）
 * - 柔和深邃的物理悬浮阴影（iOS Ambient Shadow）
 * - Android 13+ 官方 AGSL 凸透镜曲率折射
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    style: LiquidGlassStyle = LiquidGlassStyle.Card,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 16.dp,
    elevation: Dp = when (style) {
        LiquidGlassStyle.Pill -> 14.dp
        LiquidGlassStyle.Card -> 10.dp
        LiquidGlassStyle.Button -> 6.dp
        LiquidGlassStyle.UltraThin -> 3.dp
    },
    borderWidth: Dp = 0.8.dp,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // 弹簧按压物理反馈 (iOS 经典物理触控阻尼)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (interactive && isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glass_scale"
    )

    // iOS 标准半透磨砂基底配色（纯净深邃，绝不发灰发死）
    val surfaceColor = if (tint.isSpecified) {
        tint.copy(alpha = if (isDark) 0.55f else 0.65f)
    } else {
        if (isDark) {
            Color(0xFF1E1E24).copy(alpha = 0.62f)
        } else {
            Color.White.copy(alpha = 0.72f)
        }
    }

    val bottomTint = if (tint.isSpecified) {
        tint.copy(alpha = if (isDark) 0.62f else 0.72f)
    } else {
        if (isDark) {
            Color(0xFF141418).copy(alpha = 0.68f)
        } else {
            Color.White.copy(alpha = 0.58f)
        }
    }

    // iOS 经典发丝微光描边（从顶部柔和过渡到底部，彻底杜绝右下角死白）
    val borderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.32f else 0.65f),
        0.5f to Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
        1.0f to Color.White.copy(alpha = if (isDark) 0.02f else 0.08f)
    )

    // 组合 Modifier
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true

            // Android 13+ (API 33+) Kyant0 官方标准透镜折射
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LiquidGlassShaderCache.shader?.let { s ->
                    try {
                        s.setFloatUniform("size", size.width, size.height)
                        s.setFloatUniform("offset", 0f, 0f)
                        val r = when (shape) {
                            CircleShape -> size.minDimension * 0.5f
                            is RoundedCornerShape -> 24.dp.toPx().coerceAtMost(size.minDimension * 0.5f)
                            else -> 16.dp.toPx().coerceAtMost(size.minDimension * 0.5f)
                        }
                        s.setFloatUniform("cornerRadii", r, r, r, r)
                        // Kyant0 官方推荐透镜标准参数
                        val refHeight = 12.dp.toPx().coerceAtMost(size.minDimension * 0.30f)
                        s.setFloatUniform("refractionHeight", refHeight)
                        s.setFloatUniform("refractionAmount", 24.dp.toPx().coerceAtMost(size.minDimension * 0.45f))
                        s.setFloatUniform("depthEffect", 1.0f)
                        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
                    } catch (e: Throwable) {}
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
                // Android 12 (API 31/32) 优雅高斯模糊
                try {
                    val px = blurRadius.toPx()
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        px, px, android.graphics.Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                } catch (e: Throwable) {}
            }
        }
        // iOS 柔和环境空间阴影
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.25f),
            spotColor = Color.Black.copy(alpha = 0.35f)
        )
        .clip(shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
        // 绘制 iOS 标准纯净磨砂玻璃基底
        .drawWithContent {
            // 绘制纯净半透微渐变底色
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(surfaceColor, bottomTint)
                )
            )

            // 绘制内容 (图标、文字、封面)
            drawContent()
        }
        // iOS 经典发丝级顶部自然高光描边 (Hairline Highlight Border)
        .border(
            width = borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
