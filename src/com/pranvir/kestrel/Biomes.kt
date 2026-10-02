package com.pranvir.kestrel

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Ground {
    const val WATER = 0; const val LAND = 1; const val ROAD = 2; const val RAIL = 3; const val ROOF = 4; const val LAVA = 5; const val GAP = 6
}

/**
 * A biome paints the ground: a height field lit by a sun (hill-shading), coloured per pixel,
 * then vector details (trees, houses, rails, city blocks) drawn on top. Everything is a pure
 * function of position so chunks join seamlessly and unit placement can query the same ground.
 */
abstract class Biome(val seed: Int) {
    /** Ground-y where the boss arena begins (biomes may clear space there). */
    var arena = Float.MAX_VALUE
    abstract fun height(x: Float, gy: Float): Float
    abstract fun color(x: Float, gy: Float, h: Float, light: Float, slope: Float, n: Float): Int
    abstract fun kind(x: Float, gy: Float): Int
    open val relief = 7f
    open val lightX = -0.55f
    open val lightY = -0.6f
    open val lightZ = 0.58f
    open val base = 0xFF404040.toInt()
    open val shadowAlpha = 0.32f
    /** Colour of the haze laid over distant parallax clouds. */
    open val cloudTint = 0xFFFFFFFF.toInt()
    open val cloudAmount = 0.8f
    open fun overlay(c: Canvas, k: Int, top: Float) {}

    /** Height above the base plane in world units (0 for water and flat cities). */
    open val zBase = 0.5f
    open val zScale = 0f
    /** Gritty mid-scale texture on raised ground. */
    open val grit = true
    /** Colour of a cliff face seen from the south, derived from the ground colour above it. */
    open fun face(top: Int): Int = lerpColor(scaleRgb(top, 0.62f), 0xFF4A4036.toInt(), 0.35f)
    fun z(h: Float) = if (zScale == 0f) 0f else max(0f, h - zBase) * zScale
    /** How far a ground point appears pushed up the screen by the tilted camera. */
    fun lift(x: Float, gy: Float) = liftOf(z(height(x, gy)))
    /** Chunk-local y of a ground point, including its lift. */
    protected fun ly(top: Float, x: Float, gy: Float) = top - gy - lift(x, gy)

    protected val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    protected val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    protected val path = Path()
    protected val oval = android.graphics.RectF()
    // private paint + cache: chunks are painted on a worker thread, never share Draw's objects
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowCache = HashMap<Int, android.graphics.RadialGradient>()
    protected fun glow(c: Canvas, x: Float, y: Float, r: Float, strength: Float, col: Int) {
        var sh = glowCache[col]
        if (sh == null) {
            sh = android.graphics.RadialGradient(0f, 0f, 1f, intArrayOf(withAlpha(col, 210), withAlpha(col, 80), withAlpha(col, 20), withAlpha(col, 0)),
                floatArrayOf(0f, 0.22f, 0.6f, 1f), android.graphics.Shader.TileMode.CLAMP)
            glowCache[col] = sh
        }
        glowP.shader = sh
        glowP.alpha = (255 * clamp01(strength)).toInt()
        c.save(); c.translate(x, y); c.scale(r, r)
        c.drawCircle(0f, 0f, 1f, glowP)
        c.restore()
    }

    protected fun rngFor(k: Int, salt: Int) = Random(seed * 7919L + k * 104729L + salt)

    /** Iterate objects of this chunk and its neighbours so anything straddling a seam is drawn whole. */
    protected inline fun neighbours(k: Int, salt: Int, block: (kk: Int, rnd: Random) -> Unit) {
        for (kk in k - 1..k + 1) block(kk, rngFor(kk, salt))
    }

    protected fun shadowBlob(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Int) {
        f.color = withAlpha(0xFF000000.toInt(), a)
        oval.set(x - rx, y - ry, x + rx, y + ry)
        c.drawOval(oval, f)
    }

    companion object {
        const val CH = 1000f
        val TAN = kotlin.math.tan(R3.pitch)
        /** Biggest lift any biome produces (sizes the margin terrain chunks render below themselves). */
        const val MAX_LIFT = 190f
        /** Soft-saturating screen lift for a height: ~z·tan for low ground, never above MAX_LIFT. */
        fun liftOf(z: Float): Float { val v = z * TAN; return if (v <= 0f) 0f else MAX_LIFT * (1f - kotlin.math.exp(-v / MAX_LIFT)) }
        fun make(i: Int, arena: Float = Float.MAX_VALUE): Biome = (when (i) {
            0 -> Coast(11); 1 -> Canyon(23); 2 -> Glacier(37); 3 -> Sprawl(41); 4 -> Forge(53); else -> Stratos(67)
        }).also { it.arena = arena }
    }
}

// ---------------------------------------------------------------------- 1 · Coral Coast

