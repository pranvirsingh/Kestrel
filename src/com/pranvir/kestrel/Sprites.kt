package com.pranvir.kestrel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A pre-painted bitmap with optional black (shadow) and white (hit-flash) silhouettes. Sizes in world units. */
class Spr(val bmp: Bitmap, val shadow: Bitmap?, val flash: Bitmap?, val w: Float, val h: Float, val sw: Float = w, val sh: Float = h) {
    fun release() { bmp.recycle(); shadow?.recycle(); flash?.recycle() }
}

object Paintbox {
    val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val g = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()
    val rc = RectF()

    /** Mirror a right-half outline (x >= 0 points, nose to tail) into a closed symmetric path. */
    fun sym(pts: FloatArray, out: Path = path): Path {
        out.reset()
        out.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { out.lineTo(pts[i], pts[i + 1]); i += 2 }
        i = pts.size - 2
        while (i >= 0) { out.lineTo(-pts[i], pts[i + 1]); i -= 2 }
        out.close()
        return out
    }

    fun poly(pts: FloatArray, out: Path = path): Path {
        out.reset(); out.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { out.lineTo(pts[i], pts[i + 1]); i += 2 }
        out.close(); return out
    }

    /** Lit metal: a diagonal gradient from the sun (upper left) plus a dark rim. */
    fun metal(c: Canvas, p: Path, base: Int, l: Float, t: Float, r: Float, b: Float, rim: Float = 1.2f, hi: Float = 1.6f, lo: Float = 0.45f) {
        // ambient occlusion lip under the plate
        c.save(); c.translate(rim * 0.9f, rim * 1.1f)
        f.color = 0x66000000; c.drawPath(p, f)
        c.restore()
        g.shader = LinearGradient(l, t, r, b, intArrayOf(scaleRgb(base, hi), base, scaleRgb(base, lo)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(p, g)
        g.shader = null
        // bevel: a sunlit edge on the upper left, shade on the lower right
        c.save(); c.clipPath(p)
        c.translate(rim * 0.8f, rim * 0.8f)
        st.color = 0x55FFFFFF; st.strokeWidth = rim * 1.6f
        c.drawPath(p, st)
        c.translate(-rim * 1.6f, -rim * 1.6f)
        st.color = 0x44000000
        c.drawPath(p, st)
        c.restore()
        st.color = scaleRgb(base, 0.3f); st.strokeWidth = rim
        c.drawPath(p, st)
    }

    fun glass(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, tint: Int) {
        rc.set(cx - rx, cy - ry, cx + rx, cy + ry)
        g.shader = LinearGradient(cx - rx, cy - ry, cx + rx, cy + ry, intArrayOf(lerpColor(tint, 0xFFFFFFFF.toInt(), 0.65f), tint, scaleRgb(tint, 0.3f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(rc, g)
        g.shader = null
        st.color = 0xAA0A0F18.toInt(); st.strokeWidth = 0.9f; c.drawOval(rc, st)
        f.color = 0xCCFFFFFF.toInt()
        rc.set(cx - rx * 0.45f, cy - ry * 0.7f, cx - rx * 0.05f, cy - ry * 0.1f); c.drawOval(rc, f)
    }

    fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, col: Int, w: Float) { st.color = col; st.strokeWidth = w; c.drawLine(x0, y0, x1, y1, st) }

    fun radial(c: Canvas, x: Float, y: Float, r: Float, cols: IntArray, stops: FloatArray?) {
        g.shader = RadialGradient(x, y, r, cols, stops, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, g)
        g.shader = null
    }
}

/**
 * Every unit, bullet and effect pre-painted once at the device's pixel density.
 * Built on the UI thread at start and after a size change; bosses are painted per mission.
 */
class SpriteSet(val scale: Float) {
    private val all = ArrayList<Spr>()

    private fun make(w: Float, h: Float, silhouettes: Boolean = true, painter: (Canvas) -> Unit): Spr {
        val pw = ceil(w * scale).toInt().coerceAtLeast(2); val ph = ceil(h * scale).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.save()
        c.scale(pw / w, ph / h)
        c.translate(w / 2f, h / 2f)
        painter(c)
        c.restore()
        var sh: Bitmap? = null; var fl: Bitmap? = null
        if (silhouettes) {
            val px = IntArray(pw * ph)
            bmp.getPixels(px, 0, pw, 0, 0, pw, ph)
            val sp = IntArray(pw * ph); val fp = IntArray(pw * ph)
            for (i in px.indices) {
                val a = px[i] ushr 24
                sp[i] = (a shl 24)
                fp[i] = (a shl 24) or 0xFFFFFF
            }
            sh = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888); sh.setPixels(sp, 0, pw, 0, 0, pw, ph)
            fl = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888); fl.setPixels(fp, 0, pw, 0, 0, pw, ph)
        }
        val s = Spr(bmp, sh, fl, w, h)
        all.add(s)
        return s
    }

    fun release() { for (s in all) s.release(); all.clear() }

    private val P = Paintbox
    private val HULL = 0xFF3A3E47.toInt()
    private val RED = 0xFFD8323F.toInt()
    private val DARK = 0xFF16181D.toInt()
    private val STEEL = 0xFF59606C.toInt()

    // ------------------------------------------------------------------ projectiles, pickups, fx (no silhouettes)

    private fun orb(core: Int, rim: Int) = make(48f, 48f, false) { c ->
        P.radial(c, 0f, 0f, 24f, intArrayOf(withAlpha(rim, 150), withAlpha(rim, 60), withAlpha(rim, 0)), floatArrayOf(0f, 0.5f, 1f))
        P.f.color = rim; c.drawCircle(0f, 0f, 10.5f, P.f)
        P.radial(c, 0f, 0f, 8f, intArrayOf(0xFFFFFFFF.toInt(), core, withAlpha(core, 0)), floatArrayOf(0f, 0.6f, 1f))
    }
    val bulletPink = orb(0xFFFFD2E6.toInt(), Col.BULLET)
    val bulletOrange = orb(0xFFFFD0D6.toInt(), Col.BULLET2)
    val bulletBig = make(80f, 80f, false) { c ->
        P.radial(c, 0f, 0f, 40f, intArrayOf(0x99FF3D8B.toInt(), 0x44FF3D8B, 0x00FF3D8B), floatArrayOf(0f, 0.5f, 1f))
        P.f.color = 0xFFFF3D8B.toInt(); c.drawCircle(0f, 0f, 18f, P.f)
        P.radial(c, 0f, 0f, 15f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFB0D0.toInt(), 0x00FFB0D0), floatArrayOf(0f, 0.55f, 1f))
    }
    val needle = make(24f, 64f, false) { c ->
        P.rc.set(-12f, -32f, 12f, 32f)
        P.radial(c, 0f, 0f, 20f, intArrayOf(0x88FF3D8B.toInt(), 0x00FF3D8B), null)
        P.f.color = Col.BULLET; c.drawRoundRect(-5f, -22f, 5f, 22f, 5f, 5f, P.f)
        P.f.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(-2.2f, -18f, 2.2f, 18f, 2.2f, 2.2f, P.f)
    }
    val bolt = make(20f, 56f, false) { c ->
        P.g.shader = LinearGradient(0f, -28f, 0f, 28f, intArrayOf(0xFFFFFFFF.toInt(), 0xFF8FF3FF.toInt(), 0x003FB7FF), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(-4f, -26f, 4f, 26f, 4f, 4f, P.g); P.g.shader = null
        P.radial(c, 0f, -16f, 10f, intArrayOf(0x883FD8FF.toInt(), 0x003FD8FF), null)
    }
    val boltHeavy = make(28f, 66f, false) { c ->
        P.g.shader = LinearGradient(0f, -32f, 0f, 32f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE27A.toInt(), 0x00FF9A3D), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(-6f, -31f, 6f, 31f, 6f, 6f, P.g); P.g.shader = null
        P.radial(c, 0f, -18f, 14f, intArrayOf(0x88FFC145.toInt(), 0x00FFC145), null)
    }
    val droneShot = make(14f, 30f, false) { c ->
        P.f.color = 0xFF9FFFD0.toInt(); c.drawRoundRect(-2.5f, -13f, 2.5f, 13f, 2.5f, 2.5f, P.f)
        P.f.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(-1f, -11f, 1f, 6f, 1f, 1f, P.f)
    }
    val missile = make(18f, 46f, false) { c ->
        P.f.color = 0xFFE6E9EF.toInt(); c.drawRoundRect(-4f, -20f, 4f, 14f, 4f, 4f, P.f)
        P.f.color = Col.CYAN; c.drawRect(-4f, -12f, 4f, -9f, P.f)
        P.f.color = 0xFF5B6270.toInt(); c.drawPath(P.poly(floatArrayOf(-4f, 6f, -9f, 16f, 9f, 16f, 4f, 6f)), P.f)
    }
    val enemyMissile = make(20f, 48f, false) { c ->
        P.f.color = 0xFF3A3D44.toInt(); c.drawRoundRect(-4.5f, -16f, 4.5f, 20f, 4.5f, 4.5f, P.f)
        P.f.color = RED; c.drawRoundRect(-4.5f, 12f, 4.5f, 20f, 4.5f, 4.5f, P.f)
        P.f.color = 0xFF5B6270.toInt(); c.drawPath(P.poly(floatArrayOf(-4.5f, -8f, -10f, -18f, 10f, -18f, 4.5f, -8f)), P.f)
    }

    val coreSmall = make(30f, 30f, false) { c -> gem(c, 9f) }
    val coreBig = make(48f, 48f, false) { c -> gem(c, 15f) }
    private fun gem(c: Canvas, r: Float) {
        P.radial(c, 0f, 0f, r * 1.6f, intArrayOf(0x88FFC145.toInt(), 0x00FFC145), null)
        val hx = Path(); for (k in 0 until 6) { val a = PI.toFloat() / 6f + k * PI.toFloat() / 3f; if (k == 0) hx.moveTo(cos(a) * r, sin(a) * r) else hx.lineTo(cos(a) * r, sin(a) * r) }; hx.close()
        P.g.shader = LinearGradient(-r, -r, r, r, intArrayOf(0xFFFFF4C2.toInt(), 0xFFFFC145.toInt(), 0xFFC07A12.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPath(hx, P.g); P.g.shader = null
        P.st.color = 0xFF7A4A08.toInt(); P.st.strokeWidth = r * 0.12f; c.drawPath(hx, P.st)
        P.f.color = 0xCCFFFFFF.toInt(); c.drawCircle(-r * 0.3f, -r * 0.35f, r * 0.22f, P.f)
    }

    private fun capsule(col: Int, icon: Int) = make(56f, 56f, false) { c ->
        P.radial(c, 0f, 0f, 28f, intArrayOf(withAlpha(col, 140), withAlpha(col, 0)), null)
        val hx = Path(); for (k in 0 until 6) { val a = k * PI.toFloat() / 3f; if (k == 0) hx.moveTo(cos(a) * 18f, sin(a) * 18f) else hx.lineTo(cos(a) * 18f, sin(a) * 18f) }; hx.close()
        P.g.shader = LinearGradient(-18f, -18f, 18f, 18f, intArrayOf(lerpColor(col, 0xFFFFFFFF.toInt(), 0.5f), col, scaleRgb(col, 0.45f)), null, Shader.TileMode.CLAMP)
        c.drawPath(hx, P.g); P.g.shader = null
        P.st.color = 0xFFFFFFFF.toInt(); P.st.strokeWidth = 1.8f; c.drawPath(hx, P.st)
        Icon.draw(c, icon, 0f, 0f, 24f, 0xFFFFFFFF.toInt())
    }
    val capRepair = capsule(0xFF2FCB6E.toInt(), Icon.HULL)
    val capShield = capsule(0xFF3F8BFF.toInt(), Icon.UNTOUCHED)
    val capSurge = capsule(0xFFFF8A2A.toInt(), Icon.CANNON)
    val capOver = capsule(0xFFB04DFF.toInt(), Icon.OVERDRIVE)

    val fireball = make(64f, 64f, false) { c ->
        P.radial(c, 0f, 0f, 32f, intArrayOf(0xFFFFFFF0.toInt(), 0xFFFFE27A.toInt(), 0xFFFF8A2A.toInt(), 0xAAD8321F.toInt(), 0x00501008), floatArrayOf(0f, 0.18f, 0.42f, 0.7f, 1f))
    }
    val smoke = make(64f, 64f, false) { c ->
        for (k in 0 until 5) {
            val a = k * 1.3f; val r = 9f + k * 1.5f
            P.radial(c, cos(a) * 8f, sin(a) * 8f, r + 12f, intArrayOf(0x995A5652.toInt(), 0x443E3A38, 0x00302C2A), floatArrayOf(0f, 0.6f, 1f))
        }
    }
    val scorch = make(90f, 90f, false) { c ->
        P.radial(c, 0f, 0f, 45f, intArrayOf(0xCC0E0B0A.toInt(), 0x88181210.toInt(), 0x00181210), floatArrayOf(0f, 0.55f, 1f))
        for (k in 0 until 9) { val a = k * 0.7f; P.radial(c, cos(a) * 22f, sin(a) * 22f, 14f, intArrayOf(0x66100C0A, 0x00100C0A), null) }
    }
    val flare = make(64f, 64f, false) { c ->
        P.radial(c, 0f, 0f, 32f, intArrayOf(0xFFFFFFFF.toInt(), 0x88BFF3FF.toInt(), 0x003FB7FF), floatArrayOf(0f, 0.25f, 1f))
    }

    // survivor beacon (people waving beside a smoking flare)
    val survivor = make(70f, 70f, false) { c ->
        P.f.color = 0x44000000; c.drawOval(RectF(-26f, -14f, 30f, 22f), P.f)
        P.f.color = 0xFFE7E1D3.toInt(); c.drawRoundRect(-22f, -10f, 22f, 12f, 4f, 4f, P.f)
        P.f.color = 0xFFC9C2B4.toInt(); c.drawRect(-22f, 2f, 22f, 12f, P.f)
        P.f.color = 0xFF33A4E3.toInt(); c.drawRect(-6f, -10f, 6f, 12f, P.f)
        for (k in 0 until 3) {
            val px = -14f + k * 14f
            P.f.color = 0xFFFFC145.toInt(); c.drawCircle(px, -18f, 3.2f, P.f)
            P.f.color = 0xFF2C6FD0.toInt(); c.drawRoundRect(px - 3f, -15f, px + 3f, -8f, 2f, 2f, P.f)
        }
        P.radial(c, 24f, -20f, 12f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF4D5E.toInt(), 0x00FF4D5E), floatArrayOf(0f, 0.3f, 1f))
    }

    // ------------------------------------------------------------------ clouds (soft, from noise)

    val clouds: Array<Spr> = Array(4) { k ->
        val w = 520f; val h = 330f
        val cs = min(scale, 0.6f) / scale
        val pw = ceil(w * scale * cs).toInt(); val ph = ceil(h * scale * cs).toInt()
        val px = IntArray(pw * ph)
        for (j in 0 until ph) for (i in 0 until pw) {
            val u = i / pw.toFloat() * 2f - 1f; val v = j / ph.toFloat() * 2f - 1f
            val d = sqrt(u * u * 0.9f + v * v * 1.25f)
            val n = Noise.fbm(i / pw.toFloat() * 3.5f + k * 10f, j / ph.toFloat() * 2.4f, 5, 900 + k)
            val dens = clamp01((n * 1.25f - d * 0.95f) * 2.4f)
            if (dens <= 0f) continue
            // lit from the upper left: brighter where noise is higher towards the light
            val lit = clamp01(0.75f + (n - 0.5f) * 0.9f - v * 0.12f - u * 0.06f)
            val g = (215 + 40 * lit).toInt().coerceAtMost(255)
            val b = (225 + 30 * lit).toInt().coerceAtMost(255)
            px[i + j * pw] = ((dens * 235).toInt() shl 24) or (g shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, pw, 0, 0, pw, ph)
        val sp = IntArray(pw * ph)
        for (i in px.indices) sp[i] = (((px[i] ushr 24) * 0.55f).toInt() shl 24)
        val sh = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        sh.setPixels(sp, 0, pw, 0, 0, pw, ph)
        Spr(bmp, sh, null, w, h).also { all.add(it) }
    }
}
