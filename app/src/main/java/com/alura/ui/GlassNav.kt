package com.alura.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val W = Color.White
private val NAV_ICONS = listOf(Icons.HOME, Icons.SEARCH, Icons.PROFILE)

/**
 * Kapsul kaca mengambang dengan gelembung yang bisa digeser.
 * Saat ditekan/digeser gelembung membesar (melebihi kapsul besar), saat dilepas menempel ke tab terdekat.
 */
@Composable
fun GlassNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDensity.current
    val onSel by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp)) {
        val pad = with(d) { 5.dp.toPx() }
        val tabW = (constraints.maxWidth - 2 * pad) / 3f
        // pos: posisi saat settle (spring). dragX: posisi jari langsung (tanpa animasi = tanpa lag)
        val pos = remember { Animatable(selected * tabW) }
        var dragging by remember { mutableStateOf(false) }
        var dragX by remember { mutableFloatStateOf(0f) }
        val curX = { if (dragging) dragX else pos.value }

        LaunchedEffect(selected, tabW) {
            if (!dragging) pos.animateTo(selected * tabW, spring(dampingRatio = 0.8f, stiffness = 380f))
        }
        val preview by remember(tabW) { derivedStateOf { (curX() / tabW).roundToInt().coerceIn(0, 2) } }
        val t by animateFloatAsState(
            if (dragging) 1f else 0f,
            spring(dampingRatio = 0.7f, stiffness = 400f),
            label = "grow"
        )

        // Kapsul besar (kaca)
        Box(
            Modifier.matchParentSize()
                .shadow(5.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(W.copy(.16f), W.copy(.04f), W.copy(.09f))))
                .border(1.dp, W.copy(.2f), CircleShape)
                .pointerInput(tabW) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun upd(px: Float) {
                            dragX = (px - pad - tabW / 2).coerceIn(0f, 2 * tabW)
                        }
                        // mulai dari posisi gelembung saat ini agar tidak melompat
                        dragX = pos.value
                        dragging = true
                        scope.launch { pos.stop() }
                        upd(down.position.x)
                        drag(down.id) { upd(it.position.x); it.consume() }
                        val target = (dragX / tabW).roundToInt().coerceIn(0, 2)
                        val from = dragX
                        scope.launch {
                            pos.snapTo(from)
                            dragging = false
                            pos.animateTo(target * tabW, spring(dampingRatio = 0.8f, stiffness = 380f))
                        }
                        onSel(target)
                    }
                }
        )
        // Gelembung kecil: translasi per-piksel di fase draw (tanpa relayout, tanpa patah)
        Box(
            Modifier
                .offset { IntOffset(pad.roundToInt(), pad.roundToInt()) }
                .size(with(d) { tabW.toDp() }, 54.dp)
                .graphicsLayer {
                    translationX = curX()
                    scaleX = 1f + 0.3f * t
                    scaleY = 1f + 0.38f * t
                    shadowElevation = with(d) { (1 + 5 * t).dp.toPx() }
                    shape = CircleShape
                    clip = true
                }
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                W.copy(.34f + .08f * t),
                                W.copy(.08f + .04f * t),
                                Color(0xFFB3B3B3).copy(.22f + .08f * t)
                            )
                        )
                    )
                }
                .border(1.dp, W.copy(.45f), CircleShape)
        )
        // Ikon (tanpa teks)
        Row(Modifier.matchParentSize().padding(5.dp)) {
            NAV_ICONS.forEachIndexed { i, ico ->
                Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) {
                    Ico(ico, if (i == preview) Color.White else Mut, 26.dp)
                }
            }
        }
    }
}
