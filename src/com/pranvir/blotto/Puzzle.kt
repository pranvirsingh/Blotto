package com.pranvir.blotto

import java.util.Random

/**
 * Pure puzzle logic. No Android dependencies so it can be tested on a plain JVM.
 *
 * Every cell holds a pipe described by a 4-bit mask: N=1, E=2, S=4, W=8.
 * The generator builds a random spanning tree over the grid (that is the solution),
 * then rotates tiles to scramble it. Any rotation state in which every pipe end meets
 * a matching pipe end and every tile is reachable from the inkwell counts as solved.
 */
object Dir {
    const val N = 1
    const val E = 2
    const val S = 4
    const val W = 8
    val DX = intArrayOf(0, 1, 0, -1)
    val DY = intArrayOf(-1, 0, 1, 0)
    val BIT = intArrayOf(N, E, S, W)

    /** Rotate a mask clockwise [times] quarter turns. */
    fun rotate(mask: Int, times: Int): Int {
        var m = mask and 15
        val t = ((times % 4) + 4) % 4
        repeat(t) { m = ((m shl 1) or (m shr 3)) and 15 }
        return m
    }

    fun opposite(d: Int) = (d + 2) and 3
    fun degree(mask: Int) = Integer.bitCount(mask and 15)
}

data class LevelSpec(
    val cols: Int,
    val rows: Int,
    val negative: Boolean,
    val lightsOut: Boolean,
    val windiness: Float,
    val title: String,
    val blurb: String
)

