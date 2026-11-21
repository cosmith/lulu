package com.cosmith.lulu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmith.lulu.ui.theme.Fredoka
import com.cosmith.lulu.ui.theme.Lulu
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Clickable without ripple: the element squashes under the finger and springs back. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, pressedScale: Float = 0.88f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 700f),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Round button with a soft fill, like the back and settings buttons of the mockups. */
@Composable
fun RoundButton(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    fill: Color = Lulu.Sand,
    tint: Color = Lulu.Navy,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .bouncyClick(enabled, onClick = onClick)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        LuluIcon(glyph, tint, Modifier.size(size * 0.5f))
    }
}

/** Soft green hill at the bottom of the screen. */
@Composable
fun Hill(modifier: Modifier = Modifier, color: Color = Lulu.Sage.copy(alpha = 0.9f)) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.45f)
            cubicTo(size.width * 0.3f, size.height * 0.05f, size.width * 0.7f, size.height * 0.25f, size.width, size.height * 0.35f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color)
    }
}

/** A grapheme as big as the space allows. */
@Composable
fun Grapheme(text: String, color: Color, modifier: Modifier = Modifier, fill: Float = 0.86f) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val base = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 100.sp, lineHeight = 100.sp)
        val measured = remember(text) { measurer.measure(text, base).size }
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val scale = min(wPx * fill / measured.width, hPx * fill / measured.height)
        Text(
            text = text,
            color = color,
            textAlign = TextAlign.Center,
            softWrap = false,
            style = base.copy(fontSize = 100.sp * scale, lineHeight = 100.sp * scale),
        )
    }
}

/**
 * An organic pebble, like the hand-cut shapes behind the letters in the mockups.
 * The same [seed] always gives the same pebble.
 */
class PebbleShape(private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rnd = Random(seed)
        val n = 7
        val start = rnd.nextFloat() * 2f * PI.toFloat()
        val points = List(n) { i ->
            val a = start + i * 2f * PI.toFloat() / n
            val r = 0.9f + rnd.nextFloat() * 0.1f
            Offset(size.width / 2 + cos(a) * r * size.width / 2, size.height / 2 + sin(a) * r * size.height / 2)
        }
        // Closed Catmull-Rom spline through the points, as cubic Béziers.
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            val c1 = p1 + (p2 - p0) / 6f * 1.15f
            val c2 = p2 - (p3 - p1) / 6f * 1.15f
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Remembers an [Animatable] that shakes gently left and right each time [key] changes. */
@Composable
fun rememberShake(key: Int): Animatable<Float, *> {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == 0) return@LaunchedEffect
        for (amp in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(amp, tween(55))
    }
    return shake
}

/** A short burst of round confetti from [origin] (in this composable's coordinates) each time [key] changes. */
@Composable
fun Confetti(key: Int, origin: Offset, modifier: Modifier = Modifier, count: Int = 22) {
    val t = remember { Animatable(1f) }
    val particles = remember(key) {
        val rnd = Random(key * 7919 + 13)
        List(count) {
            val angle = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = 0.5f + rnd.nextFloat() * 0.7f
            Particle(
                velocity = Offset(cos(angle) * speed, sin(angle) * speed - 0.45f),
                color = (Lulu.pebbles + Lulu.Navy)[rnd.nextInt(Lulu.pebbles.size + 1)],
                radius = 0.6f + rnd.nextFloat() * 0.6f,
            )
        }
    }
    LaunchedEffect(key) {
        if (key == 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(750, easing = LinearEasing))
    }
    Canvas(modifier) {
        val progress = t.value
        if (progress >= 1f) return@Canvas
        val reach = size.minDimension * 0.55f
        val gravity = size.minDimension * 0.9f
        for (p in particles) {
            val pos = origin + p.velocity * reach * progress + Offset(0f, gravity * progress * progress * 0.5f)
            drawCircle(p.color.copy(alpha = 1f - progress * progress), radius = 9.dp.toPx() * p.radius * (1f - progress * 0.4f), center = pos)
        }
    }
}

private class Particle(val velocity: Offset, val color: Color, val radius: Float)
