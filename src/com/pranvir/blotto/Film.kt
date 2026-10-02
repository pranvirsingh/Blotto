package com.pranvir.blotto

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * The always-on old-cinema layer: grain that changes at 24 fps, dust specks, hairs,
 * vertical scratches, a very gentle luminance flicker, gate weave, vignette and the odd cue mark.
 */
class Film {
    private val rng = Random()
    private val grainCount = 4
    private val grainSize = 192
    private val grainShaders = arrayOfNulls<BitmapShader>(grainCount)
    private val grainPaint = Paint().apply { isFilterBitmap = false; isDither = false }
    private val grainMatrix = Matrix()
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val hairPath = Path()

    private var w = 1f
    private var h = 1f
    private var dp = 1f
    private var ready = false

    private var frameClock = 0f
    private var grainIdx = 0
    private var grainOx = 0f
    private var grainOy = 0f
    private var flicker = 0f
    private var flickerTarget = 0f
    var weaveX = 0f
        private set
    var weaveY = 0f
        private set
    private var weaveTX = 0f
    private var weaveTY = 0f

    // dust (lives one film frame)
    private val dustMax = 8
    private val dustX = FloatArray(dustMax)
    private val dustY = FloatArray(dustMax)
    private val dustR = FloatArray(dustMax)
    private val dustDark = BooleanArray(dustMax)
    private var dustN = 0

    // scratches
    private val scrMax = 3
    private val scrX = FloatArray(scrMax)
    private val scrLife = FloatArray(scrMax)
    private val scrMaxLife = FloatArray(scrMax)
    private val scrW = FloatArray(scrMax)
    private val scrDrift = FloatArray(scrMax)
    private val scrDark = BooleanArray(scrMax)
    private val scrGapY = FloatArray(scrMax)
    private var scrAlpha = IntArray(scrMax)

    // hair
    private var hairLife = 0f
    private var hairX = 0f
    private var hairY = 0f
    private var hairS = 0f
    private var hairRot = 0f

    // cue mark
    private var cueTimer = 18f
    private var cueLife = 0f

    var intensity = 1f

