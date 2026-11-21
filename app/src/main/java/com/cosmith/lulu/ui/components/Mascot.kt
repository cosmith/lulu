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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.cosmith.lulu.ui.theme.Lulu
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class Mood {
    /** Breathes, blinks and shows its own little habit. */
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

enum class MouthStyle { Smile, Grin, Oh, Smirk, Toothy, Cat }

enum class Extra { None, Cheeks, Freckles, Tuft, Sprout, Antenna }

/** What the mascot does when nothing happens. */
enum class Tic { LooksAround, Hops, Wiggles, Sways, Bouncy }

/**
 * A mascot's personality: its silhouette, face, one small detail and a habit.
 * Each child gets their own, drawn at random when the profile is created;
 * the same seed always gives the same mascot.
 */
data class MascotLook(
    val width: Float = 1f,
    val height: Float = 1f,
    /** -1 leans left, 1 leans right. */
    val lean: Float = 0f,
    /** -1 pointed gumdrop, 1 boxy. */
    val shoulders: Float = 0f,
    val eyeSize: Float = 1f,
    val eyeGap: Float = 1f,
    val eyeLift: Float = 0f,
    /** 1: the right eye sits higher, like on the logo; 0: level. */
    val eyeTilt: Float = 1f,
    /** How much bigger the right eye is than the left one. */
    val eyeMismatch: Float = 0.13f,
    val pupil: Float = 0.46f,
    val mouth: MouthStyle = MouthStyle.Smile,
    val extra: Extra = Extra.None,
    val legLength: Float = 1f,
    val legSpread: Float = 1f,
    val legSlant: Float = 0f,
    val tic: Tic = Tic.LooksAround,
    val blinkMs: Int = 3800,
    val breathMs: Int = 2400,
) {
    companion object {
        /** Lulu as on the logo. */
        val Lulu = MascotLook(mouth = MouthStyle.Grin, legSlant = -0.6f)

        fun from(seed: Long): MascotLook {
            val r = Random(seed)
            fun f(a: Float, b: Float) = a + r.nextFloat() * (b - a)
            return MascotLook(
                width = f(0.88f, 1.04f),
                height = f(0.86f, 1.07f),
                lean = f(-1f, 1f),
                shoulders = f(-1f, 1f),
                eyeSize = f(0.82f, 1.2f),
                eyeGap = f(0.82f, 1.15f),
                eyeLift = f(-0.04f, 0.04f),
                eyeTilt = f(-0.3f, 1.3f),
                eyeMismatch = f(0f, 0.26f),
                pupil = f(0.36f, 0.56f),
                mouth = MouthStyle.entries[r.nextInt(MouthStyle.entries.size)],
                extra = Extra.entries[r.nextInt(Extra.entries.size)],
                legLength = f(0.85f, 1.12f),
                legSpread = f(0.82f, 1.18f),
                legSlant = f(-1f, 1f),
                tic = Tic.entries[r.nextInt(Tic.entries.size)],
                blinkMs = r.nextInt(2800, 5600),
                breathMs = r.nextInt(1800, 3200),
            )
        }
    }
}

/**
 * A mascot: a soft gumdrop with big eyes, thick legs and a painted texture, in the
 * style of the logo. [jumpKey] makes it jump each time it changes.
 */
@Composable
fun Mascot(
    color: Color,
    mood: Mood,
    modifier: Modifier = Modifier,
    look: MascotLook = MascotLook.Lulu,
    jumpKey: Int = 0,
) {
    val loop = rememberInfiniteTransition(label = "mascot")
    val breathMs = if (look.tic == Tic.Bouncy) (look.breathMs * 0.55f).toInt() else look.breathMs
    val breath by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(breathMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val blink by loop.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = look.blinkMs
                1f at 0
                1f at look.blinkMs - 300
                0.1f at look.blinkMs - 220
                1f at look.blinkMs - 100
            },
        ),
        label = "blink",
    )
    val habit by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(habitPeriod(look.tic), easing = LinearEasing)),
        label = "habit",
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

    val calm = mood == Mood.Idle || mood == Mood.Listening
    val habitMove = if (calm) habitMotion(look.tic, habit) else HabitMotion()
    val eyeOpen = if (mood == Mood.Sleepy) 0.08f else blink
    val linesColor = if (color == Lulu.Mustard) Lulu.Terracotta else Lulu.Mustard
    val shade = lerp(color, Lulu.Navy, 0.35f)

    Canvas(
        modifier.graphicsLayer {
            val amplitude = if (look.tic == Tic.Bouncy && calm) 0.045f else 0.02f
            val breathe = breath * if (mood == Mood.Sleepy) 0.03f else amplitude
            val sq = squash.value * 0.12f
            scaleY = 1f + breathe - sq
            scaleX = 1f - breathe * 0.6f + sq * 0.8f
            translationY = -jump.value * size.height * 0.22f - habitMove.hop * size.height
            rotationZ = lean * 7f * (0.6f + 0.4f * sway) + habitMove.tilt
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    ) {
        val g = MascotGeometry(look, size.width, size.height)
        val w = size.width

        // Legs first, so the body sits on top of them.
        val legWidth = 0.088f * w
        for ((start, end) in g.legs()) {
            drawLine(Lulu.Navy, start, end, legWidth, StrokeCap.Round)
            drawLine(paintBrush, start, end, legWidth, StrokeCap.Round)
        }

        val body = g.body()
        drawPath(body, color)
        drawPath(body, paintBrush)

        drawExtraBehindFace(look.extra, g, w, color, shade)

        val gaze = when (mood) {
            Mood.Encourage -> Offset(0.012f, 0.02f)
            Mood.Happy -> Offset(0.02f, -0.035f)
            else -> Offset(0.03f + habitMove.gaze * 0.04f, -0.03f)
        }
        drawEye(g.leftEye, g.leftEyeRadius, eyeOpen, gaze * w, look.pupil)
        drawEye(g.rightEye, g.rightEyeRadius, eyeOpen, gaze * w, look.pupil)

        drawExtraOnFace(look.extra, g, w, shade)

        val mouthStyle = when (mood) {
            Mood.Happy -> if (look.mouth == MouthStyle.Toothy) MouthStyle.Toothy else MouthStyle.Grin
            Mood.Encourage -> MouthStyle.Oh
            Mood.Sleepy -> null
            else -> look.mouth
        }
        if (mouthStyle != null) drawMouth(mouthStyle, g.mouth, w, big = mood == Mood.Happy)

        if (mood == Mood.Listening || mood == Mood.Happy) {
            val grow = if (mood == Mood.Happy) 1.1f else 0.92f + 0.12f * linesPulse
            drawExcitement(g.raysCenter, 0.17f * w * grow, linesColor)
        }
        if (mood == Mood.Sleepy) drawSleepyZ(g.zSpot, 0.07f * w)
    }
}

