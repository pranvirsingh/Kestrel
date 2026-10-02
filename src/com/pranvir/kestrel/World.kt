package com.pranvir.kestrel

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

interface WorldEvents {
    fun sfx(id: Int, vol: Float = 1f, rate: Float = 1f)
    fun haptic(strong: Boolean)
}

class Loadout(val lv: IntArray, val perks: Int) {
    fun has(p: Int) = perks and (1 shl p) != 0
}

class Enemy {
    var kind = 0
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var bx = 0f; var ty = 0f
    var hp = 1f; var maxHp = 1f; var r = 10f
    var air = true
    var gy = 0f
    var t = 0f
    var fireT = 0f
    var burst = 0
    var ang = 0f      // body heading (radians, 0 = facing down the screen)
    var tur = 0f      // turret aim (radians, 0 = down)
    var pat = 0; var p1 = 0f; var p2 = 0f
    var state = 0
    var alive = true
    var flash = 0f
    var counted = true
    var tag = 0
    var lockX = 0f; var lockY = 0f
    var charge = 0f
    var cnt = 0
    /** Ground units: how far the tilted camera lifts them up the screen (terrain height). */
    var lift = 0f
}

class Survivor(val x: Float, val gy: Float) {
    var prog = 0f
    var state = 0   // 0 waiting, 1 rescued, 2 missed
    var beam = 0f
}

class Capsule(var x: Float, var y: Float, val type: Int) { var t = 0f; var alive = true }

class Missile { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var dmg = 0f; var target: Enemy? = null; var targetPart: BossPart? = null; var alive = true; var trailT = 0f }

class Bolt(val pts: FloatArray, val n: Int) { var life = 0.22f }

class Beam {
    var x = 0f; var y = 0f; var ang = 0f; var sweep = 0f; var width = 36f; var len = 2600f
    var warn = 0.9f; var on = 2.0f; var t = 0f; var alive = true
    var follow: BossPart? = null
    fun live() = t >= warn && t < warn + on
}

class Popup { var text = ""; var x = 0f; var y = 0f; var t = 0f; var col = 0; var size = 1f; var alive = false }

/** Struct-of-arrays pools keep the frame free of garbage. */
class ShotPool(val cap: Int) {
    val x = FloatArray(cap); val y = FloatArray(cap); val vx = FloatArray(cap); val vy = FloatArray(cap)
    val dmg = FloatArray(cap); val kind = IntArray(cap); val alive = BooleanArray(cap)
    var count = 0
    private var next = 0
    fun add(px: Float, py: Float, pvx: Float, pvy: Float, d: Float, k: Int): Int {
        for (n in 0 until cap) {
            val i = (next + n) % cap
            if (!alive[i]) {
                x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; dmg[i] = d; kind[i] = k; alive[i] = true
                next = (i + 1) % cap; count++
                return i
            }
        }
        return -1
    }
    fun kill(i: Int) { if (alive[i]) { alive[i] = false; count-- } }
    fun clear() { for (i in 0 until cap) alive[i] = false; count = 0 }
}

class PartPool(val cap: Int) {
    val x = FloatArray(cap); val y = FloatArray(cap); val vx = FloatArray(cap); val vy = FloatArray(cap)
    val life = FloatArray(cap); val max = FloatArray(cap); val size = FloatArray(cap); val grow = FloatArray(cap)
    val rot = FloatArray(cap); val vr = FloatArray(cap); val col = IntArray(cap); val kind = IntArray(cap)
    val delay = FloatArray(cap); val ground = BooleanArray(cap); val drag = FloatArray(cap)
    private var next = 0
    companion object { const val FIRE = 0; const val SMOKE = 1; const val SPARK = 2; const val RING = 3; const val DEBRIS = 4; const val FLASH = 5; const val DOT = 6; const val TRAIL = 7 }
    fun add(k: Int, px: Float, py: Float, pvx: Float, pvy: Float, l: Float, s: Float, c: Int = 0, g: Float = 0f, dl: Float = 0f, onGround: Boolean = false, dr: Float = 1.5f) {
        var i = -1
        for (n in 0 until cap) { val j = (next + n) % cap; if (life[j] <= 0f) { i = j; break } }
        if (i < 0) i = next
        next = (i + 1) % cap
        kind[i] = k; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; life[i] = l; max[i] = l; size[i] = s; grow[i] = g
        rot[i] = (px * 7f + py * 3f) % 6.28f; vr[i] = (((px * 13f).toInt() % 7) - 3) * 1.3f; col[i] = c; delay[i] = dl; ground[i] = onGround; drag[i] = dr
    }
    fun clear() { for (i in 0 until cap) life[i] = 0f }
}

/**
 * The battle. Screen-space world: x 0..1000, y 0 (top) .. vh (bottom). Ground features scroll down
 * at [scrollSpeed]; [scroll] is the ground-y at the bottom edge of the screen.
 */
class World(val sector: Int, val threat: Int, val load: Loadout, val vh: Float, seed: Long = 1L) {
    companion object {
        const val W = WORLD_W
        const val PLAYER_R = 9f
        const val BASE_SCROLL = 95f
        const val INTRO = 0; const val PLAY = 1; const val WARN = 2; const val BOSS = 3; const val CLEAR = 4; const val FAILED = 5; const val DONE = 6
        const val CAP_REPAIR = 0; const val CAP_SHIELD = 1; const val CAP_SURGE = 2; const val CAP_OVER = 3
        const val EB_PINK = 0; const val EB_ORANGE = 1; const val EB_BIG = 2; const val EB_NEEDLE = 3; const val EB_SPLIT = 4
        const val S_BOLT = 0; const val S_HEAVY = 1; const val S_DRONE = 2
        val BULLET_CAP = intArrayOf(36, 64, 110, 180)
        fun altitude(kind: Int) = when (kind) { EK.GUNSHIP -> 110f; EK.BOMBER -> 150f; EK.CARRIER -> 170f; EK.MINE -> 50f; EK.EMISSILE -> 40f; else -> 75f }
    }

    var events: WorldEvents? = null
    val rnd = Random(seed)
    val level = Level(sector, threat)
    val biome get() = level.biome
    val def = level.def

    // flow
    var state = INTRO
    var stateT = 0f
    var time = 0f
    var scroll = 0f
    var scrollSpeed = BASE_SCROLL
    var timeScale = 1f
    var shake = 0f
    private var airIdx = 0
    private var groundIdx = 0

    // player
    var px = 500f; var py = vh + 120f
    val maxHp = Up.hull(load.lv[Up.HULL]).toFloat()
    var hp = maxHp
    var inv = 0f
    var shieldT = if (load.has(Perk.DEFLECTOR)) 10f else 0f
    var surgeT = 0f
    var over = if (load.has(Perk.HOT_START)) 40f else 0f
    var overT = 0f
    var secondWind = load.has(Perk.SECOND_WIND)
    var alive = true
    var hitFlash = 0f
    private var fireT = 0f
    private var missileT = 1.2f
    private var lanceT = 1.5f
    private var droneT = 0f
    val droneX = FloatArray(2); val droneY = FloatArray(2)
    var bank = 0f
    private var lastPx = 500f
    var autoFire = true