class Puzzle(
    val cols: Int,
    val rows: Int,
    val src: Int,
    private val solution: IntArray,
    initialRot: IntArray,
    initialLock: BooleanArray
) {
    val size = cols * rows
    val rot = IntArray(size)
    val locked = BooleanArray(size)

    /** BFS distance from the inkwell along matched pipes, -1 if dry. */
    val dist = IntArray(size)
    /** Direction index (0..3) the ink came IN from, -1 for source / dry. */
    val entry = IntArray(size)
    var connectedCount = 0
        private set

    init {
        require(cols > 0 && rows > 0)
        require(solution.size == size && initialRot.size == size && initialLock.size == size)
        require(src in 0 until size)
        for (i in 0 until size) {
            rot[i] = ((initialRot[i] % 4) + 4) % 4
            locked[i] = initialLock[i]
        }
        recompute()
    }

    fun mask(i: Int): Int = Dir.rotate(solution[i], rot[i])
    fun solutionMask(i: Int): Int = solution[i]
    fun degree(i: Int): Int = Dir.degree(solution[i])
    fun isBulb(i: Int): Boolean = i != src && degree(i) == 1
    fun x(i: Int) = i % cols
    fun y(i: Int) = i / cols
    fun idx(x: Int, y: Int) = y * cols + x
    fun inBounds(x: Int, y: Int) = x in 0 until cols && y in 0 until rows

    /** Neighbour index in direction d or -1. */
    fun neighbor(i: Int, d: Int): Int {
        val nx = x(i) + Dir.DX[d]
        val ny = y(i) + Dir.DY[d]
        return if (inBounds(nx, ny)) idx(nx, ny) else -1
    }

    /** Rotation-symmetric tiles (cross, or straight rotated twice) look identical after some turns. */
    fun distinctRotations(i: Int): Int {
        val m = solution[i]
        return when {
            m == 15 || m == 0 -> 1
            Dir.rotate(m, 2) == m -> 2
            else -> 4
        }
    }

    fun isCorrect(i: Int) = mask(i) == solution[i]

    fun rotateCW(i: Int) {
        rot[i] = (rot[i] + 1) and 3
        recompute()
    }

    fun rotateCCW(i: Int) {
        rot[i] = (rot[i] + 3) and 3
        recompute()
    }

    fun setRotation(i: Int, r: Int) {
        rot[i] = ((r % 4) + 4) % 4
        recompute()
    }

    /** Fewest clockwise taps needed to put tile i in its solution orientation. */
    fun tapsToFix(i: Int): Int {
        for (k in 0..3) if (Dir.rotate(solution[i], rot[i] + k) == solution[i]) return k
        return 0
    }

    /** Rotation value that shows the solution orientation using the fewest clockwise taps. */
    fun fixedRotation(i: Int): Int = (rot[i] + tapsToFix(i)) and 3

    fun par(): Int {
        var p = 0
        for (i in 0 until size) p += tapsToFix(i)
        return p
    }

    fun recompute() {
        java.util.Arrays.fill(dist, -1)
        java.util.Arrays.fill(entry, -1)
        val queue = IntArray(size)
        var head = 0
        var tail = 0
        dist[src] = 0
        queue[tail++] = src
        while (head < tail) {
            val c = queue[head++]
            val m = mask(c)
            for (d in 0..3) {
                if (m and Dir.BIT[d] == 0) continue
                val n = neighbor(c, d)
                if (n < 0 || dist[n] >= 0) continue
                if (mask(n) and Dir.BIT[Dir.opposite(d)] == 0) continue
                dist[n] = dist[c] + 1
                entry[n] = Dir.opposite(d)
                queue[tail++] = n
            }
        }
        connectedCount = tail
    }

    fun isConnected(i: Int) = dist[i] >= 0

    /** True when pipe end d of tile i hangs open (points at edge or at a non-matching neighbour). */
    fun isLooseEnd(i: Int, d: Int): Boolean {
        if (mask(i) and Dir.BIT[d] == 0) return false
        val n = neighbor(i, d)
        if (n < 0) return true
        return mask(n) and Dir.BIT[Dir.opposite(d)] == 0
    }

    fun isSolved(): Boolean {
        if (connectedCount != size) return false
        for (i in 0 until size) for (d in 0..3) if (isLooseEnd(i, d)) return false
        return true
    }

    fun litBulbs(): Int {
        var c = 0
        for (i in 0 until size) if (isBulb(i) && isConnected(i)) c++
        return c
    }

    fun totalBulbs(): Int {
        var c = 0
        for (i in 0 until size) if (isBulb(i)) c++
        return c
    }

    /** Visible for "lights out" reels: inked tiles and their direct neighbours. */
    fun isVisibleInDark(i: Int): Boolean {
        if (isConnected(i) || i == src) return true
        for (d in 0..3) {
            val n = neighbor(i, d)
            if (n >= 0 && isConnected(n)) return true
        }
        return false
    }

    /** Depth of each tile in the solution tree, used to pick helpful hints. */
    private val solutionDepth: IntArray by lazy {
        val depth = IntArray(size) { -1 }
        val q = IntArray(size)
        var h = 0
        var t = 0
        depth[src] = 0
        q[t++] = src
        while (h < t) {
            val c = q[h++]
            for (d in 0..3) {
                if (solution[c] and Dir.BIT[d] == 0) continue
                val n = neighbor(c, d)
                if (n < 0 || depth[n] >= 0) continue
                depth[n] = depth[c] + 1
                q[t++] = n
            }
        }
        depth
    }

    /** Picks the wrong tile nearest the inkwell in the solution tree, or -1 if none. */
    fun hintTile(): Int {
        var best = -1
        var bestScore = Int.MAX_VALUE
        for (i in 0 until size) {
            if (isCorrect(i)) continue
            val d = solutionDepth[i].let { if (it < 0) size else it }
            // Prefer tiles already touching the ink so the hint visibly advances the flow.
            val touching = if (isConnected(i) || (0..3).any { neighbor(i, it).let { n -> n >= 0 && isConnected(n) } }) 0 else 1000
            val score = touching + d
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append(cols).append(',').append(rows).append(',').append(src).append(',')
        for (i in 0 until size) sb.append(Character.forDigit(solution[i], 16))
        sb.append(',')
        for (i in 0 until size) sb.append(rot[i])
        sb.append(',')
        for (i in 0 until size) sb.append(if (locked[i]) '1' else '0')
        return sb.toString()
    }

    companion object {
        const val MAX_COLS = 8
        const val MAX_ROWS = 11

        fun deserialize(s: String?): Puzzle? {
            if (s.isNullOrEmpty()) return null
            return try {
                val p = s.split(',')
                if (p.size != 6) return null
                val cols = p[0].toInt()
                val rows = p[1].toInt()
                val src = p[2].toInt()
                if (cols !in 2..MAX_COLS || rows !in 2..MAX_ROWS) return null
                val n = cols * rows
                if (p[3].length != n || p[4].length != n || p[5].length != n) return null
                val sol = IntArray(n) { Character.digit(p[3][it], 16) }
                val rot = IntArray(n) { Character.digit(p[4][it], 10) }
                val lock = BooleanArray(n) { p[5][it] == '1' }
                if (sol.any { it !in 1..15 } || rot.any { it !in 0..3 }) return null
                if (src !in 0 until n) return null
                val puzzle = Puzzle(cols, rows, src, sol, rot, lock)
                if (!puzzle.solutionIsValidTree()) return null
                if (puzzle.isSolved()) return null
                puzzle
            } catch (e: Exception) {
                null
            }
        }

        /** Builds a fresh scrambled puzzle. Deterministic for a given seed. */
        fun generate(cols: Int, rows: Int, windiness: Float, seed: Long): Puzzle {
            val rng = Random(seed)
            val n = cols * rows
            val cx = cols / 2
            val cy = rows / 2
            val sx = (cx + if (cols >= 5) rng.nextInt(3) - 1 else 0).coerceIn(0, cols - 1)
            val sy = (cy + if (rows >= 5) rng.nextInt(3) - 1 else 0).coerceIn(0, rows - 1)
            val src = sy * cols + sx

            var sol = IntArray(n)
            // Try a few trees and keep one with a pleasant number of bulbs and no 4-way crosses at the source.
            var bestScore = Int.MIN_VALUE
            for (attempt in 0 until 12) {
                val t = buildTree(cols, rows, src, windiness, rng)
                val leaves = (0 until n).count { it != src && Dir.degree(t[it]) == 1 }
                val crosses = (0 until n).count { Dir.degree(t[it]) == 4 }
                val target = (n * 0.28f).toInt().coerceAtLeast(2)
                val score = -Math.abs(leaves - target) * 3 - crosses * 2 + rng.nextInt(3)
                if (score > bestScore) {
                    bestScore = score
                    sol = t
                }
            }

            val lock = BooleanArray(n)
            for (attempt in 0 until 200) {
                val rot = IntArray(n)
                for (i in 0 until n) {
                    val m = sol[i]
                    val distinct = when {
                        m == 15 -> 1
                        Dir.rotate(m, 2) == m -> 2
                        else -> 4
                    }
                    rot[i] = if (distinct == 1) rng.nextInt(4)
                    else if (rng.nextFloat() < 0.9f) {
                        // pick a rotation that visibly differs from the solution
                        var r: Int
                        do { r = rng.nextInt(4) } while (Dir.rotate(m, r) == m)
                        r
                    } else rng.nextInt(4)
                }
                val p = Puzzle(cols, rows, src, sol, rot, lock)
                val minPar = (n / 3).coerceAtLeast(2)
                if (!p.isSolved() && p.par() >= minPar && p.connectedCount < n / 2 + 1) return p
            }
            // Fallback: rotate every asymmetric tile by one step (never solved because at least one tile differs).
            val rot = IntArray(n) { if (Dir.rotate(sol[it], 1) != sol[it]) 1 else 0 }
            return Puzzle(cols, rows, src, sol, rot, lock)
        }

        private fun buildTree(cols: Int, rows: Int, src: Int, windiness: Float, rng: Random): IntArray {
            val n = cols * rows
            val mask = IntArray(n)
            val inTree = BooleanArray(n)
            // Frontier of candidate edges (from cell, direction)
            val frontierFrom = IntArray(n * 4)
            val frontierDir = IntArray(n * 4)
            var fSize = 0
            fun addEdges(c: Int) {
                val x = c % cols
                val y = c / cols
                for (d in 0..3) {
                    val nx = x + Dir.DX[d]
                    val ny = y + Dir.DY[d]
                    if (nx !in 0 until cols || ny !in 0 until rows) continue
                    val nb = ny * cols + nx
                    if (inTree[nb]) continue
                    frontierFrom[fSize] = c
                    frontierDir[fSize] = d
                    fSize++
                }
            }
            inTree[src] = true
            addEdges(src)
            var count = 1
            while (count < n && fSize > 0) {
                // Windiness: prefer the newest edge (DFS-like corridors) vs. a random edge (branchy).
                val pick = if (rng.nextFloat() < windiness) fSize - 1 - rng.nextInt(minOf(fSize, 3)) else rng.nextInt(fSize)
                val c = frontierFrom[pick]
                val d = frontierDir[pick]
                // remove pick by swapping with last
                fSize--
                frontierFrom[pick] = frontierFrom[fSize]
                frontierDir[pick] = frontierDir[fSize]
                val nb = (c / cols + Dir.DY[d]) * cols + (c % cols + Dir.DX[d])
                if (inTree[nb]) continue
                // Avoid 4-way junctions most of the time; they are rotation-free and dull.
                if (Dir.degree(mask[c]) >= 3 && rng.nextFloat() < 0.85f) {
                    // only skip if other frontier edges exist that could still reach nb
                    var alt = false
                    for (k in 0 until fSize) {
                        val fc = frontierFrom[k]
                        val fd = frontierDir[k]
                        val tgt = (fc / cols + Dir.DY[fd]) * cols + (fc % cols + Dir.DX[fd])
                        if (tgt == nb && Dir.degree(mask[fc]) < 3) { alt = true; break }
                    }
                    if (alt) continue
                }
                mask[c] = mask[c] or Dir.BIT[d]
                mask[nb] = mask[nb] or Dir.BIT[Dir.opposite(d)]
                inTree[nb] = true
                count++
                addEdges(nb)
            }
            check(count == n) { "tree did not span grid" }
            return mask
        }

        fun storySpec(level: Int): LevelSpec {
            val l = level.coerceAtLeast(1)
            val (c, r) = when {
                l <= 1 -> 3 to 3
                l <= 2 -> 3 to 4
                l <= 3 -> 4 to 4
                l <= 5 -> 4 to 5
                l <= 7 -> 5 to 5
                l <= 10 -> 5 to 6
                l <= 14 -> 5 to 7
                l <= 19 -> 6 to 7
                l <= 26 -> 6 to 8
                l <= 35 -> 7 to 8
                l <= 50 -> 7 to 9
                l <= 75 -> 7 to 10
                l <= 99 -> 8 to 10
                else -> 8 to 11
            }
            val negative = l >= 7 && l % 7 == 0
            val lightsOut = l >= 10 && l % 5 == 0
            val windiness = when {
                l < 4 -> 0.25f
                else -> 0.2f + ((l * 37) % 11) / 20f // varies 0.2..0.7 between reels
            }
            val title: String
            val blurb: String
            when {
                negative && lightsOut -> {
                    title = "The Midnight Negative"
                    blurb = "Inverted film AND the lamps are out.\nOnly the ink lights your way. Courage!"
                }
                negative -> {
                    title = "The Negative"
                    blurb = "The projectionist loaded the reel backwards!\nWhite is black, black is white."
                }
                lightsOut -> {
                    title = "Lights Out!"
                    blurb = "The theatre went dark.\nYou can only see tiles beside the ink."
                }
                else -> {
                    title = TITLES[(l - 1) % TITLES.size]
                    blurb = BLURBS[(l * 7 + 3) % BLURBS.size]
                }
            }
            return LevelSpec(c, r, negative, lightsOut, windiness, title, blurb)
        }

        fun rushSize(solved: Int): Pair<Int, Int> = when {
            solved < 2 -> 4 to 4
            solved < 4 -> 4 to 5
            solved < 7 -> 5 to 5
            solved < 10 -> 5 to 6
            solved < 14 -> 6 to 6
            solved < 19 -> 6 to 7
            else -> 6 to 8
        }

        val TITLES = arrayOf(
            "Blotto's First Picture", "The Leaky Parlour", "Plumber's Holiday", "Ink Spills Twice",
            "The Sleepy Lamps", "Waltz of the Pipes", "Midnight Matinee", "The Grand Gusher",
            "Tangled Tubes", "Flapper's Fountain", "The Clockwork Drip", "Hot-Foot Hose",
            "Down the Drain", "The Jazz Faucet", "Swing Time Spout", "Bubble & Squeak",
            "The Great Gurgle", "Ragtime Radiator", "The Silver Sprinkler", "Nickelodeon Nights",
            "The Wiggly Works", "Spooky Spigots", "The Merry Manifold", "Hoot-Owl Hollow",
            "The Boiler Room Ball", "Cuckoo Conduit", "Vaudeville Valves", "The Wobbly Waterworks"
        )
        val BLURBS = arrayOf(
            "Twist the pipes so the ink reaches\nevery sleepy little lamp.",
            "No loose ends, no leaks.\nEvery lamp must be lit!",
            "Long-press a tile to bolt it in place\nonce you're sure of it.",
            "The ink always starts at\nBlotto's trusty inkwell.",
            "Fewer twists earn more stars.\nThink before you turn!",
            "A pipe pointing at a wall\nis a pipe gone wrong.",
            "Corners hug the edges.\nStart there, champ.",
            "When in doubt, follow the ink.\nIt never lies.",
            "Stuck? The hint lamp will\nbolt one pipe for you."
        )
    }

    fun solutionIsValidTree(): Boolean {
        // solution must connect every cell with matched ends and exactly size-1 edges
        var ends = 0
        for (i in 0 until size) {
            val m = solution[i]
            for (d in 0..3) {
                if (m and Dir.BIT[d] == 0) continue
                val nb = neighbor(i, d)
                if (nb < 0) return false
                if (solution[nb] and Dir.BIT[Dir.opposite(d)] == 0) return false
                ends++
            }
        }
        if (ends != 2 * (size - 1)) return false
        val seen = BooleanArray(size)
        val st = IntArray(size)
        var sp = 0
        st[sp++] = src
        seen[src] = true
        var cnt = 1
        while (sp > 0) {
            val c = st[--sp]
            for (d in 0..3) {
                if (solution[c] and Dir.BIT[d] == 0) continue
                val nb = neighbor(c, d)
                if (!seen[nb]) { seen[nb] = true; cnt++; st[sp++] = nb }
            }
        }
        return cnt == size
    }
}
