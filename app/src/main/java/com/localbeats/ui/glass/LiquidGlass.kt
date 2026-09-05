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
        uniform float lightAngle;
        uniform float highlightFalloff;
        uniform float highlightIntensity;

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
            if (sd > 0.0) {
                return content.eval(coord);
            }
            
            float innerDist = -sd;
            float2 refractedCoord = coord;
            float bevelProgress = 0.0;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
            
            if (innerDist < refractionHeight) {
                bevelProgress = 1.0 - innerDist / refractionHeight;
                float d = circleMap(bevelProgress) * refractionAmount;
                refractedCoord = coord + d * grad;
            }
            
            half4 col = content.eval(refractedCoord);
            
            // 真实物理光影与透镜聚焦焦散 (Kyant Specular Highlight & Caustic Bevel)
            if (innerDist < refractionHeight) {
                // 光源来自左上方 (-135度)
                float2 lightDir = float2(cos(lightAngle), sin(lightAngle));
                float dDot = dot(grad, lightDir);
                if (dDot > 0.0) {
                    float spec = pow(dDot, highlightFalloff) * highlightIntensity;
                    spec *= smoothstep(0.05, 0.95, bevelProgress);
                    col.rgb += half3(spec, spec, spec);
                }
                // 内部透镜焦散环 (Caustic Ring)
                float caustic = exp(-pow((bevelProgress - 0.45) * 3.5, 2.0)) * (highlightIntensity * 0.35);
                col.rgb += half3(caustic, caustic, caustic);
            }
            
            return col;
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
 * 遵循 Kyant0 Backdrop 标准与 Apple iOS 原生液态磨砂玻璃（Frosted / Liquid Glass）设计：
 * - 剔除厚重不透明死白底色，采用 12%~25% 超高透光晶体材质，令底层元素通透清晰可见
 * - AGSL 物理透镜折射 + 左上方精准定向 3D 物理镜面高光 (Kyant Specular Highlight)
 * - 内部透镜聚焦焦散光环 (Caustic Bevel Glow)
 * - 顶部发丝级自然反光边缘 (iOS Hairline Specular Border)
 * - 柔和环境空间悬浮阴影 (iOS Ambient Shadow)
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

    // 纯正 iOS / Kyant 磨砂玻璃通透基底配色（通透、高透光率，让背景元素自然显露）
    val surfaceColor = if (tint.isSpecified) {
        tint.copy(alpha = if (isDark) 0.28f else 0.22f)
    } else {
        when (style) {
            LiquidGlassStyle.UltraThin -> if (isDark) Color(0xFF16161C).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
            LiquidGlassStyle.Pill -> if (isDark) Color(0xFF1C1C22).copy(alpha = 0.38f) else Color.White.copy(alpha = 0.22f)
            LiquidGlassStyle.Card -> if (isDark) Color(0xFF1A1A20).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
            LiquidGlassStyle.Button -> if (isDark) Color(0xFF22222A).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.28f)
        }
    }

    val bottomTint = if (tint.isSpecified) {
        tint.copy(alpha = if (isDark) 0.38f else 0.15f)
    } else {
        when (style) {
            LiquidGlassStyle.UltraThin -> if (isDark) Color(0xFF0E0E12).copy(alpha = 0.32f) else Color.White.copy(alpha = 0.06f)
            LiquidGlassStyle.Pill -> if (isDark) Color(0xFF101014).copy(alpha = 0.46f) else Color.White.copy(alpha = 0.12f)
            LiquidGlassStyle.Card -> if (isDark) Color(0xFF101016).copy(alpha = 0.52f) else Color.White.copy(alpha = 0.15f)
            LiquidGlassStyle.Button -> if (isDark) Color(0xFF14141A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f)
        }
    }

    // iOS 经典发丝微光描边（从顶部柔和过渡到底部，自然呈现物理边缘）
    val borderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.35f else 0.55f),
        0.5f to Color.White.copy(alpha = if (isDark) 0.12f else 0.22f),
        1.0f to Color.White.copy(alpha = if (isDark) 0.03f else 0.06f)
    )

    // 组合 Modifier
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true

            // Android 13+ (API 33+) Kyant0 官方标准透镜折射 + 3D 物理镜面高光
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
                        val refHeight = 14.dp.toPx().coerceAtMost(size.minDimension * 0.35f)
                        s.setFloatUniform("refractionHeight", refHeight)
                        s.setFloatUniform("refractionAmount", 24.dp.toPx().coerceAtMost(size.minDimension * 0.45f))
                        s.setFloatUniform("depthEffect", 1.0f)
                        // Kyant 标准定向 3D 高光参数 (左上方 -135 度光源)
                        s.setFloatUniform("lightAngle", -2.3561945f)
                        s.setFloatUniform("highlightFalloff", 2.2f)
                        s.setFloatUniform("highlightIntensity", if (isDark) 0.38f else 0.48f)

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
        // 绘制 iOS 标准纯净高透磨砂玻璃基底与光影
        .drawWithContent {
            // 1. 绘制纯净半透微渐变晶体底色
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(surfaceColor, bottomTint)
                )
            )

            // 2. 绘制左上方自然散射微光带（增强玻璃的晶体厚度与通透立体感）
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.20f),
                    0.35f to Color.White.copy(alpha = if (isDark) 0.03f else 0.06f),
                    0.70f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.70f, size.height * 0.70f)
                )
            )

            // 3. 绘制内容 (图标、文字、封面)
            drawContent()
        }
        // iOS 经典发丝级顶部自然高光描边 (Hairline Highlight Border)
        .border(
            width = borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
