package com.localbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import kotlin.OptIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import java.util.Random
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localbeats.ui.components.PlayerBar
import com.localbeats.ui.components.placeholderPalettes
import com.localbeats.data.model.MusicTrack
import kotlin.math.max
import kotlin.math.roundToInt


/**
 * Windows 8/10 开始菜单风格的方形磁贴墙：
 * - 磁贴大小不一（1x1、2x1、1x2、2x2）无缝拼接
 * - 整个磁贴墙可上下左右自由拖动平移（含边界外弹性区）
 * - 点击磁贴播放对应歌曲
 * - 长按磁贴进入拖动模式，抬起时与目标磁贴交换位置（swap，不 reflow）
 *
 * 关键设计：
 * - 布局与数据的映射统一使用 track.id（而非列表索引），避免重排/重组后封面与数据错位。
 * - 子项使用 key(track.id) + layoutId(track.id)，保证 Compose 节点身份稳定。
 * - 维护独立的 id→坐标 映射 tilePositions，交换时只交换对应项，得到稳定的拖拽体验。
 * - 手势使用 Compose 提供的 detectTapGestures / detectDragGestures / detectDragGesturesAfterLongPress。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileWallScreen(
    tracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    onTrackClick: (MusicTrack) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    currentPositionProvider: () -> Long = { 0L },
    duration: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onImportClick: () -> Unit = {},
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onRescan: () -> Unit = {},
    onOrientationToggleClick: (() -> Unit)? = null,
    currentThemeMode: Int = 1,
    onThemeModeChange: (Int) -> Unit = {},
    onShuffleModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    // 基础单元尺寸：每格 110dp
    val cellPx = with(density) { 110.dp.toPx() }

    // 竖屏封面歌曲标题显示开关（持久化到 SharedPreferences）
    val prefs = remember { context.getSharedPreferences("localbeats_prefs", android.content.Context.MODE_PRIVATE) }
    var showTitle by remember {
        mutableStateOf(prefs.getBoolean("show_tile_title", false))
    }
    var shuffleEnabled by remember { mutableStateOf(prefs.getBoolean("shuffle_mode", true)) }
    var menuExpanded by remember { mutableStateOf(false) }

    var randomSeed by remember {
        mutableIntStateOf(prefs.getInt("random_seed", 0))
    }

    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }

    // 列数：视口实际测量宽度决定，+1 列保证略宽于视口（可横向平移一点），如果还没测量好，默认5列
    val columns = if (containerWidth > 0) max(3, (containerWidth / cellPx).toInt()) + 1 else 5

    // 复制曲目列表直到数量不少于 30，确保磁贴墙高度大于屏幕高度，使得 2x2 平铺无缝连接
    val displayTracks = remember(tracks) {
        if (tracks.isEmpty()) emptyList()
        else {
            val result = ArrayList<MusicTrack>()
            var virtualId = 1000000L
            while (result.size < 30) {
                tracks.forEach { track ->
                    result.add(track.copy(id = virtualId++))
                }
            }
            result
        }
    }

    // 按 track.id 取模决定 span，每个磁贴尺寸稳定（重排不变）
    val displayIdSetKey = remember(displayTracks) { displayTracks.map { it.id }.toSet() }
    val tileSpansMap = remember(displayIdSetKey) { displayTracks.associate { it.id to computeSpan(it) } }

    // 独立的 id→磁贴坐标（单元格 col,row）映射。
    val tilePositions = remember(displayIdSetKey, columns, randomSeed) {
        val shuffledTracks = if (randomSeed == 0) displayTracks else displayTracks.shuffled(Random(randomSeed.toLong()))
        packTiles(shuffledTracks, tileSpansMap, columns)
    }

    val gridW = columns * cellPx
    val gridH = remember(tilePositions, tileSpansMap) {
        val maxRow = tilePositions.maxOfOrNull { (id, pos) ->
            pos.second + (tileSpansMap[id]?.second ?: 1)
        } ?: 0
        maxRow * cellPx
    }

    // 平移偏移（整个磁贴墙相对视口的位移）
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var bottomInsetPx by remember { mutableFloatStateOf(0f) }

    // 顶部标题栏高度（动态测量）：磁贴墙内容基线 = topInsetPx
    var topInsetPx by remember { mutableFloatStateOf(0f) }
    var offsetInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(topInsetPx, gridH) {
        if (!offsetInitialized && topInsetPx > 0f && gridH > 0f) {
            offsetY = topInsetPx - gridH
            offsetInitialized = true
        }
    }

    val currentOnTrackClick by rememberUpdatedState(onTrackClick)

    val visibleTracks = displayTracks

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .onGloballyPositioned { coords ->
                containerWidth = coords.size.width
                containerHeight = coords.size.height
            }
            .pointerInput(gridW, gridH) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = offsetX + dragAmount.x
                        val newY = offsetY + dragAmount.y
                        
                        // 将 X 轴偏移收敛于 (-gridW, 0] 区间
                        offsetX = if (gridW > 0) {
                            val remainder = newX % gridW
                            if (remainder > 0) remainder - gridW else remainder
                        } else 0f
                        
                        // 将 Y 轴偏移收敛于 (-gridH, 0] 区间
                        offsetY = if (gridH > 0) {
                            val remainder = newY % gridH
                            if (remainder > 0) remainder - gridH else remainder
                        } else 0f
                    }
                )
            }
    ) {
        androidx.compose.ui.layout.Layout(
            content = {
                // 渲染 4 份拷贝 (2x2) 以在无限滑动时无缝衔接
                for (copyIndex in 0..3) {
                    visibleTracks.forEach { track ->
                        key("${track.id}_$copyIndex") {
                            val span = tileSpansMap[track.id] ?: (1 to 1)
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val localHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                            val scaleAnimatable = remember { androidx.compose.animation.core.Animatable(1f) }
                            LaunchedEffect(isPressed) {
                                if (isPressed) {
                                    scaleAnimatable.animateTo(0.90f, spring(stiffness = 1000f))
                                } else {
                                    if (scaleAnimatable.value > 0.93f) {
                                        scaleAnimatable.animateTo(0.90f, spring(stiffness = 1000f))
                                    }
                                    scaleAnimatable.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .layoutId("${track.id}_$copyIndex")
                                    .graphicsLayer {
                                        scaleX = scaleAnimatable.value
                                        scaleY = scaleAnimatable.value
                                        
                                        // 方案 1：将滑动位移放到 GPU 绘制层，避免测量重排
                                        translationX = offsetX
                                        translationY = offsetY
                                        
                                        // 方案 2：视口边界裁剪剔除 (Culling)
                                        val dx = when (copyIndex) {
                                            1, 3 -> gridW
                                            else -> 0f
                                        }
                                        val dy = when (copyIndex) {
                                            2, 3 -> gridH
                                            else -> 0f
                                        }
                                        val pos = tilePositions[track.id] ?: (0 to 0)
                                        val tileX = pos.first * cellPx + dx
                                        val tileY = pos.second * cellPx + dy
                                        
                                        val drawX = tileX + offsetX
                                        val drawY = tileY + offsetY
                                        
                                        val span = tileSpansMap[track.id] ?: (1 to 1)
                                        val tileW = span.first * cellPx
                                        val tileH = span.second * cellPx
                                        
                                        val isVisible = !(drawX + tileW < 0 || drawX > containerWidth || drawY + tileH < 0 || drawY > containerHeight)
                                        alpha = if (isVisible) 1f else 0f
                                    }
                                    .clip(RoundedCornerShape(0.dp))
                                    .combinedClickable(
                                        interactionSource = interactionSource,
                                        indication = androidx.compose.foundation.LocalIndication.current,
                                        onClick = {
                                            // 点击时找到原始列表中的歌曲
                                            val original = tracks.find { it.filePath == track.filePath } ?: track
                                            currentOnTrackClick(original)
                                        },
                                        onLongClick = {
                                            localHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                    )
                            ) {
                                TileContent(
                                    track = track,
                                    spanWidth = span.first,
                                    spanHeight = span.second,
                                    isPlaying = isPlaying && track.filePath == currentTrack?.filePath,
                                    showTitle = showTitle,
                                    cellPx = cellPx
                                )
                            }
                        }
                    }
                }
            },
            measurePolicy = { measurables, constraints ->
                val byId = HashMap<String, androidx.compose.ui.layout.Measurable>()
                measurables.forEach { m ->
                    (m.layoutId as? String)?.let { byId[it] = m }
                }

                // 在 measurePass 中依次对所有 4 份拷贝进行测量
                val placeables = (0..3).flatMap { copyIndex ->
                    visibleTracks.mapNotNull { track ->
                        val uniqueId = "${track.id}_$copyIndex"
                        val m = byId[uniqueId] ?: return@mapNotNull null
                        val span = tileSpansMap[track.id] ?: (1 to 1)
                        val w = (span.first * cellPx).toInt()
                        val h = (span.second * cellPx).toInt()
                        val placeable = m.measure(Constraints.fixed(w, h))
                        Triple(track, copyIndex, placeable)
                    }
                }

                val screenW = constraints.maxWidth
                val screenH = constraints.maxHeight

                layout(screenW, screenH) {
                    placeables.forEach { (track, copyIndex, placeable) ->
                        // 计算 2x2 平铺对应的像素增量
                        val dx = when (copyIndex) {
                            1, 3 -> gridW.toInt()
                            else -> 0
                        }
                        val dy = when (copyIndex) {
                            2, 3 -> gridH.toInt()
                            else -> 0
                        }

                        val pos = tilePositions[track.id] ?: (0 to 0)
                        val drawX = (pos.first * cellPx).toInt() + dx
                        val drawY = (pos.second * cellPx).toInt() + dy
                        placeable.placeRelative(drawX, drawY)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 顶部浮层标题栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.material3.MaterialTheme.colorScheme.background,
                            androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .onGloballyPositioned { coords ->
                    topInsetPx = coords.size.height.toFloat()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "BlingBeats",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${tracks.size} 首",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "设置",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                            )
                        }

                    }
                }
            }
        }

        // Invisible overlay to dismiss menu when clicking outside
        if (menuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { menuExpanded = false }
                    )
            )
        }

        // Liquid Glass Menu Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = menuExpanded,
            enter = androidx.compose.animation.scaleIn(
                initialScale = 0.8f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f)
            ) + fadeIn(tween(200)),
            exit = androidx.compose.animation.scaleOut(
                targetScale = 0.8f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                animationSpec = tween(150)
            ) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 56.dp, end = 16.dp)
        ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val actualDark = currentThemeMode == 2 || (currentThemeMode == 0 && isDark)
            
            val glassBg = if (actualDark) androidx.compose.ui.graphics.Color(0xD91E1E1E) else androidx.compose.ui.graphics.Color(0xE6FFFFFF)
            val glassBorder1 = androidx.compose.ui.graphics.Color.White.copy(alpha = if (actualDark) 0.15f else 0.8f)
            val glassBorder2 = androidx.compose.ui.graphics.Color.White.copy(alpha = if (actualDark) 0.02f else 0.2f)

            Column(
                modifier = Modifier
                    .width(200.dp)
                    .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(glassBg, glassBg.copy(alpha = glassBg.alpha * 0.9f))
                        )
                    )
                    .border(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(glassBorder1, glassBorder2)
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(8.dp)
            ) {
                GlassMenuItem(
                    text = "导入文件夹",
                    icon = Icons.Filled.FolderOpen,
                    onClick = { menuExpanded = false; onImportClick() }
                )
                GlassMenuItem(
                    text = "重新扫描",
                    icon = Icons.Filled.Refresh,
                    onClick = { menuExpanded = false; onRescan() }
                )
                GlassMenuItem(
                    text = "随机排列",
                    icon = Icons.Filled.Casino,
                    onClick = {
                        menuExpanded = false
                        val nextSeed = java.util.Random().nextInt(100000) + 1
                        randomSeed = nextSeed
                        prefs.edit().putInt("random_seed", nextSeed).apply()
                    }
                )
                GlassMenuItem(
                    text = "随机播放",
                    icon = Icons.Filled.Shuffle,
                    onClick = {
                        shuffleEnabled = !shuffleEnabled
                        prefs.edit().putBoolean("shuffle_mode", shuffleEnabled).apply()
                        onShuffleModeChange(shuffleEnabled)
                    },
                    trailingContent = {
                        Switch(
                            checked = shuffleEnabled,
                            onCheckedChange = null,
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                )
                GlassMenuItem(
                    text = "显示标题",
                    icon = Icons.Filled.MusicNote,
                    onClick = {
                        showTitle = !showTitle
                        prefs.edit().putBoolean("show_tile_title", showTitle).apply()
                    },
                    trailingContent = {
                        Switch(
                            checked = showTitle,
                            onCheckedChange = null,
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                )
                val modeName = when (currentThemeMode) {
                    0 -> "跟随系统"
                    1 -> "浅色模式"
                    else -> "深色模式"
                }
                val modeIcon = when (currentThemeMode) {
                    0 -> Icons.Filled.BrightnessAuto
                    1 -> Icons.Filled.LightMode
                    else -> Icons.Filled.DarkMode
                }
                GlassMenuItem(
                    text = modeName,
                    icon = modeIcon,
                    onClick = {
                        onThemeModeChange((currentThemeMode + 1) % 3)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                GlassMenuItem(
                    text = "作者：小彬Papago",
                    icon = Icons.Filled.Info,
                    onClick = {
                        val easterEggs = listOf(
                            "🎉 你戳了戳作者【小彬Papago】！",
                            "🦜 Papago 说：BlingBeats 的每一个像素都是用心打磨的哦！",
                            "🚀 叮！解锁隐藏成就：【忠实听众】",
                            "🎵 听说多听本地音乐可以增加你的日常欧气！",
                            "💡 小彬正在闭关开发更多酷炫功能，敬请期待！",
                            "🌟 给小彬投喂一块曲奇，BlingBeats 播放速度+0.01%！",
                            "✨ 你发现了本App里最帅的彩蛋——就是屏幕前的你！"
                        )
                        val eggMessage = easterEggs[java.util.Random().nextInt(easterEggs.size)]
                        android.widget.Toast.makeText(context, eggMessage, android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        PlayerBar(
            title = currentTrack?.title ?: "未选择歌曲",
            artist = currentTrack?.artist,
            coverUri = currentTrack?.coverUri,
            lyrics = currentTrack?.lyrics,
            isPlaying = isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            onSeek = onSeek,
            onOrientationToggleClick = onOrientationToggleClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coords ->
                    bottomInsetPx = coords.size.height.toFloat()
                }
        )
    }
}

/** 计算磁贴的 span（横向、纵向格子数），按 track.id 取模保证尺寸稳定 */
private fun computeSpan(track: MusicTrack): Pair<Int, Int> {
    val k = ((track.id % 7) + 7) % 7
    return when (k.toInt()) {
        0, 3 -> 2 to 2   // 大正方形磁贴
        else -> 1 to 1   // 小正方形磁贴
    }
}

