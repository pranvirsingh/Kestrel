package com.pranvir.kestrel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object PT {
    const val AIM = 0; const val FAN = 1; const val POD = 2; const val SPLIT = 3; const val GATLING = 4; const val RING = 5
    const val LASER = 6; const val BAY = 7; const val FLAMER = 8; const val MORTAR = 9; const val CORE = 10
    val HP = floatArrayOf(260f, 300f, 220f, 500f, 280f, 320f, 450f, 380f, 380f, 340f, 1700f)
}

class BossPart(val type: Int, val ox: Float, val oy: Float, val r: Float, hp: Float, val phase: Int) {
    var hp = hp
    val maxHp = hp
    var alive = true
    var flash = 0f
    var ang = 0f
    var fireT = 1.5f
    var burst = 0
    var t = 0f
    var spin = 0f
}

/**
 * Multi-part boss. Phase 0: turrets armour the core. Phase 1: core exposed. Phase 2: enraged
 * (core below half). The core is itself a part, so missiles, lance and beams treat it uniformly.
 */
class Boss(val type: Int, val w: World) {
    val name = Sectors.ALL[type].bossName
    val ground = type == 0 || type == 1 || type == 2 || type == 4
    val sizeW: Float; val sizeH: Float
    var x = 500f; var y = 0f; var t = 0f
    var phase = 0
    var alive = true
    var dying = false; var dieT = 0f
    var entering = true
    var flash = 0f
    private var targetY = 0f
    val parts = ArrayList<BossPart>()
    lateinit var core: BossPart
    private val mul = Threat.HP[w.threat] * (1f + 0.3f * type)
    private var spiral = 0f
    private var coreT = 2f
    private var rageAnnounced = false

    init {
        when (type) {
            0 -> { // LEVIATHAN: dreadnought, long axis vertical
                sizeW = 340f; sizeH = 720f
                part(PT.AIM, 0f, -250f, 30f); part(PT.AIM, 0f, -140f, 30f)
                part(PT.FAN, 0f, 150f, 32f); part(PT.FAN, 0f, 260f, 32f)
                part(PT.POD, -100f, 40f, 28f); part(PT.POD, 100f, 40f, 28f)
                core(0f, -20f, 50f)
            }
            1 -> { // SANDCRAWLER: mega tank
                sizeW = 600f; sizeH = 440f
                part(PT.SPLIT, 0f, -50f, 46f)
                part(PT.GATLING, -210f, -70f, 28f); part(PT.GATLING, 210f, -70f, 28f)
                part(PT.POD, -150f, 120f, 30f); part(PT.POD, 150f, 120f, 30f)
                core(0f, 95f, 52f)
            }
            2 -> { // FROSTWALL: fortress
                sizeW = 860f; sizeH = 400f
                part(PT.RING, -330f, -120f, 36f); part(PT.RING, 330f, -120f, 36f)
                part(PT.RING, -330f, 120f, 36f); part(PT.RING, 330f, 120f, 36f)
                part(PT.LASER, 0f, 90f, 40f)
                core(0f, -40f, 58f)
            }
            3 -> { // OVERSEER: hover gunship
                sizeW = 640f; sizeH = 320f
                part(PT.GATLING, -240f, 50f, 30f); part(PT.GATLING, 240f, 50f, 30f)
                part(PT.LASER, 0f, 110f, 36f)
                part(PT.BAY, -130f, -70f, 34f); part(PT.BAY, 130f, -70f, 34f)
                core(0f, -5f, 54f)
            }
            4 -> { // CRUCIBLE: walker mech
                sizeW = 560f; sizeH = 480f
                part(PT.FLAMER, -220f, 90f, 36f); part(PT.FLAMER, 220f, 90f, 36f)
                part(PT.MORTAR, -130f, -130f, 34f); part(PT.MORTAR, 130f, -130f, 34f)
                core(0f, -10f, 60f)
            }
            else -> { // HOLLOW KING: mothership
                sizeW = 940f; sizeH = 540f
                part(PT.BAY, -330f, -60f, 40f); part(PT.BAY, 330f, -60f, 40f)
                part(PT.RING, -200f, 150f, 34f); part(PT.RING, 200f, 150f, 34f)
                part(PT.RING, -420f, 110f, 30f); part(PT.RING, 420f, 110f, 30f)
                part(PT.LASER, 0f, 210f, 40f, 1)
                part(PT.GATLING, -110f, 170f, 26f, 1); part(PT.GATLING, 110f, 170f, 26f, 1)
                core(0f, 30f, 70f)
            }
        }
        y = -sizeH * 0.5f - 60f
        targetY = if (ground) w.vh * 0.3f else w.vh * 0.26f
        w.banner(name, 2.4f)
    }

