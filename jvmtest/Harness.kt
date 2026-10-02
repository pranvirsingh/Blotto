import android.graphics.Canvas
import android.graphics.Typeface
import com.pranvir.blotto.*
import java.awt.image.BufferedImage
import java.io.File
import java.util.Random
import javax.imageio.ImageIO

class FakeHost : Host {
    val prefs = HashMap<String, Any?>()
    var sounds = 0
    var quits = 0
    val soundLog = IntArray(Sfx.COUNT + 1)
    override fun loadInt(key: String, def: Int) = (prefs[key] as? Int) ?: def
    override fun saveInt(key: String, v: Int) { prefs[key] = v }
    override fun loadString(key: String) = prefs[key] as? String
    override fun saveString(key: String, v: String?) { prefs[key] = v }
    override fun sound(id: Int, vol: Float, rate: Float) {
        check(rate in 0.5f..2f) { "bad rate $rate" }
        check(vol in 0f..1f) { "bad vol $vol" }
        sounds++; if (id < soundLog.size) soundLog[id]++
    }
    override fun setAudio(soundOn: Boolean, musicOn: Boolean) {}
    override fun haptic(strong: Boolean) {}
    override fun quit() { quits++ }
}

const val W = 1080
const val H = 2400
val outDir = File("/home/claude/blotto/shots").apply { mkdirs() }

fun step(g: Game, seconds: Float, fps: Int = 60) {
    val n = (seconds * fps).toInt().coerceAtLeast(1)
    repeat(n) { g.update(1f / fps) }
}

fun shot(g: Game, name: String, scale: Int = 2) {
    val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val c = Canvas(img)
    g.draw(c)
    check(c.saveCount == 1) { "unbalanced save/restore in $name: ${c.saveCount}" }
    val small = BufferedImage(W / scale, H / scale, BufferedImage.TYPE_INT_RGB)
    val g2 = small.createGraphics()
    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g2.drawImage(img, 0, 0, W / scale, H / scale, null)
    ImageIO.write(small, "png", File(outDir, "$name.png"))
}

fun tap(g: Game, x: Float, y: Float) { g.touchDown(x, y); g.update(1 / 60f); g.touchUp(x, y); g.update(1 / 60f) }
fun tapBtn(g: Game, id: Int) {
    val o = FloatArray(2)
    check(g.debugButtonCenter(id, o)) { "button $id not visible (scene=${g.scene} overlay=${g.overlay})" }
    tap(g, o[0], o[1])
}
fun tapTileIdx(g: Game, i: Int) { val o = FloatArray(2); g.debugTileCenter(i, o); tap(g, o[0], o[1]) }
fun longPress(g: Game, i: Int) {
    val o = FloatArray(2); g.debugTileCenter(i, o)
    g.touchDown(o[0], o[1]); step(g, 0.6f); g.touchUp(o[0], o[1]); g.update(1 / 60f)
}
fun waitTransition(g: Game) { var k = 0; while (g.isTransitioning && k < 600) { g.update(1 / 60f); k++ } ; check(k < 600) }

fun newGame(host: FakeHost): Game {
    val g = Game(host)
    g.resize(W, H, 2.75f)
    g.setInsets(90, 60, 0, 0)
    return g
}

fun solveNow(g: Game) {
    val p = g.puzzle!!
    g.debugSolveAllButOne()
    // find the remaining wrong tile and tap it into place
    for (i in 0 until p.size) {
        if (!p.isCorrect(i)) {
            if (p.locked[i]) longPress(g, i)
            val k = p.tapsToFix(i)
            repeat(k) { tapTileIdx(g, i); step(g, 0.05f) }
        }
    }
    check(p.isSolved()) { "not solved" }
}

