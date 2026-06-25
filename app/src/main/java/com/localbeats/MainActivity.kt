package com.localbeats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localbeats.ui.screens.CarouselScreen
import com.localbeats.ui.screens.TileGridScreen
import com.localbeats.ui.theme.LocalBeatsTheme
import com.localbeats.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private var selectedFolderUri by mutableStateOf<Uri?>(null)

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            selectedFolderUri = uri
            saveSelectedFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedFolderUri = restoreSelectedFolder()

        setContent {
            LocalBeatsTheme {
                val viewModel: MusicViewModel = viewModel()
                if (selectedFolderUri == null) {
                    ImportFolderScreen(onSelectFolder = ::pickMusicFolder)
                } else {
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
        getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .edit()
            .putString("selected_folder_uri", uri.toString())
            .apply()
    }

    private fun restoreSelectedFolder(): Uri? {
        val storedValue = getSharedPreferences("localbeats_prefs", MODE_PRIVATE)
            .getString("selected_folder_uri", null) ?: return null
        return Uri.parse(storedValue)
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    LaunchedEffect(selectedFolderUri) {
        if (selectedFolderUri != null) {
            viewModel.loadMusicFromFolder(selectedFolderUri)
        } else {
            viewModel.clearTracks()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFBB86FC),
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (tracks.isEmpty()) {
            ImportFolderScreen(onSelectFolder = onImportFolderClick)
        } else if (isLandscape) {
            CarouselScreen(
                tracks = tracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onTrackClick = { viewModel.playTrack(it) },
                onPlayPauseClick = { viewModel.togglePlayPause() }
            )
        } else {
            TileGridScreen(
                tracks = tracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                onTrackClick = { viewModel.playTrack(it) },
                onPlayPauseClick = { viewModel.togglePlayPause() }
            )
        }
    }
}

@Composable
fun ImportFolderScreen(onSelectFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "选择音乐文件夹导入",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "请选择一个包含音乐文件的文件夹，LocalBeats 会扫描其中的音频文件并加载到播放列表。",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onSelectFolder) {
            Text(text = "选择文件夹")
        }
    }
}
