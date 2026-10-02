package com.pranvir.blotto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val tt = clamp01(t)
    val aa = (a ushr 24) and 255; val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
    val ba = (b ushr 24) and 255; val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
    val ra = (aa + (ba - aa) * tt).toInt(); val rr = (ar + (br - ar) * tt).toInt()
    val rg = (ag + (bg - ag) * tt).toInt(); val rb = (ab + (bb - ab) * tt).toInt()
    return (ra shl 24) or (rr shl 16) or (rg shl 8) or rb
}

fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
fun invertRgb(c: Int): Int = (c and 0xFF000000.toInt()) or (0x00FFFFFF - (c and 0x00FFFFFF))

/** Black & white palette. [inv] smoothly flips it to a film negative. */
object Pal {
    private const val N_INK = 0xFF131313.toInt()
    private const val N_PAPER = 0xFFE7E7E4.toInt()
    private const val N_PANEL = 0xFFF8F8F6.toInt()
    private const val N_G1 = 0xFFD2D2CF.toInt()
    private const val N_G2 = 0xFFA3A3A0.toInt()
    private const val N_G3 = 0xFF6B6B69.toInt()
    private const val N_G4 = 0xFF363635.toInt()

    var inv = 0f
        private set
    var ink = N_INK; var paper = N_PAPER; var panel = N_PANEL
    var g1 = N_G1; var g2 = N_G2; var g3 = N_G3; var g4 = N_G4

    fun set(v: Float) {
        inv = clamp01(v)
        ink = lerpColor(N_INK, invertRgb(N_INK), inv)
        paper = lerpColor(N_PAPER, invertRgb(N_PAPER), inv)
        panel = lerpColor(N_PANEL, invertRgb(N_PANEL), inv)
        g1 = lerpColor(N_G1, invertRgb(N_G1), inv)
        g2 = lerpColor(N_G2, invertRgb(N_G2), inv)
        g3 = lerpColor(N_G3, invertRgb(N_G3), inv)
        g4 = lerpColor(N_G4, invertRgb(N_G4), inv)
    }

    const val BLACK = 0xFF0C0C0C.toInt()
    const val WHITE = 0xFFF4F4F2.toInt()
}

object Fonts {
    var display: Typeface = Typeface.DEFAULT_BOLD
    var body: Typeface = Typeface.SERIF
}

/** All vector drawing lives here. Objects are pre-allocated so drawing never allocates. */
object Art {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val textStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val fm = Paint.FontMetrics()
    private val path = Path()
    private val path2 = Path()
    private val clip = Path()
    private val r1 = RectF()
    private val r2 = RectF()

    // ------------------------------------------------------------------ text

    fun textWidth(s: String, size: Float, face: Typeface): Float {
        text.typeface = face; text.textSize = size
        return text.measureText(s)
    }

