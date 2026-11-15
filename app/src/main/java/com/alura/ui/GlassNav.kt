package com.alura.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp)) {
        val pad = with(d) { 5.dp.toPx() }
        val tabW = (constraints.maxWidth - 2 * pad) / 3f
        var dragX by remember { mutableStateOf<Float?>(null) }
        val big = dragX != null
        val preview = dragX?.let { (it / tabW).roundToInt().coerceIn(0, 2) } ?: selected
        val x by animateFloatAsState(
            dragX ?: (selected * tabW),
            if (big) tween(60, easing = LinearEasing) else spring(dampingRatio = 0.42f, stiffness = 260f),
            label = "bubbleX"
        )
        val sx by animateFloatAsState(if (big) 1.3f else 1f, spring(dampingRatio = 0.42f, stiffness = 300f), label = "sx")
        val sy by animateFloatAsState(if (big) 1.38f else 1f, spring(dampingRatio = 0.42f, stiffness = 300f), label = "sy")

        // Kapsul besar (kaca): gradasi transparan + tepi bercahaya + bayangan
        Box(
            Modifier.matchParentSize()
                .shadow(5.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(W.copy(.16f), W.copy(.04f), W.copy(.09f))))
                .border(1.dp, W.copy(.2f), CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun upd(px: Float) {
                            dragX = (px - pad - tabW / 2).coerceIn(0f, 2 * tabW)
                        }
                        upd(down.position.x)
                        drag(down.id) { upd(it.position.x); it.consume() }
                        // Halaman baru berpindah setelah dilepas, ke tab terdekat
                        val target = ((dragX ?: (selected * tabW)) / tabW).roundToInt().coerceIn(0, 2)
                        dragX = null
                        onSel(target)
                    }
                }
        )
        // Gelembung kecil
        Box(
            Modifier
                .offset { IntOffset((pad + x).roundToInt(), pad.roundToInt()) }
                .size(with(d) { tabW.toDp() }, 54.dp)
                .graphicsLayer { scaleX = sx; scaleY = sy }
                .shadow(if (big) 6.dp else 1.dp, CircleShape, ambientColor = Acc, spotColor = Acc)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (big) listOf(W.copy(.42f), W.copy(.12f), Color(0xFF82B4FF).copy(.30f))
                        else listOf(W.copy(.34f), W.copy(.08f), Color(0xFF82B4FF).copy(.22f))
                    )
                )
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
