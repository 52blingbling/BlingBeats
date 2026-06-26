package com.localbeats.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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

    // 竖屏封面歌曲标题显示开关（持久化到 SharedPreferences）
    val prefs = remember { context.getSharedPreferences("localbeats_prefs", android.content.Context.MODE_PRIVATE) }
    var showTitle by remember {
        mutableStateOf(prefs.getBoolean("show_tile_title", true))
    }
    // 设置菜单展开状态
    var menuExpanded by remember { mutableStateOf(false) }

    val tileSpans = remember(tracks) { tracks.mapIndexed(::computeSpan) }

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

    // 顶部标题栏高度（动态测量），磁贴墙内容从此高度下方开始，避免被遮挡
    var topInsetPx by remember { mutableFloatStateOf(0f) }

    // 长按拖动相关状态
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0D0D0D))
            .onGloballyPositioned { coords ->
                viewportWidth = coords.size.width.toFloat()
                viewportHeight = coords.size.height.toFloat()
            }
            // 长按拖动：交换磁贴位置（用 detectDragGesturesAfterLongPress 可靠检测长按）
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // 长按触发，找到被按下的磁贴
                        val xInContent = offset.x - offsetX
                        val yInContent = offset.y - offsetY
                        for (i in placements.indices) {
                            val (px, py) = placements[i]
                            val (sw, sh) = tileSpans.getOrNull(i) ?: continue
                            if (xInContent >= px && xInContent < px + sw * cellPx &&
                                yInContent >= py && yInContent < py + sh * cellPx
                            ) {
                                draggedIndex = i
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                break
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (draggedIndex == null) return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y

                        val draggedIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                        val placement = placements.getOrNull(draggedIdx) ?: return@detectDragGesturesAfterLongPress
                        val span = tileSpans.getOrNull(draggedIdx) ?: return@detectDragGesturesAfterLongPress
                        val draggedCenterX = placement.first + span.first * cellPx / 2 + dragOffsetX
                        val draggedCenterY = placement.second + span.second * cellPx / 2 + dragOffsetY

                        // 找出包含被拖磁贴中心点的目标磁贴并实时交换
                        for (i in placements.indices) {
                            if (i == draggedIdx) continue
                            val (px, py) = placements[i]
                            val (sw, sh) = tileSpans[i]
                            if (draggedCenterX >= px && draggedCenterX < px + sw * cellPx &&
                                draggedCenterY >= py && draggedCenterY < py + sh * cellPx
                            ) {
                                val oldDraggedPlacement = placements[draggedIdx]
                                val oldTargetPlacement = placements[i]
                                onReorder(draggedIdx, i)
                                draggedIndex = i
                                // 调整 dragOffset 使被拖磁贴保持在手指下方
                                dragOffsetX += oldDraggedPlacement.first - oldTargetPlacement.first
                                dragOffsetY += oldDraggedPlacement.second - oldTargetPlacement.second
                                break
                            }
                        }
                    },
                    onDragEnd = {
                        draggedIndex = null
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        draggedIndex = null
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    }
                )
            }
            // 短按拖动：平移磁贴墙（仅在未进入长按拖动模式时生效）
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        // 长按拖动模式进行中时不平移
                        if (draggedIndex != null) return@detectDragGestures
                        change.consume()
                        val maxX = max(0f, contentWidth - viewportWidth)
                        val maxY = max(0f, contentHeight - viewportHeight)
                        // 自由拖动范围：四方向均允许 30% 视口尺寸的弹性越界
                        val newX = (offsetX + dragAmount.x).coerceIn(
                            -maxX - viewportWidth * 0.3f,
                            viewportWidth * 0.3f
                        )
                        val newY = (offsetY + dragAmount.y).coerceIn(
                            -maxY - viewportHeight * 0.3f,
                            viewportHeight * 0.3f
                        )
                        offsetX = newX
                        offsetY = newY
                    }
                )
            }
    ) {
        Layout(
            content = {
                tracks.forEachIndexed { index, track ->
                    val span = tileSpans[index]
                    val isDragged = draggedIndex == index
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
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        TileContent(
                            track = track,
                            spanWidth = span.first,
                            spanHeight = span.second,
                            isPlaying = isPlaying && track.id == currentTrack?.id,
                            showTitle = showTitle,
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
                .padding(top = with(density) { topInsetPx.toDp() })
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
                                        contentDescription = null
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
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("显示歌曲标题") },
                                onClick = {
                                    showTitle = !showTitle
                                    prefs.edit().putBoolean("show_tile_title", showTitle).apply()
                                },
                                leadingIcon = {
                                    Switch(
                                        checked = showTitle,
                                        onCheckedChange = {
                                            showTitle = it
                                            prefs.edit().putBoolean("show_tile_title", it).apply()
                                        }
                                    )
                                },
                                trailingIcon = {}
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
private fun computeSpan(index: Int, track: MusicTrack): Pair<Int, Int> {
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

/** 单个磁贴的内容：封面图 + 底部信息 + 播放指示 + 按压缩放动画 */
@Composable
private fun TileContent(
    track: MusicTrack,
    spanWidth: Int,
    spanHeight: Int,
    isPlaying: Boolean,
    showTitle: Boolean,
    onClick: () -> Unit,
    cellPx: Float
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 按压缩放动画：按下时缩小至 0.92，松开回弹
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tile_press"
    )

    val palette = placeholderPalettes[(track.id % placeholderPalettes.size).toInt().coerceAtLeast(0)]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
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
