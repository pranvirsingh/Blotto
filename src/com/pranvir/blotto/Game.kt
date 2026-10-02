package com.pranvir.blotto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Everything the game needs from the platform. */
interface Host {
    fun loadInt(key: String, def: Int): Int
    fun saveInt(key: String, v: Int)
    fun loadString(key: String): String?
    fun saveString(key: String, v: String?)
    fun sound(id: Int, vol: Float = 1f, rate: Float = 1f)
    fun setAudio(soundOn: Boolean, musicOn: Boolean)
    fun haptic(strong: Boolean)
    fun quit()
}

class Btn(val id: Int) {
    val rect = RectF()
    var label = ""
    var icon = 0
    var primary = false
    var circle = false
    var visible = false
    var enabled = true
    var off = false
    var badge = -1
    var press = 0f
    fun set(l: Float, t: Float, r: Float, b: Float): Btn { rect.set(l, t, r, b); visible = true; return this }
    fun contains(x: Float, y: Float, slop: Float) =
        visible && x >= rect.left - slop && x <= rect.right + slop && y >= rect.top - slop && y <= rect.bottom + slop
}

class Game(private val host: Host) {

    // ------------------------------------------------------------------ constants
    companion object {
        const val SCENE_TITLE = 0
        const val SCENE_HOWTO = 1
        const val SCENE_PLAY = 2

        const val OV_NONE = 0
        const val OV_INTRO = 1
        const val OV_PAUSE = 2
        const val OV_WIN = 3
        const val OV_LEADER = 4
        const val OV_TIMEUP = 5

        const val MODE_STORY = 0
        const val MODE_RUSH = 1

        const val ACT_TITLE = 1
        const val ACT_STORY = 2
        const val ACT_NEXT_STORY = 3
        const val ACT_RESTART_STORY = 4
        const val ACT_RUSH = 5
        const val ACT_NEXT_RUSH = 6
        const val ACT_HOWTO = 7

        const val B_PLAY = 1; const val B_RUSH = 2; const val B_HOWTO = 3; const val B_SOUND = 4; const val B_MUSIC = 5
        const val B_PAUSE = 10; const val B_UNDO = 11; const val B_HINT = 12
        const val B_RESUME = 20; const val B_RESTART = 21; const val B_P_SOUND = 22; const val B_P_MUSIC = 23; const val B_MENU = 24
        const val B_NEXT = 30; const val B_WIN_MENU = 31
        const val B_AGAIN = 40; const val B_END_MENU = 41
        const val B_BACK = 50

        const val MAX_TILES = Puzzle.MAX_COLS * Puzzle.MAX_ROWS
        const val RUSH_START = 60f
        const val MAX_HINTS = 9

        val CHEERS = arrayOf("SWELL!", "BULLY!", "SPLENDID!", "HOT DOG!", "JEEPERS!", "WHOOPEE!", "SWANKY!", "NIFTY!", "DANDY!", "KEEN!")
        val BULB_RATES = floatArrayOf(1f, 1.125f, 1.25f, 1.5f, 1.6667f, 2f)
    }

    private val rng = Random()
    val film = Film()

    // screen
    var w = 1f; private set
    var h = 1f; private set
    private var dens = 1f
    private var u = 1f
    private var insetT = 0f
    private var insetB = 0f
    private var insetL = 0f
    private var insetR = 0f
    private var sized = false

    // persistent
    var level = 1; private set
    private var stars = 0
    var hints = 3; private set
    var soundOn = true; private set
    var musicOn = true; private set
    private var rushBest = 0
    private var reels = 0
    private var seenHowTo = false

    // state
    var scene = SCENE_TITLE; private set
    var overlay = OV_NONE; private set
    var mode = MODE_STORY; private set
    var time = 0f; private set
    private var sceneT = 0f
    private var overlayT = 0f

    // puzzle
    var puzzle: Puzzle? = null; private set
    private var spec: LevelSpec = Puzzle.storySpec(1)
    var moves = 0; private set
    private var hudLevel = 1
    private var hintsUsed = 0
    private var parAtStart = 0
    private val ang = FloatArray(MAX_TILES)
    private val angT = FloatArray(MAX_TILES)
    private val angV = FloatArray(MAX_TILES)
    private val inkA = FloatArray(MAX_TILES)
    private val inkDir = IntArray(MAX_TILES)
    private val pop = FloatArray(MAX_TILES)
    private val shake = FloatArray(MAX_TILES)
    private val litA = FloatArray(MAX_TILES)
    private val revealA = FloatArray(MAX_TILES)
    private val hintGlow = FloatArray(MAX_TILES)
    private val wave = FloatArray(MAX_TILES)
    private var litCount = 0
    private var inkSfxCooldown = 0f
    private var solvedPending = false
    private var solvedWait = 0f
    private var celebrate = false
    private var celebrateT = 0f
    private var earnedStars = 0
    private var earnedHint = false
    private var cheer = CHEERS[0]
    private var starsShown = 0
    private val undo = IntArray(1024)
    private var undoTop = 0

    // rush
    private var rushTime = RUSH_START
    var rushSolved = 0; private set
    private var lastTickSec = -1
    private var newBest = false
    private var rushNextDelay = 0f

    // layout
    private val board = RectF()
    private var ts = 1f
    private var bx = 0f
    private var by = 0f
    private val blottoPos = FloatArray(2)
    private var blottoR = 1f
    private var lookX = 0f
    private var lookY = 0f
    private var lookTX = 0f
    private var lookTY = 0f

    // title layout
    private var tLogoY = 0f
    private var tRibbonY = 0f
    private var tRingCy = 0f
    private var tRingR = 1f
    private var tStatsY = 0f
    private var tCreditY = 0f

    // transitions
    private var trPhase = 0
    private var trT = 0f
    private var trDur = 0.45f
    private var trAction = 0
    private var trCx = 0f
    private var trCy = 0f
    private val irisPath = Path()

    // input
    private var down = false
    private var downX = 0f
    private var downY = 0f
    private var downT = 0f
    private var moved = false
    private var longFired = false
    private var pressedTile = -1
    private var pressedBtn: Btn? = null

    // how-to
    private var howPage = 0

    // particles
    private val pMax = 260
    private val px = FloatArray(pMax); private val py = FloatArray(pMax)
    private val pvx = FloatArray(pMax); private val pvy = FloatArray(pMax)
    private val pLife = FloatArray(pMax); private val pMaxLife = FloatArray(pMax)
    private val pSize = FloatArray(pMax); private val pType = IntArray(pMax); private val pRot = FloatArray(pMax)
    private var pNext = 0

    // floating texts
    private val fMax = 6
    private val fText = arrayOfNulls<String>(fMax)
    private val fx = FloatArray(fMax); private val fy = FloatArray(fMax); private val fLife = FloatArray(fMax)
    private var fNext = 0

    // buttons
    private val bPlay = Btn(B_PLAY); private val bRush = Btn(B_RUSH); private val bHow = Btn(B_HOWTO)
    private val bSound = Btn(B_SOUND); private val bMusic = Btn(B_MUSIC)
    private val bPause = Btn(B_PAUSE); private val bUndo = Btn(B_UNDO); private val bHint = Btn(B_HINT)
    private val bResume = Btn(B_RESUME); private val bRestart = Btn(B_RESTART); private val bPSound = Btn(B_P_SOUND)
    private val bPMusic = Btn(B_P_MUSIC); private val bMenu = Btn(B_MENU)
    private val bNext = Btn(B_NEXT); private val bWinMenu = Btn(B_WIN_MENU)
    private val bAgain = Btn(B_AGAIN); private val bEndMenu = Btn(B_END_MENU)
    private val bBack = Btn(B_BACK)
    private val allBtns = arrayOf(bPlay, bRush, bHow, bSound, bMusic, bPause, bUndo, bHint, bResume, bRestart, bPSound,
        bPMusic, bMenu, bNext, bWinMenu, bAgain, bEndMenu, bBack)

    // scratch
    private val rA = RectF()
    private val rB = RectF()
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    // cached strings
    private var hudMovesKey = -1
    private var hudMovesStr = ""
    private var hudLampKey = -1
    private var hudLampStr = ""
    private var hudTimeKey = -1
    private var hudTimeStr = ""
    private var hudStatsKey = -1
    private var hudStatsStr = ""

    init {
        level = host.loadInt("level", 1).coerceAtLeast(1)
        stars = host.loadInt("stars", 0).coerceAtLeast(0)
        hints = host.loadInt("hints", 3).coerceIn(0, MAX_HINTS)
        soundOn = host.loadInt("sound", 1) == 1
        musicOn = host.loadInt("music", 1) == 1
        rushBest = host.loadInt("rushBest", 0).coerceAtLeast(0)
        reels = host.loadInt("reels", 0).coerceAtLeast(0)
        seenHowTo = host.loadInt("seenHowTo", 0) == 1
        Pal.set(0f)
        host.setAudio(soundOn, musicOn)
    }

    // ================================================================== sizing

    fun resize(width: Int, height: Int, density: Float) {
        w = max(1, width).toFloat()
        h = max(1, height).toFloat()
        dens = max(0.5f, density)
        u = min(w / 100f, h / 165f)
        film.resize(width, height, density)
        sized = true
        layout()
    }

    fun setInsets(top: Int, bottom: Int, left: Int, right: Int) {
        insetT = top.toFloat(); insetB = bottom.toFloat(); insetL = left.toFloat(); insetR = right.toFloat()
        if (sized) layout()
    }

    private val safeTop get() = insetT + u * 2f
    private val safeBottom get() = h - insetB - u * 2f
    private val safeLeft get() = insetL
    private val safeRight get() = w - insetR

