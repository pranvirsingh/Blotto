import android.graphics.Canvas
import android.graphics.Typeface
import com.pranvir.blotto.*
import java.awt.image.BufferedImage
import java.util.Random

val botImg = BufferedImage(270, 600, BufferedImage.TYPE_INT_ARGB)
var frameNo = 0L

fun tick(g: Game, dt: Float = 1f / 60f) {
    g.update(dt)
    frameNo++
    if (frameNo % 5 == 0L) {
        val c = Canvas(botImg)
        g.draw(c)
        check(c.saveCount == 1) { "unbalanced canvas" }
    }
    g.debugSanity()?.let { throw AssertionError("sanity: $it (scene=${g.scene} ov=${g.overlay} level=${g.level})") }
}
fun ticks(g: Game, sec: Float) { repeat((sec * 60).toInt().coerceAtLeast(1)) { tick(g) } }
fun botTap(g: Game, x: Float, y: Float) { g.touchDown(x, y); tick(g); g.touchUp(x, y); tick(g) }
fun botBtn(g: Game, id: Int) { val o = FloatArray(2); check(g.debugButtonCenter(id, o)) { "btn $id missing scene=${g.scene} ov=${g.overlay}" }; botTap(g, o[0], o[1]) }
fun botTile(g: Game, i: Int) { val o = FloatArray(2); g.debugTileCenter(i, o); botTap(g, o[0], o[1]) }
fun botLong(g: Game, i: Int) { val o = FloatArray(2); g.debugTileCenter(i, o); g.touchDown(o[0], o[1]); ticks(g, 0.55f); g.touchUp(o[0], o[1]); tick(g) }
fun botWaitTr(g: Game) { var k = 0; while (g.isTransitioning) { tick(g); check(++k < 1000) { "transition stuck" } } }

/** Solve like a player: unlock wrong locked tiles, tap everything else into place (visible tiles first for lights-out). */
fun botSolve(g: Game, rnd: Random) {
    val p = g.puzzle!!
    var guard = 0
    while (!p.isSolved()) {
        check(++guard < 5000) { "solve loop" }
        var did = false
        for (i in 0 until p.size) {
            if (p.isCorrect(i)) continue
            if (!p.isVisibleInDark(i) && isLightsOut(g)) continue
            if (p.locked[i]) { botLong(g, i); check(!p.locked[i]) { "unlock failed" } }
            botTile(g, i)
            ticks(g, 0.02f + rnd.nextFloat() * 0.05f)
            did = true
            if (p.isSolved()) break
        }
        if (!did) {
            // hidden tiles only remain: in lights-out every wrong tile eventually becomes visible as ink spreads,
            // but if a wrong tile blocks the ink, use a hint
            if (g.hints > 0) { botBtn(g, Game.B_HINT); ticks(g, 0.2f) }
            else {
                // tap hidden tiles is refused; fix by rotating a visible correct tile? Not needed: pick hidden wrong tile via debug
                for (i in 0 until p.size) if (!p.isCorrect(i)) { p.setRotation(i, p.fixedRotation(i)); }
            }
        }
    }
}

var lightsOutFlag = false
fun isLightsOut(g: Game) = lightsOutFlag

