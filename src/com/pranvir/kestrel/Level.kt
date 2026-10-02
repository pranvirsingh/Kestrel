package com.pranvir.kestrel

import java.util.Random
import kotlin.math.abs

/**
 * One scripted spawn. Air spawns trigger by scroll distance ([at]); ground spawns by their ground
 * position ([y] is ground-y) entering the top of the screen. Positions as fractions of the view
 * height are resolved at runtime (marked with [frac]).
 */
class Spawn(val at: Float, val kind: Int, val x: Float, val y: Float, val pattern: Int, val p1: Float = 0f, val p2: Float = 0f, val tag: Int = 0)

object Pat {
    const val DOWN = 0; const val HOLD = 1; const val ARC = 2; const val HOVER = 3; const val DRIFT = 4; const val GROUND = 5; const val TRAIN = 6
}

object Tag { const val NONE = 0; const val ACE = 1 }

class Level(val sector: Int, val threat: Int = 0) {
    val def = Sectors.ALL[sector]
    val biome = Biome.make(sector, def.length + 700f)
    val length = def.length
    val air = ArrayList<Spawn>()
    val ground = ArrayList<Spawn>()
    /** Ground positions of the five survivor beacons. */
    val survivors = ArrayList<FloatArray>()
    var hostiles = 0
    var aceTotal = 0

    init {
        val rnd = Random(4242L + sector * 7717L + threat * 31L)
        genAir(rnd)
        genGround(rnd)
        genSurvivors(rnd)
        air.sortBy { it.at }
        ground.sortBy { it.y }
        for (s in air) if (EK.HOSTILE[s.kind]) hostiles++
        for (s in ground) if (EK.HOSTILE[s.kind]) hostiles++
        aceTotal = air.count { it.tag == Tag.ACE } + ground.count { it.tag == Tag.ACE }
    }

    private fun add(at: Float, kind: Int, x: Float, y: Float, pat: Int, p1: Float = 0f, p2: Float = 0f, tag: Int = 0) {
        air.add(Spawn(at, kind, x, y, pat, p1, p2, tag))
    }

    // ------------------------------------------------------------------ air waves

    private fun genAir(rnd: Random) {
        var pos = 750f
        val end = length - 700f
        val carriers = if (sector == 5) floatArrayOf(0.24f, 0.52f, 0.8f).map { it * length }.toMutableList() else mutableListOf()
        var lastBig = -9999f
        while (pos < end) {
            val d = pos / length
            if (carriers.isNotEmpty() && pos >= carriers[0]) {
                add(pos, EK.CARRIER, 300f + rnd.nextFloat() * 400f, -140f, Pat.HOLD, 0.2f, 14f, Tag.ACE)
                carriers.removeAt(0)
                pos += 900f
                continue
            }
            val pool = ArrayList<Int>()
            pool.addAll(listOf(0, 0, 1, 1, 2))                  // V, line, arc
            if (d > 0.2f || sector > 0) pool.addAll(listOf(3, 3))  // wasps
            if ((d > 0.35f || sector > 1) && pos - lastBig > 1600f) pool.addAll(listOf(4, 4))  // gunship
            if (sector >= 1 && d > 0.3f && pos - lastBig > 1600f) pool.add(5)  // bomber
            if (sector >= 1 && d > 0.25f) pool.addAll(listOf(6, 6))  // lancers
            if (sector >= 2) pool.add(7)                         // mines
            if (sector >= 3) pool.addAll(listOf(3, 6))
            val w = pool[rnd.nextInt(pool.size)]
            when (w) {
                0 -> { // V formation
                    val cx = 220f + rnd.nextFloat() * 560f
                    val n = if (d > 0.5f || sector > 2 || threat >= 2) 7 else 5
                    for (i in 0 until n) {
                        val k = (i + 1) / 2; val side = if (i % 2 == 0) 1f else -1f
                        add(pos + k * 30f, EK.DART, cx + side * k * 62f, -70f, Pat.DOWN, 250f + sector * 12f, 0f)
                    }
                }
                1 -> { // staggered line
                    val leftToRight = rnd.nextBoolean()
                    for (i in 0 until 6) {
                        val x = if (leftToRight) 150f + i * 140f else 850f - i * 140f
                        add(pos + i * 26f, EK.DART, x, -70f, Pat.DOWN, 300f, 45f)
                    }
                }
                2 -> { // arcing swoop squadron
                    val dir = if (rnd.nextBoolean()) 1f else -1f
                    val n = 5 + (if (d > 0.5f) 2 else 0)
                    for (i in 0 until n) add(pos + i * 30f, EK.SWOOP, if (dir > 0) -60f else 1060f, 0.12f + rnd.nextFloat() * 0.06f, Pat.ARC, dir, 0f)
                }
                3 -> { // hovering wasps
                    val n = if (sector >= 3) 4 else 3
                    for (i in 0 until n) add(pos + i * 40f, EK.WASP, 1000f * (i + 1) / (n + 1), -60f, Pat.HOVER, 0.18f + (i % 2) * 0.08f, 7f)
                }
                4 -> { add(pos, EK.GUNSHIP, 300f + rnd.nextFloat() * 400f, -120f, Pat.HOLD, 0.2f, 7f); lastBig = pos }
                5 -> { add(pos, EK.BOMBER, 250f + rnd.nextFloat() * 500f, -160f, Pat.DOWN, 75f, 0f); lastBig = pos }
                6 -> {
                    add(pos, EK.LANCER, 220f + rnd.nextFloat() * 160f, -80f, Pat.HOLD, 0.14f, 4.5f)
                    add(pos + 40f, EK.LANCER, 620f + rnd.nextFloat() * 160f, -80f, Pat.HOLD, 0.16f, 4.5f)
                }
                7 -> for (i in 0 until 7) add(pos + i * 40f, EK.MINE, 100f + rnd.nextFloat() * 800f, -40f, Pat.DRIFT, 85f, 0f)
            }
            val gap = (400f + rnd.nextFloat() * 260f) * (1f - 0.25f * d) * (1f - sector * 0.03f) * (1f - threat * 0.07f)
            pos += gap + (if (w == 4 || w == 5) 350f else 0f)
        }
    }

