import com.pranvir.blotto.*
import java.util.Random

fun fail(msg: String): Nothing = throw AssertionError(msg)

fun main() {
    val rnd = Random(42)
    var boards = 0
    var totalTime = 0L
    // Story levels 1..300 with several seeds each
    for (level in 1..300) {
        val spec = Puzzle.storySpec(level)
        for (s in 0 until 20) {
            val seed = rnd.nextLong()
            val t0 = System.nanoTime()
            val p = Puzzle.generate(spec.cols, spec.rows, spec.windiness, seed)
            totalTime += System.nanoTime() - t0
            checkBoard(p, seed)
            // determinism
            val q = Puzzle.generate(spec.cols, spec.rows, spec.windiness, seed)
            if (p.serialize() != q.serialize()) fail("non deterministic seed=$seed")
            boards++
        }
    }
    for (k in 0..30) {
        val (c, r) = Puzzle.rushSize(k)
        repeat(50) { checkBoard(Puzzle.generate(c, r, 0.4f, rnd.nextLong()), 0) ; boards++ }
    }
    // Edge sizes
    for (c in 2..8) for (r in 2..11) repeat(20) {
        checkBoard(Puzzle.generate(c, r, rnd.nextFloat(), rnd.nextLong()), 0); boards++
    }
    println("OK boards=$boards avgGenMs=${"%.3f".format(totalTime / 1e6 / 6000)}")

    // Serialization robustness
    val bad = listOf(null, "", "garbage", "3,3,4,zzzzzzzzz,000000000,000000000", "3,3,99,123412341,000000000,000000000",
        "1,1,0,1,0,0", "3,3,4,111111111,000000000,000000000")
    for (b in bad) if (Puzzle.deserialize(b) != null) fail("bad deserialize accepted: $b")

    // spec text sanity
    for (l in 1..500) {
        val s = Puzzle.storySpec(l)
        if (s.title.isEmpty() || s.blurb.isEmpty()) fail("empty spec text")
        if (s.cols > Puzzle.MAX_COLS || s.rows > Puzzle.MAX_ROWS) fail("spec too big")
    }
    println("ALL PUZZLE TESTS PASSED")
}

fun checkBoard(p: Puzzle, seed: Long) {
    if (!p.solutionIsValidTree()) fail("solution invalid seed=$seed")
    if (p.isSolved()) fail("generated solved seed=$seed")
    if (p.par() <= 0) fail("par 0")
    if (p.totalBulbs() < 1) fail("no bulbs")
    for (i in 0 until p.size) if (p.degree(i) !in 1..4) fail("degree")
    // serialize roundtrip
    val s = p.serialize()
    val r = Puzzle.deserialize(s) ?: fail("deserialize failed $s")
    if (r.serialize() != s) fail("roundtrip mismatch")

    // Solve by taps equal to par -> must be solved exactly
    val copy = Puzzle.deserialize(s)!!
    var taps = 0
    for (i in 0 until copy.size) { val k = copy.tapsToFix(i); repeat(k) { copy.rotateCW(i); taps++ } }
    if (!copy.isSolved()) fail("par solve not solved")
    if (taps != p.par()) fail("taps != par")
    if (copy.connectedCount != copy.size) fail("connected count")
    if (copy.litBulbs() != copy.totalBulbs()) fail("bulbs")

    // Solve by hints only -> must terminate within size steps
    val h = Puzzle.deserialize(s)!!
    var steps = 0
    while (!h.isSolved()) {
        val t = h.hintTile()
        if (t < 0) fail("no hint while unsolved")
        h.setRotation(t, h.fixedRotation(t))
        h.locked[t] = true
        steps++
        if (steps > h.size) fail("hint loop")
    }
    // Random taps never crash and CCW undoes CW
    val u = Puzzle.deserialize(s)!!
    val rr = Random(seed)
    repeat(50) {
        val i = rr.nextInt(u.size)
        val before = u.serialize()
        u.rotateCW(i); u.rotateCCW(i)
        if (u.serialize() != before) fail("undo mismatch")
        u.rotateCW(i)
    }
    // dark visibility: source always visible
    if (!u.isVisibleInDark(u.src)) fail("src invisible")
}
