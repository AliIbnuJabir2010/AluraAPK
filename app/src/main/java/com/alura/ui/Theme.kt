package com.alura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF070B14)
val CardC = Color(0xFF111A2B)
val Card2 = Color(0xFF16213A)
val Txt = Color(0xFFE8EDF7)
val Mut = Color(0xFF8B97B1)
val Acc = Color(0xFF4F8DFF)
val Line = Color(0xFF1F2A44)

@Composable
fun AluraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Acc, background = Bg, surface = Bg, onSurface = Txt, onBackground = Txt),
        content = content
    )
}

/** Ikon digambar dari path SVG 24x24 yang sama dengan preview. */
class IcoSpec(val d: String, val fill: Boolean = false, val circle: Triple<Float, Float, Float>? = null)

object Icons {
    val HOME = IcoSpec("M3 11.5 12 4l9 7.5M5.5 10v10h13V10M10 20v-6h4v6")
    val SEARCH = IcoSpec("m16 16 4.5 4.5", circle = Triple(11f, 11f, 6.5f))
    val PROFILE = IcoSpec("M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7", circle = Triple(12f, 8f, 4f))
    val BACK = IcoSpec("M19 12H5M11 6l-6 6 6 6")
    val CHEV = IcoSpec("M9 6l6 6-6 6")
    val LYRICS = IcoSpec("M4 7h16M4 12h16M4 17h10")
    val PLAY = IcoSpec("M8 5v14l11-7z", fill = true)
    val PAUSE = IcoSpec("M7 5h3.5v14H7zM13.5 5H17v14h-3.5z", fill = true)
    val NEXT = IcoSpec("M6 5l9 7-9 7zM17 5h2.5v14H17z", fill = true)
    val PREV = IcoSpec("M18 5l-9 7 9 7zM4.5 5H7v14H4.5z", fill = true)
    private const val HEART_PATH = "M12 20.5C12 20.5 3.5 15.5 3.5 9.5A4.5 4.5 0 0 1 12 7.5A4.5 4.5 0 0 1 20.5 9.5C20.5 15.5 12 20.5 12 20.5Z"
    val HEART = IcoSpec(HEART_PATH)
    val HEART_FILL = IcoSpec(HEART_PATH, fill = true)
    val DOWNLOAD = IcoSpec("M12 4v11M7 11l5 5 5-5M5 20h14")
    val PLUS = IcoSpec("M12 5v14M5 12h14")
    val CHECK = IcoSpec("M5 12.5l4.5 4.5L19 7.5")
}

@Composable
fun Ico(s: IcoSpec, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val path = remember(s.d) { PathParser().parsePathString(s.d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 24f, Offset.Zero) {
            val st: DrawStyle = if (s.fill) Fill else Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, color, style = st)
            s.circle?.let { (x, y, r) -> drawCircle(color, r, Offset(x, y), style = st) }
        }
    }
}
