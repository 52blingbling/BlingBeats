package com.localbeats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localbeats.ui.screens.CarouselScreen
import com.localbeats.ui.screens.TileWallScreen
import com.localbeats.ui.theme.LocalBeatsTheme
import com.localbeats.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private var selectedFolderUri by mutableStateOf<Uri?>(null)

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // 尝试获取持久化读写权限，失败则降级为只读，再失败则放弃
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e2: SecurityException) {
                    return@registerForActivityResult
                }
            }
            selectedFolderUri = uri
            saveSelectedFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 崩溃恢复机制：如果上次加载时崩溃，清除保存的 URI，让用户重新选择
        if (wasLoadingCrashed()) {
            clearSelectedFolder()
            clearCrashFlag()
            selectedFolderUri = null
        } else {
            selectedFolderUri = restoreSelectedFolder()?.also { uri ->
                if (!isUriPermissionValid(uri)) {
                    selectedFolderUri = null
                    clearSelectedFolder()
                }
            }
        }

        setContent {
            LocalBeatsTheme {
                val viewModel: MusicViewModel = viewModel()
                AnimatedVisibility(
                    visible = selectedFolderUri == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ImportFolderScreen(onSelectFolder = ::pickMusicFolder)
                }
                AnimatedVisibility(
                    visible = selectedFolderUri != null,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut()
                ) {
                    MusicApp(
                        viewModel = viewModel,
                        selectedFolderUri = selectedFolderUri,
                        onImportFolderClick = ::pickMusicFolder
                    )
                }
            }
        }
    }

    private fun pickMusicFolder() {
        folderPickerLauncher.launch(null)
    }

    private fun saveSelectedFolder(uri: Uri) {
        // 使用 commit() 同步写入，确保即使随后崩溃 URI 也能被保存
        getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .edit()
            .putString("selected_folder_uri", uri.toString())
            .commit()
    }

    private fun restoreSelectedFolder(): Uri? {
        val storedValue = getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .getString("selected_folder_uri", null) ?: return null
        return try {
            Uri.parse(storedValue)
        } catch (_: Exception) {
            null
        }
    }

    private fun isUriPermissionValid(uri: Uri): Boolean {
        return try {
            val persisted = contentResolver.getPersistedUriPermissions()
            persisted.any { it.uri == uri && it.isReadPermission }
        } catch (_: Exception) {
            false
        }
    }

    private fun clearSelectedFolder() {
        getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .edit()
            .remove("selected_folder_uri")
            .commit()
    }

    /** 崩溃检测：在开始加载前设置标记，加载完成后清除。如果应用崩溃，标记会保留。 */
    private fun clearCrashFlag() {
        getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("loading_crashed", false)
            .commit()
    }

    private fun wasLoadingCrashed(): Boolean {
        return getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .getBoolean("loading_crashed", false)
    }
}

@Composable
fun MusicApp(
    viewModel: MusicViewModel,
    selectedFolderUri: Uri?,
    onImportFolderClick: () -> Unit
) {
    val tracks by viewModel.tracks.collectAsState()
    val currentTrack by viewModel.player.currentTrack.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val toggleOrientation = {
        if (isLandscape) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    LaunchedEffect(selectedFolderUri) {
        if (selectedFolderUri != null) {
            viewModel.loadMusicFromFolder(selectedFolderUri)
        } else {
            viewModel.clearTracks()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFFBB86FC),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在扫描音乐文件...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (tracks.isEmpty()) {
            ImportFolderScreen(onSelectFolder = onImportFolderClick)
        } else if (isLandscape) {
            CarouselScreen(
                tracks = tracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onTrackClick = { viewModel.playTrack(it) },
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onPreviousClick = { viewModel.playPrevious() },
                onNextClick = { viewModel.playNext() },
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { viewModel.seekTo(it) },
                onOrientationToggleClick = toggleOrientation
            )
        } else {
            TileWallScreen(
                tracks = tracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onTrackClick = { viewModel.playTrack(it) },
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onPreviousClick = { viewModel.playPrevious() },
                onNextClick = { viewModel.playNext() },
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { viewModel.seekTo(it) },
                onImportClick = onImportFolderClick,
                onRescan = { viewModel.rescanCurrentFolder() },
                onOrientationToggleClick = toggleOrientation
            )
        }
    }
}

@Composable
fun ImportFolderScreen(onSelectFolder: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")

    // 背景光晕动画
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    // 图标脉冲
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )

    // 按钮交互
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "btn_scale"
    )

    // 内容淡入
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        // 背景光晕
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(haloScale)
                .alpha(haloAlpha)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6A1B9A).copy(alpha = 0.6f),
                            Color(0xFF1565C0).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(1.1f - haloScale * 0.05f)
                .alpha(haloAlpha * 0.8f)
                .blur(60.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF03DAC6).copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 主内容
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(600)),
            exit = slideOutVertically() + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            ) {
                // 图标容器
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(iconPulse)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "LocalBeats",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC6))
                        )
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "你的本地音乐播放器",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "选择一个文件夹，我们会扫描其中\n所有支持的音频文件",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(40.dp))

                // 选择文件夹按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .scale(buttonScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFBB86FC), Color(0xFF7C4DFF))
                            )
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onSelectFolder
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "选择音乐文件夹",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "支持 MP3 · M4A · FLAC · WAV · OGG · AAC",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