    private fun layout() {
        val cx = (safeLeft + safeRight) / 2f
        // play HUD
        val hr = u * 6f
        bPause.set(safeLeft + u * 4f, safeTop + u * 1f, safeLeft + u * 4f + hr * 2f, safeTop + u * 1f + hr * 2f)
        bPause.circle = true; bPause.icon = Art.ICON_PAUSE
        val br = u * 7.5f
        val bottomY = safeBottom - u * 3f - br
        bUndo.set(safeLeft + u * 6f, bottomY - br, safeLeft + u * 6f + br * 2f, bottomY + br)
        bUndo.circle = true; bUndo.icon = Art.ICON_UNDO
        bHint.set(safeRight - u * 6f - br * 2f, bottomY - br, safeRight - u * 6f, bottomY + br)
        bHint.circle = true; bHint.icon = Art.ICON_HINT
        blottoR = u * 5.2f
        blottoPos[0] = cx
        blottoPos[1] = safeBottom - u * 2.5f - blottoR * 1.75f

        layoutBoard()

        // title buttons
        val bw = min(u * 62f, (safeRight - safeLeft) - u * 12f)
        val bh = u * 12f
        val sr = u * 5.5f
        tCreditY = safeBottom - u * 4f
        tStatsY = tCreditY - u * 7.5f
        val howBottom = tStatsY - u * 6f
        val titleBtnTop = howBottom - bh * 3f - u * 8f
        tLogoY = safeTop + u * 12f
        tRibbonY = tLogoY + u * 13.5f
        val ringTop = tRibbonY + u * 7f
        val ringBottom = titleBtnTop - u * 4f
        tRingR = max(u * 12f, min(u * 34f, (ringBottom - ringTop) / 2f))
        tRingCy = (ringTop + ringBottom) / 2f
        bPlay.set(cx - bw / 2f, titleBtnTop, cx + bw / 2f, titleBtnTop + bh).also { it.primary = true }
        bRush.set(cx - bw / 2f, titleBtnTop + bh + u * 4f, cx + bw / 2f, titleBtnTop + bh * 2f + u * 4f)
        bHow.set(cx - bw / 2f, titleBtnTop + (bh + u * 4f) * 2f, cx + bw / 2f, titleBtnTop + bh * 3f + u * 8f)
        bRush.label = "PICTURE RUSH"
        bHow.label = "HOW TO PLAY"
        bSound.set(safeLeft + u * 4f, tCreditY - sr, safeLeft + u * 4f + sr * 2f, tCreditY + sr)
        bSound.circle = true; bSound.icon = Art.ICON_SOUND
        bMusic.set(safeRight - u * 4f - sr * 2f, tCreditY - sr, safeRight - u * 4f, tCreditY + sr)
        bMusic.circle = true; bMusic.icon = Art.ICON_MUSIC
        bBack.set(safeLeft + u * 4f, safeTop + u, safeLeft + u * 4f + sr * 2f, safeTop + u + sr * 2f)
        bBack.circle = true; bBack.icon = Art.ICON_BACK
        refreshButtons()
    }

    private fun layoutBoard() {
        val p = puzzle ?: return
        val top = safeTop + u * 22f
        val bottom = blottoPos[1] - blottoR * 2.6f
        val left = safeLeft + u * 5f
        val right = safeRight - u * 5f
        val availW = right - left - u * 4f
        val availH = max(u * 20f, bottom - top - u * 4f)
        ts = min(min(availW / p.cols, availH / p.rows), u * 26f)
        val bwid = ts * p.cols
        val bhei = ts * p.rows
        bx = (left + right) / 2f - bwid / 2f
        by = top + (bottom - top) / 2f - bhei / 2f
        board.set(bx, by, bx + bwid, by + bhei)
    }

    // ================================================================== persistence

    private fun saveMeta() {
        host.saveInt("level", level)
        host.saveInt("stars", stars)
        host.saveInt("hints", hints)
        host.saveInt("sound", if (soundOn) 1 else 0)
        host.saveInt("music", if (musicOn) 1 else 0)
        host.saveInt("rushBest", rushBest)
        host.saveInt("reels", reels)
        host.saveInt("seenHowTo", if (seenHowTo) 1 else 0)
    }

    private fun saveBoard() {
        val p = puzzle
        if (mode != MODE_STORY || p == null || solvedPending || celebrate) return
        host.saveString("board", p.serialize())
        host.saveInt("boardLevel", level)
        host.saveInt("moves", moves)
        host.saveInt("hintsUsed", hintsUsed)
        host.saveInt("par", parAtStart)
    }

    /** Called by the platform when the app goes to the background. */
    fun onPause() {
        if (scene == SCENE_PLAY && overlay == OV_NONE && trPhase == 0 && !solvedPending && !celebrate) {
            openOverlay(OV_PAUSE)
        }
        saveBoard()
        saveMeta()
        down = false
        pressedBtn = null
        pressedTile = -1
    }

    // ================================================================== sounds

    private fun sfx(id: Int, vol: Float = 1f, rate: Float = 1f) {
        if (soundOn) host.sound(id, vol, rate.coerceIn(0.5f, 2f))
    }

    // ================================================================== level setup

    private fun resetTileAnims() {
        val p = puzzle ?: return
        for (i in 0 until MAX_TILES) {
            val r = if (i < p.size) p.rot[i] else 0
            ang[i] = r * 90f; angT[i] = r * 90f; angV[i] = 0f
            inkA[i] = 0f; inkDir[i] = -1; pop[i] = 0f; shake[i] = 0f; litA[i] = 0f
            revealA[i] = 0f; hintGlow[i] = 0f; wave[i] = 0f
        }
        litCount = 0
        solvedPending = false
        solvedWait = 0f
        celebrate = false
        celebrateT = 0f
        undoTop = 0
        hudMovesKey = -1; hudLampKey = -1
        clearParticles()
    }

    private fun setupStory(restore: Boolean) {
        mode = MODE_STORY
        hudLevel = level
        spec = Puzzle.storySpec(level)
        var p: Puzzle? = null
        if (restore && host.loadInt("boardLevel", -1) == level) {
            p = Puzzle.deserialize(host.loadString("board"))
            if (p != null && (p.cols != spec.cols || p.rows != spec.rows)) p = null
        }
        if (p != null) {
            moves = host.loadInt("moves", 0).coerceAtLeast(0)
            hintsUsed = host.loadInt("hintsUsed", 0).coerceAtLeast(0)
            parAtStart = host.loadInt("par", p.par()).coerceAtLeast(1)
        } else {
            p = Puzzle.generate(spec.cols, spec.rows, spec.windiness, rng.nextLong() xor (level.toLong() shl 20))
            moves = 0
            hintsUsed = 0
            parAtStart = p.par()
        }
        puzzle = p
        Pal.set(if (spec.negative) 1f else 0f)
        resetTileAnims()
        layoutBoard()
        scene = SCENE_PLAY
        sceneT = 0f
        openOverlay(OV_INTRO)
        saveBoard()
        refreshButtons()
    }

    private fun setupRushBoard() {
        mode = MODE_RUSH
        val (c, r) = Puzzle.rushSize(rushSolved)
        spec = LevelSpec(c, r, false, false, 0.2f + rng.nextFloat() * 0.5f, "Rush", "")
        val p = Puzzle.generate(c, r, spec.windiness, rng.nextLong())
        puzzle = p
        moves = 0
        hintsUsed = 0
        parAtStart = p.par()
        Pal.set(0f)
        resetTileAnims()
        layoutBoard()
        scene = SCENE_PLAY
        sceneT = 0f
        refreshButtons()
    }

    private fun startRush() {
        rushSolved = 0
        rushTime = RUSH_START
        lastTickSec = -1
        newBest = false
        setupRushBoard()
        openOverlay(OV_LEADER)
    }

    private fun openOverlay(o: Int) {
        overlay = o
        overlayT = 0f
        starsShown = 0
        refreshButtons()
    }

    // ================================================================== transitions

    private fun transition(action: Int, cx: Float = w / 2f, cy: Float = h / 2f, fast: Boolean = false) {
        if (trPhase != 0) return
        trPhase = 1
        trT = 0f
        trDur = if (fast) 0.28f else 0.45f
        trAction = action
        trCx = cx; trCy = cy
        down = false
        pressedBtn = null
        pressedTile = -1
        if (!fast) sfx(Sfx.IRIS_CLOSE, 0.6f)
    }

    private fun performAction(a: Int) {
        when (a) {
            ACT_TITLE -> {
                saveBoard()
                scene = SCENE_TITLE
                sceneT = 0f
                overlay = OV_NONE
                Pal.set(0f)
                clearParticles()
            }
            ACT_STORY -> setupStory(true)
            ACT_NEXT_STORY -> setupStory(false)
            ACT_RESTART_STORY -> {
                host.saveInt("boardLevel", -1)
                setupStory(false)
            }
            ACT_RUSH -> startRush()
            ACT_NEXT_RUSH -> {
                setupRushBoard()
                overlay = OV_NONE
            }
            ACT_HOWTO -> {
                scene = SCENE_HOWTO
                sceneT = 0f
                howPage = 0
                overlay = OV_NONE
                Pal.set(0f)
            }
        }
        refreshButtons()
    }

    // ================================================================== buttons

    private fun refreshButtons() {
        for (b in allBtns) b.visible = false
        when (scene) {
            SCENE_TITLE -> {
                bPlay.visible = true; bRush.visible = true; bHow.visible = true
                bSound.visible = true; bMusic.visible = true
                bPlay.label = if (level <= 1 && reels == 0) "PLAY" else "PLAY  ·  REEL $level"
                bSound.off = !soundOn; bMusic.off = !musicOn
            }
            SCENE_HOWTO -> bBack.visible = true
            SCENE_PLAY -> {
                when (overlay) {
                    OV_NONE -> {
                        bPause.visible = true
                        bUndo.visible = true
                        bHint.visible = mode == MODE_STORY
                        bHint.badge = hints
                        bHint.enabled = hints > 0
                        bUndo.enabled = undoTop > 0
                    }
                    OV_PAUSE -> layoutPause()
                    OV_WIN -> layoutWin()
                    OV_TIMEUP -> layoutTimeUp()
                }
            }
        }
    }