/** Where everything goes, for one personality, in pixels. */
private class MascotGeometry(private val look: MascotLook, private val w: Float, private val h: Float) {
    /** Bends the base drawing (in 0..1 units) to this mascot's silhouette. */
    fun t(x: Float, y: Float): Offset {
        val up = ((BOTTOM - y) / 0.74f).coerceIn(0f, 1.2f)
        val x1 = 0.5f + (x - 0.5f) * look.width + look.lean * 0.07f * up
        val y1 = BOTTOM - (BOTTOM - y) * look.height
        return Offset(x1 * w, y1 * h)
    }

    fun body(): Path {
        val s = look.shoulders
        fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val a = t(x1, y1); val b = t(x2, y2); val c = t(x3, y3)
            cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
        }
        return Path().apply {
            val start = t(0.14f, 0.80f)
            moveTo(start.x, start.y)
            c(0.02f - 0.05f * s, 0.62f - 0.1f * s, 0.10f - 0.06f * s, 0.12f, 0.46f, 0.09f)
            c(0.78f + 0.06f * s, 0.06f, 0.94f + 0.05f * s, 0.40f - 0.1f * s, 0.92f, 0.70f)
            c(0.91f, 0.82f, 0.80f, 0.84f, 0.60f, 0.83f)
            c(0.42f, 0.82f, 0.24f, 0.86f, 0.14f, 0.80f)
            close()
        }
    }

    private val eyesX = 0.555f
    private val eyesY = 0.33f - look.eyeLift
    private val eyeDx = 0.105f * look.eyeGap
    private val eyeDy = 0.03f * look.eyeTilt
    private val eyeR = 0.112f * look.eyeSize * w

    val leftEye = t(eyesX - eyeDx, eyesY + eyeDy)
    val rightEye = t(eyesX + eyeDx, eyesY - eyeDy)
    val leftEyeRadius = eyeR * (1f - look.eyeMismatch / 2)
    val rightEyeRadius = eyeR * (1f + look.eyeMismatch / 2)
    val mouth = t(eyesX, eyesY + 0.19f)
    val top = t(0.47f, 0.09f)
    val raysCenter = t(0.76f, 0.24f)
    val zSpot = t(0.86f, 0.10f)

    /** Two thick legs, a little apart and slanted, one slightly longer, like on the logo. */
    fun legs(): List<Pair<Offset, Offset>> {
        val center = (0.5f + (0.465f - 0.5f) * look.width) * w
        val half = 0.095f * look.legSpread * look.width * w
        val length = 0.155f * look.legLength * h
        val slant = look.legSlant * 0.035f * w
        return listOf(
            Offset(center - half, 0.78f * h) to Offset(center - half + slant, 0.78f * h + length),
            Offset(center + half, 0.80f * h) to Offset(center + half + slant, 0.81f * h + length),
        )
    }

    companion object {
        const val BOTTOM = 0.83f
    }
}

