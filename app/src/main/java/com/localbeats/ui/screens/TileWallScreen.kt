package com.localbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.localbeats.data.model.MusicTrack
import com.localbeats.ui.components.PlayerBar
import com.localbeats.ui.components.placeholderPalettes
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
@Composable
fun TileWallScreen(
    tracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    onTrackClick: (MusicTrack) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onImportClick: () -> Unit = {},
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onRescan: () -> Unit = {},
    onOrientationToggleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    // 基础单元尺寸：每格 110dp
    val cellPx = with(density) { 110.dp.toPx() }

    // 竖屏封面歌曲标题显示开关（持久化到 SharedPreferences）
    val prefs = remember { context.getSharedPreferences("localbeats_prefs", android.content.Context.MODE_PRIVATE) }
    var showTitle by remember {
        mutableStateOf(prefs.getBoolean("show_tile_title", true))
    }
    var menuExpanded by remember { mutableStateOf(false) }

    var randomSeed by remember { mutableIntStateOf(0) }

    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }

    // 列数：视口实际测量宽度决定，+1 列保证略宽于视口（可横向平移一点），如果还没测量好，默认5列
    val columns = if (containerWidth > 0) max(3, (containerWidth / cellPx).toInt()) + 1 else 5

    // 按 track.id 取模决定 span，每个磁贴尺寸稳定（重排不变）
    val idSetKey = remember(tracks) { tracks.map { it.id }.toSet() }
    val tileSpansMap = remember(idSetKey) { tracks.associate { it.id to computeSpan(it) } }

    // 独立的 id→磁贴坐标（单元格 col,row）映射。
    val tilePositions = remember(idSetKey, columns, randomSeed) {
        val shuffledTracks = if (randomSeed == 0) tracks else tracks.shuffled(Random(randomSeed.toLong()))
        packTiles(shuffledTracks, tileSpansMap, columns)
    }

    // 内容总尺寸（由 tilePositions 派生，跟随交换实时更新）
    val contentWidth = columns * cellPx
    val contentHeight = (tilePositions.maxOfOrNull { (id, pos) ->
        pos.second + (tileSpansMap[id]?.second ?: 1)
    } ?: 0) * cellPx

    // 平移偏移（整个磁贴墙相对视口的位移）
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 顶部标题栏高度（动态测量）：磁贴墙内容基线 = topInsetPx
    var topInsetPx by remember { mutableFloatStateOf(0f) }
    var bottomInsetPx by remember { mutableFloatStateOf(0f) }
    var offsetInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(topInsetPx) {
        if (!offsetInitialized && topInsetPx > 0f) {
            offsetY = topInsetPx
            offsetInitialized = true
        }
    }

    // pointerInput(Unit) 的 lambda 只创建一次，用 rememberUpdatedState 始终读取最新值
    val currentTracks by rememberUpdatedState(tracks)
    val currentTileSpans by rememberUpdatedState(tileSpansMap)
    val currentOnTrackClick by rememberUpdatedState(onTrackClick)
    val currentOnReorder by rememberUpdatedState(onReorder)
    // contentHeight 也通过 rememberUpdatedState 封装，确保完数变化时拖动范围实时更新
    val currentContentHeight by rememberUpdatedState(contentHeight)

    // 所有曲目全部渲染，绝对稳定，无剔除逻辑，避免任何条件下的磁贴消失问题
    val visibleTracks = tracks

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .onGloballyPositioned { coords ->
                containerWidth = coords.size.width
                containerHeight = coords.size.height
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // 左右最多滑动 240 像素（防止磁贴墙水平漂移过多）
                        offsetX = (offsetX + dragAmount.x).coerceIn(-240f, 240f)
                        // Y 轴动态范围：
                        //   上滚上限 = 内容总高（随歌曲数量自动调整）
                        //   下拉缓冲 = 10000px（足够把顶部磁贴从标题栏后还拉入视野）
                        offsetY = (offsetY + dragAmount.y).coerceIn(-10000f, 10000f)
                    }
                )
            }
    ) {
        Layout(
            content = {
                visibleTracks.forEach { track ->
                    key(track.id) {
                        val span = tileSpansMap[track.id] ?: (1 to 1)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.92f else 1f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "tileScale"
                        )
                        Box(
                            modifier = Modifier
                                .layoutId(track.id)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(0.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = androidx.compose.foundation.LocalIndication.current
                                ) {
                                    currentOnTrackClick(track)
                                }
                        ) {
                            TileContent(
                                track = track,
                                spanWidth = span.first,
                                spanHeight = span.second,
                                isPlaying = isPlaying && track.id == currentTrack?.id,
                                showTitle = showTitle,
                                cellPx = cellPx
                            )
                        }
                    }
                }
            },
            measurePolicy = { measurables, _ ->
                // 按 layoutId(track.id) 索引 measurables，保证节点身份与数据对齐
                val byId = HashMap<Long, androidx.compose.ui.layout.Measurable>()
                measurables.forEach { m ->
                    (m.layoutId as? Long)?.let { byId[it] = m }
                }
                val placeables = visibleTracks.mapNotNull { track ->
                    val m = byId[track.id] ?: return@mapNotNull null
                    val span = tileSpansMap[track.id] ?: (1 to 1)
                    val w = (span.first * cellPx).toInt()
                    val h = (span.second * cellPx).toInt()
                    track to m.measure(Constraints.fixed(w, h))
                }
                val totalWidth = (columns * cellPx).toInt()
                val totalHeight = ((tilePositions.maxOfOrNull { (id, pos) ->
                    pos.second + (tileSpansMap[id]?.second ?: 1)
                } ?: 0) * cellPx).toInt()
                layout(totalWidth, totalHeight) {
                    placeables.forEach { (track, placeable) ->
                        val pos = tilePositions[track.id] ?: (0 to 0)
                        placeable.placeRelative(
                            (pos.first * cellPx).toInt(),
                            (pos.second * cellPx).toInt()
                        )
                    }
                }
            },
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                }
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
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导入文件夹") },
                                onClick = {
                                    menuExpanded = false
                                    onImportClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("重新扫描") },
                                onClick = {
                                    menuExpanded = false
                                    onRescan()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("随机排列") },
                                onClick = {
                                    menuExpanded = false
                                    randomSeed++
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("显示歌曲标题") },
                                onClick = {
                                    showTitle = !showTitle
                                    prefs.edit().putBoolean("show_tile_title", showTitle).apply()
                                },
                                trailingIcon = {
                                    Switch(
                                        checked = showTitle,
                                        onCheckedChange = {
                                            showTitle = it
                                            prefs.edit().putBoolean("show_tile_title", it).apply()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
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
            currentPosition = currentPosition,
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
            Text(
                text = track.title,
                color = Color.White,
                maxLines = if (spanHeight >= 2) 3 else 2,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart)
            )
        }
    }
}
