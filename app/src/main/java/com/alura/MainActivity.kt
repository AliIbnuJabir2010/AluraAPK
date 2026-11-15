
package com.alura

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alura.player.PlayerHolder
import com.alura.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AluraTheme {
                AluraApp()
            }
        }
    }
}

@Composable
fun AluraApp(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val p = remember { PlayerHolder.player(ctx) }
    val ps = rememberPlayerState(p)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var about by rememberSaveable { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    var navVisible by remember { mutableStateOf(true) }

    // Menu menutup saat scroll ke bawah, muncul lagi saat scroll ke atas
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < -8f) {
                    navVisible = false
                } else if (available.y > 8f) {
                    navVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val perms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { r ->
        val audio = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (r[audio] == true) {
            vm.loadLocal()
        }
    }

    LaunchedEffect(Unit) {
        val list = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.READ_MEDIA_AUDIO
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        perms.launch(list.toTypedArray())
    }

    BackHandler(full) {
        full = false
    }

    BackHandler(about && !full) {
        about = false
    }

    val showNav = navVisible && !about
    val navOff by animateDpAsState(
        if (showNav) 0.dp else 120.dp,
        label = "navOff"
    )
    val navAlpha by animateFloatAsState(
        if (showNav) 1f else 0f,
        label = "navAlpha"
    )
    val miniBottom by animateDpAsState(
        if (showNav) 90.dp else 16.dp,
        label = "miniBottom"
    )

    Surface(
        Modifier.fillMaxSize(),
        color = Bg,
        contentColor = Txt
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .nestedScroll(conn)
        ) {
            when (tab) {
                0 -> HomeScreen(vm, ps)
                1 -> SearchScreen(vm, ps)
                else -> ProfileScreen(onAbout = { about = true })
            }

            AnimatedVisibility(
                about,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                AboutScreen(onBack = { about = false })
            }

            GlassNav(
                selected = tab,
                onSelect = {
                    tab = it
                    navVisible = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .offset(y = navOff)
                    .alpha(navAlpha)
            )

            if (ps.track != null) {
                MiniPlayer(
                    ps,
                    p,
                    onOpen = { full = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                        .navigationBarsPadding()
                        .padding(bottom = miniBottom)
                )
            }

            AnimatedVisibility(
                full,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                PlayerScreen(
                    ps,
                    p,
                    onBack = { full = false }
                )
            }
        }
    }
}