private class HabitMotion(val gaze: Float = 0f, val hop: Float = 0f, val tilt: Float = 0f)

private fun habitPeriod(tic: Tic) = when (tic) {
    Tic.LooksAround -> 6000
    Tic.Hops -> 3600
    Tic.Wiggles -> 3200
    Tic.Sways -> 2200
    Tic.Bouncy -> 1000
}

/** The mascot's habit at [phase] (0..1) of its cycle. */
private fun habitMotion(tic: Tic, phase: Float): HabitMotion = when (tic) {
    Tic.LooksAround -> {
        // Look left, back, right, back.
        fun bump(from: Float, to: Float): Float {
            if (phase !in from..to) return 0f
            val p = (phase - from) / (to - from)
            return sin(p * PI.toFloat()).let { it * it }
        }
        HabitMotion(gaze = -bump(0.15f, 0.45f) + bump(0.55f, 0.85f))
    }
    Tic.Hops -> HabitMotion(
        hop = if (phase in 0.78f..0.92f) sin((phase - 0.78f) / 0.14f * 2f * PI.toFloat()).coerceAtLeast(0f) * 0.06f else 0f,
    )
    Tic.Wiggles -> HabitMotion(
        tilt = if (phase > 0.82f) {
            val p = (phase - 0.82f) / 0.18f
            sin(p * 4f * PI.toFloat()) * 6f * (1f - p)
        } else 0f,
    )
    Tic.Sways -> HabitMotion(tilt = sin(phase * 2f * PI.toFloat()) * 3.5f)
    Tic.Bouncy -> HabitMotion()
}

private fun DrawScope.drawEye(center: Offset, radius: Float, open: Float, look: Offset, pupil: Float) {
    val oval = Rect(center.x - radius, center.y - radius * open, center.x + radius, center.y + radius * open)
    drawOval(Lulu.Cream, oval.topLeft, oval.size)
    if (open > 0.3f) {
        drawCircle(Lulu.Navy, radius * pupil, center + look * (radius / (0.112f * size.width)))
    } else {
        drawLine(Lulu.Navy, Offset(oval.left + radius * 0.3f, center.y), Offset(oval.right - radius * 0.3f, center.y), radius * 0.18f, StrokeCap.Round)
    }
}

private val Dark = Color(0xFF1F2236)

private fun DrawScope.drawMouth(style: MouthStyle, at: Offset, w: Float, big: Boolean) {
    val k = if (big) 1.15f else 1f
    val line = Stroke(width = 0.024f * w, cap = StrokeCap.Round)
    when (style) {
        MouthStyle.Smile -> drawArc(
            Dark, 20f, 140f, false,
            topLeft = Offset(at.x - 0.065f * w, at.y - 0.06f * w), size = Size(0.13f * w, 0.09f * w), style = line,
        )
        MouthStyle.Grin, MouthStyle.Toothy -> {
            val half = 0.075f * w * k
            val mouth = Path().apply {
                moveTo(at.x - half, at.y - 0.02f * w)
                cubicTo(at.x - half * 0.6f, at.y + 0.1f * w * k, at.x + half * 0.9f, at.y + 0.1f * w * k, at.x + half * 1.05f, at.y - 0.035f * w)
                cubicTo(at.x + half * 0.2f, at.y - 0.01f * w, at.x - half * 0.4f, at.y - 0.01f * w, at.x - half, at.y - 0.02f * w)
                close()
            }
            drawPath(mouth, Dark)
            if (style == MouthStyle.Toothy) {
                drawRect(Lulu.Cream, Offset(at.x - 0.022f * w, at.y - 0.016f * w), Size(0.036f * w, 0.03f * w))
            }
        }
        MouthStyle.Oh -> drawCircle(Dark, 0.03f * w * k, at)
        MouthStyle.Smirk -> {
            drawArc(
                Dark, 10f, 110f, false,
                topLeft = Offset(at.x - 0.05f * w, at.y - 0.065f * w), size = Size(0.12f * w, 0.09f * w), style = line,
            )
        }
        MouthStyle.Cat -> {
            val r = 0.032f * w
            drawArc(Dark, 0f, 180f, false, Offset(at.x - 2 * r, at.y - r), Size(2 * r, 2 * r), style = line)
            drawArc(Dark, 0f, 180f, false, Offset(at.x, at.y - r), Size(2 * r, 2 * r), style = line)
        }
    }
}

