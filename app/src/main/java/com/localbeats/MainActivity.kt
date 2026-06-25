package com.localbeats

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localbeats.ui.screens.CarouselScreen
import com.localbeats.ui.screens.TileGridScreen
import com.localbeats.ui.theme.LocalBeatsTheme
import com.localbeats.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission) {
            // Reload music after permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            LocalBeatsTheme {
                if (hasPermission) {
                    MusicApp()
                } else {
                    PermissionScreen { checkAndRequestPermissions() }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            hasPermission = true
        } else {
            permissionLauncher.launch(permissions)
        }
    }
}

@Composable
fun MusicApp(
    viewModel: MusicViewModel = viewModel()
) {
    val tracks by viewModel.tracks.collectAsState()
    val currentTrack by viewModel.player.currentTrack.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFBB86FC),
                modifier = Modifier.align(Alignment.Center)
            )
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
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "LocalBeats needs access to your music files",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
