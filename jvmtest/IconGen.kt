import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.pranvir.blotto.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun drawIconArt(c: Canvas, s: Float, full: Boolean) {
    Pal.set(0f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Pal.paper
    c.drawRect(0f, 0f, s, s, p)
    Art.sunburst(c, s / 2f, s * 0.52f, s, 16, -90f, Pal.paper, lerpColor(Pal.paper, Pal.g1, 1f))
    // Blotto inside the adaptive-icon safe zone (66/108 of the size)
    val safe = s * (if (full) 0.66f else 0.86f)
    val r = safe * 0.9f / 4.1f
    Art.blotto(c, s / 2f, s / 2f + r * 0.285f, r, 0.35f, Art.MOOD_HAPPY, 0.15f, -0.1f)
}

fun save(img: BufferedImage, path: String) {
    val f = File(path); f.parentFile.mkdirs(); ImageIO.write(img, "png", f)
}

fun main() {
    val res = "/home/claude/blotto/res"
    val dens = linkedMapOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)
    for ((d, k) in dens) {
        // adaptive foreground (108dp)
        val fs = (108 * k).toInt()
        val fg = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
        drawIconArt(Canvas(fg), fs.toFloat(), true)
        save(fg, "$res/mipmap-$d/ic_launcher_fg.png")
        // monochrome: ink parts become opaque
        val mono = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
        run {
            val tmp = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            val c = Canvas(tmp)
            Pal.set(0f)
            val r = fs * 0.66f * 0.9f / 4.1f
            Art.blotto(c, fs / 2f, fs / 2f + r * 0.285f, r, 0.35f, Art.MOOD_HAPPY, 0.15f, -0.1f)
            for (y in 0 until fs) for (x in 0 until fs) {
                val px = tmp.getRGB(x, y)
                val a = (px ushr 24) and 255
                val lum = ((px shr 16) and 255) * 0.3f + ((px shr 8) and 255) * 0.59f + (px and 255) * 0.11f
                val na = (a * (1f - lum / 255f)).toInt().coerceIn(0, 255)
                mono.setRGB(x, y, (na shl 24))
            }
        }
        save(mono, "$res/mipmap-$d/ic_launcher_mono.png")
        // legacy square + round
        val ls = (48 * k).toInt()
        for (round in listOf(false, true)) {
            val big = ls * 4
            val img = BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB)
            val c = Canvas(img)
            val clip = Path()
            val inset = big * 0.04f
            if (round) clip.addCircle(big / 2f, big / 2f, big / 2f - inset, Path.Direction.CW)
            else {
                clip.addRect(inset, inset, big - inset, big - inset, Path.Direction.CW)
            }
            c.save()
            c.clipPath(clip)
            drawIconArt(c, big.toFloat(), false)
            c.restore()
            val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = big * 0.035f; color = Pal.ink }
            if (round) c.drawCircle(big / 2f, big / 2f, big / 2f - inset, st)
            else c.drawRect(RectF(inset, inset, big - inset, big - inset), st)
            // downscale with quality
            val out = BufferedImage(ls, ls, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(img.getScaledInstance(ls, ls, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
            save(out, "$res/mipmap-$d/" + (if (round) "ic_launcher_round.png" else "ic_launcher.png"))
        }
    }
    // store listing sized preview
    val prev = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
    drawIconArt(Canvas(prev), 512f, false)
    save(prev, "/home/claude/blotto/shots/icon512.png")
    println("ICONS OK")
}
