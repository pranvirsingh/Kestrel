package com.pranvir.kestrel

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val WORLD_W = 1000f

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
fun smooth(t: Float): Float { val x = clamp01(t); return x * x * (3f - 2f * x) }
fun approach(v: Float, target: Float, rate: Float) = if (v < target) min(target, v + rate) else max(target, v - rate)
fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val t = x - 1f; return 1f + c3 * t * t * t + c1 * t * t }
fun easeOut(x: Float): Float { val t = 1f - clamp01(x); return 1f - t * t * t }

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val tt = clamp01(t)
    val aa = (a ushr 24) and 255; val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
    val ba = (b ushr 24) and 255; val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
    return ((aa + (ba - aa) * tt).toInt() shl 24) or ((ar + (br - ar) * tt).toInt() shl 16) or
        ((ag + (bg - ag) * tt).toInt() shl 8) or (ab + (bb - ab) * tt).toInt()
}
fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
fun alphaF(c: Int, f: Float): Int = withAlpha(c, (((c ushr 24) and 255) * clamp01(f)).toInt())
fun scaleRgb(c: Int, k: Float): Int {
    val r = (((c shr 16) and 255) * k).toInt().coerceIn(0, 255)
    val g = (((c shr 8) and 255) * k).toInt().coerceIn(0, 255)
    val b = ((c and 255) * k).toInt().coerceIn(0, 255)
    return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
}