    private fun cardRect(out: RectF, heightU: Float) {
        val cw = min((safeRight - safeLeft) - u * 10f, u * 88f)
        val ch = min(u * heightU, safeBottom - safeTop - u * 4f)
        val cx = (safeLeft + safeRight) / 2f
        val cy = (safeTop + safeBottom) / 2f
        out.set(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
    }

    private fun layoutPause() {
        cardRect(rA, 90f)
        val bw = rA.width() - u * 20f
        val bh = u * 10.5f
        val cx = rA.centerX()
        var y = rA.top + u * 25f
        val gap = u * 3.2f
        bResume.set(cx - bw / 2f, y, cx + bw / 2f, y + bh).also { it.primary = true; it.label = "RESUME" }
        y += bh + gap
        bRestart.set(cx - bw / 2f, y, cx + bw / 2f, y + bh).also { it.label = if (mode == MODE_RUSH) "START OVER" else "RESTART REEL" }
        y += bh + gap
        val half = (bw - gap) / 2f
        bPSound.set(cx - bw / 2f, y, cx - bw / 2f + half, y + bh).also { it.label = if (soundOn) "SOUND ON" else "SOUND OFF" }
        bPMusic.set(cx + bw / 2f - half, y, cx + bw / 2f, y + bh).also { it.label = if (musicOn) "MUSIC ON" else "MUSIC OFF" }
        y += bh + gap
        bMenu.set(cx - bw / 2f, y, cx + bw / 2f, y + bh).also { it.label = "MAIN MENU" }
    }

    private fun layoutWin() {
        cardRect(rA, 102f)
        val bw = rA.width() - u * 20f
        val bh = u * 11f
        val cx = rA.centerX()
        val y = rA.bottom - u * 9f - bh * 2f - u * 3f
        bNext.set(cx - bw / 2f, y, cx + bw / 2f, y + bh).also { it.primary = true; it.label = "NEXT REEL" }
        bWinMenu.set(cx - bw / 2f, y + bh + u * 3f, cx + bw / 2f, y + bh * 2f + u * 3f).also { it.label = "MAIN MENU" }
    }

    private fun layoutTimeUp() {
        cardRect(rA, 102f)
        val bw = rA.width() - u * 20f
        val bh = u * 11f
        val cx = rA.centerX()
        val y = rA.bottom - u * 9f - bh * 2f - u * 3f
        bAgain.set(cx - bw / 2f, y, cx + bw / 2f, y + bh).also { it.primary = true; it.label = "ROLL AGAIN" }
        bEndMenu.set(cx - bw / 2f, y + bh + u * 3f, cx + bw / 2f, y + bh * 2f + u * 3f).also { it.label = "MAIN MENU" }
    }

    private fun onButton(b: Btn) {
        if (!b.enabled) {
            if (b.id == B_HINT) {
                sfx(Sfx.NOPE, 0.7f)
                floatText("Earn hints with 3-star reels!", b.rect.centerX().coerceIn(u * 30f, w - u * 30f), b.rect.top - u * 4f)
            } else if (b.id == B_UNDO) sfx(Sfx.NOPE, 0.5f)
            return
        }
        sfx(Sfx.BUTTON, 0.8f)
        host.haptic(false)
        val cx = b.rect.centerX(); val cy = b.rect.centerY()
        when (b.id) {
            B_PLAY -> {
                if (!seenHowTo) { seenHowTo = true; saveMeta(); transition(ACT_HOWTO, cx, cy) }
                else transition(ACT_STORY, cx, cy)
            }
            B_RUSH -> transition(ACT_RUSH, cx, cy)
            B_HOWTO -> transition(ACT_HOWTO, cx, cy)
            B_SOUND, B_P_SOUND -> {
                soundOn = !soundOn
                host.setAudio(soundOn, musicOn)
                saveMeta()
                if (soundOn) sfx(Sfx.BUTTON)
            }
            B_MUSIC, B_P_MUSIC -> {
                musicOn = !musicOn
                host.setAudio(soundOn, musicOn)
                saveMeta()
            }
            B_PAUSE -> openOverlay(OV_PAUSE)
            B_UNDO -> doUndo()
            B_HINT -> useHint()
            B_RESUME -> openOverlay(OV_NONE)
            B_RESTART -> if (mode == MODE_RUSH) transition(ACT_RUSH, cx, cy) else transition(ACT_RESTART_STORY, cx, cy)
            B_MENU, B_WIN_MENU, B_END_MENU -> transition(ACT_TITLE, cx, cy)
            B_NEXT -> transition(ACT_NEXT_STORY, cx, cy)
            B_AGAIN -> transition(ACT_RUSH, cx, cy)
            B_BACK -> transition(ACT_TITLE, cx, cy)
        }
        refreshButtons()
    }

    // ================================================================== input

    fun touchDown(x: Float, y: Float) {
        lookTX = x; lookTY = y
        if (trPhase != 0) { down = false; return }
        down = true
        downX = x; downY = y; downT = time
        moved = false
        longFired = false
        pressedBtn = null
        pressedTile = -1
        for (b in allBtns) if (b.contains(x, y, u * 1.5f)) { pressedBtn = b; break }
        if (pressedBtn == null && scene == SCENE_PLAY && overlay == OV_NONE && !solvedPending && !celebrate) {
            pressedTile = tileAt(x, y)
        }
    }

    fun touchMove(x: Float, y: Float) {
        lookTX = x; lookTY = y
        if (!down) return
        if (hypot(x - downX, y - downY) > u * 3.5f) {
            moved = true
            if (pressedTile >= 0 && tileAt(x, y) != pressedTile) pressedTile = -1
        }
    }

    fun touchUp(x: Float, y: Float) {
        if (!down) return
        down = false
        if (trPhase != 0) { pressedBtn = null; pressedTile = -1; return }
        val b = pressedBtn
        pressedBtn = null
        if (longFired) { pressedTile = -1; return }
        if (b != null) {
            if (b.contains(x, y, u * 3f)) onButton(b)
            return
        }
        val t = pressedTile
        pressedTile = -1
        if (t >= 0 && tileAt(x, y) == t) { tapTile(t); return }
        // generic taps
        when {
            scene == SCENE_HOWTO -> {
                if (overlayTimeOk()) nextHowPage()
            }
            scene == SCENE_PLAY && overlay == OV_INTRO -> if (overlayT > 0.35f) {
                sfx(Sfx.BUTTON, 0.6f)
                openOverlay(OV_NONE)
            }
        }
    }

    fun touchCancel() {
        down = false
        pressedBtn = null
        pressedTile = -1
    }

    private fun overlayTimeOk() = sceneT > 0.3f

    /** Returns true if handled, false if the app should close. */
    fun onBack(): Boolean {
        if (trPhase != 0) return true
        when (scene) {
            SCENE_TITLE -> return false
            SCENE_HOWTO -> { transition(ACT_TITLE); return true }
            SCENE_PLAY -> {
                when (overlay) {
                    OV_NONE -> if (!solvedPending && !celebrate) openOverlay(OV_PAUSE)
                    OV_PAUSE -> openOverlay(OV_NONE)
                    OV_INTRO -> openOverlay(OV_NONE)
                    OV_LEADER -> openOverlay(OV_PAUSE)
                    OV_WIN, OV_TIMEUP -> transition(ACT_TITLE)
                }
                refreshButtons()
                return true
            }
        }
        return true
    }

    private fun tileAt(x: Float, y: Float): Int {
        val p = puzzle ?: return -1
        if (!board.contains(x, y)) return -1
        val cx = ((x - bx) / ts).toInt().coerceIn(0, p.cols - 1)
        val cy = ((y - by) / ts).toInt().coerceIn(0, p.rows - 1)
        return p.idx(cx, cy)
    }

    private fun tileCx(p: Puzzle, i: Int) = bx + (p.x(i) + 0.5f) * ts
    private fun tileCy(p: Puzzle, i: Int) = by + (p.y(i) + 0.5f) * ts

    private fun hiddenNow(p: Puzzle, i: Int) = spec.lightsOut && !p.isVisibleInDark(i)

    private fun tapTile(i: Int) {
        val p = puzzle ?: return
        if (overlay != OV_NONE || solvedPending || celebrate || scene != SCENE_PLAY) return
        if (hiddenNow(p, i)) {
            shake[i] = 1f
            sfx(Sfx.NOPE, 0.5f, 1.2f)
            return
        }
        if (p.locked[i]) {
            shake[i] = 1f
            sfx(Sfx.NOPE, 0.6f)
            host.haptic(false)
            return
        }
        p.rotateCW(i)
        angT[i] += 90f
        angV[i] += 220f
        moves++
        pushUndo(i)
        sfx(Sfx.TAP, 0.9f, 0.94f + rng.nextFloat() * 0.12f)
        host.haptic(false)
        afterChange()
    }

    private fun pushUndo(i: Int) {
        if (undoTop >= undo.size) {
            System.arraycopy(undo, undo.size / 2, undo, 0, undo.size - undo.size / 2)
            undoTop = undo.size - undo.size / 2
        }
        undo[undoTop++] = i
    }

    private fun doUndo() {
        val p = puzzle ?: return
        if (solvedPending || celebrate) return
        while (undoTop > 0) {
            val i = undo[--undoTop]
            if (i >= p.size || p.locked[i]) continue
            p.rotateCCW(i)
            angT[i] -= 90f
            angV[i] -= 220f
            moves++
            sfx(Sfx.TAP, 0.8f, 0.8f)
            afterChange()
            return
        }
        sfx(Sfx.NOPE, 0.5f)
    }

    private fun useHint() {
        val p = puzzle ?: return
        if (solvedPending || celebrate || hints <= 0) return
        val t = p.hintTile()
        if (t < 0) return
        val k = p.tapsToFix(t)
        p.setRotation(t, p.fixedRotation(t))
        angT[t] += 90f * k
        p.locked[t] = true
        hints--
        hintsUsed++
        undoTop = 0
        hintGlow[t] = 1f
        pop[t] = 1f
        sfx(Sfx.HINT, 0.8f)
        burst(tileCx(p, t), tileCy(p, t), 10, 1, ts * 0.5f)
        saveMeta()
        afterChange()
    }

    private fun toggleLock(i: Int) {
        val p = puzzle ?: return
        if (hiddenNow(p, i)) return
        p.locked[i] = !p.locked[i]
        pop[i] = 0.6f
        sfx(Sfx.LOCK, 0.8f, if (p.locked[i]) 1f else 0.85f)
        host.haptic(true)
        saveBoard()
    }

    private fun afterChange() {
        val p = puzzle ?: return
        if (p.isSolved()) {
            solvedPending = true
            solvedWait = 0f
            pressedTile = -1
        } else saveBoard()
        refreshButtons()
    }

    // ================================================================== update

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        time += dt
        sceneT += dt
        overlayT += dt
        film.update(dt)

        // eyes follow the finger
        val bxp = if (scene == SCENE_TITLE) w / 2f else blottoPos[0]
        val byp = if (scene == SCENE_TITLE) h * 0.4f else blottoPos[1]
        val dx = lookTX - bxp; val dy = lookTY - byp
        val dd = max(1f, hypot(dx, dy))
        val tx = if (lookTX == 0f && lookTY == 0f) sin(time * 0.7f) * 0.6f else dx / dd
        val ty = if (lookTX == 0f && lookTY == 0f) 0f else dy / dd
        lookX += (tx - lookX) * min(1f, dt * 8f)
        lookY += (ty - lookY) * min(1f, dt * 8f)

        // button press anims
        for (b in allBtns) {
            val target = if (b === pressedBtn && down) 1f else 0f
            b.press += (target - b.press) * min(1f, dt * 25f)
        }

        // long press to lock
        if (down && !moved && !longFired && pressedTile >= 0 && time - downT > 0.42f) {
            longFired = true
            toggleLock(pressedTile)
            pressedTile = -1
        }

        updateTransition(dt)
        updateParticles(dt)
        for (k in 0 until fMax) if (fLife[k] > 0f) { fLife[k] -= dt; fy[k] -= u * 6f * dt }

        if (scene == SCENE_PLAY) updatePlay(dt)
    }

