package com.localbeats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localbeats.data.model.MusicTrack
import com.localbeats.ui.components.PlayerBar
import com.localbeats.ui.components.placeholderPalettes
import kotlin.math.roundToInt
import kotlin.math.max

/**
 * Windows 8/10 开始菜单风格的方形磁贴墙：
 * - 磁贴大小不一（1x1、2x1、1x2、2x2）无缝拼接
 * - 整个磁贴墙可上下左右拖动平移
 * - 点击磁贴播放对应歌曲
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // 基础单元尺寸：每格 110dp，保证磁贴不会太小也不会太大
    val cellPx = with(density) { 110.dp.toPx() }

    // 为每个磁贴确定 span（横跨格子数）：大部分 1x1，少数大磁贴
    val tileSpans = remember(tracks) { tracks.mapIndexed(::computeSpan) }

    // 拖动偏移（相对屏幕左上角）
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 容器和内容的实际尺寸，用于约束拖动范围
    var viewportWidth by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    var contentWidth by remember { mutableFloatStateOf(0f) }
    var contentHeight by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0D0D0D))
            .onGloballyPositioned { coords ->
                viewportWidth = coords.size.width.toFloat()
                viewportHeight = coords.size.height.toFloat()
            }
            // 2D 拖动平移；down 时不清零，保持累积偏移
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        var newX = offsetX + drag.x
                        var newY = offsetY + drag.y
                        // 限制平移范围：内容不能完全拖出可视区域
                        val maxX = max(0f, contentWidth - viewportWidth)
                        val maxY = max(0f, contentHeight - viewportHeight)
                        newX = newX.coerceIn(-maxX, 0f)
                        newY = newY.coerceIn(-maxY, 0f)
                        offsetX = newX
                        offsetY = newY
                    }
                )
            }
    ) {
        // 磁贴墙容器（自定义 Layout 自行排列大小不一的磁贴）
        Layout(
            content = {
                tracks.forEachIndexed { index, track ->
                    val span = tileSpans[index]
                    Box(
                        modifier = Modifier
                            .layoutId(index)
                            .clip(RoundedCornerShape(0.dp)) // 无缝拼接：无圆角
                    ) {
                        TileContent(
                            track = track,
                            spanWidth = span.first,
                            spanHeight = span.second,
                            isPlaying = isPlaying && track.id == currentTrack?.id,
                            onClick = { onTrackClick(track) },
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
                // 行数上限：每个磁贴最多占 2 行，预留充足空间
                val maxRows = (tracks.size * 2 + 4).coerceAtLeast(16)
                val occupied = Array(columns) { BooleanArray(maxRows) }
                val placements = ArrayList<Pair<Int, Int>>() // (x, y) for each tile
                var maxRowUsed = 0

                measurables.forEachIndexed { index, measurable ->
                    val span = tileSpans[index]
                    val w = span.first
                    val h = span.second
                    // 在 occupied 中寻找第一个能放下 w×h 的位置
                    var placed = false
                    var row = 0
                    while (!placed && row < maxRows) {
                        var col = 0
                        while (col + w <= columns) {
                            if (canPlace(occupied, col, row, w, h, columns, maxRows)) {
                                placements.add((col * cellPx).toInt() to (row * cellPx).toInt())
                                mark(occupied, col, row, w, h)
                                maxRowUsed = max(maxRowUsed, row + h)
                                placed = true
                                break
                            }
                            col++
                        }
                        if (!placed) row++
                    }
                    // 兜底：若因行数上限未放下，放在 (0,0) 避免空指针
                    if (!placed) {
                        placements.add(0 to 0)
                    }
                }

                val totalWidth = columns * cellPx
                val totalHeight = maxRowUsed * cellPx
                contentWidth = totalWidth
                contentHeight = totalHeight

                // 测量每个磁贴
                val placeables = measurables.mapIndexed { index, measurable ->
                    val span = tileSpans[index]
                    val w = (span.first * cellPx).toInt()
                    val h = (span.second * cellPx).toInt()
                    measurable.measure(Constraints.fixed(w, h))
                }

                layout(totalWidth.toInt(), totalHeight.toInt()) {
                    placeables.forEachIndexed { index, placeable ->
                        val (x, y) = placements[index]
                        placeable.placeRelative(x, y)
                    }
                }
            },
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        )

        // 顶部浮层标题栏（透明背景，仅渐变遮罩保证文字可读）
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
                    IconButton(onClick = onImportClick) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Import Folder",
                            tint = Color(0xFFBB86FC)
                        )
                    }
                }
            }
        }

        // 底部播放控制栏
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
private fun computeSpan(index: Int, track: MusicTrack): Pair<Int, Int> {
    // 节奏：大部分 1x1，偶尔出现 2x2 大磁贴或 2x1 宽磁贴
    return when (index % 7) {
        0 -> 2 to 2   // 大磁贴
        3 -> 2 to 1   // 宽磁贴
        5 -> 1 to 2   // 高磁贴
        else -> 1 to 1
    }
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

/** 单个磁贴的内容：封面图 + 底部信息 + 播放指示 */
@Composable
private fun TileContent(
    track: MusicTrack,
    spanWidth: Int,
    spanHeight: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    cellPx: Float
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val palette = placeholderPalettes[(track.id % placeholderPalettes.size).toInt().coerceAtLeast(0)]
    val shape = RoundedCornerShape(0.dp)

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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // 封面图或占位符
        if (track.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
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

        // 底部渐变遮罩
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

        // 标题
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