    // stats
    var coresRaw = 0
    var killed = 0
    var hostileKilled = 0
    var damageTaken = 0f
    var rescued = 0
    var chain = 0
    var chainT = 0f
    var maxChain = 0
    var aceKilled = 0
    var bossStart = 0f
    var bossTime = 0f
    var shotsFired = 0

    // entities
    val enemies = ArrayList<Enemy>()
    val shots = ShotPool(260)
    val eb = ShotPool(700)
    val cores = ShotPool(260)          // dmg = value, kind = life ticks (unused)
    val coreLife = FloatArray(260)
    val parts = PartPool(900)
    val capsules = ArrayList<Capsule>()
    val missiles = ArrayList<Missile>()
    val bolts = ArrayList<Bolt>()
    val beams = ArrayList<Beam>()
    val survivors = ArrayList<Survivor>()
    val decals = ArrayList<FloatArray>()   // x, gy, size, rot
    val popups = Array(8) { Popup() }
    var boss: Boss? = null
    var warnT = 0f
    var banner = ""
    var bannerT = 0f

    private val hpMul = Threat.HP[threat] * (1f + 0.3f * sector)
    val bulletMul = Threat.BULLET_SPEED[threat]
    val fireMul = Threat.FIRE[threat] * (1f + 0.06f * sector)
    val dmgIn = Threat.DAMAGE[threat]
    private val dmgOut = if (load.has(Perk.LEGEND)) 1.1f else 1f

    init {
        for (s in level.survivors) survivors.add(Survivor(s[0], s[1] + level.biome.lift(s[0], s[1])))
    }

    fun sfx(id: Int, vol: Float = 1f, rate: Float = 1f) { events?.sfx(id, vol, rate) }

    // ------------------------------------------------------------------ input

    /** Relative finger movement in world units. */
    fun move(dx: Float, dy: Float) {
        if (!alive || state == INTRO || state >= CLEAR) return
        px = (px + dx).coerceIn(36f, W - 36f)
        py = (py + dy).coerceIn(vh * 0.1f, vh - 70f)
    }

    fun canOverdrive() = alive && over >= 100f && overT <= 0f && (state == PLAY || state == WARN || state == BOSS)

    fun triggerOverdrive() {
        if (!canOverdrive()) return
        over = 0f
        overT = Up.overdriveTime(load.lv[Up.OVERDRIVE]) + (if (load.has(Perk.PRISM_MASTER)) 2f else 0f)
        // every hostile round on screen turns to loot
        var n = 0
        for (i in 0 until eb.cap) {
            if (!eb.alive[i]) continue
            if (n % 2 == 0) dropCore(eb.x[i], eb.y[i], 1)
            parts.add(PartPool.FLASH, eb.x[i], eb.y[i], 0f, 0f, 0.3f, 26f, Col.CYAN)
            eb.kill(i); n++
        }
        parts.add(PartPool.RING, px, py, 0f, 0f, 0.7f, 900f, 0xFFB98CFF.toInt(), dr = 0f)
        parts.add(PartPool.FLASH, px, py, 0f, 0f, 0.5f, 400f, 0xFFE2CCFF.toInt())
        shake = max(shake, 0.6f)
        sfx(Sfx.OVERDRIVE, 1f, 1f)
        events?.haptic(true)
        banner("PRISM OVERDRIVE", 1.4f)
    }

    // ------------------------------------------------------------------ main step

    fun update(realDt: Float) {
        var left = realDt.coerceIn(0f, 0.1f)
        while (left > 0f) {
            val h = min(left, 1f / 60f)
            step(h)
            left -= h
        }
    }

    private fun step(rdt: Float) {
        val targetScale = if (state == CLEAR && stateT < 1.6f) 0.35f else if (overT > 0f) 0.5f else 1f
        timeScale = approach(timeScale, targetScale, rdt * 4f)
        val dt = rdt * timeScale      // what enemies and bullets feel
        time += rdt
        stateT += rdt
        shake = max(0f, shake - rdt * 2.2f)
        hitFlash = max(0f, hitFlash - rdt * 3f)
        if (bannerT > 0f) bannerT -= rdt

        // flow
        when (state) {
            INTRO -> {
                py = lerp(vh + 120f, vh * 0.78f, easeOut(stateT / 1.5f))
                if (stateT > 1.6f) { state = PLAY; stateT = 0f }
            }
            PLAY -> if (scroll >= level.length) { state = WARN; stateT = 0f; warnT = 0f; sfx(Sfx.SIREN, 0.9f); events?.haptic(true) }
            WARN -> {
                warnT += rdt
                if (stateT > 3f) {
                    state = BOSS; stateT = 0f
                    boss = Boss(sector, this)
                    bossStart = time
                }
            }
            BOSS -> { val b = boss; if (b != null && !b.alive) { state = CLEAR; stateT = 0f; bossTime = time - bossStart } }
            CLEAR -> {
                if (stateT > 2.8f) py -= (stateT - 2.8f) * 900f * rdt
                if (stateT > 4.6f) { state = DONE; stateT = 0f }
            }
            FAILED -> if (stateT > 2.6f) { state = DONE; stateT = 0f }
        }
        val targetScroll = when (state) { WARN, BOSS -> 30f; CLEAR -> 60f; else -> BASE_SCROLL }
        scrollSpeed = approach(scrollSpeed, targetScroll, rdt * 40f)
        scroll += scrollSpeed * dt

        if (state == PLAY) spawnAir()
        if (state <= BOSS) spawnGround()

        updatePlayer(rdt, dt)
        updateEnemies(dt)
        boss?.update(dt)
        updateShots(rdt)
        updateMissiles(rdt)
        updateEnemyBullets(dt)
        updateBeams(dt)
        updatePickups(rdt, dt)
        updateSurvivors(rdt)
        updateParticles(dt)
        for (p in popups) if (p.alive) { p.t += rdt; p.y -= rdt * 50f; if (p.t > 1.2f) p.alive = false }
        val bi = bolts.iterator(); while (bi.hasNext()) { val b = bi.next(); b.life -= rdt; if (b.life <= 0f) bi.remove() }
        if (chainT > 0f) { chainT -= dt; if (chainT <= 0f) chain = 0 }
        decals.removeAll { vh - (it[1] - scroll) > vh + 120f }
    }

    // ------------------------------------------------------------------ spawning

    private fun spawnAir() {
        while (airIdx < level.air.size && level.air[airIdx].at <= scroll) {
            val s = level.air[airIdx++]
            spawn(s.kind, s.x, s.y, s.pattern, s.p1, s.p2, s.tag, true)
        }
    }

    private fun spawnGround() {
        while (groundIdx < level.ground.size && level.ground[groundIdx].y + Biome.MAX_LIFT - scroll < vh + 160f) {
            val s = level.ground[groundIdx++]
            if (s.y - scroll < -120f) continue
            val e = spawn(s.kind, s.x, 0f, s.pattern, s.p1, s.p2, s.tag, true)
            e.gy = s.y
            if (s.pattern == Pat.TRAIN) { e.x = (biome as Canyon).railX(e.gy) }
            e.lift = biome.lift(e.x, e.gy)
            e.y = vh - (e.gy + e.lift - scroll)
        }
    }

