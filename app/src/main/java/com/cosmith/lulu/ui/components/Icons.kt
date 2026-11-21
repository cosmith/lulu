package com.cosmith.lulu.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** Hand-drawn style icons with round ends, in the spirit of the mockups. */
enum class Glyph { Back, Next, Speaker, Gear, Play, Check, Cross, Mic, Stop, Trash, Plus, Minus, Home }

@Composable
fun LuluIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
        val line = Stroke(width = s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (glyph) {
            Glyph.Back, Glyph.Next -> {
                val flip = glyph == Glyph.Next
                fun x(v: Float) = if (flip) 1f - v else v
                val path = Path().apply {
                    moveTo(p(x(0.42f), 0.24f).x, p(0f, 0.24f).y)
                    lineTo(p(x(0.16f), 0.5f).x, p(0f, 0.5f).y)
                    lineTo(p(x(0.42f), 0.76f).x, p(0f, 0.76f).y)
                    moveTo(p(x(0.18f), 0.5f).x, p(0f, 0.5f).y)
                    lineTo(p(x(0.84f), 0.5f).x, p(0f, 0.5f).y)
                }
                drawPath(path, color, style = line)
            }

            Glyph.Speaker -> {
                val body = Path().apply {
                    moveTo(p(0.08f, 0.38f).x, p(0.08f, 0.38f).y)
                    lineTo(p(0.26f, 0.38f).x, p(0.26f, 0.38f).y)
                    lineTo(p(0.50f, 0.17f).x, p(0.50f, 0.17f).y)
                    lineTo(p(0.50f, 0.83f).x, p(0.50f, 0.83f).y)
                    lineTo(p(0.26f, 0.62f).x, p(0.26f, 0.62f).y)
                    lineTo(p(0.08f, 0.62f).x, p(0.08f, 0.62f).y)
                    close()
                }
                drawPath(body, color)
                drawPath(body, color, style = Stroke(width = s * 0.08f, join = StrokeJoin.Round))
                val wave = Stroke(width = s * 0.075f, cap = StrokeCap.Round)
                drawArc(color, -50f, 100f, false, p(0.44f, 0.34f), Size(s * 0.24f, s * 0.32f), style = wave)
                drawArc(color, -55f, 110f, false, p(0.46f, 0.20f), Size(s * 0.42f, s * 0.60f), style = wave)
            }

            Glyph.Gear -> {
                val c = p(0.5f, 0.5f)
                for (i in 0 until 8) {
                    rotate(i * 45f, c) {
                        drawRoundRect(
                            color, p(0.42f, 0.08f), Size(s * 0.16f, s * 0.2f),
                            CornerRadius(s * 0.04f),
                        )
                    }
                }
                // A ring rather than a filled disc, so the hole shows whatever is behind.
                drawCircle(color, s * 0.205f, c, style = Stroke(width = s * 0.19f))
            }

            Glyph.Play -> {
                val tri = Path().apply {
                    moveTo(p(0.30f, 0.18f).x, p(0.30f, 0.18f).y)
                    lineTo(p(0.84f, 0.50f).x, p(0.84f, 0.50f).y)
                    lineTo(p(0.30f, 0.82f).x, p(0.30f, 0.82f).y)
                    close()
                }
                drawPath(tri, color)
                drawPath(tri, color, style = Stroke(width = s * 0.12f, join = StrokeJoin.Round))
            }

            Glyph.Check -> {
                val path = Path().apply {
                    moveTo(p(0.18f, 0.52f).x, p(0.18f, 0.52f).y)
                    lineTo(p(0.42f, 0.74f).x, p(0.42f, 0.74f).y)
                    lineTo(p(0.82f, 0.28f).x, p(0.82f, 0.28f).y)
                }
                drawPath(path, color, style = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            Glyph.Cross -> {
                val st = s * 0.13f
                drawLine(color, p(0.24f, 0.24f), p(0.76f, 0.76f), st, StrokeCap.Round)
                drawLine(color, p(0.76f, 0.24f), p(0.24f, 0.76f), st, StrokeCap.Round)
            }

            Glyph.Plus -> {
                val st = s * 0.13f
                drawLine(color, p(0.5f, 0.2f), p(0.5f, 0.8f), st, StrokeCap.Round)
                drawLine(color, p(0.2f, 0.5f), p(0.8f, 0.5f), st, StrokeCap.Round)
            }

            Glyph.Minus -> drawLine(color, p(0.2f, 0.5f), p(0.8f, 0.5f), s * 0.13f, StrokeCap.Round)

            Glyph.Mic -> {
                drawRoundRect(color, p(0.36f, 0.08f), Size(s * 0.28f, s * 0.48f), CornerRadius(s * 0.14f))
                drawArc(color, 0f, 180f, false, p(0.22f, 0.26f), Size(s * 0.56f, s * 0.44f), style = Stroke(s * 0.08f, cap = StrokeCap.Round))
                drawLine(color, p(0.5f, 0.70f), p(0.5f, 0.88f), s * 0.08f, StrokeCap.Round)
                drawLine(color, p(0.34f, 0.89f), p(0.66f, 0.89f), s * 0.08f, StrokeCap.Round)
            }

            Glyph.Stop -> drawRoundRect(color, p(0.24f, 0.24f), Size(s * 0.52f, s * 0.52f), CornerRadius(s * 0.1f))

            Glyph.Trash -> {
                val st = s * 0.08f
                drawLine(color, p(0.18f, 0.24f), p(0.82f, 0.24f), st, StrokeCap.Round)
                drawLine(color, p(0.40f, 0.14f), p(0.60f, 0.14f), st, StrokeCap.Round)
                val bin = Path().apply {
                    moveTo(p(0.26f, 0.32f).x, p(0.26f, 0.32f).y)
                    lineTo(p(0.31f, 0.86f).x, p(0.31f, 0.86f).y)
                    lineTo(p(0.69f, 0.86f).x, p(0.69f, 0.86f).y)
                    lineTo(p(0.74f, 0.32f).x, p(0.74f, 0.32f).y)
                }
                drawPath(bin, color, style = Stroke(st, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            Glyph.Home -> {
                val house = Path().apply {
                    moveTo(p(0.14f, 0.48f).x, p(0.14f, 0.48f).y)
                    lineTo(p(0.50f, 0.16f).x, p(0.50f, 0.16f).y)
                    lineTo(p(0.86f, 0.48f).x, p(0.86f, 0.48f).y)
                    moveTo(p(0.24f, 0.42f).x, p(0.24f, 0.42f).y)
                    lineTo(p(0.24f, 0.84f).x, p(0.24f, 0.84f).y)
                    lineTo(p(0.76f, 0.84f).x, p(0.76f, 0.84f).y)
                    lineTo(p(0.76f, 0.42f).x, p(0.76f, 0.42f).y)
                }
                drawPath(house, color, style = line)
            }
        }
    }
}

/** Dashed ring, used to show a slot that can be tapped to add something. */
fun dashed(width: Float) = Stroke(width = width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 2.5f, width * 2f)))
