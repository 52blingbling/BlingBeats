package com.localbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    // 视口与内容尺寸
    var viewportWidth by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }

    // 列数：视口宽度决定，+1 列保证略宽于视口（可横向平移一点）
    val columns = if (viewportWidth > 0f) max(3, (viewportWidth / cellPx).toInt()) + 1 else 5

    // 按 track.id 取模决定 span，每个磁贴尺寸稳定（重排不变）
    val idSetKey = remember(tracks) { tracks.map { it.id }.toSet() }
    val tileSpansMap = remember(idSetKey) { tracks.associate { it.id to computeSpan(it) } }

    // 独立的 id→磁贴坐标（单元格 col,row）映射。
    // 仅在曲目集合或列数变化时重新打包；重排（仅顺序变化）不会触发重新打包，
    // 因此拖拽交换后位置保持稳定。交换时只交换对应项，避免整体 reflow。
    val tilePositions = remember(idSetKey, columns) {
        mutableStateMapOf<Long, Pair<Int, Int>>().apply {
            packTiles(tracks, tileSpansMap, columns, this)
        }
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
    var offsetInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(topInsetPx) {
        if (!offsetInitialized && topInsetPx > 0f) {
            offsetY = topInsetPx
            offsetInitialized = true
        }
    }

    // 长按拖动相关状态（全部以 track.id 为键）
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragTargetId by remember { mutableStateOf<Long?>(null) }

    // pointerInput(Unit) 的 lambda 只创建一次，用 rememberUpdatedState 始终读取最新值
    val currentTracks by rememberUpdatedState(tracks)
    val currentTileSpans by rememberUpdatedState(tileSpansMap)
    val currentOnTrackClick by rememberUpdatedState(onTrackClick)
    val currentOnReorder by rememberUpdatedState(onReorder)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0D0D0D))
            .onGloballyPositioned { coords ->
                viewportWidth = coords.size.width.toFloat()
                viewportHeight = coords.size.height.toFloat()
            }
            // 1) 轻点 → 播放对应磁贴（按 track.id 命中）
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val id = findTrackIdAt(
                        offset, offsetX, offsetY,
                        currentTracks, tilePositions, currentTileSpans, cellPx
                    )
                    if (id != null) {
                        currentTracks.firstOrNull { it.id == id }?.let { currentOnTrackClick(it) }
                    }
                }
            }
            // 2) 普通拖动 → 平移磁贴墙（长按拖动接管后会取消平移）
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (draggedId != null) return@detectDragGestures // 拖磁贴中不平移
                        change.consume()
                        val maxX = max(0f, contentWidth - viewportWidth)
                        val restY = topInsetPx
                        val usableHeight = (viewportHeight - topInsetPx).coerceAtLeast(0f)
                        val maxScrollUp = max(0f, contentHeight - usableHeight)
                        offsetX = (offsetX + dragAmount.x).coerceIn(
                            -maxX - viewportWidth * 0.25f,
                            viewportWidth * 0.25f
                        )
                        offsetY = (offsetY + dragAmount.y).coerceIn(
                            restY - maxScrollUp - viewportHeight * 0.15f,
                            restY + viewportHeight * 0.15f
                        )
                    }
                )
            }
            // 3) 长按后拖动 → 磁贴交换（Compose 处理长按判定，避免轻微抖动误判）
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val id = findTrackIdAt(
                            offset, offsetX, offsetY,
                            currentTracks, tilePositions, currentTileSpans, cellPx
                        )
                        draggedId = id
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        dragTargetId = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                        val dId = draggedId ?: return@detectDragGesturesAfterLongPress
                        val pos = tilePositions[dId] ?: return@detectDragGesturesAfterLongPress
                        val span = currentTileSpans[dId] ?: return@detectDragGesturesAfterLongPress
                        val cx = pos.first * cellPx + span.first * cellPx / 2 + dragOffsetX
                        val cy = pos.second * cellPx + span.second * cellPx / 2 + dragOffsetY
                        var hit: Long? = null
                        for (t in currentTracks) {
                            if (t.id == dId) continue
                            val p = tilePositions[t.id] ?: continue
                            val s = currentTileSpans[t.id] ?: continue
                            if (cx >= p.first * cellPx && cx < (p.first + s.first) * cellPx &&
                                cy >= p.second * cellPx && cy < (p.second + s.second) * cellPx
                            ) {
                                hit = t.id
                                break
                            }
                        }
                        dragTargetId = hit
                    },
                    onDragEnd = {
                        val from = draggedId
                        val to = dragTargetId
                        if (from != null && to != null && from != to) {
                            // 仅交换对应项的位置，不 reflow
                            val pa = tilePositions[from]
                            val pb = tilePositions[to]
                            if (pa != null && pb != null) {
                                tilePositions[from] = pb
                                tilePositions[to] = pa
                            }
                            // 持久化顺序：用 tracks 中的索引
                            val fromIdx = currentTracks.indexOfFirst { it.id == from }
                            val toIdx = currentTracks.indexOfFirst { it.id == to }
                            if (fromIdx >= 0 && toIdx >= 0) currentOnReorder(fromIdx, toIdx)
                        }
                        draggedId = null
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        dragTargetId = null
                    },
                    onDragCancel = {
                        draggedId = null
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        dragTargetId = null
                    }
                )
            }
    ) {
        Layout(
            content = {
                tracks.forEach { track ->
                    key(track.id) {
                        val span = tileSpansMap[track.id] ?: (1 to 1)
                        val isDragged = draggedId == track.id
                        val isDropTarget = dragTargetId == track.id
                        Box(
                            modifier = Modifier
                                .layoutId(track.id)
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
                }
            },
            measurePolicy = { measurables, _ ->
                // 按 layoutId(track.id) 索引 measurables，保证节点身份与数据对齐
                val byId = HashMap<Long, androidx.compose.ui.layout.Measurable>()
                measurables.forEach { m ->
                    (m.layoutId as? Long)?.let { byId[it] = m }
                }
                val placeables = tracks.mapNotNull { track ->
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

/** 计算磁贴的 span（横向、纵向格子数），按 track.id 取模保证尺寸稳定 */
private fun computeSpan(track: MusicTrack): Pair<Int, Int> {
    val k = ((track.id % 7) + 7) % 7
    return when (k.toInt()) {
        0 -> 2 to 2   // 大磁贴
        3 -> 2 to 1   // 宽磁贴
        5 -> 1 to 2   // 高磁贴
        else -> 1 to 1
    }
}

/** 打包磁贴位置：按 tracks 顺序贪心放置，写入 out 映射（id → col,row 单元格坐标） */
private fun packTiles(
    tracks: List<MusicTrack>,
    spans: Map<Long, Pair<Int, Int>>,
    columns: Int,
    out: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, Pair<Int, Int>>
) {
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
}

/**
 * 在磁贴墙坐标系中查找包含指定屏幕坐标的磁贴 track.id。
 * 屏幕坐标 → 内容坐标：减去 offset；再与各磁贴 (pos*cellPx, span*cellPx) 矩形比对。
 */
private fun findTrackIdAt(
    screenPosition: Offset,
    offsetX: Float,
    offsetY: Float,
    tracks: List<MusicTrack>,
    tilePositions: Map<Long, Pair<Int, Int>>,
    tileSpans: Map<Long, Pair<Int, Int>>,
    cellPx: Float
): Long? {
    val xInContent = screenPosition.x - offsetX
    val yInContent = screenPosition.y - offsetY
    for (track in tracks) {
        val pos = tilePositions[track.id] ?: continue
        val span = tileSpans[track.id] ?: continue
        val px = pos.first * cellPx
        val py = pos.second * cellPx
        if (xInContent >= px && xInContent < px + span.first * cellPx &&
            yInContent >= py && yInContent < py + span.second * cellPx
        ) {
            return track.id
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