    fun spawn(kind: Int, x: Float, y: Float, pat: Int, p1: Float, p2: Float, tag: Int, counted: Boolean): Enemy {
        val e = Enemy()
        e.kind = kind; e.x = x; e.bx = x; e.y = y; e.pat = pat; e.p1 = p1; e.p2 = p2; e.tag = tag
        e.air = EK.AIR[kind]
        e.maxHp = EK.HP[kind] * hpMul; e.hp = e.maxHp
        e.r = EK.R[kind]
        e.counted = counted && EK.HOSTILE[kind]
        e.fireT = 0.8f + rnd.nextFloat() * 1.2f
        e.tur = 0f
        when (pat) {
            Pat.ARC -> { e.ty = y * vh; e.y = -60f }      // y carries the arc's top as a fraction of the view
            Pat.HOLD, Pat.HOVER -> e.ty = vh * p1
        }
        enemies.add(e)
        return e
    }

    // ------------------------------------------------------------------ player

    private fun updatePlayer(rdt: Float, dt: Float) {
        if (inv > 0f) inv -= rdt
        if (shieldT > 0f) shieldT -= rdt
        if (surgeT > 0f) surgeT -= rdt
        if (overT > 0f) { overT -= rdt; overdriveBeam(rdt) }
        bank = approach(bank, ((px - lastPx) / max(rdt, 0.001f) / 900f).coerceIn(-1f, 1f), rdt * 6f)
        lastPx = px
        // engine trail
        if (alive && state != FAILED) {
            parts.add(PartPool.TRAIL, px - 4.5f, py + 50f, 0f, 260f, 0.18f, 7f, 0xFF9FF0FF.toInt())
            parts.add(PartPool.TRAIL, px + 4.5f, py + 50f, 0f, 260f, 0.18f, 7f, 0xFF9FF0FF.toInt())
        }
        // drones follow on the wings
        val dl = load.lv[Up.DRONE]
        val nd = if (dl == 0) 0 else if (dl == 1) 1 else 2
        for (i in 0 until nd) {
            val side = if (i == 0) -1f else 1f
            val tx = px + side * 74f; val ty = py + 22f + sin(time * 3f + i) * 6f
            droneX[i] = lerp(droneX[i], tx, 1f - exp(-rdt * 9f)); droneY[i] = lerp(droneY[i], ty, 1f - exp(-rdt * 9f))
        }
        if (!alive || state == INTRO || state >= CLEAR || !autoFire) return

        val cl = if (surgeT > 0f) 5 else load.lv[Up.CANNON]
        fireT -= rdt
        if (fireT <= 0f && overT <= 0f) {
            fireT += 1f / 11f
            val d = Up.cannonDamage(cl) * dmgOut * (if (surgeT > 0f) 1.25f else 1f)
            val k = if (cl >= 5) S_HEAVY else S_BOLT
            when (Up.cannonStreams(cl)) {
                2 -> { shot(-9f, 0f, d, k); shot(9f, 0f, d, k) }
                3 -> { shot(0f, 0f, d, k); shot(-14f, -4f, d, k); shot(14f, 4f, d, k) }
                4 -> { shot(-8f, 0f, d, k); shot(8f, 0f, d, k); shot(-20f, -6f, d, k); shot(20f, 6f, d, k) }
                else -> { shot(0f, 0f, d, k); shot(-12f, 0f, d, k); shot(12f, 0f, d, k); shot(-24f, -8f, d, k); shot(24f, 8f, d, k) }
            }
            shotsFired++
            if (shotsFired % 2 == 0) sfx(Sfx.SHOT, 0.18f, 0.95f + rnd.nextFloat() * 0.1f)
        }
        if (nd > 0) {
            droneT -= rdt
            if (droneT <= 0f) {
                droneT += 1f / 6f
                val dd = (5f + 2f * dl) * dmgOut * (if (dl == 3) 1.5f else 1f)
                for (i in 0 until nd) shots.add(droneX[i], droneY[i] - 14f, 0f, -1300f, dd, S_DRONE)
            }
        }
        val ml = load.lv[Up.MISSILE]
        if (ml > 0) {
            missileT -= rdt
            if (missileT <= 0f) {
                missileT += Up.missilePeriod(ml)
                val n = Up.missileCount(ml) + (if (load.has(Perk.TRACKER)) 1 else 0)
                var fired = 0
                for (i in 0 until n) {
                    val tgt = pickTarget(i)
                    val m = Missile()
                    val side = if (i % 2 == 0) -1f else 1f
                    m.x = px + side * 29f; m.y = py + 10f
                    m.vx = side * 260f; m.vy = -200f
                    m.life = 2.6f; m.dmg = Up.missileDamage(ml) * dmgOut
                    m.target = tgt
                    if (tgt == null) m.targetPart = boss?.pickPart(i)
                    missiles.add(m); fired++
                }
                if (fired > 0) sfx(Sfx.MISSILE, 0.4f, 1f)
            }
        }
        val ll = load.lv[Up.LANCE]
        if (ll > 0) {
            lanceT -= rdt
            if (lanceT <= 0f) {
                lanceT += Up.lancePeriod(ll)
                arcLance(Up.lanceTargets(ll), Up.lanceDamage(ll) * dmgOut)
            }
        }
    }

    private fun shot(ox: Float, angDeg: Float, d: Float, k: Int) {
        val a = angDeg * PI.toFloat() / 180f
        shots.add(px + ox, py - 40f, sin(a) * 1500f, -cos(a) * 1500f, d, k)
    }

    private fun pickTarget(i: Int): Enemy? {
        var best: Enemy? = null
        var bs = Float.MAX_VALUE
        for (e in enemies) {
            if (!e.alive || e.y < -20f || e.y > vh || e.kind == EK.EMISSILE) continue
            val d = hypot(e.x - px, e.y - py) + (if (e.air) 0f else 150f) + i * 37f * ((e.x.toInt() + i) % 3)
            if (d < bs) { bs = d; best = e }
        }
        return best
    }

    private fun arcLance(n: Int, d: Float) {
        val hit = ArrayList<Enemy>()
        var fx = px; var fy = py - 30f
        val pts = FloatArray(2 + n * 2 * 6)
        var np = 0
        pts[np++] = fx; pts[np++] = fy
        for (k in 0 until n) {
            var best: Enemy? = null
            var bs = if (k == 0) 460f else 330f
            for (e in enemies) {
                if (!e.alive || e.y < 0f || e.y > vh || hit.contains(e) || e.kind == EK.EMISSILE) continue
                val dd = hypot(e.x - fx, e.y - fy)
                if (dd < bs) { bs = dd; best = e }
            }
            val bp = if (best == null) boss?.nearestPart(fx, fy, if (k == 0) 520f else 330f) else null
            val tx: Float; val ty: Float
            if (best != null) { tx = best.x; ty = best.y } else if (bp != null) { tx = boss!!.partX(bp); ty = boss!!.partY(bp) } else break
            // jagged segment
            for (s in 1..5) {
                val f = s / 5f
                val jx = if (s < 5) (rnd.nextFloat() - 0.5f) * 36f else 0f
                val jy = if (s < 5) (rnd.nextFloat() - 0.5f) * 36f else 0f
                if (np + 2 <= pts.size) { pts[np++] = lerp(fx, tx, f) + jx; pts[np++] = lerp(fy, ty, f) + jy }
            }
            if (best != null) { hit.add(best); damage(best, d) } else if (bp != null) boss!!.hitPart(bp, d)
            parts.add(PartPool.FLASH, tx, ty, 0f, 0f, 0.25f, 60f, 0xFFB7F4FF.toInt())
            fx = tx; fy = ty
        }
        if (np > 2) { bolts.add(Bolt(pts, np / 2)); sfx(Sfx.ZAP, 0.5f, 0.9f + rnd.nextFloat() * 0.2f) }
    }