    private fun part(type: Int, ox: Float, oy: Float, r: Float, phase: Int = 0) {
        parts.add(BossPart(type, ox, oy, r, PT.HP[type] * mul, phase).also { it.fireT = 1.2f + parts.size * 0.37f })
    }

    private fun core(ox: Float, oy: Float, r: Float) {
        core = BossPart(PT.CORE, ox, oy, r, PT.HP[PT.CORE] * mul * (if (type == 5) 1.6f else 1f), 1)
        parts.add(core)
    }

    val maxHp: Float get() = parts.sumOf { it.maxHp.toDouble() }.toFloat()
    val hp: Float get() = parts.sumOf { if (it.alive) it.hp.toDouble() else 0.0 }.toFloat()

    fun active(p: BossPart) = p.alive && p.phase <= phase && !entering && !dying
    fun partX(p: BossPart) = x + p.ox
    fun partY(p: BossPart) = y + p.oy

    // ------------------------------------------------------------------ update

    fun update(dt: Float) {
        if (!alive) return
        t += dt
        if (flash > 0f) flash -= dt * 5f
        for (p in parts) { p.t += dt; if (p.flash > 0f) p.flash -= dt * 6f }
        if (dying) { die(dt); return }
        if (entering) {
            y += max(60f, (targetY - y) * 1.6f) * dt
            if (y >= targetY - 2f) { y = targetY; entering = false; t = 0f; w.sfx(Sfx.SIREN, 0.5f, 0.8f) }
            return
        }
        // movement
        when (type) {
            0 -> x = 500f + sin(t * 0.2f) * 70f
            1 -> x = 500f + sin(t * 0.25f) * 110f
            2 -> x = 500f
            3 -> { x = 500f + sin(t * 0.35f) * 150f; y = targetY + sin(t * 0.9f) * 14f }
            4 -> { x = 500f + sin(t * 0.3f) * 140f; y = targetY + abs(sin(t * 1.8f)) * 10f }
            else -> { x = 500f + sin(t * 0.18f) * 30f; y = targetY + sin(t * 0.6f) * 12f }
        }
        // phases
        if (phase == 0 && parts.none { it.alive && it.phase == 0 }) {
            phase = 1
            w.banner("CORE EXPOSED", 1.6f)
            w.sfx(Sfx.SIREN, 0.6f, 1.2f)
        }
        if (phase == 1 && core.hp < core.maxHp * 0.5f) {
            phase = 2
            if (!rageAnnounced) { rageAnnounced = true; w.banner("${name} ENRAGED", 1.6f); w.shake = max(w.shake, 0.6f) }
        }
        val rage = if (phase == 2) 1.35f else 1f
        if (!w.alive) return
        for (p in parts) {
            if (!active(p)) continue
            // turrets track the player
            val want = atan2(w.px - partX(p), w.py - partY(p))
            var da = want - p.ang
            while (da > PI) da -= (2 * PI).toFloat(); while (da < -PI) da += (2 * PI).toFloat()
            p.ang += da.coerceIn(-2f * dt, 2f * dt)
            if (p.type == PT.CORE) continue
            p.fireT -= dt * w.fireMul * rage
            if (p.type == PT.FLAMER) { flamer(p, dt); continue }
            if (p.fireT > 0f) continue
            fire(p)
        }
        if (phase >= 1) coreFire(dt, rage)
        // ramming a flying boss hurts
        if (!ground && w.alive && inside(w.px, w.py, 0.85f)) w.hurt(w.dmgIn * 1.5f)
    }

