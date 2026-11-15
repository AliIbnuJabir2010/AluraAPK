
package com.alura

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
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
    val screenStateHolder = rememberSaveableStateHolder()
    var about by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(-1) }
    var lastPage by remember { mutableIntStateOf(0) }
    if (page >= 0) lastPage = page
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

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perms.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(Unit) {
        vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    BackHandler(full) {
        full = false
    }

    BackHandler(about && !full) {
        about = false
    }

    BackHandler(page >= 0 && !full) {
        page = -1
    }

    val showNav = navVisible && !about && page < 0
    val navMotion = spring< androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )
    val navOff by animateDpAsState(
        if (showNav) 0.dp else 120.dp,
        animationSpec = navMotion,
        label = "navOff"
    )
    val navAlpha by animateFloatAsState(
        if (showNav) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
        label = "navAlpha"
    )
    val miniBottom by animateDpAsState(
        if (showNav) 90.dp else 16.dp,
        animationSpec = navMotion,
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
            // Ganti tab secara langsung. AnimatedContent sebelumnya menahan dua layar
            // sekaligus selama transisi sehingga daftar, gambar, dan state lama tetap
            // ikut dirender ketika layar baru mulai dibuka.
            screenStateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> HomeScreen(vm, ps, onOpen = { page = it.ordinal })
                    1 -> SearchScreen(vm, ps)
                    else -> ProfileScreen(vm, onOpen = { page = it.ordinal }, onAbout = { about = true })
                }
            }

            AnimatedVisibility(
                about,
                enter = slideInHorizontally(animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(),
                exit = slideOutHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it / 4 } + fadeOut()
            ) {
                AboutScreen(onBack = { about = false })
            }

            AnimatedVisibility(
                page >= 0,
                enter = slideInHorizontally(animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(),
                exit = slideOutHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it / 4 } + fadeOut()
            ) {
                LibraryScreen(vm, ps, LibPage.values()[lastPage], onBack = { page = -1 })
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
                enter = slideInVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it },
                exit = slideOutVertically(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it }
            ) {
                PlayerScreen(
                    vm,
                    ps,
                    p,
                    onBack = { full = false }
                )
            }
        }
    }
}