    private fun overdriveBeam(rdt: Float) {
        val dps = 340f * (1f + 0.12f * load.lv[Up.OVERDRIVE]) * dmgOut
        for (e in enemies) {
            if (!e.alive || e.y > py || e.y < -40f) continue
            if (abs(e.x - px) < 52f + e.r * 0.5f) { damage(e, dps * rdt); if (rnd.nextFloat() < 0.3f) spark(e.x, e.y + e.r * 0.5f, 0xFFE2CCFF.toInt()) }
        }
        boss?.beamDamage(px, py, 60f, dps * rdt)
        for (i in 0 until eb.cap) if (eb.alive[i] && abs(eb.x[i] - px) < 60f && eb.y[i] < py) { dropCore(eb.x[i], eb.y[i], 1); eb.kill(i) }
        if (rnd.nextFloat() < 0.6f) parts.add(PartPool.SPARK, px + (rnd.nextFloat() - 0.5f) * 80f, py - 60f - rnd.nextFloat() * 400f, (rnd.nextFloat() - 0.5f) * 200f, -600f, 0.3f, 3f, 0xFFE2CCFF.toInt())
    }

    // ------------------------------------------------------------------ enemies

    private fun updateEnemies(dt: Float) {
        val px0 = px; val py0 = py
        var i = 0
        while (i < enemies.size) {
            val e = enemies[i]
            if (!e.alive) { enemies.removeAt(i); continue }
            e.t += dt
            if (e.flash > 0f) e.flash -= dt * 6f
            if (e.air) moveAir(e, dt) else moveGround(e, dt)
            // off-screen exits (no kill credit)
            val out = if (e.air) (e.y > vh + 200f || e.y < -400f || e.x < -300f || e.x > W + 300f) else (e.y > vh + 160f)
            if (out && e.t > 1f) { e.alive = false; enemies.removeAt(i); continue }
            if (alive && state != INTRO && state < CLEAR && e.y > -10f && e.y < vh - 10f) fire(e, dt, px0, py0)
            // ram
            if (e.air && alive && e.kind != EK.EMISSILE && inv <= 0f && overT <= 0f && hypot(e.x - px, e.y - py) < e.r * 0.75f + 18f) {
                hurt(dmgIn * 1.6f)
                damage(e, 60f)
            }
            if (e.kind == EK.EMISSILE && alive && hypot(e.x - px, e.y - py) < 16f) {
                if (overT <= 0f) hurt(dmgIn * 1.6f)
                e.alive = false; boom(e.x, e.y, 0.5f, true)
            }
            i++
        }
    }

    private fun moveAir(e: Enemy, dt: Float) {
        when (e.kind) {
            EK.EMISSILE -> {
                val want = atan2(px - e.x, py - e.y)
                var da = want - e.ang
                while (da > PI) da -= (2 * PI).toFloat(); while (da < -PI) da += (2 * PI).toFloat()
                if (e.t < 4.5f) e.ang += da.coerceIn(-2.1f * dt, 2.1f * dt)
                val sp = 270f * bulletMul
                e.x += sin(e.ang) * sp * dt; e.y += cos(e.ang) * sp * dt
                if (e.t > 7f) { e.alive = false; boom(e.x, e.y, 0.4f, true) }
                if (rnd.nextFloat() < 0.5f) parts.add(PartPool.SMOKE, e.x - sin(e.ang) * 14f, e.y - cos(e.ang) * 14f, 0f, 0f, 0.5f, 12f, 0, 10f)
                return
            }
        }
        when (e.pat) {
            Pat.DOWN -> {
                e.y += e.p1 * dt
                val nx = e.bx + sin(e.t * 2f) * e.p2
                e.ang = ((nx - e.x) / max(dt, 0.001f) / 400f).coerceIn(-0.5f, 0.5f) * 0.6f
                e.x = nx
            }
            Pat.HOLD -> {
                when (e.state) {
                    0 -> { e.y += max(70f, (e.ty - e.y) * 2.2f) * dt; if (e.y >= e.ty - 4f) { e.state = 1; e.charge = 0f } }
                    1 -> {
                        e.charge += dt
                        e.x = e.bx + sin(e.charge * 0.7f) * (if (e.kind == EK.LANCER) 40f else 140f)
                        e.y = e.ty + sin(e.charge * 1.3f) * 10f
                        if (e.charge > e.p2) e.state = 2
                    }
                    else -> { e.vy += 220f * dt; e.y += e.vy * dt }
                }
            }
            Pat.ARC -> {
                val dir = e.p1
                e.x += dir * 360f * dt
                val f = (if (dir > 0) e.x + 60f else W + 60f - e.x) / (W + 120f)
                val ny = e.ty + sin(f.coerceIn(0f, 1f) * PI.toFloat()) * vh * 0.42f
                val vy = (ny - e.y) / max(dt, 0.001f)
                e.ang = atan2(dir * 360f, vy)
                e.y = ny
                if (f > 1.05f) e.y = vh + 300f
            }
            Pat.HOVER -> {
                if (e.state == 0) { e.y += max(80f, (e.ty - e.y) * 2.5f) * dt; if (e.y >= e.ty - 4f) { e.state = 1; e.charge = 0f } }
                else if (e.state == 1) {
                    e.charge += dt
                    e.x = e.bx + sin(e.charge * 1.3f) * 60f
                    e.y = e.ty + cos(e.charge * 1.3f) * 40f
                    if (e.charge > e.p2) e.state = 2
                } else { e.vy += 160f * dt; e.y += e.vy * dt }
                e.ang = e.t * 3f
            }
            Pat.DRIFT -> {
                e.y += e.p1 * dt
                e.x += ((px - e.x) * 0.35f).coerceIn(-55f, 55f) * dt
                e.ang += dt * 1.2f
                if (alive && hypot(px - e.x, py - e.y) < 125f && e.t > 0.5f) { mineBurst(e); e.alive = false }
            }
        }
    }

    private fun moveGround(e: Enemy, dt: Float) {
        if (e.pat == Pat.TRAIN) {
            e.gy += e.p1 * dt
            val cb = biome as Canyon
            val nx = cb.railX(e.gy)
            e.ang = atan2(-(cb.railX(e.gy + 5f) - nx), -5f) + PI.toFloat()
            e.x = nx
            e.lift = biome.lift(e.x, e.gy)
        }
        e.y = vh - (e.gy + e.lift - scroll)
        if (e.kind == EK.BOAT) e.ang = sin(e.t * 0.8f + e.bx) * 0.05f
        if (e.kind == EK.RADAR) e.tur += dt * 1.8f
    }

    private fun aimAt(e: Enemy) = atan2(px - e.x, py - e.y)

