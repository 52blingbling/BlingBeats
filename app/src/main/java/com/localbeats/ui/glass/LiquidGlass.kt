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
     * 来自 Kyant0/AndroidLiquidGlass (Backdrop) 的核心透镜折射与光谱色散着色器
     * 包含圆角矩形 SDF 分析梯度、球透镜映射、7 波段色散叠加
     */
    private const val AGSL_LENS_SHADER = """
        uniform shader content;
        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        uniform float chromaticAberration;

        float radiusAt(float2 centeredCoord, float4 radii) {
            if (centeredCoord.x >= 0.0) {
                if (centeredCoord.y <= 0.0) return radii.y;
                else return radii.z;
            } else {
                if (centeredCoord.y <= 0.0) return radii.x;
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
            // 核心修复 1：严格限制在玻璃边缘内部发生折射。外部(sd > 0)或中心平坦无折射区直接返回原始图层，绝不处理！
            if (sd > 0.0 || -sd >= refractionHeight) {
                return content.eval(coord);
            }
            
            float factor = circleMap(1.0 - -sd / refractionHeight);
            float d = factor * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
            
            float2 refractedCoord = coord + d * grad;
            
            // 核心修复 2：将色散严格限制在边缘微幅偏移（0.2以内），防止大面积彩条溢出
            float dispersionIntensity = chromaticAberration * 0.20 * ((abs(centeredCoord.x) * abs(centeredCoord.y)) / max(halfSize.x * halfSize.y, 1.0));
            float2 dispersedCoord = d * grad * dispersionIntensity;
            
            // 核心修复 3：预乘 Alpha 保护 (Premultiplied Alpha Protection)，RGB 严禁超过 Alpha，彻底消除满屏霓虹彩条纹！
            float2 offsetR = refractedCoord + dispersedCoord;
            float2 offsetG = refractedCoord;
            float2 offsetB = refractedCoord - dispersedCoord;
            
            half4 cR = content.eval(offsetR);
            half4 cG = content.eval(offsetG);
            half4 cB = content.eval(offsetB);
            
            half alpha = (cR.a + cG.a + cB.a) * 0.33333;
            half3 rgb = half3(cR.r, cG.g, cB.b);
            rgb = min(rgb, half3(alpha));
            
            return half4(rgb, alpha);
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
 * 集成 AndroidLiquidGlass 原理（折射、高斯模糊采样、边缘色散、镜面高光、弹簧物理触控）。
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    style: LiquidGlassStyle = LiquidGlassStyle.Card,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 20.dp,
    elevation: Dp = when (style) {
        LiquidGlassStyle.Pill -> 18.dp
        LiquidGlassStyle.Card -> 12.dp
        LiquidGlassStyle.Button -> 8.dp
        LiquidGlassStyle.UltraThin -> 4.dp
    },
    borderWidth: Dp = 0.8.dp,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // 弹簧按压物理反馈
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (interactive && isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glass_scale"
    )

    // 玻璃基础材质透明度
    val baseSurfaceAlpha = when (style) {
        LiquidGlassStyle.Pill -> if (isDark) 0.55f else 0.65f
        LiquidGlassStyle.Card -> if (isDark) 0.45f else 0.55f
        LiquidGlassStyle.Button -> if (isDark) 0.60f else 0.70f
        LiquidGlassStyle.UltraThin -> if (isDark) 0.25f else 0.35f
    }

    val baseSurfaceColor = if (tint.isSpecified) {
        tint.copy(alpha = baseSurfaceAlpha)
    } else {
        if (isDark) {
            Color(0xFF1E1E28).copy(alpha = baseSurfaceAlpha)
        } else {
            Color.White.copy(alpha = baseSurfaceAlpha)
        }
    }

    val bottomSurfaceColor = if (tint.isSpecified) {
        tint.copy(alpha = baseSurfaceAlpha * 0.75f)
    } else {
        if (isDark) {
            Color(0xFF121218).copy(alpha = baseSurfaceAlpha * 0.85f)
        } else {
            Color.White.copy(alpha = baseSurfaceAlpha * 0.50f)
        }
    }

    // 镜面高光颜色：模拟真实玻璃反射，从左上到右下
    val highlightTopLeft = if (isDark) {
        Color.White.copy(alpha = if (isPressed) 0.55f else 0.38f)
    } else {
        Color.White.copy(alpha = if (isPressed) 0.90f else 0.75f)
    }

    val highlightBottomRight = if (isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.White.copy(alpha = 0.15f)
    }

    // 组合 Modifier
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true

            // Android 13+ (API 33+) 凸透镜折射与色散着色器 (Kyant0 核心算法)
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
                        val refHeight = 14.dp.toPx().coerceAtMost(size.minDimension * 0.25f)
                        s.setFloatUniform("refractionHeight", refHeight)
                        s.setFloatUniform("refractionAmount", -refHeight * 0.65f)
                        s.setFloatUniform("depthEffect", 0.25f)
                        s.setFloatUniform("chromaticAberration", 0.75f)
                        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
                    } catch (e: Throwable) {}
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
                // Android 12 (API 31/32) 高斯模糊降级
                try {
                    val px = blurRadius.toPx()
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        px, px, android.graphics.Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                } catch (e: Throwable) {}
            }
        }
        // 外阴影增加厚度悬浮感
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = if (tint.isSpecified) tint.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f),
            spotColor = if (tint.isSpecified) tint.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f)
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
        // 绘制玻璃本体渐变与镜面内部微光反射
        .drawWithContent {
            // 1. 绘制主体半透渐变层
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(baseSurfaceColor, bottomSurfaceColor)
                )
            )

            // 2. 绘制镜面斜角高光层 (Specular Sheen)
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.28f),
                    0.3f to Color.White.copy(alpha = if (isDark) 0.04f else 0.10f),
                    1.0f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.6f, size.height)
                )
            )

            // 3. 绘制内容
            drawContent()
        }
        // 4. 双重外边缘镜面高光边框
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                0.0f to highlightTopLeft,
                0.4f to highlightTopLeft.copy(alpha = highlightTopLeft.alpha * 0.4f),
                0.8f to highlightBottomRight,
                1.0f to highlightBottomRight.copy(alpha = 0.02f),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = shape
        )
}
