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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.localbeats.ui.screens.CarouselScreen
import com.localbeats.ui.screens.TileWallScreen
import com.localbeats.ui.theme.LocalBeatsTheme
import com.localbeats.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private var hasCompletedSetup by mutableStateOf(false)
    private var showFolderSelection by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showFolderSelection = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
        hasCompletedSetup = prefs.getBoolean("has_completed_setup", false)

        if (wasLoadingCrashed()) {
            clearCrashFlag()
        }

        setContent {
            var themeMode by remember { mutableStateOf(prefs.getInt("theme_mode", 2)) }

            LocalBeatsTheme(themeMode = themeMode) {
                val viewModel: MusicViewModel = viewModel()
                AnimatedVisibility(
                    visible = !hasCompletedSetup && !showFolderSelection,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    WelcomeScreen(onScanMusic = ::requestStoragePermission)
                }
                AnimatedVisibility(
                    visible = showFolderSelection,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    FolderSelectionScreen(
                        viewModel = viewModel,
                        currentThemeMode = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
                                .edit()
                                .putInt("theme_mode", newMode)
                                .apply()
                        },
                        onConfirm = { ignored, filterShort ->
                            saveIgnoredFolders(ignored, filterShort)
                            hasCompletedSetup = true
                            showFolderSelection = false
                        }
                    )
                }
                AnimatedVisibility(
                    visible = hasCompletedSetup && !showFolderSelection,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut()
                ) {
                    MusicApp(
                        viewModel = viewModel,
                        onSettingsClick = { showFolderSelection = true }
                    )
                }
            }
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            showFolderSelection = true
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun saveIgnoredFolders(ignored: Set<String>, filterShort: Boolean) {
        getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .edit()
            .putStringSet("ignored_folders", ignored)
            .putBoolean("filter_short_audio", filterShort)
            .putBoolean("has_completed_setup", true)
            .commit()
    }

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
    onSettingsClick: () -> Unit
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

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("localbeats_prefs", android.content.Context.MODE_PRIVATE)
        val ignored = prefs.getStringSet("ignored_folders", emptySet()) ?: emptySet()
        val filterShort = prefs.getBoolean("filter_short_audio", true)
        viewModel.loadMusicFromDevice(ignored, filterShort)
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
            // 如果扫描后没有歌曲，显示个提示
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "未找到音频文件", color = MaterialTheme.colorScheme.onBackground)
            }
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
                onImportClick = onSettingsClick,
                onRescan = { viewModel.rescanDevice() },
                onOrientationToggleClick = toggleOrientation
            )
        }
    }
}

@Composable
fun WelcomeScreen(onScanMusic: () -> Unit) {
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
            .background(MaterialTheme.colorScheme.background),
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
                    text = "BlingBeats",
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
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "一键扫描手机中的所有本地音乐文件",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
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
                            onClick = onScanMusic
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "扫描本地音乐",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "支持 MP3 · M4A · FLAC · WAV · OGG · AAC",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionScreen(
    viewModel: MusicViewModel,
    currentThemeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onConfirm: (Set<String>, Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var ignoredFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filterShortAudio by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            folders = viewModel.getAudioFolders()
            val prefs = context.getSharedPreferences("localbeats_prefs", android.content.Context.MODE_PRIVATE)
            ignoredFolders = prefs.getStringSet("ignored_folders", emptySet()) ?: emptySet()
            filterShortAudio = prefs.getBoolean("filter_short_audio", true)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置与扫描文件夹", color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFBB86FC))
                        .clickable { onConfirm(ignoredFolders, filterShortAudio) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("完成", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFBB86FC))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    // 主题切换
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(text = "外观与主题", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            ThemeOption(label = "跟随系统", selected = currentThemeMode == 0, onClick = { onThemeModeChange(0) })
                            ThemeOption(label = "浅色模式", selected = currentThemeMode == 1, onClick = { onThemeModeChange(1) })
                            ThemeOption(label = "深色模式", selected = currentThemeMode == 2, onClick = { onThemeModeChange(2) })
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)))
                    
                    // 过滤短音频
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filterShortAudio = !filterShortAudio }
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "过滤短音频", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "不展示 60 秒以下的音频文件", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = filterShortAudio,
                            onCheckedChange = { filterShortAudio = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFBB86FC),
                                checkedTrackColor = Color(0xFFBB86FC).copy(alpha = 0.5f)
                            )
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(folders) { folder ->
                    val isChecked = !ignoredFolders.contains(folder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ignoredFolders = if (isChecked) {
                                    ignoredFolders + folder
                                } else {
                                    ignoredFolders - folder
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFBB86FC),
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.Black
                            )
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Column {
                            val folderName = folder.substringAfterLast("/")
                            Text(text = folderName, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                            Text(text = folder, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
        RadioButton(
            checked = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFBB86FC))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
    }
}