    private fun fire(e: Enemy, dt: Float, tx: Float, ty: Float) {
        // turrets track the player
        if (e.kind == EK.TANK || e.kind == EK.FLAK || e.kind == EK.BOAT || e.kind == EK.TRAIN) {
            val want = aimAt(e)
            var da = want - e.tur
            while (da > PI) da -= (2 * PI).toFloat(); while (da < -PI) da += (2 * PI).toFloat()
            e.tur += da.coerceIn(-2.4f * dt, 2.4f * dt)
        }
        e.fireT -= dt * fireMul
        if (e.kind == EK.LANCER) { lancer(e, dt); return }
        if (e.fireT > 0f) return
        // fairness: nothing shoots from the lower part of the screen or point-blank,
        // and the screen never fills past the threat's bullet budget
        if (e.y > vh * (if (e.air) 0.6f else 0.62f) || e.y < vh * 0.03f || hypot(e.x - px, e.y - py) < 230f || eb.count >= BULLET_CAP[threat]) {
            e.fireT = 0.25f; return
        }
        val sp = bulletMul
        when (e.kind) {
            EK.DART -> { aimed(e.x, e.y + 20f, 330f * sp, EB_PINK, 1, 0f); e.fireT = 1.9f }
            EK.SWOOP -> { if (e.state == 0 && e.y > vh * 0.12f) { aimed(e.x, e.y, 300f * sp, EB_PINK, 3, 0.26f); e.state = 1 }; e.fireT = 0.4f }
            EK.WASP -> { if (e.state == 1) ring(e.x, e.y, 8, 190f * sp, EB_ORANGE, e.t); e.fireT = 2.4f }
            EK.GUNSHIP -> {
                if (e.burst > 0) { aimed(e.x, e.y + 40f, 380f * sp, EB_PINK, 1, 0f); e.burst--; e.fireT = 0.09f }
                else {
                    if ((e.t.toInt() / 2) % 2 == 0) { e.burst = 5; e.fireT = 0.1f } else { fan(e.x, e.y + 40f, 7, 1.1f, 250f * sp, EB_ORANGE); e.fireT = 1.5f }
                }
            }
            EK.BOMBER -> {
                for (k in -2..2) bullet(e.x + k * 34f, e.y + 30f, k * 25f, 170f * sp, EB_ORANGE)
                aimed(e.x, e.y + 50f, 360f * sp, EB_NEEDLE, 1, 0f)
                e.fireT = 1.25f
            }
            EK.CARRIER -> {
                if (e.state == 1 && e.burst % 2 == 0) {
                    for (s in floatArrayOf(-95f, 95f)) { val d = spawn(EK.DART, e.x + s, e.y + 20f, Pat.DOWN, 230f, 30f, 0, false); d.t = 0.5f }
                } else ring(e.x, e.y, 14, 170f * sp, EB_PINK, e.t * 0.7f)
                e.burst++; e.fireT = 2.6f
            }
            EK.TANK -> { aimedAngle(e.x, e.y, e.tur, 250f * sp, EB_ORANGE, 28f); e.fireT = 2.3f }
            EK.FLAK -> {
                if (e.burst > 0) { aimedAngle(e.x, e.y, e.tur + (rnd.nextFloat() - 0.5f) * 0.12f, 340f * sp, EB_PINK, 26f); e.burst--; e.fireT = 0.14f }
                else { e.burst = 3; e.fireT = 2.1f }
            }
            EK.SAM -> {
                val m = spawn(EK.EMISSILE, e.x, e.y, Pat.DOWN, 0f, 0f, 0, false)
                m.ang = aimAt(e); m.t = 0f
                sfx(Sfx.LAUNCH, 0.35f)
                e.fireT = 3.6f
            }
            EK.BOAT -> { fanAngle(e.x, e.y, e.tur, if (threat < 2) 3 else 5, if (threat < 2) 0.5f else 0.9f, 230f * sp, EB_ORANGE); e.fireT = 2.8f }
            EK.TRAIN -> { aimedAngle(e.x, e.y, e.tur, 300f * sp, EB_PINK, 24f); e.fireT = 1.9f + (e.bx % 0.5f) }
            else -> e.fireT = 9f
        }
    }

    private fun lancer(e: Enemy, dt: Float) {
        if (e.state != 1) return
        when (e.burst) {
            0 -> if (e.fireT <= 0f) { e.burst = 1; e.lockX = px; e.lockY = py; e.cnt = 0 }
            1 -> {
                e.lockX = lerp(e.lockX, px, dt * 0.8f)
                if (e.fireT <= -0.9f) { e.burst = 2; e.fireT = 0f; sfx(Sfx.LASER, 0.35f) }
            }
            2 -> {
                if (e.fireT <= -0.07f) {
                    val a = atan2(e.lockX - e.x, e.lockY - e.y)
                    bullet(e.x, e.y + 40f, sin(a) * 620f * bulletMul, cos(a) * 620f * bulletMul, EB_NEEDLE)
                    e.cnt++; e.fireT = 0f
                    if (e.cnt >= 8) { e.burst = 0; e.cnt = 0; e.fireT = 2.2f }
                }
            }
        }
    }

    private fun mineBurst(e: Enemy) {
        ring(e.x, e.y, 10, 210f * bulletMul, EB_PINK, e.t)
        boom(e.x, e.y, 0.6f, true)
    }

    // bullet helpers (also used by bosses)
    fun bullet(x: Float, y: Float, vx: Float, vy: Float, k: Int) { eb.add(x, y, vx, vy, 0f, k) }
    fun aimed(x: Float, y: Float, sp: Float, k: Int, n: Int, spread: Float) {
        val a = atan2(px - x, py - y)
        fanAngle(x, y, a, n, spread * (n - 1), sp, k)
    }
    fun aimedAngle(x: Float, y: Float, a: Float, sp: Float, k: Int, muzzle: Float) {
        bullet(x + sin(a) * muzzle, y + cos(a) * muzzle, sin(a) * sp, cos(a) * sp, k)
    }
    fun fan(x: Float, y: Float, n: Int, spread: Float, sp: Float, k: Int) = fanAngle(x, y, 0f, n, spread, sp, k)
    fun fanAngle(x: Float, y: Float, center: Float, n: Int, spread: Float, sp: Float, k: Int) {
        for (i in 0 until n) {
            val a = center + if (n == 1) 0f else (i / (n - 1f) - 0.5f) * spread
            bullet(x, y, sin(a) * sp, cos(a) * sp, k)
        }
    }
    fun ring(x: Float, y: Float, n: Int, sp: Float, k: Int, off: Float) {
        for (i in 0 until n) { val a = off + i * 2f * PI.toFloat() / n; bullet(x, y, sin(a) * sp, cos(a) * sp, k) }
    }

    // ------------------------------------------------------------------ damage & death

    fun damage(e: Enemy, d: Float) {
        if (!e.alive) return
        e.hp -= d
        e.flash = 1f
        if (e.hp <= 0f) kill(e)
    }

