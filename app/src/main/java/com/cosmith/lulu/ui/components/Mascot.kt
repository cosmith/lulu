package com.cosmith.lulu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.cosmith.lulu.ui.theme.Lulu
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

enum class Mood {
    /** Breathes and blinks. */
    Idle,

    /** Ears open: excitement lines on, eyes up. The child is expected to speak or listen. */
    Listening,

    /** Jumps with an open smile. */
    Happy,

    /** Leans and sways kindly after a miss. Never sad. */
    Encourage,

    /** Eyes closed: nothing to learn right now, come back later. */
    Sleepy,
}

/**
 * Lulu, the mascot: a soft gumdrop with two big eyes looking up, like on the logo.
 * [jumpKey] makes it jump each time it changes.
 */
@Composable
fun Mascot(
    color: Color,
    mood: Mood,
    modifier: Modifier = Modifier,
    jumpKey: Int = 0,
) {
    val loop = rememberInfiniteTransition(label = "mascot")
    val breath by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val blink by loop.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3800
                1f at 0
                1f at 3500
                0.1f at 3580
                1f at 3700
            },
        ),
        label = "blink",
    )
    val sway by loop.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val linesPulse by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "lines",
    )
    val lean by animateFloatAsState(
        targetValue = if (mood == Mood.Encourage) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lean",
    )

    val jump = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) }
    LaunchedEffect(jumpKey) {
        if (jumpKey == 0) return@LaunchedEffect
        launch {
            squash.animateTo(1f, tween(90))
            squash.animateTo(-0.6f, tween(140))
            squash.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        }
        jump.animateTo(1f, tween(220, delayMillis = 80, easing = FastOutSlowInEasing))
        jump.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        squash.animateTo(0.7f, tween(70))
        squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
    }

    val eyeOpen = if (mood == Mood.Sleepy) 0.08f else blink
    val linesColor = if (color == Lulu.Mustard) Lulu.Terracotta else Lulu.Mustard
    Canvas(
        modifier.graphicsLayer {
            val breathe = if (mood == Mood.Sleepy) breath * 0.03f else breath * 0.02f
            val sq = squash.value * 0.12f
            scaleY = 1f + breathe - sq
            scaleX = 1f - breathe * 0.6f + sq * 0.8f
            translationY = -jump.value * size.height * 0.22f
            rotationZ = lean * 7f * (0.6f + 0.4f * sway)
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    ) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(x * w, y * h)

        // Legs first, so the body sits on top of them.
        val leg = Stroke(width = 0.055f * w, cap = StrokeCap.Round)
        drawLine(Lulu.Navy, p(0.38f, 0.78f), p(0.36f, 0.96f), strokeWidth = leg.width, cap = StrokeCap.Round)
        drawLine(Lulu.Navy, p(0.57f, 0.80f), p(0.56f, 0.97f), strokeWidth = leg.width, cap = StrokeCap.Round)

        val body = Path().apply {
            moveTo(0.14f * w, 0.80f * h)
            cubicTo(0.02f * w, 0.62f * h, 0.10f * w, 0.12f * h, 0.46f * w, 0.09f * h)
            cubicTo(0.78f * w, 0.06f * h, 0.94f * w, 0.40f * h, 0.92f * w, 0.70f * h)
            cubicTo(0.91f * w, 0.82f * h, 0.80f * w, 0.84f * h, 0.60f * w, 0.83f * h)
            cubicTo(0.42f * w, 0.82f * h, 0.24f * w, 0.86f * h, 0.14f * w, 0.80f * h)
            close()
        }
        drawPath(body, color)

        // Eyes look up and to the right, towards the letters and the sound.
        val look = when (mood) {
            Mood.Encourage -> Offset(0.012f, 0.02f)
            Mood.Happy -> Offset(0.02f, -0.035f)
            else -> Offset(0.03f, -0.03f)
        }
        drawEye(p(0.45f, 0.36f), 0.105f * w, eyeOpen, look * w)
        drawEye(p(0.66f, 0.30f), 0.12f * w, eyeOpen, look * w)

        when (mood) {
            Mood.Happy -> {
                val mouth = Path().apply {
                    moveTo(0.44f * w, 0.50f * h)
                    cubicTo(0.47f * w, 0.62f * h, 0.62f * w, 0.62f * h, 0.66f * w, 0.48f * h)
                    cubicTo(0.59f * w, 0.50f * h, 0.51f * w, 0.51f * h, 0.44f * w, 0.50f * h)
                    close()
                }
                drawPath(mouth, Color(0xFF1F2236))
            }
            Mood.Encourage -> drawCircle(Color(0xFF1F2236), 0.03f * w, p(0.55f, 0.53f))
            Mood.Sleepy -> Unit
            else -> drawArc(
                color = Color(0xFF1F2236),
                startAngle = 20f, sweepAngle = 120f, useCenter = false,
                topLeft = p(0.47f, 0.44f), size = Size(0.13f * w, 0.09f * h),
                style = Stroke(width = 0.022f * w, cap = StrokeCap.Round),
            )
        }

        if (mood == Mood.Listening || mood == Mood.Happy) {
            val grow = if (mood == Mood.Happy) 1.1f else 0.92f + 0.12f * linesPulse
            drawExcitement(p(0.74f, 0.26f), 0.17f * w * grow, linesColor)
        }
        if (mood == Mood.Sleepy) {
            drawSleepyZ(p(0.86f, 0.10f), 0.07f * w)
        }
    }
}

private fun DrawScope.drawEye(center: Offset, radius: Float, open: Float, look: Offset) {
    val oval = Rect(
        center.x - radius, center.y - radius * open,
        center.x + radius, center.y + radius * open,
    )
    drawOval(Lulu.Cream, oval.topLeft, oval.size)
    if (open > 0.3f) {
        drawCircle(Lulu.Navy, radius * 0.46f, center + look)
    } else {
        drawLine(Lulu.Navy, Offset(oval.left + radius * 0.3f, center.y), Offset(oval.right - radius * 0.3f, center.y), radius * 0.18f, StrokeCap.Round)
    }
}

/** Three short rays leaving the top-right of the head, like on the logo. */
private fun DrawScope.drawExcitement(center: Offset, reach: Float, color: Color) {
    val stroke = reach * 0.3f
    for ((i, degrees) in listOf(-78f, -44f, -10f).withIndex()) {
        val rad = Math.toRadians(degrees.toDouble())
        val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
        drawLine(color, center + dir * reach, center + dir * reach * (1.75f - 0.12f * i), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.drawSleepyZ(origin: Offset, s: Float) {
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        lineTo(origin.x + s, origin.y)
        lineTo(origin.x, origin.y + s)
        lineTo(origin.x + s, origin.y + s)
    }
    drawPath(path, Lulu.Navy.copy(alpha = 0.6f), style = Stroke(width = s * 0.22f, cap = StrokeCap.Round))
}