fun main(args: Array<String>) {
    Fonts.display = Typeface.createFromFile("/home/claude/tc/ultra.ttf")
    Fonts.body = Typeface.createFromFile("/home/claude/tc/cinzel.ttf")
    val host = FakeHost()
    val g = newGame(host)

    // ---- title
    step(g, 1.5f); shot(g, "01_title")
    // ---- first play goes to how-to
    tapBtn(g, Game.B_PLAY); step(g, 0.2f); shot(g, "02_iris")
    waitTransition(g); step(g, 1.0f)
    check(g.scene == Game.SCENE_HOWTO)
    shot(g, "03_howto1")
    for (k in 2..4) { tap(g, 540f, 1500f); step(g, if (k == 2) 1.9f else 1.3f); shot(g, "03_howto$k") }
    tap(g, 540f, 1500f); waitTransition(g); step(g, 0.8f)
    check(g.scene == Game.SCENE_PLAY && g.overlay == Game.OV_INTRO) { "expected intro" }
    shot(g, "04_intro")
    tap(g, 540f, 1300f); step(g, 1.5f)
    check(g.overlay == Game.OV_NONE)
    shot(g, "05_play_l1")
    // tap a few tiles
    val p = g.puzzle!!
    for (i in 0 until p.size) if (!p.isCorrect(i) && !p.isConnected(i)) { tapTileIdx(g, i); break }
    step(g, 0.08f); shot(g, "06_rotating")
    solveNow(g)
    step(g, 0.5f); shot(g, "07_solving")
    step(g, 1.2f); shot(g, "08_celebrate")
    step(g, 1.5f)
    check(g.overlay == Game.OV_WIN) { "expected win overlay, got ${g.overlay}" }
    shot(g, "09_win")
    tapBtn(g, Game.B_NEXT); waitTransition(g); step(g, 0.8f)
    check(g.level == 2)
    tap(g, 540f, 1300f); step(g, 1.0f)
    // lock a tile and hint
    longPress(g, 0)
    tapBtn(g, Game.B_HINT); step(g, 0.4f)
    shot(g, "10_lock_hint")
    tapBtn(g, Game.B_PAUSE); step(g, 0.5f); shot(g, "11_pause")
    tapBtn(g, Game.B_RESUME)

    // ---- bigger & twist levels via saved level
    for (lv in intArrayOf(7, 10, 26, 35, 100)) {
        val h2 = FakeHost()
        h2.prefs["level"] = lv; h2.prefs["seenHowTo"] = 1; h2.prefs["reels"] = lv - 1
        val g2 = newGame(h2)
        step(g2, 0.3f)
        tapBtn(g2, Game.B_PLAY); waitTransition(g2); step(g2, 0.6f)
        shot(g2, "12_intro_l$lv")
        tap(g2, 540f, 1300f)
        val pz = g2.puzzle!!
        // make some progress
        val rnd = Random(lv.toLong())
        repeat(8) { tapTileIdx(g2, pz.src); step(g2, 0.3f) }
        repeat(20) { val i = rnd.nextInt(pz.size); tapTileIdx(g2, i); step(g2, 0.05f) }
        step(g2, 2.5f)
        shot(g2, "13_play_l$lv")
    }

    // ---- rush
    val h3 = FakeHost(); h3.prefs["seenHowTo"] = 1
    val g3 = newGame(h3)
    step(g3, 0.3f)
    tapBtn(g3, Game.B_RUSH); waitTransition(g3); step(g3, 1.4f)
    check(g3.overlay == Game.OV_LEADER)
    shot(g3, "14_leader")
    step(g3, 2.0f)
    check(g3.overlay == Game.OV_NONE)
    shot(g3, "15_rush")
    solveNow(g3); step(g3, 0.4f); shot(g3, "16_rush_solved")
    step(g3, 2.5f)
    check(g3.rushSolved == 1)
    step(g3, 80f, 30)
    check(g3.overlay == Game.OV_TIMEUP) { "expected timeup" }
    step(g3, 1f); shot(g3, "17_timeup")

    // ---- title after progress
    tapBtn(g3, Game.B_END_MENU); waitTransition(g3); step(g3, 2f); shot(g3, "18_title_after")
    println("sounds played: ${host.sounds}  log=${host.soundLog.toList()}")
    println("SCREENSHOTS OK")

    if (args.isNotEmpty() && args[0] == "monkey") monkey(args.getOrNull(1)?.toInt() ?: 200000)
}

fun monkey(frames: Int) {
    val rnd = Random(99)
    for (round in 0 until 6) {
        val host = FakeHost()
        if (round % 2 == 1) { host.prefs["level"] = 1 + rnd.nextInt(120); host.prefs["seenHowTo"] = 1 }
        val g = newGame(host)
        val img = BufferedImage(360, 800, BufferedImage.TYPE_INT_ARGB)
        var downNow = false
        for (f in 0 until frames / 6) {
            val dt = when (rnd.nextInt(20)) { 0 -> 0.2f; 1 -> 0f; else -> 1f / (30 + rnd.nextInt(90)) }
            when (rnd.nextInt(40)) {
                0, 1, 2, 3, 4, 5 -> {
                    val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H
                    if (!downNow) { g.touchDown(x, y); downNow = true } else { g.touchUp(x, y); downNow = false }
                }
                6 -> if (downNow) g.touchMove(rnd.nextFloat() * W, rnd.nextFloat() * H)
                7 -> if (rnd.nextInt(10) == 0) { if (!g.onBack()) { /* would quit */ } }
                8 -> if (rnd.nextInt(50) == 0) g.onPause()
                9 -> if (rnd.nextInt(200) == 0) { g.resize(if (rnd.nextBoolean()) 720 else 1440, if (rnd.nextBoolean()) 1600 else 3200, 2f + rnd.nextFloat()); g.setInsets(rnd.nextInt(150), rnd.nextInt(150), rnd.nextInt(40), rnd.nextInt(40)) }
                10 -> if (rnd.nextInt(30) == 0) g.touchCancel().also { downNow = false }
                11 -> {
                    // aim for buttons: tap each visible button id sometimes
                    val ids = intArrayOf(1, 2, 3, 4, 5, 10, 11, 12, 20, 21, 22, 23, 24, 30, 31, 40, 41, 50)
                    val o = FloatArray(2)
                    val id = ids[rnd.nextInt(ids.size)]
                    if (id != 24 && id != 31 && id != 41 && g.debugButtonCenter(id, o)) { g.touchDown(o[0], o[1]); g.touchUp(o[0], o[1]); downNow = false }
                }
                12 -> if (g.puzzle != null && g.scene == Game.SCENE_PLAY && rnd.nextInt(8) == 0) {
                    // play smartly sometimes so we reach win screens
                    val p = g.puzzle!!
                    val i = rnd.nextInt(p.size)
                    if (!p.isCorrect(i) && !p.locked[i]) { val o = FloatArray(2); g.debugTileCenter(i, o); g.touchDown(o[0], o[1]); g.touchUp(o[0], o[1]); downNow = false }
                }
            }
            g.update(dt)
            if (f % 7 == 0) {
                val c = Canvas(img)
                g.draw(c)
                check(c.saveCount == 1) { "unbalanced save/restore scene=${g.scene} ov=${g.overlay}" }
            }
        }
        println("monkey round $round ok: level=${g.level} scene=${g.scene} overlay=${g.overlay} rush=${g.rushSolved} sounds=${host.sounds}")
    }
    println("MONKEY OK")
}
