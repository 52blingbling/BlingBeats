package com.localbeats.ui.glass

import android.graphics.Shader
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
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
 * 缓存 AGSL 3D 光学液态玻璃着色器（在 Android 13+ / API 33+ 生效）
 * 模拟 Apple iOS / visionOS 经典物理曲率透镜模型：
 * - 3D 表面法线反推 (Surface Normal)
 * - 菲涅尔边缘物理反射 (Fresnel Refraction Effect: 边缘晶莹折射、中心纯净通透)
 * - Blinn-Phong 左上方真实镜面高光 (3D Specular Gleam)
 * - 内部倒角焦散光环 (Caustic Rim)
 */
private object LiquidGlassShaderCache {
    private const val AGSL_GLASS_SHADER = """
        uniform float2 size;
        uniform float4 cornerRadii;
        uniform float4 baseColor;
        uniform float isDark;

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

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = coord - halfSize;
            float radius = radiusAt(centeredCoord, cornerRadii);
            
            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            if (sd > 0.0) {
                return half4(0.0);
            }
            
            float dist = -sd;
            float bevelWidth = min(radius * 0.85, 18.0);
            bevelWidth = max(bevelWidth, 4.0);
            
            float3 N = float3(0.0, 0.0, 1.0);
            float bevelT = 1.0;
            
            if (dist < bevelWidth) {
                bevelT = dist / bevelWidth;
                float2 grad = gradSdRoundedRect(centeredCoord, halfSize, radius);
                float slope = (1.0 - bevelT);
                N = normalize(float3(grad * slope * 1.5, sqrt(max(1.0 - slope * slope, 0.05))));
            }
            
            // 真实物理 3D 光源 (左上方 -135 度，入射角 ~45 度)
            float3 L = normalize(float3(-0.6, -0.6, 0.7));
            float3 V = float3(0.0, 0.0, 1.0);
            float3 H = normalize(L + V);
            
            // 菲涅尔边缘反射 (Fresnel: 越靠近边缘，玻璃越晶莹剔透并呈现透镜高光)
            float NdotV = clamp(dot(N, V), 0.0, 1.0);
            float fresnel = pow(1.0 - NdotV, 3.0);
            
            // 镜面高光 (Specular Highlight)
            float NdotH = max(dot(N, H), 0.0);
            float spec = pow(NdotH, 24.0) * (isDark > 0.5 ? 0.45 : 0.65);
            
            // 透镜内倒角折射聚集带 (Internal Refraction Caustic Rim)
            float caustic = exp(-pow((bevelT - 0.75) * 5.0, 2.0)) * (isDark > 0.5 ? 0.12 : 0.20);
            
            // 基础半透明底色
            half4 col = half4(baseColor);
            
            // 叠加物理光感 (光影只为亮部增辉，背光侧自然为 0，绝无死白)
            float lightBoost = spec + fresnel * (isDark > 0.5 ? 0.25 : 0.40) + caustic;
            col.rgb += half3(lightBoost);
            col.a = min(col.a + fresnel * 0.15, 0.95);
            
            return col;
        }
    """

    val shader: android.graphics.RuntimeShader? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(AGSL_GLASS_SHADER)
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }
}

/**
 * 基于 AGSL RuntimeShader 实现的 Compose ShaderBrush
 * 仅用于绘制玻璃背景层，绝不干扰、模糊或扭曲文字与图标！
 */
private class LiquidGlassShaderBrush(
    private val shader: android.graphics.RuntimeShader,
    private val cornerRadiusPx: Float,
    private val baseColor: Color,
    private val isDark: Boolean
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        shader.setFloatUniform("size", size.width, size.height)
        val r = cornerRadiusPx.coerceAtMost(size.minDimension * 0.5f)
        shader.setFloatUniform("cornerRadii", r, r, r, r)
        shader.setFloatUniform(
            "baseColor",
            baseColor.red,
            baseColor.green,
            baseColor.blue,
            baseColor.alpha
        )
        shader.setFloatUniform("isDark", if (isDark) 1.0f else 0.0f)
        return shader
    }
}

/**
 * 核心液态玻璃 Modifier：
 * 遵循 Apple iOS 原生液态磨砂玻璃（Frosted / Liquid Glass）与 Kyant0 物理标准：
 * 1. 采用 ShaderBrush 仅对玻璃背景层执行 3D 物理光学折射与高光计算，彻底保障文字、图标 100% 高清锐利，绝不模糊！
 * 2. 真实 3D 表面法线 + 菲涅尔定律 (Fresnel) + 镜面光影，边缘晶莹剔透，中心纯净通透，绝无内外分层。
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
    val density = LocalDensity.current

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

    // iOS 标准均一磨砂玻璃基底（纯净、通透、高质感）
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

    val cornerRadiusPx = remember(shape, density) {
        with(density) {
            when (shape) {
                CircleShape -> 9999f
                is RoundedCornerShape -> 24.dp.toPx()
                else -> 16.dp.toPx()
            }
        }
    }

    // 玻璃着色 Brush：在 Android 13+ 采用物理 3D 光学 AGSL 笔刷；在低版本采用高级微渐变晶体笔刷
    val glassBrush = remember(cornerRadiusPx, surfaceColor, isDark) {
        val shader = LiquidGlassShaderCache.shader
        if (shader != null) {
            LiquidGlassShaderBrush(shader, cornerRadiusPx, surfaceColor, isDark)
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    surfaceColor,
                    surfaceColor.copy(alpha = (surfaceColor.alpha * 0.9f).coerceAtLeast(0.05f))
                )
            )
        }
    }

    // iOS 经典发丝级单像素高光描边 (从左上至右下自然渐隐)
    val borderBrush = Brush.linearGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.42f else 0.70f),
        0.45f to Color.White.copy(alpha = if (isDark) 0.16f else 0.32f),
        1.0f to Color.White.copy(alpha = if (isDark) 0.03f else 0.08f),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    // 组合 Modifier：不再在外层滥用 RenderEffect，确保内部文字与图标 100% 清晰！
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true
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
        // 绘制物理液态玻璃层与前景内容
        .drawWithContent {
            // 1. 绘制物理 3D 光学折射与透镜光感背景（仅作用于背景底板）
            drawRect(brush = glassBrush)

            // 2. 绘制前景内容 (文字、图标、封面) —— 100% 原生清晰锐利，无任何模糊！
            drawContent()
        }
        // 3. iOS 经典发丝级微光高光描边 (Hairline Specular Rim)
        .border(
            width = borderWidth,
            brush = borderBrush,
            shape = shape
        )
}