/** Cheap deterministic hash to [0,1). */
fun hash01(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

object Col {
    const val WHITE = 0xFFEFF6FF.toInt()
    const val CYAN = 0xFF5CE1FF.toInt()
    const val CYAN_DIM = 0xFF2A8FB3.toInt()
    const val AMBER = 0xFFFFC145.toInt()
    const val DANGER = 0xFFFF4D5E.toInt()
    const val GREEN = 0xFF5DFF9A.toInt()
    const val INK = 0xFF060A12.toInt()
    const val PANEL = 0xD90B1422.toInt()
    const val PANEL_HI = 0xE6122036.toInt()
    const val EDGE = 0x7780D8FF
    const val MUTED = 0xFF8DA2BD.toInt()
    const val BULLET = 0xFFFF3D8B.toInt()
    const val BULLET2 = 0xFFFF2E4D.toInt()
    const val PLAYER_SHOT = 0xFF8FF3FF.toInt()
    const val GOLD = 0xFFFFD45C.toInt()
}

object Fonts {
    var semi: Typeface = Typeface.DEFAULT
    var bold: Typeface = Typeface.DEFAULT_BOLD
}

object Draw {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    val grad = Paint(Paint.ANTI_ALIAS_FLAG)
    val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val path = Path()
    val r1 = RectF()
    val r2 = RectF()
    private val fm = Paint.FontMetrics()
    private val glowCache = HashMap<Int, RadialGradient>()

    /** Soft additive-looking glow. Shaders stay immutable; the canvas carries the transform. */
    fun glow(c: Canvas, x: Float, y: Float, r: Float, strength: Float, col: Int) {
        if (strength <= 0.01f || r <= 1f) return
        var sh = glowCache[col]
        if (sh == null) {
            sh = RadialGradient(0f, 0f, 1f, intArrayOf(withAlpha(col, 210), withAlpha(col, 80), withAlpha(col, 20), withAlpha(col, 0)),
                floatArrayOf(0f, 0.22f, 0.6f, 1f), Shader.TileMode.CLAMP)
            glowCache[col] = sh
        }
        glowP.shader = sh
        glowP.alpha = (255 * clamp01(strength)).toInt()
        c.save(); c.translate(x, y); c.scale(r, r)
        c.drawCircle(0f, 0f, 1f, glowP)
        c.restore()
    }

    fun shadeRect(c: Canvas, sh: Shader, l: Float, t: Float, r: Float, b: Float, ox: Float, oy: Float, sx: Float, sy: Float, alpha: Int = 255) {
        val kx = max(sx, 0.001f); val ky = max(sy, 0.001f)
        grad.shader = sh; grad.alpha = alpha
        c.save(); c.translate(ox, oy); c.scale(kx, ky)
        c.drawRect((l - ox) / kx, (t - oy) / ky, (r - ox) / kx, (b - oy) / ky, grad)
        c.restore()
        grad.shader = null; grad.alpha = 255
    }

    fun text(c: Canvas, s: String, x: Float, cy: Float, size: Float, col: Int, face: Typeface = Fonts.bold, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f) {
        text.typeface = face; text.textSize = size; text.color = col; text.textAlign = align
        text.letterSpacing = spacing
        text.getFontMetrics(fm)
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, text)
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0f
    }

    fun width(s: String, size: Float, face: Typeface = Fonts.bold, spacing: Float = 0f): Float {
        text.typeface = face; text.textSize = size; text.letterSpacing = spacing
        val w = text.measureText(s)
        text.letterSpacing = 0f
        return w
    }

    fun fit(s: String, size: Float, maxW: Float, face: Typeface = Fonts.bold, spacing: Float = 0f): Float {
        val w = width(s, size, face, spacing)
        return if (w <= maxW || w <= 0f) size else size * maxW / w
    }

    /** Rectangle with clipped (chamfered) top-left and bottom-right corners: the HUD's signature shape. */
    fun chamfer(r: RectF, k: Float, out: Path = path): Path {
        out.reset()
        out.moveTo(r.left + k, r.top)
        out.lineTo(r.right, r.top)
        out.lineTo(r.right, r.bottom - k)
        out.lineTo(r.right - k, r.bottom)
        out.lineTo(r.left, r.bottom)
        out.lineTo(r.left, r.top + k)
        out.close()
        return out
    }

    private val panelSh = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(0x2A9FDFFF, 0x0A9FDFFF, 0x00000000), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)

    fun panel(c: Canvas, r: RectF, u: Float, fillCol: Int = Col.PANEL, edge: Int = Col.EDGE, k: Float = u * 2.2f) {
        chamfer(r, k)
        fill.color = 0x55000000
        c.save(); c.translate(0f, u * 0.7f); c.drawPath(path, fill); c.restore()
        fill.color = fillCol
        c.drawPath(path, fill)
        c.save(); c.clipPath(path)
        shadeRect(c, panelSh, r.left, r.top, r.right, r.bottom, 0f, r.top, 1f, max(1f, r.bottom - r.top))
        c.restore()
        stroke.color = edge; stroke.strokeWidth = u * 0.22f
        c.drawPath(path, stroke)
        // corner ticks
        stroke.color = alphaF(edge or 0xFF000000.toInt(), 0.9f); stroke.strokeWidth = u * 0.45f
        c.drawLine(r.right - u * 3f, r.top, r.right, r.top, stroke)
        c.drawLine(r.right, r.top, r.right, r.top + u * 3f, stroke)
        c.drawLine(r.left, r.bottom - u * 3f, r.left, r.bottom, stroke)
        c.drawLine(r.left, r.bottom, r.left + u * 3f, r.bottom, stroke)
    }

    fun bar(c: Canvas, l: Float, t: Float, w: Float, h: Float, f: Float, col: Int, back: Int = 0x66000000) {
        fill.color = back
        c.drawRect(l, t, l + w, t + h, fill)
        fill.color = col
        c.drawRect(l, t, l + w * clamp01(f), t + h, fill)
    }

    fun hexagon(c: Canvas, x: Float, y: Float, r: Float, p: Paint, rot: Float = 0f) {
        path.reset()
        for (k in 0 until 6) {
            val a = rot + k * PI.toFloat() / 3f
            val px = x + cos(a) * r; val py = y + sin(a) * r
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, p)
    }
}