class Coast(seed: Int) : Biome(seed) {
    private val SL = 0.5f
    override val base = 0xFF1C6E95.toInt()
    override val shadowAlpha = 0.3f
    override val zBase = 0.5f
    override val zScale = 1300f
    override fun height(x: Float, gy: Float): Float {
        val n = Noise.fbm(x * 0.0019f, gy * 0.0019f, 5, seed)
        val e = (x - 500f) / 500f
        val big = (Noise.value(gy * 0.00032f, 3.3f, seed + 9) - 0.5f) * 0.3f
        var h = n * 0.95f + e * e * 0.24f + big - 0.06f
        if (gy > arena - 900f) {
            val k = smooth((gy - (arena - 900f)) / 900f) * (1f - smooth((abs(x - 500f) - 330f) / 140f))
            h = lerp(h, min(h, 0.36f + n * 0.1f), k)
        }
        return h
    }
    override fun kind(x: Float, gy: Float): Int {
        val h = height(x, gy)
        return if (h < SL - 0.025f) Ground.WATER else if (h > SL + 0.03f) Ground.LAND else -1
    }
    override fun color(x: Float, gy: Float, h: Float, light: Float, slope: Float, n: Float): Int {
        if (h < SL) {
            val d = clamp01((SL - h) / 0.22f)
            var c = lerpColor(0xFF3FD0D2.toInt(), 0xFF0B3C72.toInt(), Math.pow(d.toDouble(), 0.55).toFloat())
            // sunlit ripples in the shallows
            val warp = Noise.value(x * 0.01f, gy * 0.01f, seed + 3) * 9f
            val rip = sin(x * 0.07f + warp) * sin(gy * 0.05f - warp * 0.7f)
            c = lerpColor(c, 0xFFBFF6F2.toInt(), (1f - d) * 0.28f * smooth((rip - 0.3f) / 0.6f))
            val foam = SL - h
            if (foam < 0.012f) c = lerpColor(c, 0xFFF4FBFA.toInt(), (1f - foam / 0.012f) * 0.75f)
            return scaleRgb(c, 0.97f + n * 0.06f)
        }
        val t = h - SL
        var c = when {
            t < 0.022f -> 0xFFEED9A3.toInt()
            t < 0.2f -> lerpColor(0xFF7BB54C.toInt(), 0xFF2E6B2B.toInt(), (t - 0.022f) / 0.18f)
            else -> lerpColor(0xFF2E6B2B.toInt(), 0xFF8A8577.toInt(), clamp01((t - 0.2f) / 0.08f))
        }
        if (t > 0.022f && t < 0.035f) c = lerpColor(0xFFEED9A3.toInt(), c, (t - 0.022f) / 0.013f)
        val patch = Noise.value(x * 0.012f, gy * 0.012f, seed + 5)
        c = lerpColor(c, 0xFF9AA64A.toInt(), if (t > 0.03f) smooth((patch - 0.6f) / 0.3f) * 0.4f else 0f)
        return scaleRgb(c, 0.45f + light * 0.75f + n * 0.06f)
    }
    override fun overlay(c: Canvas, k: Int, top: Float) {
        neighbours(k, 1) { kk, rnd ->
            // palm groves
            for (i in 0 until 60) {
                val x = rnd.nextFloat() * 1000f; val gy = kk * CH + rnd.nextFloat() * CH
                val h = height(x, gy) - SL
                if (h < 0.03f || h > 0.17f) continue
                val n = 3 + rnd.nextInt(6)
                for (j in 0 until n) {
                    val px = x + (rnd.nextFloat() - 0.5f) * 70f; val pg = gy + (rnd.nextFloat() - 0.5f) * 70f
                    if (height(px, pg) - SL < 0.028f) continue
                    palm(c, px, ly(top, px, pg), 9f + rnd.nextFloat() * 6f, rnd.nextFloat() * 6.28f)
                }
            }
            // fishing villages on gentle land near the water
            for (i in 0 until 4) {
                val x = 120f + rnd.nextFloat() * 760f; val gy = kk * CH + rnd.nextFloat() * CH
                val h = height(x, gy) - SL
                if (h < 0.03f || h > 0.09f) continue
                for (j in 0 until 4 + rnd.nextInt(5)) {
                    val hx = x + (rnd.nextFloat() - 0.5f) * 110f; val hg = gy + (rnd.nextFloat() - 0.5f) * 110f
                    if (height(hx, hg) - SL < 0.03f) continue
                    house(c, hx, ly(top, hx, hg), 14f + rnd.nextFloat() * 10f, 11f + rnd.nextFloat() * 8f, rnd.nextFloat() < 0.5f,
                        if (rnd.nextFloat() < 0.6f) 0xFFC8553D.toInt() else 0xFF3F7FB0.toInt())
                }
            }
        }
    }
    private fun palm(c: Canvas, x: Float, y: Float, r: Float, rot: Float) {
        shadowBlob(c, x + r * 0.7f, y + r * 0.8f, r * 1.1f, r * 0.8f, 70)
        f.color = 0xFF2C5E22.toInt()
        for (k in 0 until 6) {
            val a = rot + k * 1.047f
            c.drawCircle(x + cos(a) * r * 0.55f, y + sin(a) * r * 0.55f, r * 0.55f, f)
        }
        f.color = 0xFF4E9A35.toInt()
        for (k in 0 until 6) {
            val a = rot + k * 1.047f
            c.drawCircle(x + cos(a) * r * 0.45f - r * 0.12f, y + sin(a) * r * 0.45f - r * 0.12f, r * 0.33f, f)
        }
        f.color = 0xFF8FCB5A.toInt(); c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.22f, f)
    }
    fun house(c: Canvas, x: Float, y: Float, w: Float, h: Float, rot: Boolean, roof: Int) {
        val hw = if (rot) h else w; val hh = if (rot) w else h
        f.color = 0x55000000; c.drawRect(x - hw / 2 + 4f, y - hh / 2 + 5f, x + hw / 2 + 5f, y + hh / 2 + 6f, f)
        f.color = 0xFFF1EBDD.toInt(); c.drawRect(x - hw / 2, y - hh / 2, x + hw / 2, y + hh / 2, f)
        f.color = roof; c.drawRect(x - hw / 2, y - hh / 2, x + hw / 2, y, f)
        f.color = scaleRgb(roof, 0.75f); c.drawRect(x - hw / 2, y, x + hw / 2, y + hh / 2, f)
    }
}

