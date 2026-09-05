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
            
            // Kyant 官方物理透镜圆映射：折射位移向内聚焦 (-refractionAmount)
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
 * 遵循 Apple iOS 原生液态磨砂玻璃（Frosted / Liquid Glass）与 Kyant0 Backdrop 标准：
 * 1. 均一统一的半透磨砂基底，整块玻璃透光度自然一致，彻底根除“里面深周围浅”的分层环。
 * 2. 官方标准 RenderEffect 执行链 (Blur => Lens)：底层内容先优雅高斯模糊，再通过 AGSL 凸透镜向内微折射边缘。
 * 3. iOS 发丝级单像素微光描边 (Hairline Highlight Rim)：从左上方优雅渐隐，赋予真实玻璃晶体棱角。
 * 4. 柔和深邃的环境悬浮阴影 (Ambient Shadow)。
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    style: LiquidGlassStyle = LiquidGlassStyle.Card,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 20.dp,
    elevation: Dp = when (style) {
        LiquidGlassStyle.Pill -> 12.dp
        LiquidGlassStyle.Card -> 10.dp
        LiquidGlassStyle.Button -> 6.dp
        LiquidGlassStyle.UltraThin -> 4.dp
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

    // iOS 标准均一磨砂玻璃基底（整块材质质感纯净通透，绝无中心边缘分层）
    val surfaceColor = if (tint.isSpecified) {
        tint.copy(alpha = if (isDark) 0.45f else 0.38f)
    } else {
        when (style) {
            LiquidGlassStyle.UltraThin -> if (isDark) Color(0xFF1E1E22).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.40f)
            LiquidGlassStyle.Pill -> if (isDark) Color(0xFF242428).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.54f)
            LiquidGlassStyle.Card -> if (isDark) Color(0xFF222226).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.58f)
            LiquidGlassStyle.Button -> if (isDark) Color(0xFF28282E).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.62f)
        }
    }

    // iOS 经典发丝级单像素高光描边 (从左上至右下自然渐隐)
    val borderBrush = Brush.linearGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.40f else 0.68f),
        0.45f to Color.White.copy(alpha = if (isDark) 0.15f else 0.30f),
        1.0f to Color.White.copy(alpha = if (isDark) 0.03f else 0.08f),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    // 组合 Modifier
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true

            // Android 13+ (API 33+) Kyant0 官方标准效果链：Blur => Lens Refraction
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
                        // 关键：官方 Lens.kt 向内物理聚焦折射，使用负值位移 (-refractionAmount)
                        s.setFloatUniform("refractionAmount", -16.dp.toPx().coerceAtMost(size.minDimension * 0.30f))
                        s.setFloatUniform("depthEffect", 1.0f)

                        val lensEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content")
                        if (blurRadius > 0.dp) {
                            val px = blurRadius.toPx()
                            val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                                px, px, android.graphics.Shader.TileMode.CLAMP
                            )
                            // 官方标准顺序：先模糊背景，再进行透镜折射
                            renderEffect = android.graphics.RenderEffect.createChainEffect(
                                lensEffect, blurEffect
                            ).asComposeRenderEffect()
                        } else {
                            renderEffect = lensEffect.asComposeRenderEffect()
                        }
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
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.30f else 0.15f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.22f)
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
        // 绘制 iOS 标准纯净均一磨砂玻璃基底
        .drawWithContent {
            // 1. 绘制 iOS 统一磨砂底色（整块玻璃质感均一，绝无内外分层）
            drawRect(color = surfaceColor)

            // 2. 绘制内容 (图标、文字、封面)
            drawContent()
        }
        // 3. iOS 经典发丝级微光高光描边 (Hairline Specular Rim)
        .border(
            width = borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