/** Details that sit on the head, drawn before the face. */
private fun DrawScope.drawExtraBehindFace(extra: Extra, g: MascotGeometry, w: Float, color: Color, shade: Color) {
    val top = g.top
    when (extra) {
        Extra.Tuft -> {
            val st = Stroke(width = 0.035f * w, cap = StrokeCap.Round)
            for (dx in listOf(-0.05f, 0f, 0.05f)) {
                val p = Path().apply {
                    moveTo(top.x + dx * w * 0.6f, top.y + 0.02f * w)
                    quadraticTo(top.x + dx * w * 1.2f, top.y - 0.06f * w, top.x + dx * w * 2.2f + 0.02f * w, top.y - 0.09f * w)
                }
                drawPath(p, shade, style = st)
            }
        }
        Extra.Sprout -> {
            val leaf = Color(0xFF5F7A52)
            drawLine(leaf, top + Offset(0f, 0.02f * w), top + Offset(0.01f * w, -0.08f * w), 0.025f * w, StrokeCap.Round)
            rotate(-35f, top + Offset(0.01f * w, -0.08f * w)) {
                drawOval(leaf, top + Offset(-0.07f * w, -0.105f * w), Size(0.08f * w, 0.045f * w))
            }
            rotate(30f, top + Offset(0.01f * w, -0.08f * w)) {
                drawOval(leaf, top + Offset(0.01f * w, -0.105f * w), Size(0.08f * w, 0.045f * w))
            }
        }
        Extra.Antenna -> {
            val tip = top + Offset(-0.06f * w, -0.13f * w)
            drawLine(Lulu.Navy, top + Offset(0f, 0.02f * w), tip, 0.025f * w, StrokeCap.Round)
            drawCircle(if (color == Lulu.Mustard) Lulu.Terracotta else Lulu.Mustard, 0.04f * w, tip)
        }
        else -> Unit
    }
}

/** Details on the face, drawn after the eyes. */
private fun DrawScope.drawExtraOnFace(extra: Extra, g: MascotGeometry, w: Float, shade: Color) {
    val leftCheek = g.leftEye + Offset(-0.03f * w, g.leftEyeRadius + 0.05f * w)
    val rightCheek = g.rightEye + Offset(0.05f * w, g.rightEyeRadius + 0.06f * w)
    when (extra) {
        Extra.Cheeks -> for (c in listOf(leftCheek, rightCheek)) {
            drawOval(Color(0xFFF07C6C).copy(alpha = 0.5f), c - Offset(0.045f * w, 0.025f * w), Size(0.09f * w, 0.05f * w))
        }
        Extra.Freckles -> for (c in listOf(leftCheek, rightCheek)) {
            for ((dx, dy) in listOf(-0.025f to 0f, 0f to 0.015f, 0.025f to 0f)) {
                drawCircle(shade.copy(alpha = 0.75f), 0.011f * w, c + Offset(dx * w, dy * w))
            }
        }
        else -> Unit
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

/**
 * A tile of short diagonal strokes and specks, light and dark, laid over the flat
 * colours to give them the brushed gouache look of the logo. Drawn once.
 */
private val paintTexture: ImageBitmap by lazy {
    val size = 192
    val image = ImageBitmap(size, size)
    val canvas = Canvas(image)
    val rnd = Random(20261121)
    val paint = Paint().apply { strokeCap = StrokeCap.Round; isAntiAlias = true }
    fun wrapped(draw: (Float, Float) -> Unit) {
        for (dx in listOf(-size, 0, size)) for (dy in listOf(-size, 0, size)) draw(dx.toFloat(), dy.toFloat())
    }
    repeat(900) {
        val light = rnd.nextFloat() < 0.55f
        paint.color = if (light) Color.White.copy(alpha = 0.035f + rnd.nextFloat() * 0.05f) else Color.Black.copy(alpha = 0.02f + rnd.nextFloat() * 0.035f)
        paint.strokeWidth = 1f + rnd.nextFloat() * 1.5f
        val x = rnd.nextFloat() * size
        val y = rnd.nextFloat() * size
        val angle = (-40f + rnd.nextFloat() * 25f) * PI.toFloat() / 180f
        val length = 4f + rnd.nextFloat() * 9f
        val end = Offset(cos(angle) * length, sin(angle) * length)
        wrapped { dx, dy -> canvas.drawLine(Offset(x + dx, y + dy), Offset(x + dx + end.x, y + dy + end.y), paint) }
    }
    repeat(900) {
        paint.color = if (rnd.nextBoolean()) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
        val x = rnd.nextFloat() * size
        val y = rnd.nextFloat() * size
        val r = 0.4f + rnd.nextFloat() * 0.8f
        wrapped { dx, dy -> canvas.drawCircle(Offset(x + dx, y + dy), r, paint) }
    }
    image
}

private val paintBrush: Brush by lazy { ShaderBrush(ImageShader(paintTexture, TileMode.Repeated, TileMode.Repeated)) }