fun main() {
    Fonts.display = Typeface.createFromFile("/home/claude/tc/ultra.ttf")
    Fonts.body = Typeface.createFromFile("/home/claude/tc/cinzel.ttf")
    val rnd = Random(2024)
    val host = FakeHost()
    host.prefs["seenHowTo"] = 1
    var g = newGame(host)
    ticks(g, 0.5f)
    botBtn(g, Game.B_PLAY); botWaitTr(g); ticks(g, 0.5f)
    var starsTotal = 0
    for (round in 1..45) {
        val lvl = g.level
        check(g.scene == Game.SCENE_PLAY) { "not playing" }
        check(g.overlay == Game.OV_INTRO) { "no intro at level $lvl (ov=${g.overlay})" }
        lightsOutFlag = Puzzle.storySpec(lvl).lightsOut
        botTap(g, 500f, 1200f); ticks(g, 0.4f)
        check(g.overlay == Game.OV_NONE)
        val p = g.puzzle!!
        check(p.cols == Puzzle.storySpec(lvl).cols && p.rows == Puzzle.storySpec(lvl).rows)
        // messy play: random taps, undo, locks, pause/resume, back
        repeat(rnd.nextInt(15)) { botTile(g, rnd.nextInt(p.size)); ticks(g, rnd.nextFloat() * 0.1f) }
        if (p.isSolved()) { /* rare */ } else {
            repeat(rnd.nextInt(4)) { botBtn(g, Game.B_UNDO); tick(g) }
            if (rnd.nextBoolean()) botLong(g, rnd.nextInt(p.size))
            if (rnd.nextInt(3) == 0 && g.hints > 0) { botBtn(g, Game.B_HINT); ticks(g, 0.3f) }
            if (rnd.nextInt(4) == 0) { check(g.onBack()); check(g.overlay == Game.OV_PAUSE); ticks(g, 0.3f); botBtn(g, Game.B_RESUME) }
            if (rnd.nextInt(5) == 0) {
                // simulate app backgrounding + full process restart: board must be restored exactly
                g.onPause()
                check(g.overlay == Game.OV_PAUSE)
                val before = g.puzzle!!.serialize()
                val movesBefore = g.moves
                g = newGame(host); ticks(g, 0.3f)
                botBtn(g, Game.B_PLAY); botWaitTr(g); ticks(g, 0.5f)
                check(g.puzzle!!.serialize() == before) { "restore mismatch" }
                check(g.moves == movesBefore) { "moves not restored" }
                botTap(g, 500f, 1200f); ticks(g, 0.4f)
            }
        }
        if (!g.puzzle!!.isSolved()) botSolve(g, rnd)
        var k = 0
        while (g.overlay != Game.OV_WIN) { tick(g); check(++k < 60 * 8) { "no win overlay at level $lvl" } }
        check(g.level == lvl + 1) { "level didn't advance" }
        val s = g.debugEarnedStars
        check(s in 1..3)
        starsTotal += s
        ticks(g, 1.3f)
        botBtn(g, Game.B_NEXT); botWaitTr(g); ticks(g, 0.3f)
    }
    println("story ok: reached level ${g.level}, stars=$starsTotal, hints=${g.hints}, frames=$frameNo")

    // back to menu from play, then rush
    check(g.onBack()); check(g.overlay == Game.OV_NONE || g.overlay == Game.OV_PAUSE)
    if (g.overlay == Game.OV_NONE) check(g.onBack())
    botBtn(g, Game.B_MENU); botWaitTr(g)
    check(g.scene == Game.SCENE_TITLE)
    check(!g.onBack()) { "back on title should exit" }
    lightsOutFlag = false
    for (rushRound in 0 until 3) {
        if (rushRound == 0) botBtn(g, Game.B_RUSH) else botBtn(g, Game.B_AGAIN)
        botWaitTr(g)
        check(g.overlay == Game.OV_LEADER) { "no leader" }
        ticks(g, 3.2f)
        check(g.overlay == Game.OV_NONE)
        val target = 3 + rushRound * 4
        for (b in 0 until target) {
            val t0 = g.debugRushTime
            botSolve(g, rnd)
            var k = 0
            while (g.debugCelebrating || g.isTransitioning) { tick(g); check(++k < 600) { "rush stuck" } }
            check(g.rushSolved == b + 1) { "rush count ${g.rushSolved} vs ${b + 1}" }
            check(g.debugRushTime > 0f)
        }
        // pause during rush freezes the clock
        g.onPause(); val tp = g.debugRushTime; ticks(g, 3f); check(g.debugRushTime == tp) { "clock ran while paused" }
        botBtn(g, Game.B_RESUME)
        var k = 0
        while (g.overlay != Game.OV_TIMEUP) { tick(g, 1f / 20f); check(++k < 20 * 200) { "never timed up" } }
        check(g.rushSolved == target)
        ticks(g, 1f)
        println("rush round $rushRound ok: solved=${g.rushSolved} best=${host.prefs["rushBest"]}")
    }
    check(host.prefs["rushBest"] == 11)
    botBtn(g, Game.B_END_MENU); botWaitTr(g)
    // how-to flow via menu
    botBtn(g, Game.B_HOWTO); botWaitTr(g)
    repeat(4) { ticks(g, 0.4f); botTap(g, 500f, 1500f) }
    botWaitTr(g); ticks(g, 0.4f)
    check(g.scene == Game.SCENE_PLAY && g.overlay == Game.OV_INTRO) { "how-to should roll into play" }
    // sound/music toggles persist
    check(g.onBack()); ticks(g, 0.1f)
    if (g.overlay != Game.OV_PAUSE) { check(g.onBack()) }
    botBtn(g, Game.B_P_SOUND); botBtn(g, Game.B_P_MUSIC)
    check(host.prefs["sound"] == 0 && host.prefs["music"] == 0)
    val snd = host.sounds
    botBtn(g, Game.B_RESUME); repeat(5) { botTile(g, it) }
    check(host.sounds == snd) { "sound played while muted" }
    println("sounds total=${host.sounds}")
    println("BOT OK")
}