// ---------------------------------------------------------------------- 2 · Dust Canyon

class Canyon(seed: Int) : Biome(seed) {
    override val base = 0xFFC98A55.toInt()
    override val relief = 9f
    override val shadowAlpha = 0.38f
    override val cloudAmount = 0.35f
    override val cloudTint = 0xFFFFE9CF.toInt()
    override val zBase = 0f
    override val zScale = 230f
    override fun face(top: Int): Int = lerpColor(scaleRgb(top, 0.6f), 0xFF7A3A22.toInt(), 0.3f)
    fun riverX(gy: Float) = 500f + 230f * sin(gy * 0.00061f + 1.3f) + 70f * sin(gy * 0.0021f)
    fun railX(gy: Float) = 500f + 330f * sin(gy * 0.00037f + 4.1f) + 40f * sin(gy * 0.0013f + 2f)
    override fun height(x: Float, gy: Float): Float {
        val b = Noise.fbm(x * 0.0016f, gy * 0.0016f, 5, seed)
        val q = b * 6f
        val fl = floor(q)
        val terr = (fl + smooth((q - fl - 0.68f) / 0.32f)) / 6f
        val d = abs(x - riverX(gy))
        val carve = 0.28f * exp(-(d / 95f) * (d / 95f))
        val dune = 0.012f * sin(x * 0.06f + gy * 0.02f + Noise.value(x * 0.01f, gy * 0.01f, seed) * 6f)
        return terr * 0.85f + b * 0.15f - carve + dune * (1f - b)
    }
    override fun kind(x: Float, gy: Float): Int {
        if (abs(x - railX(gy)) < 16f) return Ground.RAIL
        if (abs(x - riverX(gy)) < 30f) return -1
        return Ground.LAND
    }
    override fun color(x: Float, gy: Float, h: Float, light: Float, slope: Float, n: Float): Int {
        val d = abs(x - riverX(gy))
        var c = when {
            h < 0.42f -> lerpColor(0xFFE7BA78.toInt(), 0xFFD49A5E.toInt(), h / 0.42f)
            h < 0.62f -> lerpColor(0xFFCB6E3E.toInt(), 0xFFB5552F.toInt(), (h - 0.42f) / 0.2f)
            else -> lerpColor(0xFFD98A55.toInt(), 0xFFE6A673.toInt(), clamp01((h - 0.62f) / 0.25f))
        }
        // strata bands on the cliffs
        val band = 0.5f + 0.5f * sin(h * 140f + Noise.value(x * 0.02f, gy * 0.02f, seed + 1) * 2f)
        c = scaleRgb(c, 0.9f + 0.14f * band * clamp01(slope * 3f))
        if (d < 120f) c = lerpColor(c, 0xFF8A5A3A.toInt(), (1f - d / 120f) * 0.45f)
        if (d < 28f) c = lerpColor(c, 0xFF7FA3AE.toInt(), (1f - d / 28f) * 0.8f)
        return scaleRgb(c, 0.42f + light * 0.78f + n * 0.07f)
    }
    override fun overlay(c: Canvas, k: Int, top: Float) {
        // railway
        st.color = 0xFF5B4632.toInt(); st.strokeWidth = 3f
        var gy = top + 20f
        while (gy > top - CH - MAX_LIFT) {
            val x = railX(gy)
            val dx = railX(gy + 1f) - x
            c.save(); c.translate(x, ly(top, x, gy)); c.rotate(-Math.toDegrees(kotlin.math.atan(dx.toDouble())).toFloat())
            f.color = 0xFF6B4E35.toInt(); c.drawRect(-16f, -2.5f, 16f, 2.5f, f)
            c.restore()
            gy -= 12f
        }
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            var first = true
            var yy = top + 20f
            while (yy > top - CH - MAX_LIFT) {
                val px = railX(yy) + side * 8f
                if (first) path.moveTo(px, ly(top, px, yy)) else path.lineTo(px, ly(top, px, yy))
                first = false; yy -= 15f
            }
            st.color = 0xFF3A3530.toInt(); st.strokeWidth = 2.6f; c.drawPath(path, st)
            st.color = 0x66FFFFFF; st.strokeWidth = 0.8f; c.drawPath(path, st)
        }
        neighbours(k, 2) { kk, rnd ->
            for (i in 0 until 70) {
                val x = rnd.nextFloat() * 1000f; val g = kk * CH + rnd.nextFloat() * CH
                val h = height(x, g)
                if (abs(x - railX(g)) < 30f || abs(x - riverX(g)) < 40f) continue
                val y = ly(top, x, g)
                if (h < 0.42f && rnd.nextFloat() < 0.5f) {
                    // cactus
                    shadowBlob(c, x + 5f, y + 6f, 6f, 4f, 70)
                    f.color = 0xFF4E7A3A.toInt(); c.drawCircle(x, y, 4.5f, f)
                    f.color = 0xFF79A85A.toInt(); c.drawCircle(x - 1.2f, y - 1.2f, 2f, f)
                } else {
                    val r = 4f + rnd.nextFloat() * 9f
                    shadowBlob(c, x + r * 0.5f, y + r * 0.6f, r, r * 0.7f, 80)
                    f.color = 0xFF9C6A48.toInt(); c.drawCircle(x, y, r * 0.8f, f)
                    f.color = 0xFFC99670.toInt(); c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.45f, f)
                }
            }
            // adobe outposts along the rail
            if (rnd.nextFloat() < 0.7f) {
                val g = kk * CH + rnd.nextFloat() * CH
                val x0 = railX(g) + (if (rnd.nextBoolean()) 70f else -70f)
                for (j in 0 until 3 + rnd.nextInt(4)) {
                    val x = x0 + (rnd.nextFloat() - 0.5f) * 90f; val y = ly(top, x, g + (rnd.nextFloat() - 0.5f) * 90f)
                    val w = 16f + rnd.nextFloat() * 14f; val hh = 14f + rnd.nextFloat() * 12f
                    f.color = 0x66000000; c.drawRect(x - w / 2 + 6f, y - hh / 2 + 7f, x + w / 2 + 7f, y + hh / 2 + 8f, f)
                    f.color = 0xFFD9AE7E.toInt(); c.drawRect(x - w / 2, y - hh / 2, x + w / 2, y + hh / 2, f)
                    f.color = 0xFFB98D60.toInt(); c.drawRect(x - w / 2, y + hh * 0.2f, x + w / 2, y + hh / 2, f)
                    f.color = 0xFF7A5A3E.toInt(); c.drawRect(x - 2f, y - 2f, x + 2f, y + 2f, f)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- 3 · Glacier Line

class Glacier(seed: Int) : Biome(seed) {
    private val SL = 0.47f
    override val base = 0xFF9FB8D0.toInt()
    override val relief = 6f
    override val zBase = 0.47f
    override val zScale = 620f
    override val grit = false
    override fun face(top: Int): Int = lerpColor(scaleRgb(top, 0.7f), 0xFF6F8FB8.toInt(), 0.45f)
    override val shadowAlpha = 0.28f
    override val cloudAmount = 1f
    override fun height(x: Float, gy: Float): Float {
        val n = Noise.fbm(x * 0.0017f, gy * 0.0017f, 5, seed, 0.47f)
        val r = Noise.ridged(x * 0.0026f, gy * 0.0026f, 3, seed + 6)
        val e = (x - 500f) / 500f
        val big = (Noise.value(gy * 0.0003f, 7.7f, seed + 4) - 0.5f) * 0.3f
        val land = n * 0.95f + e * e * 0.3f + big - 0.05f
        return land + r * 0.06f * smooth((land - 0.47f) / 0.08f)
    }
    private fun floe(x: Float, gy: Float) = Noise.fbm(x * 0.0065f, gy * 0.0065f, 3, seed + 21)
    override fun kind(x: Float, gy: Float): Int {
        val h = height(x, gy)
        return if (h < SL - 0.02f) Ground.WATER else if (h > SL + 0.02f) Ground.LAND else -1
    }
    override fun color(x: Float, gy: Float, h: Float, light: Float, slope: Float, n: Float): Int {
        if (h < SL) {
            val d = clamp01((SL - h) / 0.2f)
            val fl = floe(x, gy)
            val thr = 0.5f + d * 0.12f
            if (fl > thr) {
                val edge = clamp01((fl - thr) / 0.025f)
                val c = lerpColor(0xFF7FA6C6.toInt(), 0xFFE4EFF7.toInt(), edge)
                return scaleRgb(c, 0.92f + n * 0.08f + (Noise.value(x * 0.05f, gy * 0.05f, seed) - 0.5f) * 0.08f)
            }
            var c = lerpColor(0xFF1F4E6E.toInt(), 0xFF0A1E33.toInt(), d)
            if (fl > thr - 0.03f) c = lerpColor(c, 0xFF4F7FA3.toInt(), (fl - thr + 0.03f) / 0.03f * 0.6f)
            return c
        }
        val t = h - SL
        var c = if (slope > 0.95f && t > 0.06f) lerpColor(0xFFE8F0F8.toInt(), 0xFF59616E.toInt(), clamp01((slope - 0.95f) / 0.4f)) else 0xFFF3F7FC.toInt()
        if (t < 0.015f) c = lerpColor(0xFFB5CADB.toInt(), c, t / 0.015f)
        val lit = 0.5f + light * 0.75f
        // shadows go blue on snow
        val shade = if (lit < 1f) lerpColor(0xFF6E8DC2.toInt(), c, clamp01(lit)) else scaleRgb(c, min(1.12f, lit))
        return scaleRgb(shade, 0.96f + n * 0.05f)
    }
    override fun overlay(c: Canvas, k: Int, top: Float) {
        neighbours(k, 3) { kk, rnd ->
            for (i in 0 until 50) {
                val x = rnd.nextFloat() * 1000f; val gy = kk * CH + rnd.nextFloat() * CH
                val t = height(x, gy) - SL
                if (t < 0.03f || t > 0.16f) continue
                for (j in 0 until 4 + rnd.nextInt(8)) {
                    val px = x + (rnd.nextFloat() - 0.5f) * 80f; val pg = gy + (rnd.nextFloat() - 0.5f) * 80f
                    if (height(px, pg) - SL < 0.03f) continue
                    pine(c, px, ly(top, px, pg), 6f + rnd.nextFloat() * 5f)
                }
            }
            if (rnd.nextFloat() < 0.6f) {
                val x = 150f + rnd.nextFloat() * 700f; val gy = kk * CH + rnd.nextFloat() * CH
                val t = height(x, gy) - SL
                if (t > 0.03f && t < 0.12f) {
                    for (j in 0 until 3 + rnd.nextInt(3)) {
                        val hx = x + (rnd.nextFloat() - 0.5f) * 80f; val hy = ly(top, hx, gy + (rnd.nextFloat() - 0.5f) * 80f)
                        f.color = 0x553A4F7A; c.drawRect(hx - 11f + 5f, hy - 7f + 6f, hx + 11f + 6f, hy + 7f + 7f, f)
                        f.color = 0xFFE96A2E.toInt(); c.drawRect(hx - 11f, hy - 7f, hx + 11f, hy + 7f, f)
                        f.color = 0xFFF5F8FB.toInt(); c.drawRect(hx - 11f, hy - 7f, hx + 11f, hy - 2f, f)
                    }
                }
            }
        }
    }
    private fun pine(c: Canvas, x: Float, y: Float, r: Float) {
        shadowBlob(c, x + r * 0.9f, y + r * 0.9f, r * 1.2f, r * 0.7f, 60)
        path.reset()
        for (k in 0 until 8) {
            val a = k * 0.785f
            val rr = if (k % 2 == 0) r else r * 0.55f
            if (k == 0) path.moveTo(x + cos(a) * rr, y + sin(a) * rr) else path.lineTo(x + cos(a) * rr, y + sin(a) * rr)
        }
        path.close()
        f.color = 0xFF1E3B33.toInt(); c.drawPath(path, f)
        f.color = 0xFFDDE8F2.toInt(); c.drawCircle(x - r * 0.2f, y - r * 0.2f, r * 0.32f, f)
    }
}

// ---------------------------------------------------------------------- 4 · Neon Sprawl (night)

class Sprawl(seed: Int) : Biome(seed) {
    override val base = 0xFF141722.toInt()
    override val relief = 1f
    override val shadowAlpha = 0.16f
    override val cloudAmount = 0.45f
    override val cloudTint = 0xFF5A4C86.toInt()
    private val BLOCK = 240f
    private val STREET = 36f
    fun canalX(gy: Float) = 500f + 340f * sin(gy * 0.00031f + 0.7f)
    private fun rowOff(row: Int) = Noise.hash(row, 3, seed) * BLOCK
    /** 0 street, 1 block, 2 canal */
    fun cell(x: Float, gy: Float): Int {
        if (abs(x - canalX(gy)) < 42f) return 2
        val row = floor(gy / BLOCK).toInt()
        val ly = gy - row * BLOCK
        val lx = ((x + rowOff(row)) % BLOCK + BLOCK) % BLOCK
        return if (ly < STREET || lx < STREET) 0 else 1
    }
    override fun height(x: Float, gy: Float) = 0.5f
    override fun kind(x: Float, gy: Float) = when (cell(x, gy)) { 0 -> Ground.ROAD; 2 -> Ground.WATER; else -> Ground.ROOF }
    override fun color(x: Float, gy: Float, h: Float, light: Float, slope: Float, n: Float): Int {
        return when (cell(x, gy)) {
            2 -> {
                val r = Noise.value(x * 0.08f, gy * 0.01f, seed)
                lerpColor(0xFF0A0F1F.toInt(), 0xFF3A2E6E.toInt(), smooth((r - 0.6f) / 0.4f) * 0.7f)
            }
            0 -> {
                val row = floor(gy / BLOCK).toInt()
                val ly = gy - row * BLOCK
                val lx = ((x + rowOff(row)) % BLOCK + BLOCK) % BLOCK
                var c = 0xFF1B1E29.toInt()
                // lane dashes
                if (ly < STREET && abs(ly - STREET / 2f) < 1.2f && (x.toInt() / 14) % 2 == 0) c = 0xFF8C7A3A.toInt()
                if (lx < STREET && abs(lx - STREET / 2f) < 1.2f && (gy.toInt() / 14) % 2 == 0) c = 0xFF8C7A3A.toInt()
                scaleRgb(c, 0.9f + n * 0.2f)
            }
            else -> scaleRgb(0xFF262A38.toInt(), 0.85f + n * 0.2f)
        }
    }
    override fun overlay(c: Canvas, k: Int, top: Float) {
        // buildings, block by block (each block deterministic from its own row/col)
        val row0 = floor((top - CH - BLOCK) / BLOCK).toInt()
        val row1 = floor((top + BLOCK) / BLOCK).toInt()
        for (row in row0..row1) {
            val off = rowOff(row)
            for (col in -1..5) {
                val bx = col * BLOCK - off + STREET
                val gyb = row * BLOCK + STREET
                val bw = BLOCK - STREET; val bh = BLOCK - STREET
                if (bx > 1000f || bx + bw < 0f) continue
                val rnd = Random(seed * 31L + row * 1009L + col * 7L)
                val cx = bx + bw / 2f; val cgy = gyb + bh / 2f
                if (abs(cx - canalX(cgy)) < 42f + bw * 0.6f) { canalSide(c, bx, top - gyb - bh, bw, bh, rnd); continue }
                if (rnd.nextFloat() < 0.12f) { park(c, bx, top - gyb - bh, bw, bh, rnd); continue }
                // split into lots
                val split = rnd.nextInt(3)
                when (split) {
                    0 -> tower(c, bx + 6f, top - gyb - bh + 6f, bw - 12f, bh - 12f, rnd)
                    1 -> { tower(c, bx + 6f, top - gyb - bh + 6f, bw / 2f - 9f, bh - 12f, rnd); tower(c, bx + bw / 2f + 3f, top - gyb - bh + 6f, bw / 2f - 9f, bh - 12f, rnd) }
                    else -> for (q in 0 until 4) {
                        val qx = bx + 6f + (q % 2) * (bw / 2f - 3f); val qy = top - gyb - bh + 6f + (q / 2) * (bh / 2f - 3f)
                        tower(c, qx, qy, bw / 2f - 9f, bh / 2f - 9f, rnd)
                    }
                }
            }
            // street lamps at intersections
            for (col in -1..5) {
                val ix = col * BLOCK - off + STREET / 2f
                val iy = top - (row * BLOCK + STREET / 2f)
                if (ix < -20f || ix > 1020f) continue
                glow(c, ix, iy, 46f, 0.55f, 0xFFFFB45C.toInt())
                f.color = 0xFFFFE2A8.toInt(); c.drawCircle(ix, iy, 2f, f)
            }
        }
        // traffic light trails
        val rnd = rngFor(k, 9)
        for (i in 0 until 60) {
            val gy = top - rnd.nextFloat() * CH
            val row = floor(gy / BLOCK).toInt()
            val x = rnd.nextFloat() * 1000f
            if (cell(x, gy) != 0) continue
            val ly = gy - row * BLOCK
            val horiz = ly < STREET
            val red = rnd.nextBoolean()
            f.color = if (red) 0xFFFF3B4A.toInt() else 0xFFFFF4D8.toInt()
            val y = top - gy
            if (horiz) { glow(c, x, y, 14f, 0.7f, f.color); c.drawRect(x - 7f, y - 1f, x + 7f, y + 1f, f) }
            else { glow(c, x, y, 14f, 0.7f, f.color); c.drawRect(x - 1f, y - 7f, x + 1f, y + 7f, f) }
        }
    }
    private val roofs = intArrayOf(0xFF2B3042.toInt(), 0xFF33293F.toInt(), 0xFF203041.toInt(), 0xFF3A3A44.toInt(), 0xFF262638.toInt())
    private val neon = intArrayOf(0xFFFF3FD0.toInt(), 0xFF3FF2FF.toInt(), 0xFFFFD23F.toInt(), 0xFF7A5CFF.toInt(), 0xFF46FF9A.toInt())
    private fun tower(c: Canvas, x: Float, y: Float, w: Float, h: Float, rnd: Random) {
        if (w < 20f || h < 20f) return
        val tall = 6f + rnd.nextFloat() * 16f
        // lit side wall (we look slightly from the south)
        f.color = 0xFF0E1018.toInt(); c.drawRect(x, y + h, x + w, y + h + tall, f)
        f.color = 0x88000000.toInt(); c.drawRect(x + tall * 0.6f, y + h + tall, x + w + tall * 0.6f, y + h + tall * 1.6f, f)
        val warm = rnd.nextBoolean()
        for (wx in 0 until (w / 7f).toInt()) {
            if (rnd.nextFloat() < 0.45f) continue
            f.color = if (warm) 0xFFFFC874.toInt() else 0xFF9FE3FF.toInt()
            c.drawRect(x + 3f + wx * 7f, y + h + tall * 0.25f, x + 6f + wx * 7f, y + h + tall * 0.7f, f)
        }
        val rc = roofs[rnd.nextInt(roofs.size)]
        f.color = rc; c.drawRect(x, y, x + w, y + h, f)
        f.color = scaleRgb(rc, 1.35f); c.drawRect(x, y, x + w, y + 2.5f, f)
        f.color = scaleRgb(rc, 0.7f); c.drawRect(x + w - 2.5f, y, x + w, y + h, f)
        // roof clutter
        for (i in 0 until rnd.nextInt(5)) {
            val ax = x + 6f + rnd.nextFloat() * (w - 18f); val ay = y + 6f + rnd.nextFloat() * (h - 18f)
            f.color = 0xFF4A4F60.toInt(); c.drawRect(ax, ay, ax + 9f, ay + 7f, f)
            f.color = 0xFF14161E.toInt(); c.drawRect(ax + 9f, ay + 2f, ax + 11f, ay + 9f, f)
        }
        if (rnd.nextFloat() < 0.15f && w > 50f && h > 50f) {
            st.color = 0xFFE6E6E6.toInt(); st.strokeWidth = 2f
            c.drawCircle(x + w / 2f, y + h / 2f, 14f, st)
            c.drawLine(x + w / 2f - 5f, y + h / 2f - 7f, x + w / 2f - 5f, y + h / 2f + 7f, st)
            c.drawLine(x + w / 2f + 5f, y + h / 2f - 7f, x + w / 2f + 5f, y + h / 2f + 7f, st)
            c.drawLine(x + w / 2f - 5f, y + h / 2f, x + w / 2f + 5f, y + h / 2f, st)
        }
        if (rnd.nextFloat() < 0.45f) {
            val nc = neon[rnd.nextInt(neon.size)]
            val horiz = rnd.nextBoolean()
            glow(c, x + w / 2f, y + (if (horiz) 4f else h / 2f), w * 0.55f, 0.5f, nc)
            st.color = nc; st.strokeWidth = 2.6f
            if (horiz) c.drawLine(x + 5f, y + 4f, x + w - 5f, y + 4f, st) else c.drawLine(x + 4f, y + 5f, x + 4f, y + h - 5f, st)
            st.color = 0xFFFFFFFF.toInt(); st.strokeWidth = 0.9f
            if (horiz) c.drawLine(x + 5f, y + 4f, x + w - 5f, y + 4f, st) else c.drawLine(x + 4f, y + 5f, x + 4f, y + h - 5f, st)
        }
        if (rnd.nextFloat() < 0.3f) {
            f.color = 0xFFFF3B3B.toInt()
            glow(c, x + w - 6f, y + 6f, 12f, 0.8f, 0xFFFF3B3B.toInt()); c.drawCircle(x + w - 6f, y + 6f, 1.6f, f)
        }
    }
    private fun park(c: Canvas, x: Float, y: Float, w: Float, h: Float, rnd: Random) {
        f.color = 0xFF16261C.toInt(); c.drawRect(x + 4f, y + 4f, x + w - 4f, y + h - 4f, f)
        st.color = 0xFF2B3A30.toInt(); st.strokeWidth = 5f
        c.drawLine(x + 4f, y + h / 2f, x + w - 4f, y + h / 2f, st); c.drawLine(x + w / 2f, y + 4f, x + w / 2f, y + h - 4f, st)
        for (i in 0 until 18) {
            val tx = x + 14f + rnd.nextFloat() * (w - 28f); val ty = y + 14f + rnd.nextFloat() * (h - 28f)
            f.color = 0xFF1F3A26.toInt(); c.drawCircle(tx, ty, 8f, f)
            f.color = 0xFF2F5236.toInt(); c.drawCircle(tx - 2f, ty - 2f, 4f, f)
        }
        for (i in 0 until 4) {
            val lx = x + 20f + rnd.nextFloat() * (w - 40f); val ly = y + 20f + rnd.nextFloat() * (h - 40f)
            glow(c, lx, ly, 20f, 0.6f, 0xFFB6FF9E.toInt())
        }
    }
    private fun canalSide(c: Canvas, x: Float, y: Float, w: Float, h: Float, rnd: Random) {
        // a few low warehouses beside the canal, avoiding the water
        for (i in 0 until 3) {
            val bx = x + 10f + rnd.nextFloat() * (w - 60f); val by = y + 10f + rnd.nextFloat() * (h - 50f)
            f.color = 0xFF232733.toInt(); c.drawRect(bx, by, bx + 44f, by + 30f, f)
            f.color = 0xFF2E3344.toInt(); c.drawRect(bx, by, bx + 44f, by + 3f, f)
        }
    }
}

// ---------------------------------------------------------------------- 5 · Ember Forge

class Forge(seed: Int) : Biome(seed) {
    override val base = 0xFF2E2624.toInt()
    override val relief = 10f
    override val shadowAlpha = 0.22f
    override val cloudAmount = 0.55f
    override val cloudTint = 0xFF4A3A36.toInt()
    override val zBase = 0.15f
    override val zScale = 330f
    override fun face(top: Int): Int = lerpColor(scaleRgb(top, 0.55f), 0xFF1A1514.toInt(), 0.4f)
    fun lava(x: Float, gy: Float) = Noise.ridged(x * 0.0021f, gy * 0.0021f, 3, seed + 5)
    override fun height(x: Float, gy: Float): Float {
        val n = Noise.fbm(x * 0.002f, gy * 0.002f, 5, seed)
        val r = Noise.ridged(x * 0.0013f, gy * 0.0013f, 4, seed + 2)
        return n * 0.6f + r * 0.4f - lava(x, gy) * 0.08f
    }
    override fun kind(x: Float, gy: Float) = if (lava(x, gy) > 0.72f) Ground.LAVA else Ground.LAND
    override fun color(x: Float, gy: Float, h: Float, light: Float, slope: Float, n: Float): Int {
        val lv = lava(x, gy)
        if (lv > 0.8f) {
            val k = clamp01((lv - 0.8f) / 0.12f)
            val flow = Noise.value(x * 0.03f + gy * 0.004f, gy * 0.03f, seed + 7)
            var c = lerpColor(0xFFB3260E.toInt(), 0xFFFF8A1F.toInt(), k)
            c = lerpColor(c, 0xFFFFE9A0.toInt(), k * k * (0.4f + 0.6f * flow))
            return c
        }
        var c = lerpColor(0xFF221C1B.toInt(), 0xFF56483F.toInt(), clamp01(h * 1.2f))
        c = scaleRgb(c, 0.4f + light * 0.95f + n * 0.08f)
        // ember glow spilling onto the rock beside the flows
        val glow = smooth((lv - 0.62f) / 0.18f)
        if (glow > 0f) c = lerpColor(c, 0xFFE0521B.toInt(), glow * 0.55f)
        // hot cracks
        val crack = Noise.ridged(x * 0.012f, gy * 0.012f, 2, seed + 13)
        if (crack > 0.9f) c = lerpColor(c, 0xFFFF6A1E.toInt(), (crack - 0.9f) / 0.1f * 0.8f)
        return c
    }
    override fun overlay(c: Canvas, k: Int, top: Float) {
        neighbours(k, 5) { kk, rnd ->
            for (i in 0 until 2) {
                val x = 150f + rnd.nextFloat() * 700f; val gy = kk * CH + rnd.nextFloat() * CH
                if (lava(x, gy) > 0.6f) continue
                factory(c, x, ly(top, x, gy), rnd)
            }
            for (i in 0 until 40) {
                val x = rnd.nextFloat() * 1000f; val gy = kk * CH + rnd.nextFloat() * CH
                if (lava(x, gy) > 0.7f) continue
                val r = 3f + rnd.nextFloat() * 8f
                val y = ly(top, x, gy)
                shadowBlob(c, x + r * 0.5f, y + r * 0.6f, r, r * 0.7f, 90)
                f.color = 0xFF3B3230.toInt(); c.drawCircle(x, y, r * 0.8f, f)
                f.color = 0xFF5E4E46.toInt(); c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.4f, f)
            }
        }
    }
    private fun factory(c: Canvas, x: Float, y: Float, rnd: Random) {
        val w = 60f + rnd.nextFloat() * 50f; val h = 40f + rnd.nextFloat() * 40f
        f.color = 0x88000000.toInt(); c.drawRect(x - w / 2 + 10f, y - h / 2 + 12f, x + w / 2 + 12f, y + h / 2 + 14f, f)
        f.color = 0xFF3A3F47.toInt(); c.drawRect(x - w / 2, y - h / 2, x + w / 2, y + h / 2, f)
        f.color = 0xFF515866.toInt(); c.drawRect(x - w / 2, y - h / 2, x + w / 2, y - h / 2 + 4f, f)
        for (i in 0 until (w / 12f).toInt()) {
            f.color = 0xFF2A2E35.toInt(); c.drawRect(x - w / 2 + 4f + i * 12f, y - h / 2 + 8f, x - w / 2 + 10f + i * 12f, y + h / 2 - 6f, f)
        }
        for (i in 0 until 3) {
            val vx = x - w / 2 + 10f + rnd.nextFloat() * (w - 20f); val vy = y - h / 2 + 10f + rnd.nextFloat() * (h - 20f)
            glow(c, vx, vy, 18f, 0.8f, 0xFFFF7A1F.toInt())
            f.color = 0xFFFFC46B.toInt(); c.drawCircle(vx, vy, 3f, f)
        }
        // chimney
        val cx = x + w / 2 - 10f; val cy = y - h / 2 - 6f
        f.color = 0xFF2B2F36.toInt(); c.drawCircle(cx, cy, 9f, f)
        f.color = 0xFF111215.toInt(); c.drawCircle(cx, cy, 6f, f)
        glow(c, cx, cy, 14f, 0.6f, 0xFFFF5A1F.toInt())
    }
}

// ---------------------------------------------------------------------- 6 · Stratos (above the clouds)

class Stratos(seed: Int) : Biome(seed) {
    override val base = 0xFFD9D2EC.toInt()
    override val relief = 9f
    override val lightX = 0.6f
    override val lightY = -0.55f
    override val lightZ = 0.58f
    override val shadowAlpha = 0.24f
    override val cloudAmount = 1.2f
    override val cloudTint = 0xFFFFE6F2.toInt()
    override val zBase = 0.33f
    override val zScale = 150f
    override val grit = false
    override fun face(top: Int): Int = lerpColor(scaleRgb(top, 0.85f), 0xFF8E8CC8.toInt(), 0.4f)
    override fun height(x: Float, gy: Float): Float {
        val b = Noise.billow(x * 0.0024f, gy * 0.0024f, 5, seed)
        val big = Noise.fbm(x * 0.0009f, gy * 0.0009f, 3, seed + 8)
        return b * 0.5f + big * 0.62f + 0.02f
    }
    override fun kind(x: Float, gy: Float) = Ground.GAP
    override fun color(x: Float, gy: Float, h: Float, light: Float, slope: Float, n: Float): Int {
        if (h < 0.33f) {
            val d = clamp01((0.33f - h) / 0.1f)
            val far = Noise.fbm(x * 0.01f, gy * 0.01f, 3, seed + 30)
            var c = lerpColor(0xFF5A5FA8.toInt(), 0xFF1A1D4A.toInt(), d)
            c = lerpColor(c, 0xFF3D5A3F.toInt(), d * 0.35f * smooth((far - 0.5f) / 0.3f))
            return c
        }
        val t = clamp01((h - 0.33f) / 0.5f)
        val lit = clamp01(light * 1.25f + t * 0.25f)
        var c = lerpColor(0xFFA7A3D6.toInt(), 0xFFF8F4FC.toInt(), smooth(lit))
        c = lerpColor(c, 0xFFFFDDB8.toInt(), smooth((lit - 0.8f) / 0.2f) * 0.55f)
        if (t < 0.05f) c = lerpColor(0xFF7A7FC2.toInt(), c, t / 0.05f)
        return scaleRgb(c, 0.97f + n * 0.04f)
    }
}
