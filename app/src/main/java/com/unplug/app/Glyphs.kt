package com.unplug.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DEG = (PI / 180.0).toFloat()

private fun polar(angleDeg: Float): Offset {
    val a = (angleDeg - 90f) * DEG
    return Offset(cos(a), sin(a))
}

/**
 * A 60-tick instrument ring. Ticks light up as [fraction] goes 0 → 1 and a glowing dot
 * orbits at the current position. [fraction] is read at draw time, so animating it only
 * redraws the canvas — nothing recomposes.
 */
@Composable
fun TickRing(
    fraction: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val ink = Ink
    val dim = Dim
    val faint = Faint
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val f = fraction().coerceIn(0f, 1f)
            val r = size.minDimension / 2f
            val outer = r - 2.dp.toPx()
            val lit = (f * 60f).toInt()

            for (i in 0 until 60) {
                val dir = polar(i * 6f)
                val major = i % 5 == 0
                val len = (if (major) 12.dp else 6.dp).toPx()
                val color = when {
                    i < lit -> if (major) ink else ink.copy(alpha = 0.7f)
                    major -> dim.copy(alpha = 0.6f)
                    else -> faint
                }
                drawLine(
                    color = color,
                    start = center + dir * (outer - len),
                    end = center + dir * outer,
                    strokeWidth = (if (major) 2.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val orbit = outer - 22.dp.toPx()
            drawCircle(faint, radius = orbit - 8.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
            glowDot(ink, center + polar(f * 360f) * orbit)
        }
        content()
    }
}

/** Classic hands on a tick dial. The second hand sweeps smoothly via [secFraction]. */
@Composable
fun AnalogFace(
    hour: Int,
    minute: Int,
    second: Int,
    secFraction: () -> Float,
    showSeconds: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = Ink
    val dim = Dim
    val faint = Faint
    val paper = Paper
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val outer = r - 2.dp.toPx()
        for (i in 0 until 60) {
            val dir = polar(i * 6f)
            val major = i % 5 == 0
            val len = (if (major) 14.dp else 5.dp).toPx()
            drawLine(
                color = if (major) ink else faint,
                start = center + dir * (outer - len),
                end = center + dir * outer,
                strokeWidth = (if (major) 2.dp else 1.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }
        val hourAngle = ((hour % 12) + minute / 60f) * 30f
        val minuteAngle = (minute + second / 60f) * 6f
        hand(hourAngle, outer * 0.5f, 3.dp.toPx(), ink)
        hand(minuteAngle, outer * 0.78f, 1.6.dp.toPx(), ink)
        if (showSeconds) {
            hand(secFraction() * 360f, outer * 0.88f, 1.dp.toPx(), dim, tail = outer * 0.16f)
        }
        drawCircle(ink, radius = 4.5.dp.toPx(), center = center)
        drawCircle(paper, radius = 1.6.dp.toPx(), center = center)
    }
}

/** Three concentric orbits: hours (inner), minutes, seconds (outer). */
@Composable
fun OrbitFace(
    hour: Int,
    minute: Int,
    second: Int,
    secFraction: () -> Float,
    showSeconds: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val ink = Ink
    val dim = Dim
    val faint = Faint
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f - 8.dp.toPx()
            val gap = 16.dp.toPx()
            val rings = buildList {
                if (showSeconds) add(Triple(outer, secFraction(), dim))
                val start = if (showSeconds) outer - gap else outer
                add(Triple(start, (minute + second / 60f) / 60f, ink))
                add(Triple(start - gap, ((hour % 12) + minute / 60f) / 12f, ink))
            }
            rings.forEach { (radius, f, color) ->
                drawCircle(faint, radius = radius, center = center, style = Stroke(1.dp.toPx()))
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * f.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
                glowDot(ink, center + polar(f * 360f) * radius, small = true)
            }
        }
        content()
    }
}

private fun DrawScope.hand(angleDeg: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    val dir = polar(angleDeg)
    drawLine(color, center - dir * tail, center + dir * length, width, StrokeCap.Round)
}

private fun DrawScope.glowDot(color: Color, at: Offset, small: Boolean = false) {
    val k = if (small) 0.7f else 1f
    drawCircle(color.copy(alpha = 0.08f), radius = 12.dp.toPx() * k, center = at)
    drawCircle(color.copy(alpha = 0.22f), radius = 6.dp.toPx() * k, center = at)
    drawCircle(color, radius = 2.5.dp.toPx() * k, center = at)
}

/** Ring with a gap at the top and a line through it — the "unplug" mark. */
@Composable
fun PowerGlyph(modifier: Modifier = Modifier.size(26.dp), color: Color = Ink) {
    Canvas(modifier) {
        val w = 1.8.dp.toPx()
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w, w),
            size = Size(size.width - 2 * w, size.height - 2 * w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(center.x, w / 2), Offset(center.x, center.y), w, StrokeCap.Round)
    }
}

/** Line-drawn chat bubble for the WhatsApp dock button. */
@Composable
fun ChatGlyph(modifier: Modifier = Modifier.size(24.dp), color: Color = Ink) {
    Canvas(modifier) {
        val w = 1.6.dp.toPx()
        val r = size.minDimension / 2f - w * 1.5f
        drawCircle(color, radius = r, center = center, style = Stroke(w))
        val start = center + polar(225f) * r
        drawLine(color, start, Offset(w, size.height - w), w, StrokeCap.Round)
    }
}