    // ------------------------------------------------------------------ ground

    private fun gadd(kind: Int, x: Float, gy: Float, pat: Int = Pat.GROUND, p1: Float = 0f, tag: Int = 0) {
        ground.add(Spawn(0f, kind, x, gy, pat, p1, 0f, tag))
    }

    private fun near(x: Float, gy: Float, r: Float): Boolean {
        for (s in ground) if (abs(s.y - gy) < r && abs(s.x - x) < r) return true
        return false
    }

    private fun genGround(rnd: Random) {
        if (sector == 5) return // nothing stands on clouds
        var gy = 1150f
        val end = length - 500f
        var boats = 0
        while (gy < end) {
            val d = gy / length
            val cluster = 1 + rnd.nextInt(if (d > 0.4f) 3 else 2)
            for (c in 0 until cluster) {
                for (attempt in 0 until 14) {
                    val x = 80f + rnd.nextFloat() * 840f
                    val y = gy + (rnd.nextFloat() - 0.5f) * 180f
                    val k = biome.kind(x, y)
                    if (near(x, y, 95f)) continue
                    val kind = when (k) {
                        Ground.WATER -> if (sector == 0 || sector == 2) EK.BOAT else -1
                        Ground.ROAD -> EK.TANK
                        Ground.ROOF -> if (rnd.nextFloat() < 0.5f) EK.FLAK else EK.SAM
                        Ground.LAND -> {
                            val r = rnd.nextFloat()
                            when {
                                r < 0.45f -> EK.TANK
                                r < 0.75f -> EK.FLAK
                                r < 0.88f && (sector >= 1 || d > 0.4f) -> EK.SAM
                                else -> EK.BUNKER
                            }
                        }
                        else -> -1
                    }
                    if (kind < 0) continue
                    // clearance for big footprints
                    if (kind == EK.BUNKER && (biome.kind(x - 50f, y) != Ground.LAND || biome.kind(x + 50f, y) != Ground.LAND)) continue
                    if (kind == EK.BOAT && (biome.kind(x, y + 60f) != Ground.WATER || biome.kind(x, y - 60f) != Ground.WATER)) continue
                    if (kind == EK.BOAT) boats++
                    gadd(kind, x, y, tag = if (kind == EK.BOAT && sector == 0) Tag.ACE else Tag.NONE)
                    break
                }
            }
            gy += (300f + rnd.nextFloat() * 220f) * (1f - 0.2f * d)
        }
        if (sector == 0 && boats < 6) {
            // the blockade must exist: scan for open water
            var y = 1500f
            while (boats < 6 && y < end) {
                for (x in floatArrayOf(500f, 350f, 650f, 250f, 750f)) {
                    if (biome.kind(x, y) == Ground.WATER && biome.kind(x, y + 60f) == Ground.WATER && biome.kind(x, y - 60f) == Ground.WATER && !near(x, y, 95f)) {
                        gadd(EK.BOAT, x, y, tag = Tag.ACE); boats++; break
                    }
                }
                y += 900f
            }
        }
        if (sector == 1) {
            // the armoured train: six cars rolling along the rail, heading the same way we fly
            val g0 = length * 0.5f
            for (i in 0 until 6) gadd(EK.TRAIN, 0f, g0 - i * 128f, Pat.TRAIN, 42f, Tag.ACE)
        }
        if (sector == 3) {
            var placed = 0
            var y = length * 0.12f
            while (placed < 6 && y < end) {
                for (attempt in 0 until 30) {
                    val x = 120f + rnd.nextFloat() * 760f
                    val yy = y + (rnd.nextFloat() - 0.5f) * 300f
                    if (biome.kind(x, yy) == Ground.ROOF && !near(x, yy, 110f)) { gadd(EK.RADAR, x, yy, tag = Tag.ACE); placed++; break }
                }
                y += length * 0.13f
            }
        }
    }

    private fun genSurvivors(rnd: Random) {
        for (f in floatArrayOf(0.14f, 0.31f, 0.48f, 0.65f, 0.82f)) {
            var gy = f * length
            var x = 500f
            var ok = false
            for (attempt in 0 until 60) {
                x = 140f + rnd.nextFloat() * 720f
                val yy = gy + (rnd.nextFloat() - 0.5f) * 400f
                val k = biome.kind(x, yy)
                val good = when (sector) {
                    5 -> true
                    3 -> k == Ground.ROOF || k == Ground.ROAD
                    else -> k == Ground.LAND
                }
                if (good && !near(x, yy, 120f)) { gy = yy; ok = true; break }
            }
            if (!ok) x = 500f
            survivors.add(floatArrayOf(x, gy))
        }
    }
}
