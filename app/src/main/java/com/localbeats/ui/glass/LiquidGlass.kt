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
     * 3D 液态玻璃透镜折射与物理光影着色器 (AGSL)
     * 结合 Kyant0 SDF 梯度法线，实现：
     * 1. 物理凸透镜曲率折射 (Meniscus Refraction)
     * 2. 棱镜边缘微色散 (Prismatic Dispersion)
     * 3. 3D 定向镜面高光弧 (3D Specular Glint)
     * 4. 菲涅尔内边缘聚光光环 (Caustic Halo)
     * 5. 底部环境漫反射反光 (Ambient Bounce)
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
            
            // 边缘外部严格不处理
            if (sd > 0.0) {
                return content.eval(coord);
            }
            
            // 1. 物理凸透镜曲率折射位移计算
            float2 grad = float2(0.0);
            float2 refractedCoord = coord;
            float factor = 0.0;
            
            if (-sd < refractionHeight) {
                factor = circleMap(1.0 - -sd / refractionHeight);
                float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
                grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
                float d = factor * refractionAmount;
                refractedCoord = coord + d * grad;
            }
            
            // 2. 棱镜微色散采样 (Chromatic Dispersion)
            float dispersion = chromaticAberration * 0.20 * factor;
            float2 disp = grad * (refractionAmount * dispersion);
            
            half4 cR = content.eval(refractedCoord + disp);
            half4 cG = content.eval(refractedCoord);
            half4 cB = content.eval(refractedCoord - disp);
            
            half baseA = (cR.a + cG.a + cB.a) * 0.33333;
            half3 baseRgb = min(half3(cR.r, cG.g, cB.b), half3(baseA));
            
            // 3. 动态 3D 光影与高光反射模型 (3D Specular & Caustic Ring)
            // 虚拟光源从左上方照射 (约 -45 度夹角)
            float2 lightDir = normalize(float2(-0.707, -0.707));
            float gradRadiusFull = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 surfaceNormal = gradSdRoundedRect(centeredCoord, halfSize, gradRadiusFull);
            
            // 镜面强高光 (Specular Glint) - 沿左上边缘产生耀眼的玻璃折射光弧
            float nDotL = max(dot(surfaceNormal, -lightDir), 0.0);
            float specular = pow(nDotL, 12.0);
            float specularMask = smoothstep(0.0, -2.5, sd) * (1.0 - smoothstep(-refractionHeight * 0.75, -refractionHeight, sd));
            float specularIntensity = specular * specularMask * 0.92;
            
            // 菲涅尔内透镜聚光折射环 (Caustic Halo) - 形成水珠般晶莹剔透的光环
            float rimMask = smoothstep(0.0, -1.2, sd) * (1.0 - smoothstep(-refractionHeight, -refractionHeight - 4.0, sd));
            float causticGlow = pow(1.0 - (-sd / refractionHeight), 2.0) * rimMask * 0.40;
            
            // 底部环境漫反射反光 (Ambient Bounce Light)
            float ambientDot = max(dot(surfaceNormal, lightDir), 0.0);
            float ambientBounce = pow(ambientDot, 6.0) * specularMask * 0.22;
            
            // 4. 光影与图层物理混合 (保障 Skia 预乘 Alpha 规范)
            float totalLight = specularIntensity + causticGlow + ambientBounce;
            half3 lightRgb = half3(1.0, 1.0, 1.0) * totalLight;
            
            half3 finalRgb = baseRgb + lightRgb;
            half finalA = max(baseA, half(totalLight * 0.85));
            finalRgb = min(finalRgb, half3(finalA));
            
            return half4(finalRgb, finalA);
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
 * 集成真实物理光学（凸透镜弧面折射、3D 镜面强高光弧、菲涅尔晶体聚光环、倒角深阴影、微色散、弹簧物理触控）。
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
    borderWidth: Dp = 1.2.dp,
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

    // 晶体通透基底透明度（告别死板灰块，还原清澈晶体）
    val crystalTopAlpha = when (style) {
        LiquidGlassStyle.Pill -> if (isDark) 0.16f else 0.55f
        LiquidGlassStyle.Card -> if (isDark) 0.12f else 0.45f
        LiquidGlassStyle.Button -> if (isDark) 0.20f else 0.65f
        LiquidGlassStyle.UltraThin -> if (isDark) 0.08f else 0.30f
    }
    val crystalBottomAlpha = when (style) {
        LiquidGlassStyle.Pill -> if (isDark) 0.32f else 0.35f
        LiquidGlassStyle.Card -> if (isDark) 0.24f else 0.28f
        LiquidGlassStyle.Button -> if (isDark) 0.38f else 0.45f
        LiquidGlassStyle.UltraThin -> if (isDark) 0.16f else 0.18f
    }

    val topSurfaceColor = if (tint.isSpecified) {
        tint.copy(alpha = crystalTopAlpha)
    } else {
        if (isDark) Color.White.copy(alpha = crystalTopAlpha) else Color.White.copy(alpha = crystalTopAlpha)
    }

    val bottomSurfaceColor = if (tint.isSpecified) {
        tint.copy(alpha = crystalBottomAlpha)
    } else {
        if (isDark) Color(0xFF0D0D18).copy(alpha = crystalBottomAlpha) else Color.White.copy(alpha = crystalBottomAlpha)
    }

    // 3D 镜面棱镜边框高光（从左上强光到右下微反光）
    val borderTopLeft = if (isDark) {
        Color.White.copy(alpha = if (isPressed) 0.95f else 0.85f)
    } else {
        Color.White.copy(alpha = if (isPressed) 0.98f else 0.90f)
    }
    val borderMidAngle = if (isDark) Color(0xFFD2E3FC).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.60f)
    val borderBottomRight = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.25f)

    // 组合 Modifier
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true

            // Android 13+ (API 33+) 3D 透镜折射与物理光影着色器
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
                        val refHeight = 16.dp.toPx().coerceAtMost(size.minDimension * 0.35f)
                        s.setFloatUniform("refractionHeight", refHeight)
                        s.setFloatUniform("refractionAmount", -refHeight * 0.70f)
                        s.setFloatUniform("depthEffect", 0.30f)
                        s.setFloatUniform("chromaticAberration", 0.85f)
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
        // 外阴影增加空间悬浮厚度
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = if (tint.isSpecified) tint.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.50f),
            spotColor = if (tint.isSpecified) tint.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.60f)
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
        // 绘制多层次真实玻璃材质与物理光影
        .drawWithContent {
            // 1. 绘制清澈通透晶体基底
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to topSurfaceColor,
                    1.0f to bottomSurfaceColor
                )
            )

            // 2. 绘制 3D 镜面斜角聚光扫光束 (Specular Light Beam)
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = if (isDark) 0.35f else 0.45f),
                    0.25f to Color.White.copy(alpha = if (isDark) 0.10f else 0.18f),
                    0.65f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.70f, size.height)
                )
            )

            // 3. 绘制顶部折射聚光棱线 (Top Crest Caustic Highlight)
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = if (isDark) 0.40f else 0.50f),
                    0.12f to Color.White.copy(alpha = if (isDark) 0.08f else 0.15f),
                    0.30f to Color.Transparent,
                    startY = 0f,
                    endY = size.height * 0.4f
                )
            )

            // 4. 绘制底部内倒角物理厚度阴影 (Bottom Inner Depth Bevel)
            drawRect(
                brush = Brush.verticalGradient(
                    0.70f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = if (isDark) 0.38f else 0.15f),
                    startY = size.height * 0.5f,
                    endY = size.height
                )
            )

            // 5. 绘制内容 (图标、文字、封面)
            drawContent()
        }
        // 6. 3D 棱镜折射双重高光外边框 (Prismatic Light Rim)
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                0.0f to borderTopLeft,
                0.35f to borderMidAngle,
                0.70f to borderBottomRight.copy(alpha = borderBottomRight.alpha * 0.4f),
                1.0f to borderBottomRight,
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = shape
        )
}