    private fun kill(e: Enemy) {
        e.alive = false
        killed++
        if (e.counted) hostileKilled++
        if (e.tag == Tag.ACE) { aceKilled++; popup("TARGET DOWN", e.x, e.y - 40f, Col.AMBER, 1f) }
        if (e.kind != EK.EMISSILE) {
            chain++; chainT = 2.0f
            if (chain > maxChain) maxChain = chain
            if (chain % 10 == 0) popup("CHAIN ×$chain", e.x, e.y - 30f, Col.CYAN, 1.1f)
        }
        val big = EK.BIG[e.kind]
        val size = when (e.kind) { EK.BOMBER, EK.CARRIER -> 2.2f; EK.GUNSHIP, EK.BUNKER, EK.TRAIN -> 1.5f; EK.EMISSILE, EK.MINE -> 0.5f; else -> 1f }
        boom(e.x, e.y, size, e.air)
        if (!e.air && biome.kind(e.x, e.gy) != Ground.WATER) decals.add(floatArrayOf(e.x, e.gy + e.lift, 70f * size, rnd.nextFloat() * 360f))
        if (!e.air && biome.kind(e.x, e.gy) == Ground.WATER) parts.add(PartPool.RING, e.x, e.y, 0f, 0f, 1.2f, 110f * size, 0xCCFFFFFF.toInt(), dr = 0f, onGround = true)
        over = min(100f, over + (if (big) 12f else 3f) * Up.overdriveGain(load.lv[Up.OVERDRIVE]))
        val v = EK.CORES[e.kind]
        if (v > 0) dropCores(e.x, e.y, v)
        val chance = (if (big) 0.4f else if (e.kind == EK.BUNKER) 0.25f else 0.025f) * (if (load.has(Perk.LUCKY)) 1.25f else 1f)
        if (rnd.nextFloat() < chance) {
            val r = rnd.nextFloat()
            val type = if (r < 0.34f) CAP_REPAIR else if (r < 0.54f) CAP_SHIELD else if (r < 0.8f) CAP_SURGE else CAP_OVER
            capsules.add(Capsule(e.x, e.y, type))
        }
        sfx(if (size >= 1.4f) Sfx.BOOM_BIG else Sfx.BOOM, if (size >= 1.4f) 0.9f else 0.55f, 0.9f + rnd.nextFloat() * 0.2f)
        if (size >= 1.4f) { shake = max(shake, 0.5f); events?.haptic(true) }
    }