    private fun updateTransition(dt: Float) {
        if (trPhase == 1) {
            trT += dt
            if (trT >= trDur) {
                performAction(trAction)
                trPhase = 2
                trT = 0f
                trCx = w / 2f; trCy = h / 2f
                if (trDur > 0.3f) sfx(Sfx.IRIS_OPEN, 0.5f)
            }
        } else if (trPhase == 2) {
            trT += dt
            if (trT >= trDur) { trPhase = 0; trT = 0f }
        }
    }

    private fun updatePlay(dt: Float) {
        val p = puzzle ?: return
        inkSfxCooldown -= dt
        val settleAll = trPhase == 0
        for (i in 0 until p.size) {
            // springy rotation (rubber hose!)
            val k = 520f
            val damp = 26f
            val acc = (angT[i] - ang[i]) * k - angV[i] * damp
            angV[i] += acc * dt
            ang[i] += angV[i] * dt
            if (abs(angT[i] - ang[i]) < 0.05f && abs(angV[i]) < 0.5f) { ang[i] = angT[i]; angV[i] = 0f }
            // keep numbers bounded
            if (angT[i] >= 3600f || angT[i] <= -3600f) {
                val wrap = (angT[i] / 360f).toInt() * 360f
                angT[i] -= wrap; ang[i] -= wrap
            }
            pop[i] = max(0f, pop[i] - dt * 3f)
            shake[i] = max(0f, shake[i] - dt * 3.5f)
            hintGlow[i] = max(0f, hintGlow[i] - dt * 0.8f)
            wave[i] = max(0f, wave[i] - dt * 2.5f)

            // ink flow
            val settled = abs(angT[i] - ang[i]) < 10f
            if (p.isConnected(i)) {
                val parentFull = if (i == p.src) true else {
                    val par = p.neighbor(i, p.entry[i])
                    par >= 0 && inkA[par] >= 1f
                }
                if (inkA[i] <= 0f) inkDir[i] = if (i == p.src) -1 else (p.entry[i] - p.rot[i] + 4) and 3
                if (settled && parentFull && settleAll && inkA[i] < 1f) {
                    val speed = if (solvedPending) 14f else 8f
                    inkA[i] = min(1f, inkA[i] + dt * speed)
                    if (inkA[i] >= 1f) onInkArrived(p, i)
                }
            } else if (inkA[i] > 0f) {
                inkA[i] = max(0f, inkA[i] - dt * 7f)
            }
            // bulbs
            if (p.isBulb(i)) {
                val want = if (p.isConnected(i) && inkA[i] >= 1f) 1f else 0f
                val before = litA[i]
                litA[i] += (want - litA[i]) * min(1f, dt * 10f)
                if (want == 1f && before < 0.5f && litA[i] >= 0.5f) onBulbLit(p, i)
                if (want == 0f && before >= 0.5f && litA[i] < 0.5f) litCount = max(0, litCount - 1)
            }
            // lights-out reveal
            val vis = if (spec.lightsOut) (if (p.isVisibleInDark(i)) 1f else 0f) else 1f
            val beforeR = revealA[i]
            revealA[i] += (vis - revealA[i]) * min(1f, dt * 6f)
            if (spec.lightsOut && beforeR < 0.5f && revealA[i] >= 0.5f && sceneT > 0.5f) {
                pop[i] = 0.7f
                burst(tileCx(p, i), tileCy(p, i), 5, 3, ts * 0.4f)
            }
        }

        // solved -> wait for the ink to reach everything, then celebrate
        if (solvedPending) {
            solvedWait += dt
            var all = true
            for (i in 0 until p.size) if (inkA[i] < 1f) { all = false; break }
            if (all || solvedWait > 5f) {
                solvedPending = false
                if (mode == MODE_STORY) startCelebration() else rushSolvedBoard()
            }
        }

        if (celebrate) {
            celebrateT += dt
            if (mode == MODE_STORY && celebrateT > 1.6f && overlay == OV_NONE) {
                openOverlay(OV_WIN)
            }
        }
        if (overlay == OV_WIN) {
            while (starsShown < 3 && overlayT >= 0.45f + starsShown * 0.3f) {
                if (starsShown < earnedStars) sfx(Sfx.STAR, 0.8f, 1f + starsShown * 0.12f)
                starsShown++
            }
        }

        // intro auto-dismiss
        if (overlay == OV_INTRO && overlayT > 6f) openOverlay(OV_NONE)

        // leader countdown
        if (overlay == OV_LEADER) {
            val sec = overlayT.toInt()
            if (sec != lastTickSec && sec < 3) {
                lastTickSec = sec
                sfx(Sfx.BEEP, 0.6f)
            }
            if (overlayT >= 3f) {
                lastTickSec = -1
                openOverlay(OV_NONE)
            }
        }

        // rush clock
        if (mode == MODE_RUSH) {
            if (overlay == OV_NONE && trPhase == 0 && !solvedPending && !celebrate) {
                rushTime -= dt
                val sec = kotlin.math.ceil(rushTime).toInt()
                if (sec <= 10 && sec != lastTickSec && sec > 0) {
                    lastTickSec = sec
                    sfx(Sfx.TICK, 0.7f)
                }
                if (rushTime <= 0f) {
                    rushTime = 0f
                    timeUp()
                }
            }
            if (celebrate) {
                rushNextDelay -= dt
                if (rushNextDelay <= 0f) {
                    celebrate = false
                    transition(ACT_NEXT_RUSH, w / 2f, h / 2f, fast = true)
                }
            }
        }
    }

    private fun onInkArrived(p: Puzzle, i: Int) {
        pop[i] = max(pop[i], 0.35f)
        if (inkSfxCooldown <= 0f && i != p.src) {
            inkSfxCooldown = 0.07f
            sfx(Sfx.INK, 0.28f, 0.8f + min(p.dist[i], 20) * 0.04f)
        }
    }

    private fun onBulbLit(p: Puzzle, i: Int) {
        pop[i] = 1f
        val rate = BULB_RATES[litCount % BULB_RATES.size]
        litCount++
        sfx(Sfx.BULB, 0.55f, rate)
        burst(tileCx(p, i), tileCy(p, i), 8, 2, ts * 0.35f)
    }

    private fun startCelebration() {
        val p = puzzle ?: return
        celebrate = true
        celebrateT = 0f
        sfx(Sfx.WIN, 0.9f)
        host.haptic(true)
        // wave outward from the inkwell
        for (i in 0 until p.size) wave[i] = 1f + p.dist[i].coerceAtLeast(0) * 0.08f
        for (i in 0 until p.size) if (p.isBulb(i)) burst(tileCx(p, i), tileCy(p, i), 12, 0, ts * 0.3f)
        // score
        val par = max(1, parAtStart)
        var s = when {
            moves <= par + max(2, par / 5) -> 3
            moves <= par * 2 + 3 -> 2
            else -> 1
        }
        if (hintsUsed >= 3) s = min(s, 1) else if (hintsUsed >= 1) s = min(s, 2)
        earnedStars = s
        earnedHint = s == 3 && hints < MAX_HINTS
        if (earnedHint) hints++
        stars += s
        reels++
        level++
        cheer = CHEERS[rng.nextInt(CHEERS.size)]
        host.saveInt("boardLevel", -1)
        host.saveString("board", null)
        saveMeta()
    }

    private fun rushSolvedBoard() {
        val p = puzzle ?: return
        rushSolved++
        val bonus = (4 + p.size / 3).coerceAtMost(20)
        rushTime = min(99f, rushTime + bonus)
        sfx(Sfx.SOLVED, 0.9f)
        host.haptic(true)
        floatText("+${bonus}s", w / 2f, board.centerY())
        for (i in 0 until p.size) wave[i] = 1f + p.dist[i].coerceAtLeast(0) * 0.06f
        for (i in 0 until p.size) if (p.isBulb(i)) burst(tileCx(p, i), tileCy(p, i), 6, 0, ts * 0.3f)
        celebrate = true
        celebrateT = 0f
        rushNextDelay = 0.8f
    }