    fun resize(width: Int, height: Int, density: Float) {
        w = max(1, width).toFloat()
        h = max(1, height).toFloat()
        dp = max(0.5f, density)
        if (grainShaders[0] == null) buildGrain()
        val r = hypot(w, h) * 0.56f
        vignettePaint.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(0x00000000, 0x00000000, 0x2A000000, 0x8C000000.toInt()),
            floatArrayOf(0f, 0.55f, 0.82f, 1f), Shader.TileMode.CLAMP
        )
        ready = true
    }

    private fun buildGrain() {
        val px = IntArray(grainSize * grainSize)
        for (g in 0 until grainCount) {
            for (i in px.indices) {
                val v = rng.nextFloat()
                px[i] = when {
                    v < 0.045f -> ((25 + rng.nextInt(55)) shl 24) or 0x000000
                    v < 0.075f -> ((30 + rng.nextInt(60)) shl 24) or 0xFFFFFF
                    v < 0.083f -> ((80 + rng.nextInt(70)) shl 24) or 0x101010
                    else -> 0
                }
            }
            // a few chunkier specks
            repeat(40) {
                val x = rng.nextInt(grainSize - 2)
                val y = rng.nextInt(grainSize - 2)
                val col = if (rng.nextBoolean()) ((70 + rng.nextInt(60)) shl 24) else (((60 + rng.nextInt(60)) shl 24) or 0xFFFFFF)
                px[y * grainSize + x] = col
                px[y * grainSize + x + 1] = col
                px[(y + 1) * grainSize + x] = col
                if (rng.nextBoolean()) px[(y + 1) * grainSize + x + 1] = col
            }
            val bmp = Bitmap.createBitmap(grainSize, grainSize, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, grainSize, 0, 0, grainSize, grainSize)
            grainShaders[g] = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    fun update(dt: Float) {
        frameClock += dt
        // the projector runs at 24 frames per second
        if (frameClock >= 1f / 24f) {
            frameClock %= (1f / 24f)
            newFilmFrame()
        }
        // scratches age continuously
        for (i in 0 until scrMax) if (scrLife[i] > 0f) {
            scrLife[i] -= dt
            scrX[i] += scrDrift[i] * dt
        }
        if (hairLife > 0f) hairLife -= dt
        if (cueLife > 0f) cueLife -= dt
        cueTimer -= dt
        if (cueTimer <= 0f) {
            cueTimer = 22f + rng.nextFloat() * 25f
            cueLife = 4f / 24f
        }
        flicker += (flickerTarget - flicker) * minOf(1f, dt * 30f)
        weaveX += (weaveTX - weaveX) * minOf(1f, dt * 12f)
        weaveY += (weaveTY - weaveY) * minOf(1f, dt * 12f)
    }

    private fun newFilmFrame() {
        grainIdx = rng.nextInt(grainCount)
        grainOx = rng.nextFloat() * grainSize
        grainOy = rng.nextFloat() * grainSize
        // gentle flicker, occasional small pulse
        flickerTarget = (rng.nextFloat() - 0.5f) * 1.0f
        if (rng.nextFloat() < 0.02f) flickerTarget = if (rng.nextBoolean()) 1.8f else -1.6f
        // gate weave
        if (rng.nextFloat() < 0.35f) {
            weaveTX = (rng.nextFloat() - 0.5f) * 1.3f * dp
            weaveTY = (rng.nextFloat() - 0.5f) * 1.6f * dp
        }
        // dust
        dustN = 0
        val roll = rng.nextFloat()
        val count = when {
            roll < 0.45f -> 0
            roll < 0.8f -> 1
            roll < 0.95f -> 2
            else -> 4
        }
        for (k in 0 until count) {
            dustX[dustN] = rng.nextFloat() * w
            dustY[dustN] = rng.nextFloat() * h
            dustR[dustN] = (0.6f + rng.nextFloat() * rng.nextFloat() * 3.2f) * dp
            dustDark[dustN] = rng.nextFloat() < 0.7f
            dustN++
        }
        // scratches
        if (rng.nextFloat() < 0.035f) {
            for (i in 0 until scrMax) if (scrLife[i] <= 0f) {
                scrX[i] = rng.nextFloat() * w
                scrMaxLife[i] = 0.35f + rng.nextFloat() * 1.8f
                scrLife[i] = scrMaxLife[i]
                scrW[i] = (0.6f + rng.nextFloat() * 1.1f) * dp
                scrDrift[i] = (rng.nextFloat() - 0.5f) * 30f * dp
                scrDark[i] = rng.nextFloat() < 0.35f
                break
            }
        }
        for (i in 0 until scrMax) {
            scrAlpha[i] = 35 + rng.nextInt(80)
            scrGapY[i] = if (rng.nextFloat() < 0.4f) rng.nextFloat() * h else -1f
        }
        // hair
        if (hairLife <= 0f && rng.nextFloat() < 0.008f) {
            hairLife = (3 + rng.nextInt(6)) / 24f
            hairX = rng.nextFloat() * w
            hairY = rng.nextFloat() * h
            hairS = (14f + rng.nextFloat() * 26f) * dp
            hairRot = rng.nextFloat() * 6.28f
        }
    }

    fun draw(c: Canvas) {
        if (!ready) return
        val k = intensity
        // flicker
        if (flicker > 0f) {
            paint.color = ((flicker * 9f * k).toInt().coerceIn(0, 40) shl 24) or 0xFFFFFF
            c.drawRect(0f, 0f, w, h, paint)
        } else if (flicker < 0f) {
            paint.color = ((-flicker * 12f * k).toInt().coerceIn(0, 40) shl 24)
            c.drawRect(0f, 0f, w, h, paint)
        }
        // grain
        val sh = grainShaders[grainIdx]
        if (sh != null) {
            val sc = max(1f, dp / 1.5f)
            grainMatrix.setScale(sc, sc)
            grainMatrix.postTranslate(-grainOx * sc, -grainOy * sc)
            sh.setLocalMatrix(grainMatrix)
            grainPaint.shader = sh
            grainPaint.alpha = (255 * k).toInt().coerceIn(0, 255)
            c.drawRect(0f, 0f, w, h, grainPaint)
        }
        // dust
        for (i in 0 until dustN) {
            paint.color = if (dustDark[i]) 0xB0101010.toInt() else 0x90FFFFFF.toInt()
            c.drawCircle(dustX[i], dustY[i], dustR[i], paint)
            if (dustR[i] > 2f * dp) c.drawCircle(dustX[i] + dustR[i] * 0.8f, dustY[i] - dustR[i] * 0.4f, dustR[i] * 0.5f, paint)
        }
        // scratches
        for (i in 0 until scrMax) {
            if (scrLife[i] <= 0f) continue
            val fade = minOf(1f, scrLife[i] / 0.2f, (scrMaxLife[i] - scrLife[i]) / 0.1f + 0.2f)
            val a = (scrAlpha[i] * fade * k).toInt().coerceIn(0, 255)
            strokeP.color = (a shl 24) or (if (scrDark[i]) 0x111111 else 0xFFFFFF)
            strokeP.strokeWidth = scrW[i]
            val gy = scrGapY[i]
            if (gy < 0f) c.drawLine(scrX[i], 0f, scrX[i], h, strokeP)
            else {
                c.drawLine(scrX[i], 0f, scrX[i], gy, strokeP)
                c.drawLine(scrX[i], gy + h * 0.12f, scrX[i], h, strokeP)
            }
        }
        // hair
        if (hairLife > 0f) {
            strokeP.color = 0xA0141414.toInt()
            strokeP.strokeWidth = 0.9f * dp
            hairPath.reset()
            val s = hairS
            hairPath.moveTo(hairX, hairY)
            hairPath.cubicTo(hairX + s * 0.6f, hairY - s * 0.4f, hairX + s * sin(hairRot), hairY + s * 0.9f, hairX + s * 1.3f, hairY + s * 0.3f)
            c.drawPath(hairPath, strokeP)
        }
        // cue mark (the famous "cigarette burn" in the top right)
        if (cueLife > 0f) {
            paint.color = 0x9A0A0A0A.toInt()
            val r = w * 0.028f
            c.drawCircle(w * 0.9f, h * 0.08f, r, paint)
            paint.color = 0x40FFFFFF
            c.drawCircle(w * 0.9f - r * 0.2f, h * 0.08f - r * 0.2f, r * 0.45f, paint)
        }
        // vignette
        c.drawRect(0f, 0f, w, h, vignettePaint)
    }
}