    /** Draws text centred vertically on [cy]. */
    fun label(c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int, face: Typeface = Fonts.body,
              align: Paint.Align = Paint.Align.CENTER) {
        text.typeface = face; text.textSize = size; text.color = color; text.textAlign = align
        text.getFontMetrics(fm)
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, text)
        text.textAlign = Paint.Align.CENTER
    }

    /** Cartoon title text: solid drop shadow, thick outline, fill. */
    fun title(c: Canvas, s: String, x: Float, cy: Float, size: Float, fillCol: Int, lineCol: Int,
              outline: Float, shadow: Float, face: Typeface = Fonts.display) {
        text.typeface = face; text.textSize = size
        textStroke.typeface = face; textStroke.textSize = size
        text.getFontMetrics(fm)
        val base = cy - (fm.ascent + fm.descent) / 2f
        if (shadow != 0f) {
            textStroke.color = lineCol; textStroke.strokeWidth = outline * 2f
            c.drawText(s, x + shadow, base + shadow, textStroke)
            text.color = lineCol
            c.drawText(s, x + shadow, base + shadow, text)
        }
        textStroke.color = lineCol; textStroke.strokeWidth = outline * 2f
        c.drawText(s, x, base, textStroke)
        text.color = fillCol
        c.drawText(s, x, base, text)
    }

    /** Fits text into maxWidth by shrinking size. */
    fun fitSize(s: String, size: Float, maxWidth: Float, face: Typeface): Float {
        val w = textWidth(s, size, face)
        return if (w <= maxWidth || w <= 0f) size else size * maxWidth / w
    }

    // ------------------------------------------------------------------ backgrounds

    fun sunburst(c: Canvas, cx: Float, cy: Float, radius: Float, rays: Int, angle: Float, colA: Int, colB: Int) {
        fill.color = colA
        c.drawCircle(cx, cy, radius, fill)
        fill.color = colB
        r1.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val step = 360f / rays
        for (i in 0 until rays) c.drawArc(r1, angle + i * step, step * 0.5f, true, fill)
    }

    /** Art-deco frame: double line border with corner fans. */
    fun decoFrame(c: Canvas, r: RectF, u: Float, lineCol: Int, fillCol: Int, radius: Float) {
        fill.color = fillCol
        c.drawRoundRect(r, radius, radius, fill)
        stroke.color = lineCol
        stroke.strokeWidth = u * 0.9f
        c.drawRoundRect(r, radius, radius, stroke)
        r2.set(r.left + u * 1.8f, r.top + u * 1.8f, r.right - u * 1.8f, r.bottom - u * 1.8f)
        stroke.strokeWidth = u * 0.35f
        val ir = max(0f, radius - u * 1.8f)
        c.drawRoundRect(r2, ir, ir, stroke)
        // corner fans
        val fr = u * 4.2f
        fanCorner(c, r2.left, r2.top, fr, 0f, lineCol, u)
        fanCorner(c, r2.right, r2.top, fr, 90f, lineCol, u)
        fanCorner(c, r2.right, r2.bottom, fr, 180f, lineCol, u)
        fanCorner(c, r2.left, r2.bottom, fr, 270f, lineCol, u)
    }

    private fun fanCorner(c: Canvas, x: Float, y: Float, r: Float, rot: Float, col: Int, u: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        fill.color = col
        r1.set(-r, -r, r, r)
        c.drawArc(r1, 0f, 90f, true, fill)
        stroke.color = col
        stroke.strokeWidth = u * 0.3f
        r1.set(-r * 1.45f, -r * 1.45f, r * 1.45f, r * 1.45f)
        c.drawArc(r1, 5f, 80f, false, stroke)
        c.restore()
    }

    fun star(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean, lineCol: Int, fillCol: Int, emptyCol: Int, lw: Float) {
        path.reset()
        for (k in 0 until 10) {
            val a = -PI / 2 + k * PI / 5
            val rr = if (k % 2 == 0) r else r * 0.45f
            val px = cx + (cos(a) * rr).toFloat()
            val py = cy + (sin(a) * rr).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.color = if (filled) fillCol else emptyCol
        c.drawPath(path, fill)
        stroke.color = lineCol
        stroke.strokeWidth = lw
        c.drawPath(path, stroke)
    }

    // ------------------------------------------------------------------ pie-cut eye

    /** Classic 1930s pie-cut pupil. */
    fun pieEye(c: Canvas, cx: Float, cy: Float, ew: Float, eh: Float, lookX: Float, lookY: Float, blink: Float,
               whiteCol: Int, inkCol: Int, lw: Float) {
        val sy = max(0.08f, 1f - blink)
        r1.set(cx - ew, cy - eh * sy, cx + ew, cy + eh * sy)
        fill.color = whiteCol
        c.drawOval(r1, fill)
        stroke.color = inkCol; stroke.strokeWidth = lw
        c.drawOval(r1, stroke)
        if (sy < 0.3f) return
        val pw = ew * 0.62f
        val ph = eh * 0.62f * sy
        val px = cx + lookX * (ew - pw) * 0.9f
        val py = cy + lookY * (eh - ph / sy) * sy * 0.8f + eh * 0.12f * sy
        r2.set(px - pw, py - ph, px + pw, py + ph)
        fill.color = inkCol
        c.drawOval(r2, fill)
        fill.color = whiteCol
        c.drawArc(r2, -78f, 34f, true, fill)
    }

    // ------------------------------------------------------------------ pipe tile

    /**
     * Draws one tile in local coordinates (canvas already translated to the tile centre and rotated).
     * [mask] is the tile's solution mask (un-rotated), [inkAmt] 0..1, [inkDir] local dir the ink enters (-1 centre).
     */
    fun tile(c: Canvas, t: Float, mask: Int, kind: Int, inkAmt: Float, inkDir: Int, locked: Boolean,
             pressed: Boolean, time: Float, u: Float) {
        val h = t * 0.46f
        val rad = t * 0.13f
        // panel
        r1.set(-h, -h, h, h)
        fill.color = if (locked) Pal.g1 else Pal.panel
        c.drawRoundRect(r1, rad, rad, fill)
        stroke.color = Pal.ink
        stroke.strokeWidth = max(1.2f, t * 0.028f)
        c.drawRoundRect(r1, rad, rad, stroke)
        if (pressed) {
            fill.color = withAlpha(Pal.ink, 28)
            c.drawRoundRect(r1, rad, rad, fill)
        }

        val pw = t * 0.25f
        val ow = max(1.6f, t * 0.042f)

        // outline pass
        fill.color = Pal.ink
        armShapes(c, mask, h, pw / 2f + ow, fill, kind)
        // interior
        fill.color = Pal.panel
        armShapes(c, mask, h - ow * 0.2f, pw / 2f, fill, kind)
        // ink pass
        if (inkAmt > 0.001f) {
            c.save()
            if (inkAmt < 0.999f) {
                val ex: Float; val ey: Float
                if (inkDir in 0..3) { ex = Dir.DX[inkDir] * h; ey = Dir.DY[inkDir] * h } else { ex = 0f; ey = 0f }
                clip.reset()
                clip.addCircle(ex, ey, inkAmt * t * 1.2f, Path.Direction.CW)
                c.clipPath(clip)
            }
            fill.color = Pal.ink
            armShapes(c, mask, h - ow * 0.2f, pw / 2f, fill, kind)
            // glossy rubber highlight
            stroke.color = withAlpha(Pal.panel, 210)
            stroke.strokeWidth = pw * 0.16f
            for (d in 0..3) {
                if (mask and Dir.BIT[d] == 0) continue
                val px = -Dir.DY[d].toFloat()
                val py = Dir.DX[d].toFloat()
                val off = pw * 0.2f
                val s0 = pw * 0.75f
                val s1 = h - t * 0.13f
                c.drawLine(Dir.DX[d] * s0 + px * off, Dir.DY[d] * s0 + py * off,
                    Dir.DX[d] * s1 + px * off, Dir.DY[d] * s1 + py * off, stroke)
            }
            c.restore()
        }
        // couplings (pipe fittings near the tile edge)
        fill.color = Pal.ink
        val cw = pw / 2f + ow + t * 0.035f
        val cth = t * 0.045f
        val cpos = h - t * 0.085f
        for (d in 0..3) {
            if (mask and Dir.BIT[d] == 0) continue
            when (d) {
                0 -> r2.set(-cw, -cpos - cth, cw, -cpos + cth)
                1 -> r2.set(cpos - cth, -cw, cpos + cth, cw)
                2 -> r2.set(-cw, cpos - cth, cw, cpos + cth)
                else -> r2.set(-cpos - cth, -cw, -cpos + cth, cw)
            }
            c.drawRoundRect(r2, cth * 0.6f, cth * 0.6f, fill)
        }
        if (locked) rivets(c, h, t)
    }

    private fun armShapes(c: Canvas, mask: Int, h: Float, half: Float, p: Paint, kind: Int) {
        for (d in 0..3) {
            if (mask and Dir.BIT[d] == 0) continue
            when (d) {
                0 -> c.drawRect(-half, -h, half, 0f, p)
                1 -> c.drawRect(0f, -half, h, half, p)
                2 -> c.drawRect(-half, 0f, half, h, p)
                else -> c.drawRect(-h, -half, 0f, half, p)
            }
        }
        if (kind == KIND_PIPE) c.drawCircle(0f, 0f, half, p)
    }

    private fun rivets(c: Canvas, h: Float, t: Float) {
        val rr = t * 0.045f
        val o = h - t * 0.1f
        for (sx in -1..1 step 2) for (sy in -1..1 step 2) {
            fill.color = Pal.ink
            c.drawCircle(sx * o, sy * o, rr, fill)
            fill.color = Pal.panel
            c.drawCircle(sx * o - rr * 0.3f, sy * o - rr * 0.3f, rr * 0.35f, fill)
        }
    }

    const val KIND_PIPE = 0
    const val KIND_BULB = 1
    const val KIND_SOURCE = 2

    /** Hidden tile for "lights out" reels. */
    fun hiddenTile(c: Canvas, t: Float, time: Float, seed: Int) {
        val h = t * 0.46f
        val rad = t * 0.13f
        r1.set(-h, -h, h, h)
        fill.color = Pal.g4
        c.drawRoundRect(r1, rad, rad, fill)
        stroke.color = Pal.ink
        stroke.strokeWidth = max(1.2f, t * 0.028f)
        c.drawRoundRect(r1, rad, rad, stroke)
        r2.set(-h * 0.78f, -h * 0.78f, h * 0.78f, h * 0.78f)
        stroke.color = withAlpha(Pal.g3, 160)
        stroke.strokeWidth = max(1f, t * 0.018f)
        c.drawRoundRect(r2, rad * 0.7f, rad * 0.7f, stroke)
        val bob = sin(time * 2f + seed) * t * 0.03f
        label(c, "?", 0f, bob, t * 0.34f, withAlpha(Pal.g3, 220), Fonts.display)
    }

    /** Bulb head (upright, drawn after un-rotating). [base] is local screw direction in screen space (radians). */
    fun bulb(c: Canvas, t: Float, lit: Float, dance: Float, time: Float, pop: Float, u: Float, baseAngle: Float) {
        val gr = t * 0.24f * (1f + pop * 0.25f)
        val lw = max(1.4f, t * 0.03f)
        // screw base pointing towards the pipe
        c.save()
        c.rotate(baseAngle)
        fill.color = Pal.ink
        r1.set(-gr * 0.55f, gr * 0.45f, gr * 0.55f, gr * 1.25f)
        c.drawRoundRect(r1, gr * 0.15f, gr * 0.15f, fill)
        stroke.color = Pal.panel
        stroke.strokeWidth = lw * 0.7f
        c.drawLine(-gr * 0.45f, gr * 0.72f, gr * 0.45f, gr * 0.62f, stroke)
        c.drawLine(-gr * 0.45f, gr * 0.98f, gr * 0.45f, gr * 0.88f, stroke)
        c.restore()

        c.save()
        c.rotate(sin(time * 5.2f) * 9f * dance)
        // rays when lit
        if (lit > 0.01f) {
            stroke.color = withAlpha(Pal.ink, (255 * lit).toInt())
            stroke.strokeWidth = lw
            val rot = time * 1.2f
            for (k in 0 until 8) {
                val a = rot + k * (PI / 4).toFloat()
                val r0 = gr * 1.25f
                val r1v = gr * (1.55f + 0.18f * sin(time * 6f + k))
                c.drawLine(cos(a) * r0, sin(a) * r0, cos(a) * r1v, sin(a) * r1v, stroke)
            }
        }
        // glass
        fill.color = lerpColor(Pal.g1, Pal.panel, lit)
        c.drawCircle(0f, 0f, gr, fill)
        stroke.color = Pal.ink
        stroke.strokeWidth = lw
        c.drawCircle(0f, 0f, gr, stroke)
        // shine
        stroke.color = withAlpha(Pal.panel, 230)
        stroke.strokeWidth = lw * 0.9f
        r1.set(-gr * 0.72f, -gr * 0.72f, gr * 0.72f, gr * 0.72f)
        c.drawArc(r1, 200f, 50f, false, stroke)

        if (lit > 0.5f) {
            // wide awake: pie eyes + grin
            val ew = gr * 0.2f; val eh = gr * 0.3f
            pieEye(c, -gr * 0.3f, -gr * 0.18f, ew, eh, 0.2f, -0.3f, 0f, Pal.panel, Pal.ink, lw * 0.6f)
            pieEye(c, gr * 0.3f, -gr * 0.18f, ew, eh, 0.2f, -0.3f, 0f, Pal.panel, Pal.ink, lw * 0.6f)
            path.reset()
            path.moveTo(-gr * 0.42f, gr * 0.22f)
            path.quadTo(0f, gr * 0.85f, gr * 0.42f, gr * 0.22f)
            path.quadTo(0f, gr * 0.42f, -gr * 0.42f, gr * 0.22f)
            path.close()
            fill.color = Pal.ink
            c.drawPath(path, fill)
        } else {
            // sleepy
            stroke.color = Pal.ink
            stroke.strokeWidth = lw * 0.8f
            r1.set(-gr * 0.45f, -gr * 0.3f, -gr * 0.12f, -gr * 0.02f)
            c.drawArc(r1, 20f, 140f, false, stroke)
            r1.set(gr * 0.12f, -gr * 0.3f, gr * 0.45f, -gr * 0.02f)
            c.drawArc(r1, 20f, 140f, false, stroke)
            fill.color = Pal.ink
            c.drawCircle(0f, gr * 0.38f, gr * (0.09f + 0.03f * sin(time * 2.4f)), fill)
        }
        c.restore()
        if (lit < 0.5f) {
            // floating Zzz
            val zt = (time * 0.5f) % 1f
            val a = (255 * sin(zt * PI).toFloat()).toInt()
            label(c, "z", gr * (0.8f + zt * 0.5f), -gr * (0.9f + zt * 1.1f), t * (0.13f + zt * 0.08f), withAlpha(Pal.ink, a), Fonts.display)
        }
    }

    /** The inkwell at the source tile (upright). */
    fun inkwell(c: Canvas, t: Float, time: Float, happy: Float, u: Float) {
        val s = t * 0.5f
        val lw = max(1.4f, t * 0.03f)
        val bounce = 1f + 0.05f * sin(time * PI.toFloat() * 2f / 0.6f)
        // plate so the inkwell stands out from the inked pipes
        fill.color = Pal.panel
        c.drawCircle(0f, 0f, t * 0.36f, fill)
        stroke.color = Pal.ink
        stroke.strokeWidth = lw
        c.drawCircle(0f, 0f, t * 0.36f, stroke)
        c.save()
        c.scale(1f / bounce, bounce, 0f, s * 0.55f)
        // jar body
        path.reset()
        path.moveTo(-s * 0.55f, -s * 0.05f)
        path.quadTo(-s * 0.72f, s * 0.55f, -s * 0.45f, s * 0.58f)
        path.lineTo(s * 0.45f, s * 0.58f)
        path.quadTo(s * 0.72f, s * 0.55f, s * 0.55f, -s * 0.05f)
        path.quadTo(0f, -s * 0.2f, -s * 0.55f, -s * 0.05f)
        path.close()
        fill.color = Pal.ink
        c.drawPath(path, fill)
        // neck and rim
        r1.set(-s * 0.3f, -s * 0.34f, s * 0.3f, -s * 0.06f)
        c.drawRoundRect(r1, s * 0.06f, s * 0.06f, fill)
        r1.set(-s * 0.4f, -s * 0.46f, s * 0.4f, -s * 0.3f)
        c.drawRoundRect(r1, s * 0.08f, s * 0.08f, fill)
        stroke.color = Pal.panel
        stroke.strokeWidth = lw * 0.8f
        c.drawLine(-s * 0.3f, -s * 0.38f, s * 0.3f, -s * 0.38f, stroke)
        // label face
        r1.set(-s * 0.4f, s * 0.02f, s * 0.4f, s * 0.48f)
        fill.color = Pal.panel
        c.drawRoundRect(r1, s * 0.12f, s * 0.12f, fill)
        val ew = s * 0.09f; val eh = s * 0.14f
        val lx = sin(time * 0.9f) * 0.6f
        pieEye(c, -s * 0.14f, s * 0.17f, ew, eh, lx, 0f, blinkAt(time, 1.3f), Pal.panel, Pal.ink, lw * 0.6f)
        pieEye(c, s * 0.14f, s * 0.17f, ew, eh, lx, 0f, blinkAt(time, 1.3f), Pal.panel, Pal.ink, lw * 0.6f)
        path.reset()
        val mw = s * (0.16f + 0.06f * happy)
        path.moveTo(-mw, s * 0.33f)
        path.quadTo(0f, s * (0.46f + 0.06f * happy), mw, s * 0.33f)
        stroke.color = Pal.ink
        stroke.strokeWidth = lw
        c.drawPath(path, stroke)
        // shine on the glass
        stroke.color = withAlpha(Pal.panel, 200)
        stroke.strokeWidth = lw
        c.drawLine(s * 0.5f, s * 0.05f, s * 0.53f, s * 0.3f, stroke)
        c.restore()
    }

    /** Returns 0..1 eyelid closure: quick blink every few seconds. */
    fun blinkAt(time: Float, offset: Float): Float {
        val period = 3.7f
        val ph = ((time + offset) % period + period) % period
        return if (ph < 0.14f) sin(ph / 0.14f * PI.toFloat()) else 0f
    }

    // ------------------------------------------------------------------ Blotto, the mascot

    const val MOOD_IDLE = 0
    const val MOOD_HAPPY = 1
    const val MOOD_SAD = 2
    const val MOOD_WORRY = 3

    /**
     * Blotto: a rubber-hose ink-drop fellow. (cx, cy) is the centre of the round belly, r its radius.
     * lookX/lookY in -1..1.
     */
    fun blotto(c: Canvas, cx: Float, cy: Float, r: Float, time: Float, mood: Int, lookX: Float, lookY: Float,
               jump: Float = 0f) {
        val beat = time * PI.toFloat() * 2f / 0.6f
        val happy = mood == MOOD_HAPPY
        val bounceAmt = if (happy) 1f else 0.5f
        val hop = if (happy) abs(sin(beat / 2f)) * r * 0.35f else 0f
        val squash = 1f + 0.06f * sin(beat) * bounceAmt
        val lw = max(1.5f, r * 0.07f)
        val ink = Pal.ink
        val white = Pal.panel

        // shadow
        fill.color = withAlpha(Pal.g3, 90)
        val shw = r * (0.95f - hop / r * 0.4f)
        r1.set(cx - shw, cy + r * 1.58f, cx + shw, cy + r * 1.78f)
        c.drawOval(r1, fill)

        c.save()
        c.translate(cx, cy - hop - jump)
        // legs
        stroke.color = ink
        stroke.strokeWidth = r * 0.15f
        val legSwing = if (happy) sin(beat) * r * 0.18f else 0f
        for (s in -1..1 step 2) {
            path.reset()
            path.moveTo(s * r * 0.35f, r * 0.85f)
            path.quadTo(s * r * 0.55f + legSwing * s, r * 1.2f, s * r * 0.42f + legSwing, r * 1.5f)
            c.drawPath(path, stroke)
            // shoe
            fill.color = ink
            r2.set(s * r * 0.42f + legSwing - (if (s < 0) r * 0.5f else r * 0.12f), r * 1.4f,
                s * r * 0.42f + legSwing + (if (s < 0) r * 0.12f else r * 0.5f), r * 1.7f)
            c.drawOval(r2, fill)
            fill.color = withAlpha(white, 200)
            c.drawCircle(s * r * 0.42f + legSwing + s * r * 0.22f, r * 1.49f, r * 0.05f, fill)
        }

        c.scale(1f / squash, squash, 0f, r * 1.2f)

        // arms (behind body if idle hanging, in front if waving)
        val waveL: Float; val waveR: Float
        when (mood) {
            MOOD_HAPPY -> { waveL = -1f; waveR = -1f }
            MOOD_SAD -> { waveL = 0.6f; waveR = 0.6f }
            MOOD_WORRY -> { waveL = -0.2f; waveR = 0.5f }
            else -> { waveL = 0.35f; waveR = 0.35f }
        }
        for (s in -1..1 step 2) {
            val w = if (s < 0) waveL else waveR
            val swing = sin(beat + s) * (if (happy) 0.35f else 0.12f)
            val hx = s * r * (1.35f + 0.1f * swing)
            val hy = r * (0.15f + w * 0.75f + swing)
            val sxp = s * r * 0.82f
            val syp = r * 0.1f
            stroke.color = ink
            stroke.strokeWidth = r * 0.15f
            path.reset()
            path.moveTo(sxp, syp)
            path.quadTo(s * r * 1.35f, syp + (hy - syp) * 0.1f - r * 0.25f * (if (w < 0) 1f else -0.3f), hx, hy)
            c.drawPath(path, stroke)
            glove(c, hx, hy, r * 0.25f, lw, s, time)
        }

        // body: an ink drop with a curly tip
        val sway = sin(time * 1.7f) * 0.18f
        path.reset()
        path.moveTo(r * sway, -r * 2.0f)
        path.cubicTo(r * (0.3f + sway * 0.5f), -r * 1.45f, r, -r * 0.8f, r, 0f)
        path.cubicTo(r, r * 0.62f, r * 0.56f, r * 1.02f, 0f, r * 1.02f)
        path.cubicTo(-r * 0.56f, r * 1.02f, -r, r * 0.62f, -r, 0f)
        path.cubicTo(-r, -r * 0.8f, -r * (0.3f - sway * 0.5f), -r * 1.45f, r * sway, -r * 2.0f)
        path.close()
        fill.color = ink
        c.drawPath(path, fill)
        // curl on the tip
        stroke.color = ink
        stroke.strokeWidth = r * 0.12f
        path2.reset()
        path2.moveTo(r * sway, -r * 1.98f)
        path2.quadTo(r * (sway + 0.35f), -r * 2.35f, r * (sway + 0.4f), -r * 2.05f)
        path2.quadTo(r * (sway + 0.42f), -r * 1.85f, r * (sway + 0.22f), -r * 1.9f)
        c.drawPath(path2, stroke)
        // gloss
        stroke.color = withAlpha(white, 150)
        stroke.strokeWidth = r * 0.09f
        r1.set(-r * 0.86f, -r * 0.9f, r * 0.86f, r * 0.9f)
        c.drawArc(r1, 195f, 45f, false, stroke)

        // face mask
        path.reset()
        path.moveTo(0f, -r * 0.45f)
        path.cubicTo(r * 0.25f, -r * 0.78f, r * 0.8f, -r * 0.72f, r * 0.78f, -r * 0.1f)
        path.cubicTo(r * 0.82f, r * 0.45f, r * 0.45f, r * 0.72f, 0f, r * 0.72f)
        path.cubicTo(-r * 0.45f, r * 0.72f, -r * 0.82f, r * 0.45f, -r * 0.78f, -r * 0.1f)
        path.cubicTo(-r * 0.8f, -r * 0.72f, -r * 0.25f, -r * 0.78f, 0f, -r * 0.45f)
        path.close()
        fill.color = white
        c.drawPath(path, fill)

        // eyes
        val blink = if (mood == MOOD_SAD) 0.35f else blinkAt(time, 0f)
        val ew = r * 0.2f; val eh = r * 0.32f
        pieEye(c, -r * 0.27f, -r * 0.3f, ew, eh, lookX, lookY, blink, white, ink, lw * 0.8f)
        pieEye(c, r * 0.27f, -r * 0.3f, ew, eh, lookX, lookY, blink, white, ink, lw * 0.8f)
        // brows for worry/sad
        if (mood == MOOD_SAD || mood == MOOD_WORRY) {
            stroke.color = ink
            stroke.strokeWidth = lw
            c.drawLine(-r * 0.45f, -r * 0.72f, -r * 0.12f, -r * 0.82f, stroke)
            c.drawLine(r * 0.45f, -r * 0.72f, r * 0.12f, -r * 0.82f, stroke)
        }
        // nose
        fill.color = ink
        r1.set(-r * 0.11f, -r * 0.02f, r * 0.11f, r * 0.13f)
        c.drawOval(r1, fill)
        fill.color = withAlpha(white, 220)
        c.drawCircle(-r * 0.04f, r * 0.02f, r * 0.03f, fill)
        // mouth
        path.reset()
        when (mood) {
            MOOD_SAD -> {
                stroke.color = ink; stroke.strokeWidth = lw * 1.1f
                path.moveTo(-r * 0.3f, r * 0.5f)
                path.quadTo(0f, r * 0.28f, r * 0.3f, r * 0.5f)
                c.drawPath(path, stroke)
            }
            MOOD_WORRY -> {
                fill.color = ink
                r1.set(-r * 0.1f, r * 0.28f, r * 0.1f, r * 0.5f)
                c.drawOval(r1, fill)
            }
            else -> {
                val open = if (happy) 0.62f + 0.08f * sin(beat) else 0.5f
                path.moveTo(-r * 0.42f, r * 0.2f)
                path.quadTo(0f, r * open * 1.25f, r * 0.42f, r * 0.2f)
                path.quadTo(0f, r * 0.34f, -r * 0.42f, r * 0.2f)
                path.close()
                fill.color = ink
                c.drawPath(path, fill)
                // tongue
                c.save()
                c.clipPath(path)
                fill.color = Pal.g2
                c.drawCircle(0f, r * (open * 0.95f), r * 0.2f, fill)
                c.restore()
                // cheek dimples
                stroke.color = ink; stroke.strokeWidth = lw * 0.7f
                c.drawLine(-r * 0.47f, r * 0.13f, -r * 0.39f, r * 0.26f, stroke)
                c.drawLine(r * 0.47f, r * 0.13f, r * 0.39f, r * 0.26f, stroke)
            }
        }
        c.restore()
    }

    private fun glove(c: Canvas, x: Float, y: Float, gr: Float, lw: Float, side: Int, time: Float) {
        fill.color = Pal.panel
        c.drawCircle(x, y, gr, fill)
        stroke.color = Pal.ink
        stroke.strokeWidth = lw
        c.drawCircle(x, y, gr, stroke)
        // thumb bump
        fill.color = Pal.panel
        c.drawCircle(x - side * gr * 0.8f, y - gr * 0.35f, gr * 0.42f, fill)
        c.drawCircle(x - side * gr * 0.8f, y - gr * 0.35f, gr * 0.42f, stroke)
        fill.color = Pal.panel
        c.drawCircle(x - side * gr * 0.45f, y - gr * 0.15f, gr * 0.42f, fill)
        // finger creases
        stroke.strokeWidth = lw * 0.7f
        c.drawLine(x - gr * 0.1f, y - gr * 0.5f, x - gr * 0.1f, y + gr * 0.1f, stroke)
        c.drawLine(x + gr * 0.3f, y - gr * 0.4f, x + gr * 0.3f, y + gr * 0.15f, stroke)
    }

    // ------------------------------------------------------------------ icons

    const val ICON_PAUSE = 1
    const val ICON_UNDO = 2
    const val ICON_HINT = 3
    const val ICON_SOUND = 4
    const val ICON_MUSIC = 5
    const val ICON_BACK = 6
    const val ICON_NEXT = 7
    const val ICON_RESTART = 8

    fun icon(c: Canvas, id: Int, cx: Float, cy: Float, s: Float, col: Int, off: Boolean) {
        fill.color = col
        stroke.color = col
        stroke.strokeWidth = s * 0.16f
        when (id) {
            ICON_PAUSE -> {
                r1.set(cx - s * 0.42f, cy - s * 0.5f, cx - s * 0.12f, cy + s * 0.5f)
                c.drawRoundRect(r1, s * 0.08f, s * 0.08f, fill)
                r1.set(cx + s * 0.12f, cy - s * 0.5f, cx + s * 0.42f, cy + s * 0.5f)
                c.drawRoundRect(r1, s * 0.08f, s * 0.08f, fill)
            }
            ICON_UNDO, ICON_RESTART -> {
                val rr = s * 0.42f
                r1.set(cx - rr, cy - rr, cx + rr, cy + rr)
                val sweep = if (id == ICON_UNDO) 250f else 290f
                c.drawArc(r1, 195f, sweep, false, stroke)
                // arrow head at the start of the arc (left side), pointing down (counter-clockwise travel)
                val a = Math.toRadians(195.0)
                val ax = cx + (cos(a) * rr).toFloat()
                val ay = cy + (sin(a) * rr).toFloat()
                path.reset()
                path.moveTo(ax - s * 0.24f, ay - s * 0.02f)
                path.lineTo(ax + s * 0.24f, ay - s * 0.02f)
                path.lineTo(ax, ay + s * 0.32f)
                path.close()
                c.drawPath(path, fill)
            }
            ICON_HINT -> {
                c.drawCircle(cx, cy - s * 0.12f, s * 0.36f, fill)
                r1.set(cx - s * 0.2f, cy + s * 0.18f, cx + s * 0.2f, cy + s * 0.5f)
                c.drawRoundRect(r1, s * 0.06f, s * 0.06f, fill)
                stroke.strokeWidth = s * 0.08f
                for (k in 0 until 5) {
                    val a = (-PI + k * PI / 4).toFloat()
                    c.drawLine(cx + cos(a) * s * 0.5f, cy - s * 0.12f + sin(a) * s * 0.5f,
                        cx + cos(a) * s * 0.66f, cy - s * 0.12f + sin(a) * s * 0.66f, stroke)
                }
            }
            ICON_SOUND -> {
                path.reset()
                path.moveTo(cx - s * 0.5f, cy - s * 0.18f)
                path.lineTo(cx - s * 0.25f, cy - s * 0.18f)
                path.lineTo(cx + s * 0.05f, cy - s * 0.45f)
                path.lineTo(cx + s * 0.05f, cy + s * 0.45f)
                path.lineTo(cx - s * 0.25f, cy + s * 0.18f)
                path.lineTo(cx - s * 0.5f, cy + s * 0.18f)
                path.close()
                c.drawPath(path, fill)
                stroke.strokeWidth = s * 0.1f
                if (!off) {
                    r1.set(cx - s * 0.2f, cy - s * 0.3f, cx + s * 0.4f, cy + s * 0.3f)
                    c.drawArc(r1, -50f, 100f, false, stroke)
                    r1.set(cx - s * 0.2f, cy - s * 0.5f, cx + s * 0.6f, cy + s * 0.5f)
                    c.drawArc(r1, -50f, 100f, false, stroke)
                }
            }
            ICON_MUSIC -> {
                c.drawCircle(cx - s * 0.2f, cy + s * 0.3f, s * 0.2f, fill)
                c.drawCircle(cx + s * 0.35f, cy + s * 0.2f, s * 0.2f, fill)
                stroke.strokeWidth = s * 0.1f
                c.drawLine(cx - s * 0.02f, cy + s * 0.3f, cx - s * 0.02f, cy - s * 0.45f, stroke)
                c.drawLine(cx + s * 0.53f, cy + s * 0.2f, cx + s * 0.53f, cy - s * 0.55f, stroke)
                stroke.strokeWidth = s * 0.18f
                c.drawLine(cx - s * 0.02f, cy - s * 0.42f, cx + s * 0.53f, cy - s * 0.52f, stroke)
            }
            ICON_BACK, ICON_NEXT -> {
                val d = if (id == ICON_BACK) -1f else 1f
                path.reset()
                path.moveTo(cx + d * s * 0.42f, cy)
                path.lineTo(cx - d * s * 0.22f, cy - s * 0.45f)
                path.lineTo(cx - d * s * 0.22f, cy + s * 0.45f)
                path.close()
                c.drawPath(path, fill)
            }
        }
        if (off) {
            stroke.strokeWidth = s * 0.13f
            stroke.color = col
            c.drawLine(cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f, stroke)
        }
    }

    // ------------------------------------------------------------------ particles

    fun splat(c: Canvas, x: Float, y: Float, r: Float, rot: Float, col: Int) {
        fill.color = col
        c.drawCircle(x, y, r, fill)
        for (k in 0 until 4) {
            val a = rot + k * 1.7f
            c.drawCircle(x + cos(a) * r * 1.1f, y + sin(a) * r * 1.1f, r * 0.35f, fill)
        }
    }

    fun drop(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        path.reset()
        path.moveTo(x, y - r * 2.2f)
        path.quadTo(x + r * 1.1f, y - r * 0.4f, x + r, y)
        path.quadTo(x + r, y + r, x, y + r)
        path.quadTo(x - r, y + r, x - r, y)
        path.quadTo(x - r * 1.1f, y - r * 0.4f, x, y - r * 2.2f)
        path.close()
        fill.color = col
        c.drawPath(path, fill)
    }

    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    fun ribbon(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, col: Int, lineCol: Int, lw: Float) {
        // banner with notched tails
        val tail = h * 0.9f
        fill.color = Pal.g3
        for (s in -1..1 step 2) {
            path.reset()
            val x0 = cx + s * (w / 2f - tail * 0.3f)
            path.moveTo(x0, cy - h * 0.25f)
            path.lineTo(x0 + s * tail * 1.2f, cy - h * 0.25f)
            path.lineTo(x0 + s * tail * 0.85f, cy + h * 0.25f)
            path.lineTo(x0 + s * tail * 1.2f, cy + h * 0.75f)
            path.lineTo(x0, cy + h * 0.75f)
            path.close()
            c.drawPath(path, fill)
            stroke.color = lineCol; stroke.strokeWidth = lw
            c.drawPath(path, stroke)
        }
        r1.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        fill.color = col
        c.drawRect(r1, fill)
        stroke.color = lineCol; stroke.strokeWidth = lw
        c.drawRect(r1, stroke)
    }

    fun min3(a: Float, b: Float, c: Float) = min(a, min(b, c))
}