    private fun timeUp() {
        newBest = rushSolved > rushBest
        if (newBest) rushBest = rushSolved
        saveMeta()
        sfx(Sfx.TIMEUP, 0.9f)
        host.haptic(true)
        openOverlay(OV_TIMEUP)
    }

    private fun nextHowPage() {
        howPage++
        sfx(Sfx.BUTTON, 0.7f)
        sceneT = 0f
        if (howPage >= 4) {
            howPage = 3
            transition(ACT_STORY)
        }
    }

    // ================================================================== particles

    private fun clearParticles() {
        for (k in 0 until pMax) pLife[k] = 0f
        for (k in 0 until fMax) fLife[k] = 0f
    }

    /** type 0 = ink splat, 1 = sparkle star, 2 = ray dot, 3 = smoke puff */
    private fun burst(x: Float, y: Float, n: Int, type: Int, spread: Float) {
        for (k in 0 until n) {
            val i = pNext
            pNext = (pNext + 1) % pMax
            val a = rng.nextFloat() * 6.2832f
            val sp = (0.4f + rng.nextFloat()) * u * (if (type == 0) 45f else if (type == 3) 12f else 30f)
            px[i] = x + cos(a) * spread * 0.3f
            py[i] = y + sin(a) * spread * 0.3f
            pvx[i] = cos(a) * sp
            pvy[i] = sin(a) * sp - (if (type == 0) u * 25f else 0f)
            pMaxLife[i] = 0.5f + rng.nextFloat() * 0.6f
            pLife[i] = pMaxLife[i]
            pSize[i] = u * (if (type == 0) 0.8f + rng.nextFloat() * 1.4f else if (type == 3) 2f + rng.nextFloat() * 2f else 0.9f + rng.nextFloat())
            pType[i] = type
            pRot[i] = rng.nextFloat() * 6f
        }
    }

    private fun updateParticles(dt: Float) {
        for (i in 0 until pMax) {
            if (pLife[i] <= 0f) continue
            pLife[i] -= dt
            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt
            when (pType[i]) {
                0 -> pvy[i] += u * 140f * dt
                3 -> { pvx[i] *= (1f - dt * 3f); pvy[i] = pvy[i] * (1f - dt * 3f) - u * 4f * dt }
                else -> { pvx[i] *= (1f - dt * 2.5f); pvy[i] *= (1f - dt * 2.5f) }
            }
            pRot[i] += dt * 4f
        }
    }

    private fun floatText(s: String, x: Float, y: Float) {
        val k = fNext
        fNext = (fNext + 1) % fMax
        fText[k] = s; fx[k] = x; fy[k] = y; fLife[k] = 1.4f
    }

    // ================================================================== drawing

    fun draw(c: Canvas) {
        c.save()
        c.translate(film.weaveX, film.weaveY)
        when (scene) {
            SCENE_TITLE -> drawTitle(c)
            SCENE_HOWTO -> drawHowTo(c)
            SCENE_PLAY -> drawPlay(c)
        }
        drawParticles(c)
        drawFloatTexts(c)
        drawIris(c)
        c.restore()
        film.draw(c)
    }

    private fun drawButton(c: Canvas, b: Btn) {
        if (!b.visible) return
        val sh = u * 1.2f
        val pr = b.press
        val off = sh * pr
        val alphaDim = !b.enabled
        if (b.circle) {
            val r = b.rect.width() / 2f
            val cx = b.rect.centerX(); val cy = b.rect.centerY()
            fillP.color = Pal.ink
            c.drawCircle(cx + sh, cy + sh, r, fillP)
            fillP.color = if (alphaDim) Pal.g1 else Pal.panel
            c.drawCircle(cx + off, cy + off, r, fillP)
            strokeP.color = Pal.ink
            strokeP.strokeWidth = u * 0.7f
            c.drawCircle(cx + off, cy + off, r, strokeP)
            Art.icon(c, b.icon, cx + off, cy + off, r * 0.9f, if (alphaDim) Pal.g2 else Pal.ink, b.off)
            if (b.badge >= 0) {
                val bxp = cx + r * 0.72f + off
                val byp = cy - r * 0.72f + off
                fillP.color = Pal.ink
                c.drawCircle(bxp, byp, r * 0.42f, fillP)
                Art.label(c, b.badge.toString(), bxp, byp, r * 0.5f, Pal.panel, Fonts.display)
            }
            return
        }
        val rad = b.rect.height() * 0.5f
        rA.set(b.rect); rA.offset(sh, sh)
        fillP.color = Pal.ink
        c.drawRoundRect(rA, rad, rad, fillP)
        rA.set(b.rect); rA.offset(off, off)
        fillP.color = if (b.primary) Pal.ink else Pal.panel
        c.drawRoundRect(rA, rad, rad, fillP)
        strokeP.color = Pal.ink
        strokeP.strokeWidth = u * 0.7f
        c.drawRoundRect(rA, rad, rad, strokeP)
        if (b.primary) {
            strokeP.color = Pal.panel
            strokeP.strokeWidth = u * 0.3f
            rB.set(rA.left + u * 1.1f, rA.top + u * 1.1f, rA.right - u * 1.1f, rA.bottom - u * 1.1f)
            c.drawRoundRect(rB, rad - u, rad - u, strokeP)
        }
        val size = Art.fitSize(b.label, b.rect.height() * 0.4f, b.rect.width() - u * 8f, Fonts.display)
        Art.label(c, b.label, rA.centerX(), rA.centerY(), size, if (b.primary) Pal.panel else Pal.ink, Fonts.display)
    }

    // ------------------------------------------------------------------ title

    private fun drawTitle(c: Canvas) {
        c.drawColor(Pal.paper)
        val cx = w / 2f
        val cy = tRingCy
        Art.sunburst(c, cx, cy, hypot(w, h), 22, time * 6f, Pal.paper, lerpColor(Pal.paper, Pal.g1, 0.75f))
        // deco ring, like a film reel
        val ringR = tRingR
        rB.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        rB.offset(u * 1.4f, u * 1.8f)
        fillP.color = Pal.ink
        c.drawOval(rB, fillP)
        fillP.color = Pal.panel
        c.drawCircle(cx, cy, ringR, fillP)
        strokeP.color = Pal.ink
        strokeP.strokeWidth = u * 0.8f
        c.drawCircle(cx, cy, ringR, strokeP)
        strokeP.strokeWidth = u * 0.3f
        c.drawCircle(cx, cy, ringR - u * 1.8f, strokeP)
        for (k in 0 until 6) {
            val a = time * 0.4f + k * (PI / 3).toFloat()
            fillP.color = Pal.g1
            c.drawCircle(cx + cos(a) * ringR * 0.7f, cy + sin(a) * ringR * 0.7f, ringR * 0.16f, fillP)
        }
        fillP.color = Pal.g1
        c.drawCircle(cx, cy, ringR * 0.08f, fillP)
        // Blotto
        val br = ringR * 0.37f
        Art.blotto(c, cx, cy + br * 0.3f, br, time, if (sceneT < 1.2f) Art.MOOD_HAPPY else Art.MOOD_IDLE, lookX, lookY)

        // Logo with bouncing letters
        val logo = "BLOTTO"
        val size = Art.fitSize(logo, u * 19f, w - u * 14f, Fonts.display)
        val lw = Art.textWidth(logo, size, Fonts.display)
        var x = cx - lw / 2f
        val ly = tLogoY
        for (k in logo.indices) {
            val ch = logo.substring(k, k + 1)
            val cw = Art.textWidth(ch, size, Fonts.display)
            val bounce = sin(time * 5.2f - k * 0.7f) * u * 1.4f
            c.save()
            c.rotate(sin(time * 2.6f - k) * 4f, x + cw / 2f, ly)
            Art.title(c, ch, x + cw / 2f, ly + bounce, size, Pal.panel, Pal.ink, u * 1.1f, u * 1.3f)
            c.restore()
            x += cw
        }
        // ribbon subtitle
        val rw = min(u * 74f, w - u * 22f)
        val sub = "AN INK-PIPE PUZZLE REEL"
        Art.ribbon(c, cx, tRibbonY, rw, u * 7.5f, Pal.panel, Pal.ink, u * 0.5f)
        Art.label(c, sub, cx, tRibbonY, Art.fitSize(sub, u * 4.2f, rw - u * 6f, Fonts.body), Pal.ink)

        for (b in allBtns) drawButton(c, b)

        // footer
        if (hudStatsKey != reels * 1000003 + stars * 1009 + rushBest) {
            hudStatsKey = reels * 1000003 + stars * 1009 + rushBest
            hudStatsStr = "Reels $reels   ·   Stars $stars   ·   Best Rush $rushBest"
        }
        Art.label(c, hudStatsStr, cx, tStatsY, Art.fitSize(hudStatsStr, u * 4f, w - u * 10f, Fonts.body), Pal.ink)
        val credit = "~ A Pranvir Singh Picture ~"
        Art.label(c, credit, cx, tCreditY, Art.fitSize(credit, u * 3.4f, w - u * 34f, Fonts.body), Pal.g3)
    }

    // ------------------------------------------------------------------ how to play