/** 打包磁贴位置：按 tracks 顺序贪心放置，返回 id → col,row 单元格坐标的映射 */
private fun packTiles(
    tracks: List<MusicTrack>,
    spans: Map<Long, Pair<Int, Int>>,
    columns: Int
): Map<Long, Pair<Int, Int>> {
    val out = HashMap<Long, Pair<Int, Int>>()
    val maxRows = (tracks.size * 2 + 4).coerceAtLeast(16)
    val occupied = Array(columns) { BooleanArray(maxRows) }
    for (track in tracks) {
        val span = spans[track.id] ?: (1 to 1)
        val w = span.first
        val h = span.second
        var placed = false
        var row = 0
        while (!placed && row < maxRows) {
            var col = 0
            while (col + w <= columns) {
                if (canPlace(occupied, col, row, w, h, columns, maxRows)) {
                    out[track.id] = col to row
                    mark(occupied, col, row, w, h)
                    placed = true
                    break
                }
                col++
            }
            if (!placed) row++
        }
        if (!placed) out[track.id] = 0 to 0
    }
    return out
}

private fun canPlace(occupied: Array<BooleanArray>, col: Int, row: Int, w: Int, h: Int, columns: Int, maxRows: Int): Boolean {
    if (col + w > columns) return false
    if (row + h > maxRows) return false
    for (y in row until row + h) {
        if (y >= occupied[0].size) return false
        for (x in col until col + w) {
            if (occupied[x][y]) return false
        }
    }
    return true
}

