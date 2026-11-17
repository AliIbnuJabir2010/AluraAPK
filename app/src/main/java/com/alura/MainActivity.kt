package com.alura

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
        val preferences = getSharedPreferences("alura_preferences", MODE_PRIVATE)
        setContent {
            var styleName by rememberSaveable {
                mutableStateOf(preferences.getString("appearance", "LIQUID_GLASS") ?: "LIQUID_GLASS")
            }
            val style = runCatching { AluraStyle.valueOf(styleName) }
                .getOrDefault(AluraStyle.LIQUID_GLASS)
            SideEffect { setAluraStyle(style) }
            AluraTheme {
                AluraApp(
                    currentStyle = style,
                    onStyleChange = { next ->
                        styleName = next.name
                        preferences.edit().putString("appearance", next.name).apply()
                        setAluraStyle(next)
                    }
                )
            }
        }
    }
}

@Composable
fun AluraApp(
    vm: AppViewModel = viewModel(),
    currentStyle: AluraStyle = AluraStyle.LIQUID_GLASS,
    onStyleChange: (AluraStyle) -> Unit = {}
) {
    val context = LocalContext.current
    remember { com.alura.data.AppSettings.init(context) }
    val player = remember { PlayerHolder.player(context) }
    // State ini hanya berubah saat event player, bukan setiap 400-1500 ms.
    val playerState = rememberPlayerState(player)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()
    var about by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var recap by rememberSaveable { mutableStateOf(false) }
    var pageIndex by rememberSaveable { mutableIntStateOf(-1) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var navVisible by remember { mutableStateOf(true) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        vm.setLocalUnavailable()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    LaunchedEffect(Unit) {
        vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    BackHandler(enabled = fullPlayer) { fullPlayer = false }
    BackHandler(enabled = !fullPlayer && pageIndex >= 0) { pageIndex = -1 }
    BackHandler(enabled = !fullPlayer && settings) { settings = false }
    BackHandler(enabled = !fullPlayer && !settings && about) { about = false }
    BackHandler(enabled = !fullPlayer && !settings && !about && recap) { recap = false }

    val mainVisible = !fullPlayer && pageIndex < 0 && !about && !settings && !recap
    val showNav = mainVisible && navVisible
    val navMotion = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessMediumLow
    )
    val navOffset by animateDpAsState(
        targetValue = if (showNav) 0.dp else 120.dp,
        animationSpec = navMotion,
        label = "navOffset"
    )
    val navAlpha by animateFloatAsState(
        targetValue = if (showNav) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
        label = "navAlpha"
    )
    val miniBottom by animateDpAsState(
        targetValue = if (showNav) 90.dp else 16.dp,
        animationSpec = navMotion,
        label = "miniBottom"
    )

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) navVisible = false
                else if (available.y > 8f) navVisible = true
                return Offset.Zero
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Txt) {
        Box(
            Modifier.fillMaxSize().background(Bg).nestedScroll(nestedScroll)
        ) {
            // Hanya satu layar yang aktif pada satu waktu. Halaman di belakang tidak
            // ikut dirender ulang ketika library, statistik, atau player berubah.
            when {
                fullPlayer && playerState.track != null -> {
                    PlayerScreen(vm, playerState, player) { fullPlayer = false }
                }
                settings -> {
                    SettingsScreen(currentStyle, onStyleChange, extra = { ExtraSettings(vm) }) { settings = false }
                }
                about -> {
                    AboutScreen { about = false }
                }
                recap -> {
                    RecapScreen(vm) { recap = false }
                }
                pageIndex >= 0 -> {
                    val page = LibPage.values().getOrNull(pageIndex) ?: LibPage.PLAYLISTS
                    LibraryScreen(vm, playerState, page) { pageIndex = -1 }
                }
                else -> {
                    stateHolder.SaveableStateProvider(tab) {
                        when (tab) {
                            0 -> HomeScreen(vm, playerState, onOpen = { pageIndex = it.ordinal })
                            1 -> SearchScreen(vm, playerState)
                            else -> ProfileScreen(
                                vm = vm,
                                onOpen = { pageIndex = it.ordinal },
                                onAbout = { about = true },
                                onSettings = { settings = true },
                                onRecap = { recap = true }
                            )
                        }
                    }

                    GlassNav(
                        selected = tab,
                        onSelect = {
                            if (tab != it) tab = it
                            navVisible = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 14.dp)
                            .offset(y = navOffset)
                            .alpha(navAlpha)
                    )

                    if (playerState.track != null) {
                        MiniPlayer(
                            playerState,
                            player,
                            onOpen = { fullPlayer = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp)
                                .navigationBarsPadding()
                                .padding(bottom = miniBottom)
                        )
                    }
                }
            }
        }
    }
}