    private fun drawHowTo(c: Canvas) {
        c.drawColor(Pal.ink)
        cardRect(rA, 150f)
        Art.decoFrame(c, rA, u, Pal.panel, Pal.ink, u * 3f)
        val cx = rA.centerX()
        val titles = arrayOf("TWIST THE PIPES", "FLOW THE INK", "BOLT IT DOWN", "NO LOOSE ENDS")
        val bodies = arrayOf(
            arrayOf("Tap any pipe tile", "to give it a quarter turn."),
            arrayOf("Carry the ink from Blotto's", "inkwell to every sleepy lamp."),
            arrayOf("Sure of a pipe? Long-press", "to bolt it so it won't turn."),
            arrayOf("Every pipe end must meet another.", "Fewer twists earn more stars!")
        )
        val pg = howPage.coerceIn(0, 3)
        Art.label(c, "~ CHAPTER ${pg + 1} ~", cx, rA.top + u * 12f, u * 4.2f, Pal.g2)
        val tSize = Art.fitSize(titles[pg], u * 8.5f, rA.width() - u * 16f, Fonts.display)
        Art.title(c, titles[pg], cx, rA.top + u * 22f, tSize, Pal.panel, Pal.ink, u * 0.3f, 0f)
        // demo stage
        val dcy = rA.top + rA.height() * 0.47f
        val saveInv = Pal.inv
        drawHowDemo(c, pg, cx, dcy)
        Pal.set(saveInv)
        for ((k, line) in bodies[pg].withIndex()) {
            Art.label(c, line, cx, rA.bottom - u * 30f + k * u * 7f,
                Art.fitSize(line, u * 5f, rA.width() - u * 14f, Fonts.body), Pal.panel)
        }
        val blink = if (((time * 1.6f).toInt() % 2) == 0) 255 else 130
        Art.label(c, if (pg < 3) "tap to continue  (${pg + 1}/4)" else "tap to roll the first reel!", cx, rA.bottom - u * 10f,
            u * 4f, withAlpha(Pal.panel, blink))
        drawButton(c, bBack)
    }

    private fun drawHowDemo(c: Canvas, pg: Int, cx: Float, cy: Float) {
        val t = u * 24f
        val cyc = sceneT
        when (pg) {
            0 -> {
                val step = (cyc / 1.2f).toInt()
                val frac = (cyc % 1.2f) / 1.2f
                val a = step * 90f + 90f * easeOutBack(clamp01((frac - 0.1f) / 0.25f))
                c.save(); c.translate(cx, cy); c.rotate(a)
                Art.tile(c, t * 1.4f, Dir.N or Dir.E, Art.KIND_PIPE, 0f, -1, false, false, time, u)
                c.restore()
                val press = if (frac < 0.12f) 1f else 0f
                drawGlove(c, cx + t * 0.5f, cy + t * 0.55f - press * u * 2f)
            }
            1 -> {
                val period = 3.2f
                val ph = cyc % period
                val turned = ph > 0.8f
                val a = if (turned) 90f * easeOutBack(clamp01((ph - 0.8f) / 0.3f)) else 0f
                val inkMid = if (turned) clamp01((ph - 1.2f) / 0.35f) else 0f
                val inkEnd = if (turned) clamp01((ph - 1.55f) / 0.35f) else 0f
                val xs = floatArrayOf(cx - t, cx, cx + t)
                // source
                c.save(); c.translate(xs[0], cy)
                Art.tile(c, t, Dir.E, Art.KIND_SOURCE, 1f, -1, false, false, time, u)
                Art.inkwell(c, t, time, 1f, u)
                c.restore()
                c.save(); c.translate(xs[1], cy); c.rotate(a)
                Art.tile(c, t, Dir.N or Dir.S, Art.KIND_PIPE, inkMid, 2, false, false, time, u)
                c.restore()
                c.save(); c.translate(xs[2], cy)
                Art.tile(c, t, Dir.W, Art.KIND_BULB, inkEnd, 3, false, false, time, u)
                Art.bulb(c, t, if (inkEnd >= 1f) 1f else 0f, if (inkEnd >= 1f) 1f else 0f, time, 0f, u, 90f)
                c.restore()
                if (ph < 0.8f) drawGlove(c, xs[1] + t * 0.3f, cy + t * 0.4f)
            }
            2 -> {
                val period = 2.4f
                val ph = cyc % period
                val locked = ph > 0.9f
                val s = 1f + (if (ph in 0.9f..1.1f) 0.12f else 0f)
                c.save(); c.translate(cx, cy); c.scale(s, s)
                Art.tile(c, t * 1.4f, Dir.N or Dir.E or Dir.S, Art.KIND_PIPE, 0f, -1, locked, ph < 0.9f, time, u)
                c.restore()
                if (ph < 1.2f) drawGlove(c, cx + t * 0.5f, cy + t * 0.55f)
                if (ph < 0.9f) {
                    // progress ring for the long-press
                    strokeP.color = Pal.panel
                    strokeP.strokeWidth = u * 1.2f
                    rB.set(cx - t, cy - t, cx + t, cy + t)
                    c.drawArc(rB, -90f, 360f * clamp01(ph / 0.9f), false, strokeP)
                }
            }
            else -> {
                for (k in 0 until 3) {
                    val appear = clamp01((cyc - 0.3f - k * 0.3f) / 0.2f)
                    val sc = easeOutBack(appear)
                    if (sc <= 0.01f) continue
                    c.save()
                    c.translate(cx + (k - 1) * u * 20f, cy - u * 10f - (if (k == 1) u * 4f else 0f))
                    c.scale(sc, sc)
                    Art.star(c, 0f, 0f, u * 8f, true, Pal.panel, Pal.panel, Pal.ink, u * 0.6f)
                    c.restore()
                }
                val mcy = cy + u * 20f
                val mr = u * 15f
                c.save()
                irisPath.reset()
                irisPath.addCircle(cx, mcy, mr, Path.Direction.CW)
                c.clipPath(irisPath)
                Art.sunburst(c, cx, mcy, mr, 16, time * 12f, Pal.panel, Pal.g1)
                Art.blotto(c, cx, mcy + u * 2.2f, u * 5.2f, time, Art.MOOD_HAPPY, 0f, -0.5f)
                c.restore()
                strokeP.color = Pal.panel
                strokeP.strokeWidth = u * 0.8f
                c.drawCircle(cx, mcy, mr, strokeP)
            }
        }
    }

    private fun drawGlove(c: Canvas, x: Float, y: Float) {
        val r = u * 3.2f
        fillP.color = Pal.WHITE
        strokeP.color = Pal.BLACK
        strokeP.strokeWidth = u * 0.5f
        // pointing finger
        rB.set(x - r * 0.3f, y - r * 1.9f, x + r * 0.3f, y)
        c.drawRoundRect(rB, r * 0.3f, r * 0.3f, fillP)
        c.drawRoundRect(rB, r * 0.3f, r * 0.3f, strokeP)
        c.drawCircle(x + r * 0.2f, y + r * 0.5f, r, fillP)
        c.drawCircle(x + r * 0.2f, y + r * 0.5f, r, strokeP)
        rB.set(x - r * 0.4f, y + r * 1.3f, x + r * 1.0f, y + r * 1.9f)
        c.drawRect(rB, fillP)
        c.drawRect(rB, strokeP)
    }

    // ------------------------------------------------------------------ play

    private fun drawPlay(c: Canvas) {
        val p = puzzle ?: return
        c.drawColor(Pal.paper)
        val bcx = board.centerX(); val bcy = board.centerY()
        Art.sunburst(c, bcx, bcy, hypot(w, h), 26, -time * 4f, Pal.paper, lerpColor(Pal.paper, Pal.g1, 0.55f))

        // frame
        val pad = u * 2.4f
        rA.set(board.left - pad, board.top - pad, board.right + pad, board.bottom + pad)
        rB.set(rA); rB.offset(u * 1.5f, u * 1.8f)
        fillP.color = Pal.ink
        c.drawRoundRect(rB, u * 3f, u * 3f, fillP)
        fillP.color = Pal.g1
        c.drawRoundRect(rA, u * 3f, u * 3f, fillP)
        strokeP.color = Pal.ink
        strokeP.strokeWidth = u * 0.8f
        c.drawRoundRect(rA, u * 3f, u * 3f, strokeP)
        // hatch lines inside frame for texture
        strokeP.color = withAlpha(Pal.ink, 22)
        strokeP.strokeWidth = u * 0.25f
        c.save()
        c.clipRect(rA)
        var hx = rA.left - rA.height()
        while (hx < rA.right) {
            c.drawLine(hx, rA.bottom, hx + rA.height(), rA.top, strokeP)
            hx += u * 2.2f
        }
        c.restore()

        val beat = time * PI.toFloat() * 2f / 0.6f
        // tiles
        for (i in 0 until p.size) {
            val cx = tileCx(p, i)
            val cy = tileCy(p, i)
            val idle = 0.012f * sin(beat + p.x(i) * 0.7f + p.y(i) * 0.5f)
            val wv = wave[i]
            val waveBump = if (wv > 1f) 0f else sin(wv * PI.toFloat()) * 0.16f
            val pressS = if (i == pressedTile && down) -0.06f else 0f
            val sc = 1f + idle + pop[i] * 0.14f + waveBump + pressS
            val sx = if (shake[i] > 0f) sin(shake[i] * 40f) * u * 1.2f * shake[i] else 0f
            c.save()
            c.translate(cx + sx, cy - waveBump * ts * 0.4f)
            c.scale(sc, sc)
            // shadow (rotates with the panel but offset in screen space)
            c.save()
            c.translate(ts * 0.045f, ts * 0.055f)
            c.rotate(ang[i])
            fillP.color = withAlpha(Pal.ink, 150)
            val hh = ts * 0.46f
            rB.set(-hh, -hh, hh, hh)
            c.drawRoundRect(rB, ts * 0.13f, ts * 0.13f, fillP)
            c.restore()

            c.rotate(ang[i])
            val kind = if (i == p.src) Art.KIND_SOURCE else if (p.isBulb(i)) Art.KIND_BULB else Art.KIND_PIPE
            if (spec.lightsOut && revealA[i] < 0.5f) {
                Art.hiddenTile(c, ts, time, i * 7)
            } else {
                Art.tile(c, ts, p.solutionMask(i), kind, inkA[i], inkDir[i], p.locked[i], i == pressedTile && down, time, u)
                if (kind != Art.KIND_PIPE) {
                    c.rotate(-ang[i])
                    if (kind == Art.KIND_SOURCE) {
                        Art.inkwell(c, ts, time, if (celebrate) 1f else 0f, u)
                    } else {
                        val sm = p.solutionMask(i)
                        var dl = 0
                        for (d in 0..3) if (sm and Dir.BIT[d] != 0) { dl = d; break }
                        val base = 180f + dl * 90f + ang[i]
                        Art.bulb(c, ts, litA[i], if (celebrate) 1.6f else litA[i], time + i * 0.37f, pop[i], u, base)
                    }
                }
            }
            c.restore()
            if (hintGlow[i] > 0f) {
                strokeP.color = withAlpha(Pal.ink, (255 * hintGlow[i]).toInt())
                strokeP.strokeWidth = u * 0.8f
                c.drawCircle(cx, cy, ts * (0.55f + (1f - hintGlow[i]) * 0.4f), strokeP)
            }
        }
        // ink drips from open pipe ends
        if (!celebrate) {
            for (i in 0 until p.size) {
                if (inkA[i] < 1f || !p.isConnected(i) || abs(angT[i] - ang[i]) > 1f) continue
                if (spec.lightsOut && revealA[i] < 0.5f) continue
                for (d in 0..3) {
                    if (!p.isLooseEnd(i, d)) continue
                    val ph = ((time * 1.3f + i * 0.31f + d * 0.17f) % 1f)
                    val dist = ts * (0.47f + ph * 0.3f)
                    val x = tileCx(p, i) + Dir.DX[d] * dist
                    val y = tileCy(p, i) + Dir.DY[d] * dist
                    val a = ((1f - ph) * 230).toInt()
                    c.save()
                    c.translate(x, y)
                    c.rotate(Dir.opposite(d) * 90f)
                    Art.drop(c, 0f, 0f, ts * 0.05f * (1f - ph * 0.4f), withAlpha(Pal.ink, a))
                    c.restore()
                }
            }
        }

        drawHud(c, p)

        // Blotto
        val mood = when {
            overlay == OV_TIMEUP -> if (newBest) Art.MOOD_HAPPY else Art.MOOD_SAD
            celebrate || overlay == OV_WIN -> Art.MOOD_HAPPY
            mode == MODE_RUSH && rushTime < 10f && overlay == OV_NONE -> Art.MOOD_WORRY
            else -> Art.MOOD_IDLE
        }
        Art.blotto(c, blottoPos[0], blottoPos[1], blottoR, time, mood, lookX, lookY)

        when (overlay) {
            OV_INTRO -> drawIntro(c)
            OV_PAUSE -> drawPause(c)
            OV_WIN -> drawWin(c)
            OV_LEADER -> drawLeader(c)
            OV_TIMEUP -> drawTimeUp(c)
        }
    }

