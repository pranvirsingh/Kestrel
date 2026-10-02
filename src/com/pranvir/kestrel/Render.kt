package com.pranvir.kestrel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws a [World] in world units (the caller scales the canvas to pixels). */
class Render(val sp: SpriteSet, val u3: Units3D) {
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val f = Draw.fill
    private val st = Draw.stroke
    private val DEG = 57.29578f
    private val beamSh = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(0x00B98CFF, 0xCCB98CFF.toInt(), 0xFFFFFFFF.toInt(), 0xCC7FE8FF.toInt(), 0x007FE8FF), floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)

    fun spr(c: Canvas, s: Spr, x: Float, y: Float, rotRad: Float = 0f, scale: Float = 1f, alpha: Int = 255, bmp: Bitmap = s.bmp, sx: Float = 1f) {
        bp.alpha = alpha.coerceIn(0, 255)
        val hw = s.w * 0.5f * scale * sx; val hh = s.h * 0.5f * scale
        if (rotRad != 0f) {
            c.save(); c.translate(x, y); c.rotate(-rotRad * DEG)
            dst.set(-hw, -hh, hw, hh)
            c.drawBitmap(bmp, null, dst, bp)
            c.restore()
        } else {
            dst.set(x - hw, y - hh, x + hw, y + hh)
            c.drawBitmap(bmp, null, dst, bp)
        }
    }

    /** The cast shadow uses the straight-down silhouette, which has its own size. */
    fun shadowOf(c: Canvas, s: Spr, x: Float, y: Float, rot: Float, alt: Float, a: Float, scale: Float = 1f) {
        val sh = s.shadow ?: return
        bp.alpha = (255 * a).toInt().coerceIn(0, 255)
        val hw = s.sw * 0.5f * scale * 0.94f; val hh = s.sh * 0.5f * scale * 0.94f
        val cx = x + alt * 0.32f; val cy = y + alt * 0.48f
        if (rot != 0f) {
            c.save(); c.translate(cx, cy); c.rotate(-rot * DEG)
            dst.set(-hw, -hh, hw, hh); c.drawBitmap(sh, null, dst, bp)
            c.restore()
        } else { dst.set(cx - hw, cy - hh, cx + hw, cy + hh); c.drawBitmap(sh, null, dst, bp) }
    }

    fun groundSprite(k: Int): Spr = when (k) {
        EK.TANK -> u3.tank; EK.FLAK -> u3.flakBase; EK.SAM -> u3.sam; EK.BOAT -> u3.gunboat; EK.TRAIN -> u3.trainCar
        EK.RADAR -> u3.radarBase; else -> u3.bunker
    }
    fun airSprite(k: Int): Spr = when (k) {
        EK.DART -> u3.dart; EK.SWOOP -> u3.swoop; EK.WASP -> u3.wasp; EK.GUNSHIP -> u3.gunship; EK.BOMBER -> u3.bomber
        EK.LANCER -> u3.lancer; EK.MINE -> u3.mine; EK.CARRIER -> u3.carrier; else -> u3.enemyMissile
    }
    fun playerSprite(w: World): Spr {
        val set = if (w.load.has(Perk.LEGEND)) u3.playerGold else if (w.load.lv[Up.MISSILE] > 0) u3.playerPods else u3.player
        val i = (((w.bank + 1f) * 0.5f) * (set.size - 1)).roundToInt().coerceIn(0, set.size - 1)
        return set[i]
    }

    // ------------------------------------------------------------------ frame

    fun draw(c: Canvas, w: World, terrain: Terrain?, art: BossArt?, t: Float) {
        val vh = w.vh
        val sh = w.shake * w.shake
        val ox = sh * 16f * sin(t * 57f); val oy = sh * 16f * cos(t * 45f)
        c.save()
        c.translate(ox, oy)
        ground(c, w, terrain)
        cloudShadows(c, w, t)
        for (d in w.decals) spr(c, sp.scorch, d[0], vh - (d[1] - w.scroll), d[3] / DEG, d[2] / 90f, 200)
        survivors(c, w, t)
        for (e in w.enemies) if (!e.air) groundUnit(c, w, e, t)
        val b = w.boss
        if (b != null && art != null && b.ground) bossDraw(c, w, b, art, t)
        // air shadows on the ground
        val sa = w.biome.shadowAlpha
        for (e in w.enemies) if (e.air && e.kind != EK.EMISSILE) shadowOf(c, airSprite(e.kind), e.x, e.y, e.ang, World.altitude(e.kind), sa)
        if (b != null && art != null && !b.ground && b.alive) shadowOf(c, art.hull, b.x, b.y, 0f, 220f, sa * 0.8f)
        if (w.alive) shadowOf(c, playerSprite(w), w.px, w.py, 0f, 120f, sa)
        clouds(c, w, t, false)
        for (e in w.enemies) if (e.air) airUnit(c, w, e, t)
        if (b != null && art != null && !b.ground) bossDraw(c, w, b, art, t)
        pickups(c, w, t)
        playerShots(c, w)
        for (m in w.missiles) spr(c, u3.missile, m.x, m.y, atan2(m.vx, m.vy) + PI.toFloat())
        player(c, w, t)
        bolts(c, w)
        particles(c, w)
        beams(c, w, t)
        enemyBullets(c, w, t)
        clouds(c, w, t, true)
        popups(c, w)
        c.restore()
        grade(c, w)
    }

    // per-biome colour grade and lens vignette, in world units
    private val gradeSh = arrayOfNulls<LinearGradient>(6)
    private var vignSh: android.graphics.RadialGradient? = null
    private val GRADE = arrayOf(
        intArrayOf(0x22FFF2C8, 0x00000000, 0x1A00284A),
        intArrayOf(0x30FFB060, 0x00000000, 0x22401000),
        intArrayOf(0x22E8F6FF, 0x00000000, 0x22102A60),
        intArrayOf(0x30401870, 0x00000000, 0x30200040),
        intArrayOf(0x30FF5010, 0x00000000, 0x30300800),
        intArrayOf(0x30FFD0E8, 0x00000000, 0x22302060))
    private fun grade(c: Canvas, w: World) {
        val i = w.sector.coerceIn(0, 5)
        val g = gradeSh[i] ?: LinearGradient(0f, 0f, 0f, 1f, GRADE[i], floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP).also { gradeSh[i] = it }
        Draw.shadeRect(c, g, 0f, 0f, World.W, w.vh, 0f, 0f, 1f, w.vh)
        val v = vignSh ?: android.graphics.RadialGradient(0f, 0f, 1f, intArrayOf(0x00000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP).also { vignSh = it }
        c.save(); c.translate(World.W / 2f, w.vh / 2f); c.scale(World.W * 0.75f, w.vh * 0.62f)
        Draw.grad.shader = v; c.drawRect(-1.5f, -1.5f, 1.5f, 1.5f, Draw.grad); Draw.grad.shader = null
        c.restore()
    }

    private fun ground(c: Canvas, w: World, terrain: Terrain?) {
        val vh = w.vh
        val k0 = floor(w.scroll / Biome.CH).toInt()
        val k1 = floor((w.scroll + vh) / Biome.CH).toInt()
        for (k in k0..k1) {
            val top = vh - ((k + 1) * Biome.CH - w.scroll)
            val bmp = terrain?.get(k)
            if (bmp != null) {
                bp.alpha = 255
                dst.set(-1f, top - 0.5f, World.W + 1f, top + Biome.CH + 0.5f)
                c.drawBitmap(bmp, null, dst, bp)
            } else {
                f.color = w.biome.base
                c.drawRect(-20f, top, World.W + 20f, top + Biome.CH, f)
            }
        }
    }

    // ------------------------------------------------------------------ clouds

    private fun cloudSlot(n: Int, layer: Int, w: World): Boolean = hash01(n, 71 + layer) < w.biome.cloudAmount * (if (layer == 0) 0.55f else 0.35f)

    private fun cloudShadows(c: Canvas, w: World, t: Float) {
        val track = w.scroll * 1.35f + t * 6f
        val gap = 620f
        val n0 = floor((track - 400f) / gap).toInt()
        val n1 = floor((track + w.vh + 400f) / gap).toInt()
        for (n in n0..n1) {
            if (!cloudSlot(n, 0, w)) continue
            val cy = w.vh - (n * gap - track)
            val cx = hash01(n, 3) * 1100f - 50f
            val s = sp.clouds[(n and 0x7FFFFFFF) % 4]
            val sc = 0.9f + hash01(n, 5) * 0.8f
            spr(c, s, cx + 60f, cy + 110f, 0f, sc, (90 * w.biome.shadowAlpha / 0.3f).toInt(), s.shadow ?: s.bmp)
        }
    }

    private fun clouds(c: Canvas, w: World, t: Float, high: Boolean) {
        val layer = if (high) 1 else 0
        val track = w.scroll * (if (high) 2.3f else 1.35f) + t * (if (high) 14f else 6f)
        val gap = if (high) 1150f else 620f
        val n0 = floor((track - 400f) / gap).toInt()
        val n1 = floor((track + w.vh + 400f) / gap).toInt()
        val tint = w.biome.cloudTint
        for (n in n0..n1) {
            if (!cloudSlot(n, layer, w)) continue
            val cy = w.vh - (n * gap - track)
            val cx = hash01(n, 3 + layer * 11) * 1100f - 50f
            val s = sp.clouds[((n + layer) and 0x7FFFFFFF) % 4]
            val sc = (0.9f + hash01(n, 5) * 0.8f) * (if (high) 1.6f else 1f)
            val a = if (high) 0.22f else 0.55f + 0.25f * hash01(n, 9)
            // night and ash skies keep their clouds dim
            val dim = if (tint == 0xFFFFFFFF.toInt()) 1f else 0.55f
            spr(c, s, cx, cy, 0f, sc, (255 * a * dim * min(1f, w.biome.cloudAmount)).toInt())
        }
    }

    // ------------------------------------------------------------------ units

    private fun survivors(c: Canvas, w: World, t: Float) {
        for (s in w.survivors) {
            val y = w.vh - (s.gy - w.scroll)
            if (y < -80f || y > w.vh + 80f) continue
            if (s.state == 1) {
                if (s.beam > 0f) {
                    f.color = alphaF(0x887FE8FF.toInt(), s.beam)
                    c.drawRect(s.x - 26f * s.beam, -10f, s.x + 26f * s.beam, y, f)
                    Draw.glow(c, s.x, y, 90f, s.beam, 0xFF7FE8FF.toInt())
                }
                continue
            }
            if (s.state == 2) { spr(c, sp.survivor, s.x, y, 0f, 1f, 120); continue }
            val pulse = 0.5f + 0.5f * sin(t * 5f)
            Draw.glow(c, s.x, y, 70f + pulse * 20f, 0.35f, 0xFF5DFF9A.toInt())
            spr(c, sp.survivor, s.x, y)
            // pickup radius and progress
            st.color = alphaF(0xFF5DFF9A.toInt(), 0.5f + 0.3f * pulse); st.strokeWidth = 2.5f
            val rr = 125f
            var a = 0f
            while (a < 360f) { Draw.r1.set(s.x - rr, y - rr, s.x + rr, y + rr); c.drawArc(Draw.r1, a + t * 20f, 14f, false, st); a += 30f }
            if (s.prog > 0f) {
                st.color = Col.GREEN; st.strokeWidth = 6f
                Draw.r1.set(s.x - 46f, y - 46f, s.x + 46f, y + 46f)
                c.drawArc(Draw.r1, -90f, 360f * clamp01(s.prog), false, st)
            }
        }
    }

    private fun groundUnit(c: Canvas, w: World, e: Enemy, t: Float) {
        if (e.y < -140f || e.y > w.vh + 140f) return
        val s = groundSprite(e.kind)
        val rot = if (e.kind == EK.TRAIN) e.ang else if (e.kind == EK.BOAT) e.ang else 0f
        // wake for boats
        if (e.kind == EK.BOAT) {
            f.color = 0x55FFFFFF
            for (k in 0 until 4) c.drawCircle(e.x + sin(t * 3f + k) * 3f, e.y - 70f - k * 16f, 12f - k * 2f, f)
        }
        Draw.glow(c, e.x, e.y, e.r * 1.9f, 0.22f, Col.DANGER)
        shadowOf(c, s, e.x, e.y, rot, 22f, 0.45f)
        spr(c, s, e.x, e.y, rot)
        if (e.flash > 0f) s.flash?.let { spr(c, s, e.x, e.y, rot, 1f, (200 * e.flash).toInt(), it) }
        when (e.kind) {
            EK.TANK -> spr(c, u3.tankTurret.pick(e.tur), e.x, e.y)
            EK.FLAK -> spr(c, u3.flakGun.pick(e.tur), e.x, e.y)
            EK.BOAT -> spr(c, u3.smallTurret.pick(e.tur), e.x, e.y + 18f)
            EK.TRAIN -> spr(c, u3.smallTurret.pick(e.tur), e.x, e.y - 6f)
            EK.RADAR -> { spr(c, u3.radarDish.pick(e.tur), e.x, e.y); Draw.glow(c, e.x, e.y - 20f, 40f, 0.4f + 0.3f * sin(t * 4f), 0xFFFF3BD0.toInt()) }
        }
        if (e.maxHp > 60f && e.hp < e.maxHp) hpBar(c, e.x, e.y + e.r + 12f, e.r * 1.4f, e.hp / e.maxHp)
    }

    private fun airUnit(c: Canvas, w: World, e: Enemy, t: Float) {
        if (e.y < -200f || e.y > w.vh + 200f) return
        val s = airSprite(e.kind)
        val rot = when (e.kind) { EK.WASP, EK.MINE -> e.ang; EK.EMISSILE -> e.ang; else -> e.ang }
        // engine glow
        if (e.kind != EK.MINE && e.kind != EK.WASP) {
            val gx = e.x - sin(rot) * s.h * 0.45f; val gy = e.y - cos(rot) * s.h * 0.45f
            Draw.glow(c, gx, gy, if (EK.BIG[e.kind]) 60f else 26f, 0.7f + 0.3f * sin(t * 30f + e.bx), 0xFFFF8A3D.toInt())
        }
        spr(c, s, e.x, e.y, rot)
        if (e.kind == EK.MINE) Draw.glow(c, e.x, e.y, 22f, 0.5f + 0.5f * sin(t * 9f + e.bx), Col.DANGER)
        if (e.kind == EK.LANCER && e.burst == 1) {
            // charging: targeting line and a growing glow at the prongs
            val k = clamp01(-e.fireT / 0.9f)
            st.color = alphaF(Col.BULLET, 0.25f + 0.5f * k * (0.5f + 0.5f * sin(t * 40f))); st.strokeWidth = 2f + 3f * k
            c.drawLine(e.x, e.y + 40f, e.lockX, e.lockY, st)
            Draw.glow(c, e.x, e.y + 40f, 20f + 30f * k, 0.9f, Col.BULLET)
        }
        if (e.flash > 0f) s.flash?.let { spr(c, s, e.x, e.y, rot, 1f, (210 * e.flash).toInt(), it) }
        if (EK.BIG[e.kind] && e.hp < e.maxHp) hpBar(c, e.x, e.y - e.r - 14f, e.r * 1.2f, e.hp / e.maxHp)
    }

    private fun hpBar(c: Canvas, x: Float, y: Float, w: Float, f0: Float) {
        f.color = 0xAA000000.toInt(); c.drawRect(x - w / 2f - 2f, y - 3.5f, x + w / 2f + 2f, y + 3.5f, f)
        f.color = lerpColor(Col.DANGER, Col.AMBER, f0); c.drawRect(x - w / 2f, y - 2f, x - w / 2f + w * clamp01(f0), y + 2f, f)
    }

    private fun bossDraw(c: Canvas, w: World, b: Boss, art: BossArt, t: Float) {
        if (!b.alive) return
        if (b.type == 0) {
            // bow wave around the dreadnought
            f.color = 0x66FFFFFF
            for (k in 0 until 10) c.drawCircle(b.x + sin(t * 2f + k) * 6f + (if (k % 2 == 0) -1f else 1f) * (60f + k * 10f), b.y + 300f - k * 40f, 26f - k, f)
        }
        if (b.ground) shadowOf(c, art.hull, b.x, b.y, 0f, 40f, 0.4f)
        val shake = if (b.dying) (b.dieT * 6f) else 0f
        val bx = b.x + sin(t * 70f) * shake; val by = b.y + cos(t * 63f) * shake
        spr(c, art.hull, bx, by)
        if (b.flash > 0f) { f.color = alphaF(0x22FFFFFF, b.flash) }
        for (p in b.parts) {
            val x = bx + p.ox; val y = by + p.oy
            if (!p.alive) {
                spr(c, sp.scorch, x, y, p.ox * 0.01f, p.r / 40f, 220)
                if ((t * 10f + p.ox).toInt() % 3 == 0) Draw.glow(c, x, y, p.r, 0.5f, 0xFFFF6A1E.toInt())
                continue
            }
            val active = b.active(p)
            when (p.type) {
                PT.CORE -> {
                    val open = b.phase >= 1
                    val s = if (open) art.coreOpen else art.coreShut
                    if (open) Draw.glow(c, x, y, p.r * 2.6f, 0.6f + 0.3f * sin(t * 6f), if (b.phase == 2) 0xFFFF3D8B.toInt() else 0xFFB04DFF.toInt())
                    spr(c, s, x, y, if (open) t * 0.6f else 0f, p.r / 52f)
                    if (p.flash > 0f) art.coreOpen.shadow?.let { Draw.glow(c, x, y, p.r * 1.4f, p.flash, 0xFFFFFFFF.toInt()) }
                }
                PT.AIM -> { spr(c, u3.flakBase, x, y, 0f, p.r / 30f); spr(c, u3.flakGun.pick(p.ang), x, y, 0f, p.r / 26f) }
                PT.FAN -> { spr(c, u3.flakBase, x, y, 0f, p.r / 30f); spr(c, u3.flakGun.pick(p.ang), x, y, 0f, p.r / 24f) }
                PT.POD -> spr(c, u3.sam, x, y, 0f, p.r / 24f)
                PT.SPLIT -> spr(c, art.bigCannon.pick(p.ang), x, y)
                PT.GATLING -> spr(c, art.gatling.pick(p.ang), x, y)
                PT.FLAMER -> spr(c, art.flamer.pick(p.ang), x, y)
                PT.RING -> spr(c, art.ringCannon, x, y, p.spin * 0.3f)
                PT.LASER -> spr(c, art.laser, x, y)
                PT.BAY -> spr(c, art.bay, x, y)
                PT.MORTAR -> spr(c, art.mortar, x, y)
            }
            if (p.flash > 0f && p.type != PT.CORE) Draw.glow(c, x, y, p.r * 1.3f, p.flash * 0.8f, 0xFFFFFFFF.toInt())
            if (active && p.type != PT.CORE && p.hp < p.maxHp) hpBar(c, x, y + p.r + 10f, p.r * 1.6f, p.hp / p.maxHp)
            if (p.type == PT.FLAMER && p.burst == 1) Draw.glow(c, x + sin(p.ang) * 50f, y + kotlin.math.cos(p.ang) * 50f, 60f, 0.8f, 0xFFFF8A2A.toInt())
        }
        if (b.entering) {
            // ominous scan lines while it arrives
            st.color = 0x55FF4D5E; st.strokeWidth = 2f
            c.drawLine(0f, by + b.sizeH * 0.5f, World.W, by + b.sizeH * 0.5f, st)
        }
    }

    // ------------------------------------------------------------------ player & projectiles

    private fun player(c: Canvas, w: World, t: Float) {
        for (i in 0 until 2) {
            val dl = w.load.lv[Up.DRONE]
            if (dl == 0 || (dl == 1 && i == 1) || !w.alive) continue
            Draw.glow(c, w.droneX[i], w.droneY[i], 26f, 0.5f, Col.CYAN)
            spr(c, u3.drone, w.droneX[i], w.droneY[i])
        }
        if (!w.alive) return
        if (w.inv > 0f && w.shieldT <= 0f && ((w.inv * 14f).toInt() % 2 == 0)) return
        val s = playerSprite(w)
        val legend = w.load.has(Perk.LEGEND)
        // afterburners
        val flick = 0.8f + 0.2f * sin(t * 50f)
        val ec = if (legend) 0xFFFFD45C.toInt() else 0xFF7FE8FF.toInt()
        Draw.glow(c, w.px - 4.5f, w.py + 54f, 26f * flick, 0.9f, ec)
        Draw.glow(c, w.px + 4.5f, w.py + 54f, 26f * flick, 0.9f, ec)
        spr(c, s, w.px, w.py)
        if (w.hitFlash > 0f) s.flash?.let { spr(c, s, w.px, w.py, 0f, 1f, (220 * w.hitFlash).toInt(), it) }
        if (w.shieldT > 0f) {
            val a = if (w.shieldT < 2f) 0.5f + 0.5f * sin(t * 20f) else 1f
            Draw.glow(c, w.px, w.py, 78f, 0.35f * a, 0xFF7FB8FF.toInt())
            st.color = alphaF(0xFFAFD3FF.toInt(), 0.7f * a); st.strokeWidth = 2.5f
            c.drawCircle(w.px, w.py, 62f, st)
        }
        if (w.overT > 0f) overdrive(c, w, t)
        // tiny hitbox reminder
        f.color = 0xFFFFFFFF.toInt(); c.drawCircle(w.px, w.py, 3.5f, f)
        st.color = Col.CYAN; st.strokeWidth = 1.5f; c.drawCircle(w.px, w.py, 6f, st)
    }

    private fun overdrive(c: Canvas, w: World, t: Float) {
        val hw = 52f + 8f * sin(t * 30f)
        c.save()
        c.translate(w.px - hw, 0f); c.scale(hw * 2f, 1f)
        Draw.grad.shader = beamSh
        c.drawRect(0f, -20f, 1f, w.py - 40f, Draw.grad)
        Draw.grad.shader = null
        c.restore()
        Draw.glow(c, w.px, w.py - 50f, 110f, 0.9f, 0xFFE2CCFF.toInt())
        for (k in 0 until 6) {
            val y = ((t * 1400f + k * 260f) % (w.py))
            f.color = 0xAAFFFFFF.toInt(); c.drawRect(w.px - hw * 0.3f, w.py - 40f - y, w.px + hw * 0.3f, w.py - 40f - y + 26f, f)
        }
    }

    private fun playerShots(c: Canvas, w: World) {
        val s = w.shots
        for (i in 0 until s.cap) {
            if (!s.alive[i]) continue
            val spr = when (s.kind[i]) { World.S_HEAVY -> sp.boltHeavy; World.S_DRONE -> sp.droneShot; else -> sp.bolt }
            val rot = if (s.vx[i] == 0f) PI.toFloat() else atan2(s.vx[i], s.vy[i])
            spr(c, spr, s.x[i], s.y[i], rot, if (s.kind[i] == World.S_DRONE) 1f else 1.25f)
        }
    }

    private fun enemyBullets(c: Canvas, w: World, t: Float) {
        val b = w.eb
        for (i in 0 until b.cap) {
            if (!b.alive[i]) continue
            when (b.kind[i]) {
                World.EB_PINK -> spr(c, sp.bulletPink, b.x[i], b.y[i], 0f, 0.92f)
                World.EB_ORANGE -> spr(c, sp.bulletOrange, b.x[i], b.y[i], 0f, 0.92f)
                World.EB_NEEDLE -> spr(c, sp.needle, b.x[i], b.y[i], atan2(b.vx[i], b.vy[i]))
                else -> spr(c, sp.bulletBig, b.x[i], b.y[i], 0f, 0.9f + 0.1f * sin(t * 20f + i))
            }
        }
    }

    private fun pickups(c: Canvas, w: World, t: Float) {
        val cs = w.cores
        for (i in 0 until cs.cap) {
            if (!cs.alive[i]) continue
            if (w.coreLife[i] < 2f && ((w.coreLife[i] * 10f).toInt() % 2 == 0)) continue
            val big = cs.dmg[i] >= 5f
            spr(c, if (big) sp.coreBig else sp.coreSmall, cs.x[i], cs.y[i], t * 2f + i, 1f)
        }
        for (cap in w.capsules) {
            val s = when (cap.type) { World.CAP_REPAIR -> sp.capRepair; World.CAP_SHIELD -> sp.capShield; World.CAP_SURGE -> sp.capSurge; else -> sp.capOver }
            spr(c, s, cap.x, cap.y, 0f, 1f + 0.08f * sin(t * 6f))
        }
    }

    private fun bolts(c: Canvas, w: World) {
        for (b in w.bolts) {
            val a = clamp01(b.life / 0.22f)
            for (pass in 0..1) {
                st.color = if (pass == 0) alphaF(0x887FB8FF.toInt(), a) else alphaF(0xFFFFFFFF.toInt(), a)
                st.strokeWidth = if (pass == 0) 9f else 2.6f
                for (k in 0 until b.n - 1) c.drawLine(b.pts[k * 2], b.pts[k * 2 + 1], b.pts[k * 2 + 2], b.pts[k * 2 + 3], st)
            }
        }
    }

    private fun beams(c: Canvas, w: World, t: Float) {
        for (b in w.beams) {
            val ex = b.x + sin(b.ang) * b.len; val ey = b.y + cos(b.ang) * b.len
            if (!b.live()) {
                // warning: thin flicker along where it will sweep from
                val k = clamp01(b.t / b.warn)
                st.color = alphaF(Col.DANGER, (0.3f + 0.5f * k) * (if (sin(t * 40f) > 0f) 1f else 0.4f)); st.strokeWidth = 2f + 4f * k
                c.drawLine(b.x, b.y, ex, ey, st)
                Draw.glow(c, b.x, b.y, 30f + 40f * k, 0.8f, 0xFF9FE8FF.toInt())
            } else {
                st.color = 0x667FD8FF; st.strokeWidth = b.width * 1.8f; c.drawLine(b.x, b.y, ex, ey, st)
                st.color = 0xFF9FE8FF.toInt(); st.strokeWidth = b.width * 0.8f; c.drawLine(b.x, b.y, ex, ey, st)
                st.color = 0xFFFFFFFF.toInt(); st.strokeWidth = b.width * 0.3f; c.drawLine(b.x, b.y, ex, ey, st)
                Draw.glow(c, b.x, b.y, 80f, 1f, 0xFFBFF0FF.toInt())
            }
        }
    }

    private fun particles(c: Canvas, w: World) {
        val p = w.parts
        for (i in 0 until p.cap) {
            if (p.life[i] <= 0f || p.delay[i] > 0f) continue
            val a = clamp01(p.life[i] / p.max[i])
            when (p.kind[i]) {
                PartPool.FIRE -> spr(c, sp.fireball, p.x[i], p.y[i], p.rot[i], p.size[i] / 32f, (255 * Math.pow(a.toDouble(), 0.6).toFloat()).toInt())
                PartPool.SMOKE -> spr(c, sp.smoke, p.x[i], p.y[i], p.rot[i], p.size[i] / 32f, (150 * a).toInt())
                PartPool.SPARK -> { st.color = alphaF(p.col[i], a); st.strokeWidth = p.size[i]; c.drawLine(p.x[i], p.y[i], p.x[i] - p.vx[i] * 0.035f, p.y[i] - p.vy[i] * 0.035f, st) }
                PartPool.RING -> { st.color = alphaF(p.col[i], a * 0.9f); st.strokeWidth = 7f * a + 1f; c.drawCircle(p.x[i], p.y[i], p.size[i] * (1f - a * 0.85f), st) }
                PartPool.DEBRIS -> {
                    c.save(); c.translate(p.x[i], p.y[i]); c.rotate(p.rot[i] * DEG)
                    f.color = alphaF(p.col[i], min(1f, a * 2f)); val s = p.size[i]; c.drawRect(-s, -s * 0.5f, s, s * 0.5f, f)
                    c.restore()
                }
                PartPool.FLASH -> spr(c, sp.flare, p.x[i], p.y[i], 0f, p.size[i] / 32f * (0.6f + 0.4f * (1f - a)), (255 * a).toInt())
                PartPool.TRAIL -> { f.color = alphaF(p.col[i], a * 0.5f); c.drawCircle(p.x[i], p.y[i], p.size[i] * a, f) }
                else -> { f.color = alphaF(p.col[i], a); c.drawCircle(p.x[i], p.y[i], p.size[i], f) }
            }
        }
    }

    private fun popups(c: Canvas, w: World) {
        for (p in w.popups) {
            if (!p.alive) continue
            val sc = if (p.t < 0.15f) easeOutBack(p.t / 0.15f) else 1f
            val a = if (p.t > 0.85f) 1f - (p.t - 0.85f) / 0.35f else 1f
            Draw.text(c, p.text, p.x + 2f, p.y + 3f, 40f * p.size * sc, alphaF(0xAA000000.toInt(), a), Fonts.bold, Paint.Align.CENTER, 0.08f)
            Draw.text(c, p.text, p.x, p.y, 40f * p.size * sc, alphaF(p.col, a), Fonts.bold, Paint.Align.CENTER, 0.08f)
        }
    }
}