object Icon {
    const val PAUSE = 1; const val PLAY = 2; const val HOME = 3; const val RETRY = 4; const val SOUND = 5; const val MUSIC = 6
    const val VIBE = 7; const val CLOSE = 8; const val CORE = 9; const val LOCK = 10; const val CHECK = 11
    const val CANNON = 12; const val MISSILE = 13; const val LANCE = 14; const val DRONE = 15; const val HULL = 16; const val MAGNET = 17
    const val OVERDRIVE = 18; const val MAP = 19; const val WRENCH = 20; const val RANK = 21; const val GEAR = 22
    const val SWEEP = 23; const val LIFELINE = 24; const val UNTOUCHED = 25; const val ACE = 26; const val BACK = 27; const val ARROW = 28
    private val p = Path()

    fun draw(c: Canvas, id: Int, x: Float, y: Float, s: Float, col: Int, off: Boolean = false) {
        val f = Draw.fill; val st = Draw.stroke
        f.color = col; st.color = col; st.strokeWidth = s * 0.09f
        when (id) {
            PAUSE -> { c.drawRect(x - s * 0.24f, y - s * 0.28f, x - s * 0.08f, y + s * 0.28f, f); c.drawRect(x + s * 0.08f, y - s * 0.28f, x + s * 0.24f, y + s * 0.28f, f) }
            PLAY -> { p.reset(); p.moveTo(x - s * 0.18f, y - s * 0.3f); p.lineTo(x + s * 0.3f, y); p.lineTo(x - s * 0.18f, y + s * 0.3f); p.close(); c.drawPath(p, f) }
            HOME -> {
                p.reset(); p.moveTo(x - s * 0.32f, y - s * 0.02f); p.lineTo(x, y - s * 0.3f); p.lineTo(x + s * 0.32f, y - s * 0.02f); c.drawPath(p, st)
                c.drawRect(x - s * 0.22f, y - s * 0.06f, x + s * 0.22f, y + s * 0.3f, st)
            }
            RETRY -> {
                Draw.r2.set(x - s * 0.28f, y - s * 0.28f, x + s * 0.28f, y + s * 0.28f); c.drawArc(Draw.r2, -60f, 300f, false, st)
                val a = Math.toRadians(-60.0); val ax = x + (cos(a) * s * 0.28f).toFloat(); val ay = y + (sin(a) * s * 0.28f).toFloat()
                p.reset(); p.moveTo(ax + s * 0.02f, ay - s * 0.2f); p.lineTo(ax + s * 0.16f, ay + s * 0.06f); p.lineTo(ax - s * 0.12f, ay + s * 0.06f); p.close(); c.drawPath(p, f)
            }
            SOUND -> {
                p.reset(); p.moveTo(x - s * 0.32f, y - s * 0.1f); p.lineTo(x - s * 0.16f, y - s * 0.1f); p.lineTo(x + s * 0.04f, y - s * 0.28f)
                p.lineTo(x + s * 0.04f, y + s * 0.28f); p.lineTo(x - s * 0.16f, y + s * 0.1f); p.lineTo(x - s * 0.32f, y + s * 0.1f); p.close(); c.drawPath(p, f)
                if (!off) { Draw.r2.set(x - s * 0.08f, y - s * 0.2f, x + s * 0.28f, y + s * 0.2f); c.drawArc(Draw.r2, -50f, 100f, false, st) }
                else { st.strokeWidth = s * 0.08f; c.drawLine(x + s * 0.12f, y - s * 0.14f, x + s * 0.36f, y + s * 0.14f, st); c.drawLine(x + s * 0.36f, y - s * 0.14f, x + s * 0.12f, y + s * 0.14f, st) }
            }
            MUSIC -> {
                c.drawCircle(x - s * 0.14f, y + s * 0.2f, s * 0.11f, f); c.drawCircle(x + s * 0.2f, y + s * 0.13f, s * 0.11f, f)
                c.drawLine(x - s * 0.04f, y + s * 0.2f, x - s * 0.04f, y - s * 0.28f, st); c.drawLine(x + s * 0.3f, y + s * 0.13f, x + s * 0.3f, y - s * 0.34f, st)
                st.strokeWidth = s * 0.13f; c.drawLine(x - s * 0.04f, y - s * 0.26f, x + s * 0.3f, y - s * 0.33f, st)
            }
            VIBE -> {
                c.drawRoundRect(x - s * 0.13f, y - s * 0.26f, x + s * 0.13f, y + s * 0.26f, s * 0.05f, s * 0.05f, st)
                if (!off) { c.drawLine(x - s * 0.26f, y - s * 0.12f, x - s * 0.26f, y + s * 0.12f, st); c.drawLine(x + s * 0.26f, y - s * 0.12f, x + s * 0.26f, y + s * 0.12f, st) }
            }
            CLOSE -> { c.drawLine(x - s * 0.22f, y - s * 0.22f, x + s * 0.22f, y + s * 0.22f, st); c.drawLine(x + s * 0.22f, y - s * 0.22f, x - s * 0.22f, y + s * 0.22f, st) }
            CORE -> {
                f.color = col; Draw.hexagon(c, x, y, s * 0.32f, f, PI.toFloat() / 6f)
                f.color = 0x88FFFFFF.toInt(); Draw.hexagon(c, x - s * 0.04f, y - s * 0.05f, s * 0.14f, f, PI.toFloat() / 6f)
            }
            LOCK -> {
                c.drawRoundRect(x - s * 0.22f, y - s * 0.04f, x + s * 0.22f, y + s * 0.3f, s * 0.05f, s * 0.05f, f)
                Draw.r2.set(x - s * 0.14f, y - s * 0.3f, x + s * 0.14f, y + s * 0.02f); c.drawArc(Draw.r2, 180f, 180f, false, st)
                c.drawLine(x - s * 0.14f, y - s * 0.14f, x - s * 0.14f, y - s * 0.04f, st); c.drawLine(x + s * 0.14f, y - s * 0.14f, x + s * 0.14f, y - s * 0.04f, st)
            }
            CHECK -> { st.strokeWidth = s * 0.12f; c.drawLine(x - s * 0.24f, y, x - s * 0.06f, y + s * 0.18f, st); c.drawLine(x - s * 0.06f, y + s * 0.18f, x + s * 0.26f, y - s * 0.2f, st) }
            CANNON -> {
                c.drawRect(x - s * 0.2f, y - s * 0.3f, x - s * 0.08f, y + s * 0.18f, f); c.drawRect(x + s * 0.08f, y - s * 0.3f, x + s * 0.2f, y + s * 0.18f, f)
                c.drawRoundRect(x - s * 0.3f, y + s * 0.08f, x + s * 0.3f, y + s * 0.32f, s * 0.06f, s * 0.06f, f)
            }
            MISSILE -> {
                c.save(); c.rotate(35f, x, y)
                c.drawRoundRect(x - s * 0.08f, y - s * 0.32f, x + s * 0.08f, y + s * 0.2f, s * 0.08f, s * 0.08f, f)
                p.reset(); p.moveTo(x - s * 0.08f, y + s * 0.08f); p.lineTo(x - s * 0.2f, y + s * 0.3f); p.lineTo(x + s * 0.2f, y + s * 0.3f); p.lineTo(x + s * 0.08f, y + s * 0.08f); p.close(); c.drawPath(p, f)
                c.restore()
            }
            LANCE -> {
                p.reset(); p.moveTo(x + s * 0.08f, y - s * 0.36f); p.lineTo(x - s * 0.2f, y + s * 0.04f); p.lineTo(x - s * 0.01f, y + s * 0.04f)
                p.lineTo(x - s * 0.1f, y + s * 0.36f); p.lineTo(x + s * 0.22f, y - s * 0.08f); p.lineTo(x + s * 0.02f, y - s * 0.08f); p.close(); c.drawPath(p, f)
            }
            DRONE -> {
                c.drawCircle(x, y, s * 0.12f, f)
                c.drawCircle(x - s * 0.26f, y - s * 0.14f, s * 0.09f, st); c.drawCircle(x + s * 0.26f, y - s * 0.14f, s * 0.09f, st)
                c.drawCircle(x - s * 0.26f, y + s * 0.18f, s * 0.09f, st); c.drawCircle(x + s * 0.26f, y + s * 0.18f, s * 0.09f, st)
                c.drawLine(x - s * 0.2f, y - s * 0.1f, x + s * 0.2f, y + s * 0.14f, st); c.drawLine(x + s * 0.2f, y - s * 0.1f, x - s * 0.2f, y + s * 0.14f, st)
            }
            HULL -> {
                p.reset(); p.moveTo(x, y - s * 0.34f); p.lineTo(x + s * 0.28f, y - s * 0.22f); p.lineTo(x + s * 0.24f, y + s * 0.1f)
                p.lineTo(x, y + s * 0.34f); p.lineTo(x - s * 0.24f, y + s * 0.1f); p.lineTo(x - s * 0.28f, y - s * 0.22f); p.close(); c.drawPath(p, f)
            }
            MAGNET -> {
                st.strokeWidth = s * 0.14f; st.strokeCap = Paint.Cap.BUTT
                Draw.r2.set(x - s * 0.24f, y - s * 0.24f, x + s * 0.24f, y + s * 0.24f); c.drawArc(Draw.r2, 180f, 180f, false, st)
                c.drawLine(x - s * 0.24f, y, x - s * 0.24f, y + s * 0.3f, st); c.drawLine(x + s * 0.24f, y, x + s * 0.24f, y + s * 0.3f, st)
                st.strokeCap = Paint.Cap.ROUND
            }
            OVERDRIVE -> {
                p.reset(); p.moveTo(x, y - s * 0.34f); p.lineTo(x + s * 0.3f, y + s * 0.24f); p.lineTo(x - s * 0.3f, y + s * 0.24f); p.close(); c.drawPath(p, st)
                c.drawLine(x - s * 0.4f, y - s * 0.02f, x - s * 0.08f, y + s * 0.02f, st)
                f.color = col; c.drawCircle(x, y + s * 0.06f, s * 0.08f, f)
            }
            MAP -> {
                p.reset(); p.moveTo(x - s * 0.32f, y - s * 0.22f); p.lineTo(x - s * 0.1f, y - s * 0.3f); p.lineTo(x + s * 0.1f, y - s * 0.22f)
                p.lineTo(x + s * 0.32f, y - s * 0.3f); p.lineTo(x + s * 0.32f, y + s * 0.22f); p.lineTo(x + s * 0.1f, y + s * 0.3f)
                p.lineTo(x - s * 0.1f, y + s * 0.22f); p.lineTo(x - s * 0.32f, y + s * 0.3f); p.close(); c.drawPath(p, st)
                c.drawLine(x - s * 0.1f, y - s * 0.3f, x - s * 0.1f, y + s * 0.22f, st); c.drawLine(x + s * 0.1f, y - s * 0.22f, x + s * 0.1f, y + s * 0.3f, st)
            }
            WRENCH -> {
                st.strokeWidth = s * 0.13f
                c.drawLine(x - s * 0.22f, y + s * 0.22f, x + s * 0.08f, y - s * 0.08f, st)
                Draw.r2.set(x - s * 0.02f, y - s * 0.36f, x + s * 0.34f, y); c.drawArc(Draw.r2, 120f, 280f, false, st)
            }
            RANK -> {
                st.strokeWidth = s * 0.11f
                for (k in 0 until 3) {
                    val yy = y - s * 0.2f + k * s * 0.18f
                    c.drawLine(x - s * 0.28f, yy, x, yy + s * 0.14f, st); c.drawLine(x, yy + s * 0.14f, x + s * 0.28f, yy, st)
                }
            }
            GEAR -> {
                for (k in 0 until 8) {
                    c.save(); c.rotate(k * 45f, x, y); c.drawRect(x - s * 0.06f, y - s * 0.36f, x + s * 0.06f, y - s * 0.2f, f); c.restore()
                }
                st.strokeWidth = s * 0.1f; c.drawCircle(x, y, s * 0.2f, st)
            }
            SWEEP -> {
                c.drawCircle(x, y, s * 0.24f, st)
                c.drawLine(x, y - s * 0.38f, x, y - s * 0.12f, st); c.drawLine(x, y + s * 0.12f, x, y + s * 0.38f, st)
                c.drawLine(x - s * 0.38f, y, x - s * 0.12f, y, st); c.drawLine(x + s * 0.12f, y, x + s * 0.38f, y, st)
                c.drawCircle(x, y, s * 0.05f, f)
            }
            LIFELINE -> {
                c.drawCircle(x, y - s * 0.18f, s * 0.1f, f)
                p.reset(); p.moveTo(x - s * 0.16f, y + s * 0.26f); p.lineTo(x - s * 0.12f, y - s * 0.02f); p.lineTo(x + s * 0.12f, y - s * 0.02f); p.lineTo(x + s * 0.16f, y + s * 0.26f); p.close(); c.drawPath(p, f)
                Draw.r2.set(x - s * 0.36f, y - s * 0.36f, x + s * 0.36f, y + s * 0.36f); c.drawArc(Draw.r2, 200f, 140f, false, st)
            }
            UNTOUCHED -> {
                p.reset(); p.moveTo(x, y - s * 0.34f); p.lineTo(x + s * 0.28f, y - s * 0.22f); p.lineTo(x + s * 0.24f, y + s * 0.1f)
                p.lineTo(x, y + s * 0.34f); p.lineTo(x - s * 0.24f, y + s * 0.1f); p.lineTo(x - s * 0.28f, y - s * 0.22f); p.close(); c.drawPath(p, st)
                st.strokeWidth = s * 0.1f; c.drawLine(x - s * 0.12f, y, x - s * 0.02f, y + s * 0.1f, st); c.drawLine(x - s * 0.02f, y + s * 0.1f, x + s * 0.14f, y - s * 0.1f, st)
            }
            ACE -> {
                p.reset(); p.moveTo(x, y - s * 0.3f); p.lineTo(x + s * 0.09f, y - s * 0.09f); p.lineTo(x + s * 0.4f, y - s * 0.02f); p.lineTo(x + s * 0.12f, y + s * 0.08f)
                p.lineTo(x, y + s * 0.32f); p.lineTo(x - s * 0.12f, y + s * 0.08f); p.lineTo(x - s * 0.4f, y - s * 0.02f); p.lineTo(x - s * 0.09f, y - s * 0.09f); p.close(); c.drawPath(p, f)
            }
            BACK -> { st.strokeWidth = s * 0.12f; c.drawLine(x + s * 0.1f, y - s * 0.24f, x - s * 0.14f, y, st); c.drawLine(x - s * 0.14f, y, x + s * 0.1f, y + s * 0.24f, st) }
            ARROW -> { st.strokeWidth = s * 0.12f; c.drawLine(x - s * 0.1f, y - s * 0.24f, x + s * 0.14f, y, st); c.drawLine(x + s * 0.14f, y, x - s * 0.1f, y + s * 0.24f, st) }
        }
        if (off && id != SOUND && id != VIBE) { st.strokeWidth = s * 0.08f; c.drawLine(x - s * 0.34f, y - s * 0.34f, x + s * 0.34f, y + s * 0.34f, st) }
    }
}