    private fun drawHud(c: Canvas, p: Puzzle) {
        val cx = (safeLeft + safeRight) / 2f
        val top = safeTop + u * 1f
        drawButton(c, bPause)
        drawButton(c, bUndo)
        drawButton(c, bHint)
        if (mode == MODE_STORY) {
            // plaque
            val pw = u * 44f
            val ph = u * 10.5f
            rA.set(cx - pw / 2f, top, cx + pw / 2f, top + ph)
            rB.set(rA); rB.offset(u * 1f, u * 1.2f)
            fillP.color = Pal.ink
            c.drawRoundRect(rB, u * 2f, u * 2f, fillP)
            fillP.color = Pal.ink
            c.drawRoundRect(rA, u * 2f, u * 2f, fillP)
            strokeP.color = Pal.panel
            strokeP.strokeWidth = u * 0.3f
            rB.set(rA.left + u, rA.top + u, rA.right - u, rA.bottom - u)
            c.drawRoundRect(rB, u * 1.2f, u * 1.2f, strokeP)
            Art.label(c, "REEL $hudLevel", cx, rA.centerY(), u * 5.8f, Pal.panel, Fonts.display)
            if (hudMovesKey != moves * 1000 + parAtStart) {
                hudMovesKey = moves * 1000 + parAtStart
                hudMovesStr = "Twists $moves  ·  Par $parAtStart"
            }
            Art.label(c, hudMovesStr, cx, rA.bottom + u * 4.5f, u * 4f, Pal.ink)
        } else {
            // film-reel timer
            val r = u * 8.5f
            val tcx = cx
            val tcy = top + r
            fillP.color = Pal.ink
            c.drawCircle(tcx + u, tcy + u * 1.2f, r, fillP)
            fillP.color = Pal.panel
            c.drawCircle(tcx, tcy, r, fillP)
            val frac = clamp01(rushTime / RUSH_START)
            fillP.color = Pal.g1
            rA.set(tcx - r, tcy - r, tcx + r, tcy + r)
            c.drawArc(rA, -90f, 360f * frac, true, fillP)
            strokeP.color = Pal.ink
            strokeP.strokeWidth = u * 0.7f
            c.drawCircle(tcx, tcy, r, strokeP)
            val sec = kotlin.math.ceil(rushTime).toInt()
            if (hudTimeKey != sec) { hudTimeKey = sec; hudTimeStr = sec.toString() }
            val shakeT = if (rushTime < 10f && overlay == OV_NONE) sin(time * 40f) * u * 0.4f else 0f
            Art.title(c, hudTimeStr, tcx + shakeT, tcy, u * 7f, Pal.panel, Pal.ink, u * 0.6f, 0f)
            Art.label(c, "SOLVED $rushSolved", cx, tcy + r + u * 4.5f, u * 4f, Pal.ink, Fonts.display)
        }
        // lamps counter at the top right
        val lit = p.litBulbs()
        val tot = p.totalBulbs()
        if (hudLampKey != lit * 1000 + tot) { hudLampKey = lit * 1000 + tot; hudLampStr = "$lit/$tot" }
        val lx = safeRight - u * 11f
        val ly = top + u * 5f
        Art.icon(c, Art.ICON_HINT, lx - u * 5f, ly, u * 4.2f, Pal.ink, false)
        Art.label(c, hudLampStr, lx + u * 2.5f, ly + u * 0.5f, u * 4.6f, Pal.ink, Fonts.display)
    }

    private fun dim(c: Canvas, a: Float) {
        fillP.color = withAlpha(Pal.BLACK, (a * 255).toInt())
        c.drawRect(-u * 4f, -u * 4f, w + u * 4f, h + u * 4f, fillP)
    }

    private fun drawIntro(c: Canvas) {
        // fades out quickly after dismissal is handled by overlay switch; here full intertitle
        c.drawColor(Pal.ink)
        cardRect(rA, 150f)
        Art.decoFrame(c, rA, u, Pal.panel, Pal.ink, u * 3f)
        val cx = rA.centerX()
        val appear = easeOutBack(clamp01(overlayT / 0.5f))
        c.save()
        c.scale(appear, appear, cx, rA.centerY())
        Art.label(c, "~ REEL $hudLevel ~", cx, rA.top + u * 16f, u * 5f, Pal.g2)
        val tSize = Art.fitSize(spec.title.uppercase(), u * 9f, rA.width() - u * 16f, Fonts.display)
        Art.title(c, spec.title.uppercase(), cx, rA.top + u * 30f, tSize, Pal.panel, Pal.ink, u * 0.3f, 0f)
        // divider flourish
        strokeP.color = Pal.panel
        strokeP.strokeWidth = u * 0.4f
        c.drawLine(cx - u * 22f, rA.top + u * 39f, cx - u * 3f, rA.top + u * 39f, strokeP)
        c.drawLine(cx + u * 3f, rA.top + u * 39f, cx + u * 22f, rA.top + u * 39f, strokeP)
        fillP.color = Pal.panel
        c.drawCircle(cx, rA.top + u * 39f, u * 1.2f, fillP)
        val lines = spec.blurb.split('\n')
        for ((k, line) in lines.withIndex()) {
            Art.label(c, line, cx, rA.top + u * 50f + k * u * 7f, Art.fitSize(line, u * 5f, rA.width() - u * 14f, Fonts.body), Pal.panel)
        }
        // medallion with Blotto
        val mTop = rA.top + u * 52f + lines.size * u * 7f
        val mBottom = rA.bottom - u * 28f
        val mr = min(u * 26f, (mBottom - mTop) / 2f)
        if (mr > u * 8f) {
            val mcy = (mTop + mBottom) / 2f
            c.save()
            irisPath.reset()
            irisPath.addCircle(cx, mcy, mr, Path.Direction.CW)
            c.clipPath(irisPath)
            Art.sunburst(c, cx, mcy, mr, 18, time * 12f, Pal.panel, Pal.g1)
            val br = mr * 0.36f
            val mood = if (spec.negative || spec.lightsOut) Art.MOOD_WORRY else Art.MOOD_HAPPY
            Art.blotto(c, cx, mcy + br * 0.45f, br, time, mood, sin(time) * 0.5f, -0.2f)
            c.restore()
            strokeP.color = Pal.panel
            strokeP.strokeWidth = u * 0.9f
            c.drawCircle(cx, mcy, mr, strokeP)
            strokeP.strokeWidth = u * 0.3f
            c.drawCircle(cx, mcy, mr + u * 1.6f, strokeP)
        }
        val p = puzzle
        if (p != null) {
            val info = "${p.cols} × ${p.rows} tiles  ·  ${p.totalBulbs()} lamps"
            Art.label(c, info, cx, rA.bottom - u * 20f, u * 4f, Pal.g2)
        }
        c.restore()
        val blink = if (((time * 1.6f).toInt() % 2) == 0) 255 else 120
        Art.label(c, "tap to roll film", cx, rA.bottom - u * 10f, u * 4.4f, withAlpha(Pal.panel, blink))
    }

    private fun drawCard(c: Canvas, heightU: Float, appear: Float) {
        cardRect(rA, heightU)
        val drop = (1f - appear) * -h * 0.6f
        rA.offset(0f, drop)
        rB.set(rA); rB.offset(u * 1.8f, u * 2.2f)
        fillP.color = Pal.BLACK
        c.drawRoundRect(rB, u * 3f, u * 3f, fillP)
        Art.decoFrame(c, rA, u, Pal.ink, Pal.panel, u * 3f)
    }