    private fun fire(p: BossPart) {
        val sp = w.bulletMul
        val bx = partX(p); val by = partY(p)
        when (p.type) {
            PT.AIM -> {
                if (p.burst > 0) { w.aimedAngle(bx, by, p.ang, 360f * sp, World.EB_PINK, 30f); p.burst--; p.fireT = 0.12f }
                else { p.burst = 3; p.fireT = 1.7f }
            }
            PT.FAN -> { w.fanAngle(bx, by, p.ang, 7, 1.2f, 235f * sp, World.EB_ORANGE); p.fireT = 2.3f }
            PT.POD -> {
                val m = w.spawn(EK.EMISSILE, bx, by, Pat.DOWN, 0f, 0f, 0, false)
                m.ang = p.ang
                w.sfx(Sfx.LAUNCH, 0.35f)
                p.fireT = 3.4f
            }
            PT.SPLIT, PT.MORTAR -> {
                w.eb.add(bx, by, sin(p.ang) * 190f * sp, kotlin.math.cos(p.ang) * 190f * sp, 0f, World.EB_SPLIT)
                w.sfx(Sfx.CANNON, 0.5f)
                p.fireT = if (p.type == PT.SPLIT) 2.8f else 2.4f
            }
            PT.GATLING -> {
                if (p.burst > 0) { w.aimedAngle(bx, by, p.ang + (w.rnd.nextFloat() - 0.5f) * 0.08f, 430f * sp, World.EB_PINK, 26f); p.burst--; p.fireT = 0.075f }
                else { p.burst = 10; p.fireT = 2.4f }
            }
            PT.RING -> {
                p.spin += 0.26f
                w.ring(bx, by, 12, 195f * sp, World.EB_ORANGE, p.spin)
                p.fireT = 2.1f
            }
            PT.LASER -> {
                val b = Beam()
                b.follow = p; b.x = bx; b.y = by
                val dir = if (w.px > bx) 1f else -1f
                b.ang = -0.62f * dir; b.sweep = 0.52f * dir
                b.warn = 1.0f; b.on = 2.3f; b.width = 40f
                w.beams.add(b)
                w.sfx(Sfx.LASER, 0.7f, 0.8f)
                p.fireT = 6.2f
            }
            PT.BAY -> {
                val k = if (type == 3) EK.WASP else EK.DART
                val e = if (k == EK.WASP) w.spawn(k, bx, by, Pat.HOVER, 0.45f + w.rnd.nextFloat() * 0.15f, 5f, 0, false)
                else w.spawn(k, bx, by, Pat.DOWN, 240f, 40f, 0, false)
                e.t = 1.01f
                p.fireT = 4.4f
            }
        }
    }

    private fun flamer(p: BossPart, dt: Float) {
        // 1.4 s of fire, then a pause
        if (p.fireT <= 0f) { p.burst = 1; p.fireT = 3.6f; p.spin = 0f; w.sfx(Sfx.FLAME, 0.6f) }
        if (p.burst == 1) {
            p.spin += dt
            if (p.spin > 1.4f) { p.burst = 0; return }
            if (w.rnd.nextFloat() < dt * 22f) {
                val a = p.ang + (w.rnd.nextFloat() - 0.5f) * 0.9f
                val sp = (250f + w.rnd.nextFloat() * 90f) * w.bulletMul
                w.bullet(partX(p), partY(p), sin(a) * sp, kotlin.math.cos(a) * sp, World.EB_ORANGE)
            }
        }
    }

    private fun coreFire(dt: Float, rage: Float) {
        coreT -= dt * w.fireMul * 0.85f
        if (coreT > 0f) return
        val cx = partX(core); val cy = partY(core)
        val sp = w.bulletMul
        when (type) {
            0 -> { spiral += 0.37f; for (k in 0 until 3) w.aimedAngle(cx, cy, spiral + k * 2.094f, 210f * sp, World.EB_PINK, 40f); coreT = 0.17f / rage
                   if (phase == 2 && w.rnd.nextFloat() < 0.08f) w.aimed(cx, cy, 380f * sp, World.EB_NEEDLE, 3, 0.15f) }
            1 -> { spiral += 1f; w.fanAngle(cx, cy, (if (spiral % 2f < 1f) 0.25f else -0.25f), 9, 1.4f, 230f * sp, World.EB_ORANGE); coreT = 1f / rage
                   if (phase == 2) w.ring(cx, cy, 14, 170f * sp, World.EB_PINK, spiral * 0.3f) }
            2 -> { spiral += 0.2f; w.ring(cx, cy, 16, 185f * sp, World.EB_PINK, spiral); coreT = 1.4f / rage
                   if (phase == 2 && w.beams.isEmpty()) { val b = Beam(); b.follow = core; b.ang = -0.7f; b.sweep = 0.6f; b.warn = 1f; b.on = 2.4f; w.beams.add(b); w.sfx(Sfx.LASER, 0.7f, 0.7f) } }
            3 -> { spiral += 0.29f; w.aimedAngle(cx, cy, spiral, 230f * sp, World.EB_PINK, 40f); w.aimedAngle(cx, cy, -spiral + 1.5f, 230f * sp, World.EB_ORANGE, 40f); coreT = 0.15f / rage
                   if (phase == 2 && w.rnd.nextFloat() < 0.1f) w.aimed(cx, cy, 420f * sp, World.EB_NEEDLE, 1, 0f) }
            4 -> { w.aimed(cx, cy, 400f * sp, World.EB_NEEDLE, 3, 0.18f); coreT = 0.65f / rage
                   if (phase == 2) { spiral += 0.5f; w.ring(cx, cy, 12, 180f * sp, World.EB_ORANGE, spiral) } }
            else -> {
                spiral += 0.23f
                val arms = if (phase == 2) 6 else 4
                for (k in 0 until arms) w.aimedAngle(cx, cy, spiral + k * 2f * PI.toFloat() / arms, 200f * sp, if (k % 2 == 0) World.EB_PINK else World.EB_ORANGE, 50f)
                coreT = 0.2f / rage
                if (phase == 2 && w.rnd.nextFloat() < 0.06f) w.aimed(cx, cy, 420f * sp, World.EB_NEEDLE, 5, 0.12f)
            }
        }
    }

