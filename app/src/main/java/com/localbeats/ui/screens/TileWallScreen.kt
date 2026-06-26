package com.localbeats.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Windows 8/10 开始菜单风格的方形磁贴墙：
 * - 磁贴大小不一（1x1、2x1、1x2、2x2）无缝拼接
 * - 整个磁贴墙可上下左右自由拖动平移（含边界外弹性区）
 * - 点击磁贴播放对应歌曲（带按压反馈动画）
 * - 长按磁贴进入拖动模式，跟随手指实时交换位置（带 swap 动画）
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    // 基础单元尺寸：每格 110dp
    val cellPx = with(density) { 110.dp.toPx() }
    // 触发拖动的位移阈值；长按延时（短于系统默认 500ms，更跟手）
    val touchSlop = with(density) { 18.dp.toPx() }
    val longPressMs = 400L

    // 竖屏封面歌曲标题显示开关（持久化到 SharedPreferences）
    val prefs = remember { context.getSharedPreferences("localbeats_prefs", android.content.Context.MODE_PRIVATE) }
    var showTitle by remember {
        mutableStateOf(prefs.getBoolean("show_tile_title", true))
    }
    // 设置菜单展开状态
    var menuExpanded by remember { mutableStateOf(false) }

    val tileSpans = remember(tracks) { tracks.map { computeSpan(it) } }
    // rememberUpdatedState 让 pointerInput(Unit) 内部始终读取最新的 tracks/spans/回调，
    // 避免重排或重扫后 pointerInput 仍捕获旧引用导致点击错歌、拖动命中失效
    val currentTracks by rememberUpdatedState(tracks)
    val currentTileSpans by rememberUpdatedState(tileSpans)
    val currentOnTrackClick by rememberUpdatedState(onTrackClick)
    val currentOnReorder by rememberUpdatedState(onReorder)

    // 平移偏移（整个磁贴墙相对视口的位移）
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 视口与内容尺寸
    var viewportWidth by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    var contentWidth by remember { mutableFloatStateOf(0f) }
    var contentHeight by remember { mutableFloatStateOf(0f) }

    // 每个磁贴在磁贴墙坐标系中的左上角坐标（measure 后填充）
    val placements = remember { mutableStateListOf<Pair<Float, Float>>() }

    // 顶部标题栏高度（动态测量）：磁贴墙内容基线 = topInsetPx，保证顶部磁贴不被标题栏遮挡
    var topInsetPx by remember { mutableFloatStateOf(0f) }
    // offsetY 是否已按 topInsetPx 初始化（仅首次测量后设置一次）
    var offsetInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(topInsetPx) {
        if (!offsetInitialized && topInsetPx > 0f) {
            offsetY = topInsetPx
            offsetInitialized = true
        }
    }

    // 长按拖动相关状态
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // 拖动过程中手指悬停的目标磁贴索引（用于高亮，抬起时才真正 reorder，避免拖动中 reflow）
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0D0D0D))
            .onGloballyPositioned { coords ->
                viewportWidth = coords.size.width.toFloat()
                viewportHeight = coords.size.height.toFloat()
            }
            // 统一手势识别（单 pointerInput，避免与 clickable 冲突）：
            //   轻点（按下后短时间抬起、未移动）→ 播放对应磁贴
            //   移动超过 touchSlop → 平移磁贴墙
            //   静止超过 longPressMs → 进入磁贴拖动模式，抬起时才 reorder（避免拖动中 reflow 乱跳）
            // 用 withTimeoutOrNull 让手指静止时也能可靠触发长按（awaitPointerEvent 静止时不返回）
            // 通过 rememberUpdatedState 的 current* 引用，始终读取最新的 tracks/spans/回调
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // 等待首个按下事件
                        var down: PointerInputChange? = null
                        while (down == null) {
                            val initEvent = awaitPointerEvent()
                            down = initEvent.changes.firstOrNull { it.pressed }
                        }
                        val downChange = down
                        val downPosition = downChange.position
                        val downTime = System.currentTimeMillis()
                        // 模式：0=未决, 1=平移, 2=磁贴拖动
                        var mode = 0
                        var moved = 0f

                        // 第一阶段：判定长按 / 平移 / 轻点抬起
                        while (mode == 0) {
                            val elapsed = System.currentTimeMillis() - downTime
                            val remaining = longPressMs - elapsed
                            if (remaining <= 0) {
                                // 长按超时：进入磁贴拖动模式
                                draggedIndex = findTileAt(downPosition, offsetX, offsetY, placements, currentTileSpans, cellPx)
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                dragTargetIndex = null
                                mode = 2
                                break
                            }
                            val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
                            if (event == null) {
                                // 超时：进入磁贴拖动模式
                                draggedIndex = findTileAt(downPosition, offsetX, offsetY, placements, currentTileSpans, cellPx)
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                dragTargetIndex = null
                                mode = 2
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == downChange.id } ?: break
                            if (!change.pressed) {
                                // 抬起：若几乎未移动则视为轻点 → 播放
                                if (moved < touchSlop) {
                                    val idx = findTileAt(downPosition, offsetX, offsetY, placements, currentTileSpans, cellPx)
                                    if (idx != null) {
                                        currentTracks.getOrNull(idx)?.let { currentOnTrackClick(it) }
                                    }
                                }
                                break
                            }
                            val dx = change.position.x - downPosition.x
                            val dy = change.position.y - downPosition.y
                            moved = sqrt(dx * dx + dy * dy)
                            if (moved >= touchSlop) {
                                mode = 1 // 超过位移阈值：进入平移模式
                            }
                        }

                        // 第二阶段：按模式处理后续拖动，直到抬起
                        if (mode == 1 || mode == 2) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == downChange.id } ?: break
                                if (!change.pressed) break
                                val dragX = change.positionChange().x
                                val dragY = change.positionChange().y

                                if (mode == 1) {
                                    // 平移磁贴墙
                                    change.consume()
                                    val maxX = max(0f, contentWidth - viewportWidth)
                                    // 内容基线：顶部磁贴完整显示在标题栏下方
                                    val restY = topInsetPx
                                    val usableHeight = (viewportHeight - topInsetPx).coerceAtLeast(0f)
                                    // 限制最大向上拖动距离：内容总高减去可用高度，避免过度滚动
                                    val maxScrollUp = max(0f, contentHeight - usableHeight)
                                    val newX = (offsetX + dragX).coerceIn(
                                        -maxX - viewportWidth * 0.25f,
                                        viewportWidth * 0.25f
                                    )
                                    // 向下拖不超过基线（顶部封面始终完整可见），向上拖限制在 maxScrollUp
                                    val newY = (offsetY + dragY).coerceIn(
                                        restY - maxScrollUp - viewportHeight * 0.15f,
                                        restY + viewportHeight * 0.15f
                                    )
                                    offsetX = newX
                                    offsetY = newY
                                } else {
                                    // 磁贴拖动：跟随手指移动，检测目标高亮，但不 reorder（抬起时才换位）
                                    change.consume()
                                    dragOffsetX += dragX
                                    dragOffsetY += dragY

                                    val draggedIdx = draggedIndex ?: continue
                                    val placement = placements.getOrNull(draggedIdx) ?: continue
                                    val span = currentTileSpans.getOrNull(draggedIdx) ?: continue
                                    val draggedCenterX = placement.first + span.first * cellPx / 2 + dragOffsetX
                                    val draggedCenterY = placement.second + span.second * cellPx / 2 + dragOffsetY

                                    var hit: Int? = null
                                    for (i in placements.indices) {
                                        if (i == draggedIdx) continue
                                        val (px, py) = placements[i]
                                        val (sw, sh) = currentTileSpans[i]
                                        if (draggedCenterX >= px && draggedCenterX < px + sw * cellPx &&
                                            draggedCenterY >= py && draggedCenterY < py + sh * cellPx
                                        ) {
                                            hit = i
                                            break
                                        }
                                    }
                                    dragTargetIndex = hit
                                }
                            }
                        }

                        // 抬起：若处于磁贴拖动模式且检测到目标，执行一次性 reorder
                        val fromSafe = draggedIndex
                        val toSafe = dragTargetIndex
                        if (fromSafe != null && toSafe != null && toSafe != fromSafe &&
                            fromSafe in currentTracks.indices && toSafe in currentTracks.indices
                        ) {
                            currentOnReorder(fromSafe, toSafe)
                        }
                        if (draggedIndex != null) {
                            draggedIndex = null
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            dragTargetIndex = null
                        }
                    }
                }
            }
    ) {
        Layout(
            content = {
                tracks.forEachIndexed { index, track ->
                    val span = tileSpans[index]
                    val isDragged = draggedIndex == index
                    val isDropTarget = dragTargetIndex == index
                    Box(
                        modifier = Modifier
                            .layoutId(index)
                            .zIndex(if (isDragged) 100f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationX = dragOffsetX
                                    translationY = dragOffsetY
                                    scaleX = 1.15f
                                    scaleY = 1.15f
                                    shadowElevation = 24f
                                    alpha = 0.95f
                                }
                            }
                            .then(
                                if (isDropTarget) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = Color(0xFFBB86FC),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                } else Modifier
                            )
                            .clip(RoundedCornerShape(0.dp))
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
            },
            measurePolicy = { measurables, constraints ->
                val viewportCells = if (viewportWidth > 0f) {
                    max(3, (viewportWidth / cellPx).toInt())
                } else 4
                val columns = viewportCells + 1
                val maxRows = (tracks.size * 2 + 4).coerceAtLeast(16)
                val occupied = Array(columns) { BooleanArray(maxRows) }
                val newPlacements = ArrayList<Pair<Int, Int>>()
                var maxRowUsed = 0

                measurables.forEachIndexed { index, measurable ->
                    val span = tileSpans[index]
                    val w = span.first
                    val h = span.second
                    var placed = false
                    var row = 0
                    while (!placed && row < maxRows) {
                        var col = 0
                        while (col + w <= columns) {
                            if (canPlace(occupied, col, row, w, h, columns, maxRows)) {
                                newPlacements.add((col * cellPx).toInt() to (row * cellPx).toInt())
                                mark(occupied, col, row, w, h)
                                maxRowUsed = max(maxRowUsed, row + h)
                                placed = true
                                break
                            }
                            col++
                        }
                        if (!placed) row++
                    }
                    if (!placed) newPlacements.add(0 to 0)
                }

                val totalWidth = columns * cellPx
                val totalHeight = maxRowUsed * cellPx
                contentWidth = totalWidth
                contentHeight = totalHeight

                // 同步 placements 给手势识别使用
                placements.clear()
                placements.addAll(newPlacements.map { it.first.toFloat() to it.second.toFloat() })

                val placeables = measurables.mapIndexed { index, measurable ->
                    val span = tileSpans[index]
                    val w = (span.first * cellPx).toInt()
                    val h = (span.second * cellPx).toInt()
                    measurable.measure(Constraints.fixed(w, h))
                }

                layout(totalWidth.toInt(), totalHeight.toInt()) {
                    placeables.forEachIndexed { index, placeable ->
                        val (x, y) = newPlacements[index]
                        placeable.placeRelative(x, y)
                    }
                }
            },
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        )

        // 顶部浮层标题栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D0D0D),
                            Color(0xFF0D0D0D).copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .onGloballyPositioned { coords ->
                    // 测量标题栏实际高度（含状态栏 padding），供磁贴墙顶部留白使用
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
                    text = "LocalBeats",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${tracks.size} 首",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                    // 设置按钮：点击弹出菜单（导入文件夹、重新扫描、显示标题开关）
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "设置",
                                tint = Color(0xFFBB86FC)
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
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 计算磁贴的 span（横向、纵向格子数），制造大小不一的视觉节奏 */
private fun computeSpan(track: MusicTrack): Pair<Int, Int> {
    // 按 track.id 取模决定尺寸，使每个磁贴的尺寸稳定（重排时不变），避免拖动时整体 reflow 混乱
    val k = ((track.id % 7) + 7) % 7
    return when (k.toInt()) {
        0 -> 2 to 2   // 大磁贴
        3 -> 2 to 1   // 宽磁贴
        5 -> 1 to 2   // 高磁贴
        else -> 1 to 1
    }
}

/**
 * 在磁贴墙坐标系中查找包含指定屏幕坐标的磁贴索引。
 * 屏幕坐标 → 内容坐标：减去 offset；再与各磁贴的 placement + span*cellPx 矩形比对。
 */
private fun findTileAt(
    screenPosition: androidx.compose.ui.geometry.Offset,
    offsetX: Float,
    offsetY: Float,
    placements: List<Pair<Float, Float>>,
    tileSpans: List<Pair<Int, Int>>,
    cellPx: Float
): Int? {
    val xInContent = screenPosition.x - offsetX
    val yInContent = screenPosition.y - offsetY
    for (i in placements.indices) {
        val (px, py) = placements[i]
        val (sw, sh) = tileSpans.getOrNull(i) ?: continue
        if (xInContent >= px && xInContent < px + sw * cellPx &&
            yInContent >= py && yInContent < py + sh * cellPx
        ) {
            return i
        }
    }
    return null
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
        // 封面图或占位符
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
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(if (spanWidth >= 2 && spanHeight >= 2) 40.dp else 28.dp)
                )
            }
        }

        // 底部渐变遮罩：仅在显示标题时绘制（用于衬托标题文字）
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

        // 正在播放时：右上角发光点
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

        // 标题：受开关控制
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