    private fun drawPause(c: Canvas) {
        dim(c, 0.55f)
        drawCard(c, 90f, 1f)
        val cx = rA.centerX()
        Art.title(c, "INTERMISSION", cx, rA.top + u * 13f, Art.fitSize("INTERMISSION", u * 8f, rA.width() - u * 16f, Fonts.display),
            Pal.panel, Pal.ink, u * 0.9f, u * 0.9f)
        Art.label(c, "~ the band plays on ~", cx, rA.top + u * 20f, u * 4f, Pal.g3)
        drawButton(c, bResume); drawButton(c, bRestart); drawButton(c, bPSound); drawButton(c, bPMusic); drawButton(c, bMenu)
    }

    private fun drawWin(c: Canvas) {
        val appear = easeOutBack(clamp01(overlayT / 0.55f))
        dim(c, 0.45f * clamp01(overlayT / 0.3f))
        drawCard(c, 102f, appear)
        val cx = rA.centerX()
        val headSize = Art.fitSize(cheer, u * 12f, rA.width() - u * 14f, Fonts.display)
        val wob = sin(time * 6f) * 3f
        c.save()
        c.rotate(wob, cx, rA.top + u * 15f)
        Art.title(c, cheer, cx, rA.top + u * 15f, headSize, Pal.panel, Pal.ink, u * 1.1f, u * 1.3f)
        c.restore()
        Art.label(c, "Reel $hudLevel complete!", cx, rA.top + u * 26f, u * 4.6f, Pal.ink)
        for (k in 0 until 3) {
            val shown = k < starsShown
            val filled = k < earnedStars && shown
            val sc = if (shown) 1f + (if (filled) 0.15f * sin(time * 5f + k) else 0f) else 0.85f
            val sx = cx + (k - 1) * u * 17f
            val sy = rA.top + u * 40f - (if (k == 1) u * 3f else 0f)
            c.save()
            c.scale(sc, sc, sx, sy)
            Art.star(c, sx, sy, u * 7.5f, filled, Pal.ink, Pal.ink, Pal.g1, u * 0.7f)
            c.restore()
        }
        val info = "Twists $moves  ·  Par $parAtStart" + if (hintsUsed > 0) "  ·  Hints $hintsUsed" else ""
        Art.label(c, info, cx, rA.top + u * 53f, Art.fitSize(info, u * 4.3f, rA.width() - u * 14f, Fonts.body), Pal.ink)
        if (earnedHint && starsShown >= 3) Art.label(c, "Perfect! +1 hint lamp", cx, rA.top + u * 60f, u * 4.2f, Pal.g3, Fonts.display)
        if (appear > 0.95f) { drawButton(c, bNext); drawButton(c, bWinMenu) }
    }

    private fun drawLeader(c: Canvas) {
        // classic academy countdown leader
        c.drawColor(Pal.g2)
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.36f
        val frac = overlayT % 1f
        val num = 3 - overlayT.toInt()
        fillP.color = Pal.g1
        c.drawCircle(cx, cy, r, fillP)
        fillP.color = Pal.g3
        rA.set(cx - r * 2f, cy - r * 2f, cx + r * 2f, cy + r * 2f)
        c.drawArc(rA, -90f, 360f * frac, true, fillP)
        strokeP.color = Pal.ink
        strokeP.strokeWidth = u * 0.6f
        c.drawLine(0f, cy, w, cy, strokeP)
        c.drawLine(cx, 0f, cx, h, strokeP)
        strokeP.strokeWidth = u * 0.9f
        c.drawCircle(cx, cy, r, strokeP)
        c.drawCircle(cx, cy, r * 0.82f, strokeP)
        fillP.color = Pal.panel
        c.drawCircle(cx, cy, r * 0.6f, fillP)
        c.drawCircle(cx, cy, r * 0.6f, strokeP)
        if (num in 1..3) Art.title(c, num.toString(), cx, cy, r * 0.9f, Pal.ink, Pal.ink, 0f, 0f)
        Art.label(c, "PICTURE RUSH", cx, cy - r - u * 10f, u * 7f, Pal.ink, Fonts.display)
        Art.label(c, "solve as many reels as you can!", cx, cy + r + u * 9f, u * 4.5f, Pal.ink)
    }

    private fun drawTimeUp(c: Canvas) {
        val appear = easeOutBack(clamp01(overlayT / 0.6f))
        dim(c, 0.55f * clamp01(overlayT / 0.3f))
        drawCard(c, 102f, appear)
        val cx = rA.centerX()
        // "THE END" in an oval medallion
        val my = rA.top + u * 20f
        rB.set(cx - u * 30f, my - u * 12f, cx + u * 30f, my + u * 12f)
        fillP.color = Pal.ink
        c.drawOval(rB, fillP)
        strokeP.color = Pal.panel
        strokeP.strokeWidth = u * 0.4f
        rB.inset(u * 1.5f, u * 1.5f)
        c.drawOval(rB, strokeP)
        Art.title(c, "THE END", cx, my, u * 10f, Pal.panel, Pal.ink, u * 0.3f, 0f)
        Art.label(c, "Reels solved", cx, rA.top + u * 40f, u * 4.4f, Pal.g3)
        Art.title(c, rushSolved.toString(), cx, rA.top + u * 50f, u * 12f, Pal.panel, Pal.ink, u * 1f, u * 1.2f)
        val best = if (newBest) "NEW BEST!" else "Best: $rushBest"
        Art.label(c, best, cx, rA.top + u * 61f, u * 4.8f, Pal.ink, if (newBest) Fonts.display else Fonts.body)
        if (appear > 0.95f) { drawButton(c, bAgain); drawButton(c, bEndMenu) }
    }

    // ------------------------------------------------------------------ fx

    private fun drawParticles(c: Canvas) {
        for (i in 0 until pMax) {
            if (pLife[i] <= 0f) continue
            val f = clamp01(pLife[i] / pMaxLife[i])
            when (pType[i]) {
                0 -> Art.splat(c, px[i], py[i], pSize[i] * (0.6f + 0.4f * f), pRot[i], withAlpha(Pal.ink, (255 * f).toInt()))
                1 -> Art.star(c, px[i], py[i], pSize[i] * 2f * f + 1f, true, Pal.ink, Pal.panel, Pal.panel, u * 0.3f)
                2 -> {
                    fillP.color = withAlpha(Pal.ink, (255 * f).toInt())
                    c.drawCircle(px[i], py[i], pSize[i] * f, fillP)
                }
                else -> {
                    fillP.color = withAlpha(Pal.g2, (200 * f).toInt())
                    c.drawCircle(px[i], py[i], pSize[i] * (1.6f - f * 0.6f), fillP)
                }
            }
        }
    }

    private fun drawFloatTexts(c: Canvas) {
        for (k in 0 until fMax) {
            val s = fText[k] ?: continue
            if (fLife[k] <= 0f) continue
            val a = clamp01(fLife[k] / 0.4f)
            val size = Art.fitSize(s, u * 6f, w - u * 10f, Fonts.display)
            Art.title(c, s, fx[k], fy[k], size, withAlpha(Pal.panel, (255 * a).toInt()), withAlpha(Pal.ink, (255 * a).toInt()), u * 0.8f, 0f)
        }
    }

    private fun drawIris(c: Canvas) {
        if (trPhase == 0) return
        val maxR = hypot(max(trCx, w - trCx), max(trCy, h - trCy)) * 1.05f
        val f = clamp01(trT / trDur)
        val e = f * f * (3f - 2f * f)
        val r = if (trPhase == 1) maxR * (1f - e) else maxR * e
        irisPath.reset()
        irisPath.fillType = Path.FillType.EVEN_ODD
        irisPath.addRect(-u * 10f, -u * 10f, w + u * 10f, h + u * 10f, Path.Direction.CW)
        if (r > 0.5f) irisPath.addCircle(trCx, trCy, r, Path.Direction.CW)
        fillP.color = Pal.BLACK
        c.drawPath(irisPath, fillP)
        if (r > 0.5f) {
            strokeP.color = 0xFF3A3A3A.toInt()
            strokeP.strokeWidth = u * 1.2f
            c.drawCircle(trCx, trCy, r, strokeP)
        }
    }

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val t = x - 1f
        return 1f + c3 * t * t * t + c1 * t * t
    }

    // ================================================================== test hooks

    /** For automated tests: current solved-by-taps state. */
    fun debugSolveAllButOne(): Boolean {
        val p = puzzle ?: return false
        var last = -1
        for (i in 0 until p.size) if (!p.isCorrect(i) && !p.locked[i]) last = i
        for (i in 0 until p.size) {
            if (i == last) continue
            val k = p.tapsToFix(i)
            if (k > 0) { p.setRotation(i, p.fixedRotation(i)); angT[i] += 90f * k; ang[i] = angT[i] }
        }
        return last >= 0
    }

    fun debugTileCenter(i: Int, out: FloatArray) {
        val p = puzzle ?: return
        out[0] = tileCx(p, i); out[1] = tileCy(p, i)
    }

    fun debugButtonCenter(id: Int, out: FloatArray): Boolean {
        for (b in allBtns) if (b.id == id && b.visible) { out[0] = b.rect.centerX(); out[1] = b.rect.centerY(); return true }
        return false
    }

    val isTransitioning get() = trPhase != 0

    /** Returns a description of any broken invariant, or null when all is well. */
    fun debugSanity(): String? {
        val p = puzzle
        if (p != null) for (i in 0 until p.size) {
            if (ang[i].isNaN() || angT[i].isNaN() || abs(ang[i]) > 20000f) return "angle $i=${ang[i]}"
            if (inkA[i] !in 0f..1f) return "ink $i=${inkA[i]}"
            if (litA[i].isNaN() || litA[i] < -0.01f || litA[i] > 1.01f) return "lit $i"
            if (inkDir[i] !in -1..3) return "inkDir $i"
        }
        if (hints !in 0..MAX_HINTS) return "hints $hints"
        if (moves < 0) return "moves"
        if (litCount < 0) return "litCount"
        if (rushTime < 0f || rushTime > 99.5f) return "rushTime $rushTime"
        if (lookX.isNaN() || lookY.isNaN()) return "look NaN"
        return null
    }
    val debugRushTime get() = rushTime
    val debugEarnedStars get() = earnedStars
    val debugCelebrating get() = celebrate || solvedPending
}