private fun mark(occupied: Array<BooleanArray>, col: Int, row: Int, w: Int, h: Int) {
    for (y in row until row + h) {
        for (x in col until col + w) {
            occupied[x][y] = true
        }
    }
}

/** 单个磁贴的内容：封面图 + 底部信息 + 播放指示 + 按压缩放动画 */
@Composable
private fun TileContent(
    track: MusicTrack,
    spanWidth: Int,
    spanHeight: Int,
    isPlaying: Boolean,
    showTitle: Boolean,
    cellPx: Float
) {
    val palette = placeholderPalettes[(track.id % placeholderPalettes.size).toInt().coerceAtLeast(0)]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (track.coverUri != null) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier.background(Brush.linearGradient(colors = palette))
                }
            )
            .then(
                if (isPlaying) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFBB86FC).copy(alpha = 0.35f),
                                Color(0xFF03DAC6).copy(alpha = 0.25f)
                            )
                        )
                    )
                } else Modifier
            )
    ) {
        if (track.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(if (spanWidth >= 2 && spanHeight >= 2) 40.dp else 28.dp)
                )
            }
        }

        if (showTitle) {
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
        }

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

        if (showTitle) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart)
            ) {
                Text(
                    text = track.title,
                    color = Color.White,
                    maxLines = if (spanHeight >= 2) 2 else 1,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis
                )
                if (!track.artist.isNullOrBlank() && track.artist != "<unknown>") {
                    Text(
                        text = track.artist,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun GlassMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        androidx.compose.material3.Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}