    fun boom(x: Float, y: Float, size: Float, air: Boolean) {
        val n = (5 + size * 5).toInt()
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.28f; val sp = (40f + rnd.nextFloat() * 160f) * size
            parts.add(PartPool.FIRE, x + cos(a) * 8f * size, y + sin(a) * 8f * size, cos(a) * sp, sin(a) * sp - (if (air) 0f else 0f),
                0.45f + rnd.nextFloat() * 0.35f, (26f + rnd.nextFloat() * 22f) * size, 0, 70f * size, rnd.nextFloat() * 0.12f * size, !air, 3f)
        }
        for (i in 0 until (3 + size * 3).toInt()) {
            val a = rnd.nextFloat() * 6.28f; val sp = 30f + rnd.nextFloat() * 60f
            parts.add(PartPool.SMOKE, x, y, cos(a) * sp, sin(a) * sp - 20f, 1.1f + rnd.nextFloat() * 0.8f, (24f + rnd.nextFloat() * 18f) * size, 0, 40f * size, 0.15f + rnd.nextFloat() * 0.2f, !air, 1.2f)
        }
        for (i in 0 until (6 + size * 8).toInt()) {
            val a = rnd.nextFloat() * 6.28f; val sp = 250f + rnd.nextFloat() * 450f * size
            parts.add(PartPool.SPARK, x, y, cos(a) * sp, sin(a) * sp, 0.25f + rnd.nextFloat() * 0.3f, 3f, 0xFFFFD27A.toInt(), dr = 2.5f)
        }
        for (i in 0 until (2 + size * 3).toInt()) {
            val a = rnd.nextFloat() * 6.28f; val sp = 120f + rnd.nextFloat() * 220f * size
            parts.add(PartPool.DEBRIS, x, y, cos(a) * sp, sin(a) * sp, 0.6f + rnd.nextFloat() * 0.5f, 4f + rnd.nextFloat() * 5f * size, 0xFF2A2D33.toInt(), dr = 1.8f)
        }
        parts.add(PartPool.FLASH, x, y, 0f, 0f, 0.18f, 90f * size, 0xFFFFF0C8.toInt())
        if (size >= 1f) parts.add(PartPool.RING, x, y, 0f, 0f, 0.45f, 130f * size, 0xFFFFD9A0.toInt(), dr = 0f)
    }

    fun spark(x: Float, y: Float, col: Int) {
        val a = rnd.nextFloat() * 6.28f
        parts.add(PartPool.SPARK, x, y, cos(a) * 220f, sin(a) * 220f - 120f, 0.15f, 2.5f, col, dr = 3f)
    }

    fun hurt(d: Float) {
        if (!alive || state >= CLEAR || inv > 0f || overT > 0f) return
        if (shieldT > 0f) {
            parts.add(PartPool.RING, px, py, 0f, 0f, 0.3f, 80f, 0xFF7FB8FF.toInt(), dr = 0f)
            sfx(Sfx.SHIELD_HIT, 0.6f)
            return
        }
        hp -= d
        damageTaken += d
        inv = 1.3f
        hitFlash = 1f
        shake = max(shake, 0.45f)
        events?.haptic(true)
        sfx(Sfx.HURT, 0.8f)
        if (hp <= 0f) {
            if (secondWind) {
                secondWind = false; hp = 1f; inv = 2.2f
                banner("SECOND WIND", 1.4f)
                return
            }
            hp = 0f
            alive = false
            state = FAILED; stateT = 0f
            boom(px, py, 2.4f, true)
            for (k in 0 until 4) boom(px + (rnd.nextFloat() - 0.5f) * 80f, py + (rnd.nextFloat() - 0.5f) * 80f, 1.2f, true)
            sfx(Sfx.BOOM_BIG, 1f, 0.8f)
            shake = 1.2f
        }
    }

    fun banner(s: String, t: Float) { banner = s; bannerT = t }

    fun popup(text: String, x: Float, y: Float, col: Int, size: Float) {
        var p = popups[0]
        for (q in popups) if (!q.alive) { p = q; break } else if (q.t > p.t) p = q
        p.text = text; p.x = x.coerceIn(140f, W - 140f); p.y = y; p.t = 0f; p.col = col; p.size = size; p.alive = true
    }

    // ------------------------------------------------------------------ projectiles

    private fun updateShots(rdt: Float) {
        val b = boss
        for (i in 0 until shots.cap) {
            if (!shots.alive[i]) continue
            shots.x[i] += shots.vx[i] * rdt; shots.y[i] += shots.vy[i] * rdt
            val x = shots.x[i]; val y = shots.y[i]
            if (y < -60f || x < -40f || x > W + 40f) { shots.kill(i); continue }
            var hit = false
            for (e in enemies) {
                if (!e.alive || e.kind == EK.EMISSILE && e.hp <= 0f) continue
                if (e.y < -30f) continue
                val dx = x - e.x; val dy = y - e.y
                val rr = e.r + 6f
                if (dx * dx + dy * dy < rr * rr) {
                    damage(e, shots.dmg[i])
                    if (rnd.nextFloat() < 0.5f) spark(x, y, 0xFFBFF6FF.toInt())
                    hit = true; break
                }
            }
            if (!hit && b != null && b.alive && b.hitTest(x, y, shots.dmg[i])) { hit = true; if (rnd.nextFloat() < 0.5f) spark(x, y, 0xFFBFF6FF.toInt()) }
            if (hit) shots.kill(i)
        }
    }

    private fun updateMissiles(rdt: Float) {
        val it = missiles.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.life -= rdt
            var t = m.target
            if (t != null && !t.alive) { t = null; m.target = null }
            val tp = m.targetPart
            var tx = Float.NaN; var ty = 0f
            if (t != null) { tx = t.x; ty = t.y }
            else if (tp != null && tp.alive && boss != null) { tx = boss!!.partX(tp); ty = boss!!.partY(tp) }
            else if (m.life < 2.3f) { val n = pickTarget(0); if (n != null) m.target = n }
            val sp = 780f
            if (!tx.isNaN()) {
                val want = atan2(tx - m.x, ty - m.y)
                val cur = atan2(m.vx, m.vy)
                var da = want - cur
                while (da > PI) da -= (2 * PI).toFloat(); while (da < -PI) da += (2 * PI).toFloat()
                val na = cur + da.coerceIn(-7f * rdt, 7f * rdt)
                m.vx = sin(na) * sp; m.vy = cos(na) * sp
            } else { m.vy = approach(m.vy, -sp, rdt * 1500f) }
            m.x += m.vx * rdt; m.y += m.vy * rdt
            m.trailT -= rdt
            if (m.trailT <= 0f) { m.trailT = 0.025f; parts.add(PartPool.SMOKE, m.x, m.y, 0f, 0f, 0.45f, 9f, 0, 22f, 0f, false, 2f) }
            var hit = false
            if (t != null && hypot(t.x - m.x, t.y - m.y) < t.r + 10f) { damage(t, m.dmg); hit = true }
            else if (boss != null && boss!!.alive && boss!!.hitTest(m.x, m.y, m.dmg)) hit = true
            if (hit) { boom(m.x, m.y, 0.45f, true); sfx(Sfx.BOOM, 0.3f, 1.3f) }
            if (hit || m.life <= 0f || m.y < -120f || m.y > vh + 100f || m.x < -100f || m.x > W + 100f) it.remove()
        }
    }

    private fun updateEnemyBullets(dt: Float) {
        for (i in 0 until eb.cap) {
            if (!eb.alive[i]) continue
            eb.x[i] += eb.vx[i] * dt; eb.y[i] += eb.vy[i] * dt
            val x = eb.x[i]; val y = eb.y[i]
            if (y > vh + 40f || y < -200f || x < -60f || x > W + 60f) { eb.kill(i); continue }
            if (eb.kind[i] == EB_SPLIT) {
                eb.dmg[i] += dt
                if (eb.dmg[i] > 1.1f) { ring(x, y, 8, 200f * bulletMul, EB_ORANGE, eb.dmg[i]); eb.kill(i); parts.add(PartPool.FLASH, x, y, 0f, 0f, 0.2f, 60f, 0xFFFFB0D0.toInt()); continue }
            }
            if (!alive) continue
            val r = when (eb.kind[i]) { EB_BIG, EB_SPLIT -> 16f; EB_NEEDLE -> 6f; else -> 8f } + PLAYER_R
            val dx = x - px; val dy = y - py
            if (dx * dx + dy * dy < r * r) {
                if (overT > 0f) { dropCore(x, y, 1); eb.kill(i); continue }
                if (inv > 0f && shieldT <= 0f) continue
                eb.kill(i)
                hurt(dmgIn * (if (eb.kind[i] == EB_BIG || eb.kind[i] == EB_SPLIT) 1.6f else 1f))
            }
        }
    }

    private fun updateBeams(dt: Float) {
        val it = beams.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.t += dt
            val f = b.follow
            if (f != null) { if (!f.alive) { it.remove(); continue }; b.x = boss!!.partX(f); b.y = boss!!.partY(f) }
            if (b.live()) {
                b.ang += b.sweep * dt
                if (alive && overT <= 0f) {
                    val ex = b.x + sin(b.ang) * b.len; val ey = b.y + cos(b.ang) * b.len
                    if (segDist(px, py, b.x, b.y, ex, ey) < b.width * 0.5f + PLAYER_R - 4f) hurt(dmgIn * 1.4f)
                }
            }
            if (b.t > b.warn + b.on) it.remove()
        }
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 <= 0f) 0f else clamp01(((px - ax) * dx + (py - ay) * dy) / l2)
        return hypot(px - (ax + dx * t), py - (ay + dy * t))
    }

    // ------------------------------------------------------------------ pickups

    fun dropCores(x: Float, y: Float, value: Int) {
        var v = value
        while (v > 0) {
            val c = if (v >= 10) 5 else 1
            dropCore(x + (rnd.nextFloat() - 0.5f) * 30f, y + (rnd.nextFloat() - 0.5f) * 30f, c)
            v -= c
        }
    }

    fun dropCore(x: Float, y: Float, v: Int) {
        val a = rnd.nextFloat() * 6.28f; val sp = 60f + rnd.nextFloat() * 160f
        val i = cores.add(x, y, cos(a) * sp, sin(a) * sp, v.toFloat(), 0)
        if (i >= 0) coreLife[i] = 9f
    }

    private fun updatePickups(rdt: Float, dt: Float) {
        val mag = Up.magnet(load.lv[Up.MAGNET])
        for (i in 0 until cores.cap) {
            if (!cores.alive[i]) continue
            coreLife[i] -= rdt
            if (coreLife[i] <= 0f) { cores.kill(i); continue }
            var vx = cores.vx[i]; var vy = cores.vy[i]
            val dx = px - cores.x[i]; val dy = py - cores.y[i]
            val d = hypot(dx, dy)
            if (alive && (d < mag || state == CLEAR)) {
                val pull = 2600f * (1f - d / (mag + 400f)).coerceAtLeast(0.3f)
                vx += dx / max(d, 1f) * pull * rdt; vy += dy / max(d, 1f) * pull * rdt
                vx *= 0.9f; vy *= 0.9f
            } else {
                vx *= exp(-2.5f * rdt); vy *= exp(-2.5f * rdt)
                cores.y[i] += scrollSpeed * 0.6f * dt
            }
            cores.vx[i] = vx; cores.vy[i] = vy
            cores.x[i] += vx * rdt; cores.y[i] += vy * rdt
            if (cores.y[i] > vh + 30f) { cores.kill(i); continue }
            if (alive && d < 34f) {
                coresRaw += cores.dmg[i].toInt()
                cores.kill(i)
                if (rnd.nextFloat() < 0.5f) sfx(Sfx.PICK, 0.25f, 1f + rnd.nextFloat() * 0.3f)
            }
        }
        val it = capsules.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.t += dt
            c.y += 70f * dt
            c.x += sin(c.t * 2f) * 30f * dt
            if (c.y > vh + 40f) { it.remove(); continue }
            if (alive && hypot(c.x - px, c.y - py) < 52f) {
                it.remove()
                when (c.type) {
                    CAP_REPAIR -> { val heal = maxHp * 0.25f * (if (load.has(Perk.MEDIC)) 1.5f else 1f); hp = min(maxHp, hp + heal); popup("REPAIRED", px, py - 70f, Col.GREEN, 1f) }
                    CAP_SHIELD -> { shieldT = 8f; popup("SHIELD", px, py - 70f, 0xFF7FB8FF.toInt(), 1f) }
                    CAP_SURGE -> { surgeT = 10f; popup("WEAPON SURGE", px, py - 70f, Col.AMBER, 1f) }
                    else -> { over = min(100f, over + 40f); popup("+OVERDRIVE", px, py - 70f, 0xFFC9A0FF.toInt(), 1f) }
                }
                sfx(Sfx.POWER, 0.8f)
                events?.haptic(false)
            }
        }
    }

    private fun updateSurvivors(rdt: Float) {
        for (s in survivors) {
            if (s.state == 1) { s.beam = max(0f, s.beam - rdt * 1.5f); continue }
            if (s.state == 2) continue
            val y = vh - (s.gy - scroll)
            if (y > vh + 60f) { s.state = 2; continue }
            if (y < -40f) continue
            if (alive && state != FAILED && hypot(s.x - px, y - py) < 125f) {
                s.prog += rdt / 1.1f
                if (s.prog >= 1f) {
                    s.state = 1; s.beam = 1f
                    rescued++
                    coresRaw += 30
                    popup("RESCUED $rescued/5", s.x, y - 60f, Col.GREEN, 1.1f)
                    sfx(Sfx.RESCUE, 0.8f)
                    events?.haptic(false)
                }
            } else s.prog = max(0f, s.prog - rdt * 0.3f)
        }
    }

    private fun updateParticles(dt: Float) {
        val p = parts
        for (i in 0 until p.cap) {
            if (p.life[i] <= 0f) continue
            if (p.delay[i] > 0f) { p.delay[i] -= dt; continue }
            p.life[i] -= dt
            val d = exp(-p.drag[i] * dt)
            p.vx[i] *= d; p.vy[i] *= d
            p.x[i] += p.vx[i] * dt; p.y[i] += p.vy[i] * dt
            if (p.ground[i]) p.y[i] += scrollSpeed * dt
            p.size[i] += p.grow[i] * dt
            p.rot[i] += p.vr[i] * dt
        }
    }

    // ------------------------------------------------------------------ results

    fun killRatio() = if (level.hostiles == 0) 1f else hostileKilled / level.hostiles.toFloat()

    fun aceDone(): Boolean = when (sector) {
        0, 1, 3, 5 -> level.aceTotal > 0 && aceKilled >= level.aceTotal
        2 -> state >= CLEAR && bossTime > 0f && bossTime <= 75f
        else -> maxChain >= 35
    }

    fun aceProgress(): String = when (sector) {
        0, 1, 3, 5 -> "$aceKilled/${level.aceTotal}"
        2 -> if (bossTime > 0f) "${bossTime.toInt()} s" else "--"
        else -> "×$maxChain"
    }

    fun cleared() = alive && (state == CLEAR || state == DONE)

    /** Badge bits earned this run (only on a cleared mission). */
    fun badges(): Int {
        if (!alive) return 0
        var b = 0
        if (killRatio() >= 0.8f) b = b or 1
        if (rescued >= 5) b = b or 2
        if (damageTaken <= 0f) b = b or 4
        if (aceDone()) b = b or 8
        return b
    }

    fun payout(): Int {
        var m = Threat.REWARD[threat]
        if (load.has(Perk.SALVAGER)) m *= 1.1f
        if (load.has(Perk.ACE)) m *= 1.25f
        return (coresRaw * m).toInt()
    }

    // ------------------------------------------------------------------ test hooks / autopilot

    /** Jump ahead without spawning what was skipped (tests and the attract demo). */
    fun skipTo(to: Float) {
        scroll = to
        while (airIdx < level.air.size && level.air[airIdx].at <= scroll) airIdx++
        while (groundIdx < level.ground.size && level.ground[groundIdx].y - scroll < vh - 200f) groundIdx++
        for (s in survivors) if (vh - (s.gy - scroll) > vh) s.state = 2
    }

    /** A cautious bot: hunts survivors and enemies, sidesteps nearby rounds. Returns desired (dx, dy). */
    var apSkill = 1f
    private var apX = 0f; private var apY = 0f
    fun autopilot(out: FloatArray) {
        out[0] = 0f; out[1] = 0f
        if (apSkill < 1f && rnd.nextFloat() < 0.45f * (1f - apSkill)) { out[0] = apX * 0.8f; out[1] = apY * 0.8f; return }
        if (!alive || state == INTRO || state >= CLEAR) return
        // preferred spot: under the nearest threat, or over an open survivor
        var tx = 500f; var ty = vh * 0.72f
        var bestS = Float.MAX_VALUE
        for (s in survivors) {
            if (s.state != 0) continue
            val y = vh - (s.gy - scroll)
            if (y < vh * 0.15f || y > vh - 40f) continue
            if (y < bestS) { bestS = y; tx = s.x; ty = (y + 40f).coerceIn(vh * 0.3f, vh - 90f) }
        }
        if (bestS == Float.MAX_VALUE) {
            var be: Enemy? = null; var bd = Float.MAX_VALUE
            for (e in enemies) { if (!e.alive || e.y < 0f || e.y > py - 60f || e.kind == EK.EMISSILE) continue; val d = abs(e.x - px) + (py - e.y) * 0.3f; if (d < bd) { bd = d; be = e } }
            val b = boss
            if (be != null) tx = be.x else if (b != null && b.alive) tx = b.aimX()
            // drift towards loot
            var cx = 0f; var cn = 0
            for (i in 0 until cores.cap) if (cores.alive[i] && cores.y[i] > vh * 0.4f) { cx += cores.x[i]; cn++ }
            if (cn > 3 && be == null) tx = cx / cn
            for (c in capsules) if (c.y > vh * 0.3f) { tx = c.x; ty = c.y + 30f }
        }
        // evaluate candidate moves against incoming danger
        var bestScore = Float.MAX_VALUE
        var bx = 0f; var by = 0f
        val step = 26f
        for (gx in -3..3) for (gy in -3..3) {
            val cx = (px + gx * step).coerceIn(36f, W - 36f); val cy = (py + gy * step).coerceIn(vh * 0.1f, vh - 70f)
            var danger = 0f
            for (i in 0 until eb.cap) {
                if (!eb.alive[i]) continue
                for (k in 1..(1 + (3 * apSkill).toInt())) {
                    val tt = k * 0.07f
                    val bxp = eb.x[i] + eb.vx[i] * tt * timeScale; val byp = eb.y[i] + eb.vy[i] * tt * timeScale
                    val d = hypot(bxp - cx, byp - cy)
                    if (d < 46f) danger += (46f - d) * (5 - k)
                }
            }
            for (e in enemies) if (e.air && e.alive) { val d = hypot(e.x - cx, e.y - cy); if (d < e.r + 50f) danger += (e.r + 50f - d) * 6f }
            for (b in beams) if (b.t > b.warn * 0.4f) {
                val ex = b.x + sin(b.ang) * b.len; val ey = b.y + cos(b.ang) * b.len
                val d = segDist(cx, cy, b.x, b.y, ex, ey)
                if (d < b.width + 50f) danger += (b.width + 50f - d) * 8f
            }
            val score = danger * 4f + abs(cx - tx) * 0.35f + abs(cy - ty) * 0.25f + (abs(gx) + abs(gy)) * 0.5f
            if (score < bestScore) { bestScore = score; bx = cx - px; by = cy - py }
        }
        out[0] = bx; out[1] = by
        apX = bx; apY = by
    }
}