    private fun die(dt: Float) {
        dieT += dt
        if (w.rnd.nextFloat() < dt * 9f) {
            w.boom(x + (w.rnd.nextFloat() - 0.5f) * sizeW * 0.8f, y + (w.rnd.nextFloat() - 0.5f) * sizeH * 0.8f, 1.2f + w.rnd.nextFloat(), !ground)
            w.sfx(Sfx.BOOM, 0.6f, 0.8f + w.rnd.nextFloat() * 0.4f)
            w.shake = max(w.shake, 0.4f)
        }
        if (dieT > 2.6f) {
            alive = false
            for (k in 0 until 6) w.boom(x + (w.rnd.nextFloat() - 0.5f) * sizeW * 0.5f, y + (w.rnd.nextFloat() - 0.5f) * sizeH * 0.5f, 2.6f, !ground)
            w.dropCores(x, y, 150)
            w.shake = 1.4f
            w.sfx(Sfx.BOOM_BIG, 1f, 0.7f)
            w.events?.haptic(true)
            // remaining rounds fizzle into loot
            for (i in 0 until w.eb.cap) if (w.eb.alive[i]) { if (i % 3 == 0) w.dropCore(w.eb.x[i], w.eb.y[i], 1); w.eb.kill(i) }
            w.beams.clear()
            for (e in w.enemies) if (e.alive && !e.counted) { e.alive = false; w.boom(e.x, e.y, 0.6f, e.air) }
        }
    }

    // ------------------------------------------------------------------ damage

    private fun inside(px: Float, py: Float, k: Float): Boolean {
        val dx = (px - x) / (sizeW * 0.5f * k); val dy = (py - y) / (sizeH * 0.5f * k)
        return dx * dx + dy * dy < 1f
    }

    fun hitPart(p: BossPart, d: Float) {
        if (!active(p)) return
        p.hp -= d
        p.flash = 1f
        flash = 0.5f
        if (p.hp <= 0f) {
            p.alive = false
            if (p === core) { dying = true; dieT = 0f; w.banner("$name DOWN", 2f) }
            else {
                w.boom(partX(p), partY(p), 1.3f, !ground)
                w.dropCores(partX(p), partY(p), 12)
                w.over = min(100f, w.over + 10f)
                w.sfx(Sfx.BOOM_BIG, 0.8f, 1.1f)
                w.shake = max(w.shake, 0.5f)
            }
        }
    }

    /** Shot at (x, y): true if it struck a live part or the core. Rounds fly over the armoured hull. */
    fun hitTest(px: Float, py: Float, d: Float): Boolean {
        if (dying || entering) return false
        for (p in parts) {
            if (!active(p)) continue
            val dx = px - partX(p); val dy = py - partY(p)
            if (dx * dx + dy * dy < (p.r + 6f) * (p.r + 6f)) { hitPart(p, d); return true }
        }
        return false
    }

    fun beamDamage(px: Float, py: Float, halfW: Float, d: Float) {
        for (p in parts) if (active(p) && abs(partX(p) - px) < halfW + p.r && partY(p) < py) hitPart(p, d)
    }

    fun nearestPart(fx: Float, fy: Float, maxD: Float): BossPart? {
        var best: BossPart? = null; var bd = maxD
        for (p in parts) { if (!active(p)) continue; val d = hypot(partX(p) - fx, partY(p) - fy); if (d < bd) { bd = d; best = p } }
        return best
    }

    fun pickPart(i: Int): BossPart? {
        val list = parts.filter { active(it) }
        return if (list.isEmpty()) null else list[i % list.size]
    }

    fun aimX(): Float {
        val p = parts.firstOrNull { active(it) } ?: return x
        return partX(p)
    }
}
