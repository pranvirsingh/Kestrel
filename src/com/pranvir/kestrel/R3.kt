package com.pranvir.kestrel

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Surface description for the 3D sprite renderer. Colours are ARGB ints. */
class Mat(
    val albedo: Int,
    val spec: Float = 0.5f,
    val shine: Float = 24f,
    val rim: Float = 0.35f,
    val emissive: Int = 0,
    val grime: Float = 0.35f,
    val glass: Boolean = false,
    val panels: Boolean = false
)

/**
 * Triangle soup with per-vertex normals, built by a small modelling toolkit (lofts, wings, boxes,
 * cylinders, ellipsoids) under a transform stack. Model space: x right, y forward (nose), z up.
 */
class Mesh {
    var count = 0
    var p = FloatArray(9 * 512)
    var n = FloatArray(9 * 512)
    var m = IntArray(512)
    val mats = ArrayList<Mat>()

    // transform stack: 3x4 matrices
    private val stack = ArrayList<FloatArray>()
    private var cur = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun mat(mt: Mat): Int { val i = mats.indexOf(mt); if (i >= 0) return i; mats.add(mt); return mats.size - 1 }

    fun push() { stack.add(cur.copyOf()) }
    fun pop() { cur = stack.removeAt(stack.size - 1) }
    private fun mul(b: FloatArray) {
        val a = cur
        val r = FloatArray(12)
        for (i in 0 until 3) {
            for (j in 0 until 3) r[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j]
            r[i * 4 + 3] = a[i * 4] * b[3] + a[i * 4 + 1] * b[7] + a[i * 4 + 2] * b[11] + a[i * 4 + 3]
        }
        cur = r
    }
    fun translate(x: Float, y: Float, z: Float) = mul(floatArrayOf(1f, 0f, 0f, x, 0f, 1f, 0f, y, 0f, 0f, 1f, z))
    fun rotZ(a: Float) { val c = cos(a); val s = sin(a); mul(floatArrayOf(c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f, 0f)) }
    fun rotX(a: Float) { val c = cos(a); val s = sin(a); mul(floatArrayOf(1f, 0f, 0f, 0f, 0f, c, -s, 0f, 0f, s, c, 0f)) }
    fun rotY(a: Float) { val c = cos(a); val s = sin(a); mul(floatArrayOf(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f)) }
    fun scale(x: Float, y: Float, z: Float) = mul(floatArrayOf(x, 0f, 0f, 0f, 0f, y, 0f, 0f, 0f, 0f, z, 0f))

    private fun grow() {
        p = p.copyOf(p.size * 2); n = n.copyOf(n.size * 2); m = m.copyOf(m.size * 2)
    }

    private val tv = FloatArray(3)
    private fun xp(x: Float, y: Float, z: Float) {
        val c = cur
        tv[0] = c[0] * x + c[1] * y + c[2] * z + c[3]
        tv[1] = c[4] * x + c[5] * y + c[6] * z + c[7]
        tv[2] = c[8] * x + c[9] * y + c[10] * z + c[11]
    }
    private fun xn(x: Float, y: Float, z: Float) {
        // normals: rotation part is enough for our uniform-ish scales; renormalise
        val c = cur
        var a = c[0] * x + c[1] * y + c[2] * z
        var b = c[4] * x + c[5] * y + c[6] * z
        var d = c[8] * x + c[9] * y + c[10] * z
        val l = sqrt(a * a + b * b + d * d).coerceAtLeast(1e-6f)
        a /= l; b /= l; d /= l
        tv[0] = a; tv[1] = b; tv[2] = d
    }

    /** One triangle: 3 positions + 3 normals (model space, transformed by the current matrix). */
    fun tri(v: FloatArray, nn: FloatArray, mt: Int) {
        if (count * 9 + 9 > p.size) grow()
        val o = count * 9
        for (k in 0 until 3) {
            xp(v[k * 3], v[k * 3 + 1], v[k * 3 + 2]); p[o + k * 3] = tv[0]; p[o + k * 3 + 1] = tv[1]; p[o + k * 3 + 2] = tv[2]
            xn(nn[k * 3], nn[k * 3 + 1], nn[k * 3 + 2]); n[o + k * 3] = tv[0]; n[o + k * 3 + 1] = tv[1]; n[o + k * 3 + 2] = tv[2]
        }
        m[count] = mt
        count++
    }

    private val tvv = FloatArray(9); private val tnn = FloatArray(9)
    fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
             na: FloatArray, nb: FloatArray, nc: FloatArray, nd: FloatArray, mt: Int) {
        tvv[0] = ax; tvv[1] = ay; tvv[2] = az; tvv[3] = bx; tvv[4] = by; tvv[5] = bz; tvv[6] = cx; tvv[7] = cy; tvv[8] = cz
        System.arraycopy(na, 0, tnn, 0, 3); System.arraycopy(nb, 0, tnn, 3, 3); System.arraycopy(nc, 0, tnn, 6, 3)
        tri(tvv, tnn, mt)
        tvv[0] = ax; tvv[1] = ay; tvv[2] = az; tvv[3] = cx; tvv[4] = cy; tvv[5] = cz; tvv[6] = dx; tvv[7] = dy; tvv[8] = dz
        System.arraycopy(na, 0, tnn, 0, 3); System.arraycopy(nc, 0, tnn, 3, 3); System.arraycopy(nd, 0, tnn, 6, 3)
        tri(tvv, tnn, mt)
    }

    private fun flatN(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float): FloatArray {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= l; ny /= l; nz /= l
        return floatArrayOf(nx, ny, nz)
    }

    fun flatQuad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, mt: Int) {
        val nn = flatN(ax, ay, az, bx, by, bz, cx, cy, cz)
        quad(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, nn, nn, nn, nn, mt)
    }

    // ------------------------------------------------------------------ primitives

    /** Axis-aligned box centred at (x, y, z), with an optional bevel that rounds the normals at the edges. */
    fun box(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, mt: Int) {
        val x0 = x - sx / 2; val x1 = x + sx / 2; val y0 = y - sy / 2; val y1 = y + sy / 2; val z0 = z - sz / 2; val z1 = z + sz / 2
        flatQuad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, mt)   // top
        flatQuad(x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, mt)   // bottom
        flatQuad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, mt)   // back (-y)
        flatQuad(x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1, mt)   // front (+y)
        flatQuad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, mt)   // left
        flatQuad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, mt)   // right
    }

    /** Frustum between two points along any axis, with smooth sides and flat caps. */
    fun cyl(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r0: Float, r1: Float, seg: Int, mt: Int, caps: Boolean = true) {
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
        dx /= len; dy /= len; dz /= len
        // orthonormal basis
        var ux: Float; var uy: Float; var uz: Float
        if (abs(dz) < 0.9f) { ux = dy; uy = -dx; uz = 0f } else { ux = 0f; uy = dz; uz = -dy }
        val ul = sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul
        val vx = dy * uz - dz * uy; val vy = dz * ux - dx * uz; val vz = dx * uy - dy * ux
        val slope = (r0 - r1) / len
        for (i in 0 until seg) {
            val a0 = i * 2f * PI.toFloat() / seg; val a1 = (i + 1) * 2f * PI.toFloat() / seg
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val n0x = ux * c0 + vx * s0; val n0y = uy * c0 + vy * s0; val n0z = uz * c0 + vz * s0
            val n1x = ux * c1 + vx * s1; val n1y = uy * c1 + vy * s1; val n1z = uz * c1 + vz * s1
            val na = floatArrayOf(n0x + dx * slope, n0y + dy * slope, n0z + dz * slope)
            val nb = floatArrayOf(n1x + dx * slope, n1y + dy * slope, n1z + dz * slope)
            quad(ax + n0x * r0, ay + n0y * r0, az + n0z * r0, ax + n1x * r0, ay + n1y * r0, az + n1z * r0,
                bx + n1x * r1, by + n1y * r1, bz + n1z * r1, bx + n0x * r1, by + n0y * r1, bz + n0z * r1, na, nb, nb, na, mt)
            if (caps) {
                val nbk = floatArrayOf(-dx, -dy, -dz); val nfw = floatArrayOf(dx, dy, dz)
                tri(floatArrayOf(ax, ay, az, ax + n1x * r0, ay + n1y * r0, az + n1z * r0, ax + n0x * r0, ay + n0y * r0, az + n0z * r0), nbk + nbk + nbk, mt)
                tri(floatArrayOf(bx, by, bz, bx + n0x * r1, by + n0y * r1, bz + n0z * r1, bx + n1x * r1, by + n1y * r1, bz + n1z * r1), nfw + nfw + nfw, mt)
            }
        }
    }

    fun ellipsoid(x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float, seg: Int, mt: Int) {
        val rings = max(4, seg / 2)
        for (i in 0 until rings) {
            val t0 = PI.toFloat() * i / rings - PI.toFloat() / 2; val t1 = PI.toFloat() * (i + 1) / rings - PI.toFloat() / 2
            for (j in 0 until seg) {
                val a0 = 2f * PI.toFloat() * j / seg; val a1 = 2f * PI.toFloat() * (j + 1) / seg
                fun pt(t: Float, a: Float, out: FloatArray, o: Int) { out[o] = cos(t) * cos(a); out[o + 1] = cos(t) * sin(a); out[o + 2] = sin(t) }
                val u = FloatArray(12)
                pt(t0, a0, u, 0); pt(t0, a1, u, 3); pt(t1, a1, u, 6); pt(t1, a0, u, 9)
                val nrm = Array(4) { k -> val nx = u[k * 3] / rx; val ny = u[k * 3 + 1] / ry; val nz = u[k * 3 + 2] / rz; val l = sqrt(nx * nx + ny * ny + nz * nz); floatArrayOf(nx / l, ny / l, nz / l) }
                quad(x + u[0] * rx, y + u[1] * ry, z + u[2] * rz, x + u[3] * rx, y + u[4] * ry, z + u[5] * rz,
                    x + u[6] * rx, y + u[7] * ry, z + u[8] * rz, x + u[9] * rx, y + u[10] * ry, z + u[11] * rz, nrm[0], nrm[1], nrm[2], nrm[3], mt)
            }
        }
    }

    /**
     * Lofted body along y. Each section: y, centre-x, centre-z, half-width, top half-height, bottom half-height.
     * Normals come from the surface itself so the hull reads smooth.
     */
    fun loft(sec: Array<FloatArray>, seg: Int, mt: Int, capBack: Boolean = true, capFront: Boolean = false) {
        val ns = sec.size
        val P = Array(ns) { Array(seg) { FloatArray(3) } }
        for (i in 0 until ns) {
            val s = sec[i]
            for (j in 0 until seg) {
                val a = 2f * PI.toFloat() * j / seg
                val c = cos(a); val sn = sin(a)
                P[i][j][0] = s[1] + s[3] * c
                P[i][j][1] = s[0]
                P[i][j][2] = s[2] + (if (sn >= 0f) s[4] else s[5]) * sn
            }
        }
        val N = Array(ns) { Array(seg) { FloatArray(3) } }
        for (i in 0 until ns) for (j in 0 until seg) {
            val ip = min(ns - 1, i + 1); val im = max(0, i - 1)
            val jp = (j + 1) % seg; val jm = (j - 1 + seg) % seg
            val tx = P[i][jp][0] - P[i][jm][0]; val ty = P[i][jp][1] - P[i][jm][1]; val tz = P[i][jp][2] - P[i][jm][2]
            val lx = P[ip][j][0] - P[im][j][0]; val ly = P[ip][j][1] - P[im][j][1]; val lz = P[ip][j][2] - P[im][j][2]
            var nx = ty * lz - tz * ly; var ny = tz * lx - tx * lz; var nz = tx * ly - ty * lx
            // make it point outwards from the section centre
            val ox = P[i][j][0] - sec[i][1]; val oz = P[i][j][2] - sec[i][2]
            if (nx * ox + nz * oz < 0f) { nx = -nx; ny = -ny; nz = -nz }
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            N[i][j][0] = nx / l; N[i][j][1] = ny / l; N[i][j][2] = nz / l
        }
        for (i in 0 until ns - 1) for (j in 0 until seg) {
            val jn = (j + 1) % seg
            val a = P[i][j]; val b = P[i][jn]; val c = P[i + 1][jn]; val d = P[i + 1][j]
            quad(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], N[i][j], N[i][jn], N[i + 1][jn], N[i + 1][j], mt)
        }
        if (capBack) { val s = sec[0]; for (j in 0 until seg) { val a = P[0][j]; val b = P[0][(j + 1) % seg]; tri(floatArrayOf(s[1], s[0], s[2], b[0], b[1], b[2], a[0], a[1], a[2]), floatArrayOf(0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f), mt) } }
        if (capFront) { val s = sec[ns - 1]; for (j in 0 until seg) { val a = P[ns - 1][j]; val b = P[ns - 1][(j + 1) % seg]; tri(floatArrayOf(s[1], s[0], s[2], a[0], a[1], a[2], b[0], b[1], b[2]), floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f), mt) } }
    }

    /**
     * A wing (or fin / plate): convex planform given as (x, y) pairs, extruded with a thickness that tapers
     * towards the tips. Top normals lean outwards near the edges, which reads as a rounded airfoil.
     */
    fun wing(pts: FloatArray, z: Float, thick: Float, mt: Int, mirror: Boolean = true, tipThin: Float = 0.5f, span: Float = 0f) {
        for (side in if (mirror) intArrayOf(1, -1) else intArrayOf(1)) {
            val k = pts.size / 2
            val xs = FloatArray(k) { pts[it * 2] * side }; val ys = FloatArray(k) { pts[it * 2 + 1] }
            val maxX = if (span > 0f) span else xs.maxOf { abs(it) }.coerceAtLeast(1f)
            fun th(x: Float) = thick * (1f - (1f - tipThin) * clamp01(abs(x) / maxX)) * 0.5f
            var cx = 0f; var cy = 0f
            for (i in 0 until k) { cx += xs[i]; cy += ys[i] }
            cx /= k; cy /= k
            val up = floatArrayOf(0f, 0f, 1f); val dn = floatArrayOf(0f, 0f, -1f)
            for (i in 0 until k) {
                val j = (i + 1) % k
                var ax = xs[i]; var ay = ys[i]; var bx = xs[j]; var by = ys[j]
                // edge outward normal in plane
                var ex = by - ay; var ey = -(bx - ax)
                val el = sqrt(ex * ex + ey * ey).coerceAtLeast(1e-6f); ex /= el; ey /= el
                if ((ax - cx) * ex + (ay - cy) * ey < 0f) { ex = -ex; ey = -ey }
                val windCcw = side > 0
                // top fan + bottom fan through the centroid (planforms are convex)
                val tc = th(cx)
                val nTopEdge = floatArrayOf(ex * 0.12f, ey * 0.12f, 0.99f)
                val nBotEdge = floatArrayOf(ex * 0.12f, ey * 0.12f, -0.99f)
                if (windCcw xor ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) > 0f)) { val t = ax; ax = bx; bx = t; val u = ay; ay = by; by = u }
                val tA = th(ax); val tB = th(bx)
                tri(floatArrayOf(cx, cy, z + tc, ax, ay, z + tA, bx, by, z + tB), up + nTopEdge + nTopEdge, mt)
                tri(floatArrayOf(cx, cy, z - tc, bx, by, z - tB, ax, ay, z - tA), dn + nBotEdge + nBotEdge, mt)
                val ns = floatArrayOf(ex, ey, 0f)
                quad(ax, ay, z - tA, bx, by, z - tB, bx, by, z + tB, ax, ay, z + tA, ns, ns, ns, ns, mt)
            }
        }
    }
}

/**
 * Software rasteriser for pre-rendered sprites: tilted orthographic camera (we look north and down,
 * like a chase camera), deferred Blinn-Phong with hemisphere ambient, fresnel rim, procedural grime and
 * panel seams, screen-space ambient occlusion and supersampling.
 */
object R3 {
    var pitch = 0.5f   // ~29 degrees
    // sun from the north-west, high: matches the terrain hill-shading and the ground shadows
    private val L = norm(-0.5f, 0.55f, 0.67f)
    private val SKY = floatArrayOf(0.62f, 0.72f, 0.88f)
    private val BOUNCE = floatArrayOf(0.42f, 0.36f, 0.3f)

    private fun norm(x: Float, y: Float, z: Float): FloatArray { val l = sqrt(x * x + y * y + z * z); return floatArrayOf(x / l, y / l, z / l) }

    class Out(val w: Int, val h: Int, val color: IntArray, val shadowCov: IntArray?, val sw: Int, val sh: Int, val halfW: Float, val halfH: Float, val shHalfW: Float, val shHalfH: Float)

    /**
     * Render [mesh] rotated by yaw (about z, radians, 0 = nose up the screen) and roll (about the nose axis)
     * at [scale] pixels per world unit. Returns the colour sprite plus a straight-down silhouette for the ground shadow.
     */
    fun render(mesh: Mesh, scale: Float, yaw: Float = 0f, roll: Float = 0f, ss0: Int = 2, withShadow: Boolean = true, pitchOverride: Float = pitch): Out {
        val cp = cos(pitchOverride); val sp = sin(pitchOverride)
        val cyw = cos(yaw); val syw = sin(yaw); val cr = cos(roll); val sr = sin(roll)
        val nT = mesh.count
        // transform to world (roll about y, then yaw about z)
        val wp = FloatArray(nT * 9); val wn = FloatArray(nT * 9)
        for (i in 0 until nT * 3) {
            for (q in 0..1) {
                val src = if (q == 0) mesh.p else mesh.n
                val dst = if (q == 0) wp else wn
                val x = src[i * 3]; val y = src[i * 3 + 1]; val z = src[i * 3 + 2]
                val rx = x * cr + z * sr; val rz = -x * sr + z * cr
                dst[i * 3] = rx * cyw - y * syw
                dst[i * 3 + 1] = rx * syw + y * cyw
                dst[i * 3 + 2] = rz
            }
        }
        // bounds in screen space
        var hw = 1f; var hh = 1f
        for (i in 0 until nT * 3) {
            val x = wp[i * 3]; val y = wp[i * 3 + 1]; val z = wp[i * 3 + 2]
            hw = max(hw, abs(x)); hh = max(hh, abs(y * cp + z * sp))
        }
        hw += 3f; hh += 3f
        var ss = ss0
        if (hw * hh * 4f * scale * scale * ss * ss > 2_400_000f) ss = 1
        val k = scale * ss
        val W = ceil(hw * 2f * k).toInt().coerceAtLeast(2); val H = ceil(hh * 2f * k).toInt().coerceAtLeast(2)
        val depth = FloatArray(W * H) { -1e9f }
        val gN = FloatArray(W * H * 3)
        val gP = FloatArray(W * H * 3)
        val gM = IntArray(W * H) { -1 }
        val sx = FloatArray(3); val sy = FloatArray(3); val sd = FloatArray(3)
        for (t in 0 until nT) {
            val o = t * 9
            for (v in 0 until 3) {
                val x = wp[o + v * 3]; val y = wp[o + v * 3 + 1]; val z = wp[o + v * 3 + 2]
                sx[v] = W / 2f + x * k
                sy[v] = H / 2f - (y * cp + z * sp) * k
                sd[v] = -y * sp + z * cp
            }
            val area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
            if (abs(area) < 1e-6f) continue
            val x0 = max(0, floor(min(sx[0], min(sx[1], sx[2]))).toInt()); val x1 = min(W - 1, ceil(max(sx[0], max(sx[1], sx[2]))).toInt())
            val y0 = max(0, floor(min(sy[0], min(sy[1], sy[2]))).toInt()); val y1 = min(H - 1, ceil(max(sy[0], max(sy[1], sy[2]))).toInt())
            val inv = 1f / area
            for (py in y0..y1) {
                val fy = py + 0.5f
                for (px in x0..x1) {
                    val fx = px + 0.5f
                    val w0 = ((sx[1] - fx) * (sy[2] - fy) - (sx[2] - fx) * (sy[1] - fy)) * inv
                    val w1 = ((sx[2] - fx) * (sy[0] - fy) - (sx[0] - fx) * (sy[2] - fy)) * inv
                    val w2 = 1f - w0 - w1
                    if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) continue
                    val d = w0 * sd[0] + w1 * sd[1] + w2 * sd[2]
                    val idx = px + py * W
                    if (d <= depth[idx]) continue
                    depth[idx] = d
                    var nx = w0 * wn[o] + w1 * wn[o + 3] + w2 * wn[o + 6]
                    var ny = w0 * wn[o + 1] + w1 * wn[o + 4] + w2 * wn[o + 7]
                    var nz = w0 * wn[o + 2] + w1 * wn[o + 5] + w2 * wn[o + 8]
                    val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                    gN[idx * 3] = nx / nl; gN[idx * 3 + 1] = ny / nl; gN[idx * 3 + 2] = nz / nl
                    gP[idx * 3] = w0 * mesh.p[o] + w1 * mesh.p[o + 3] + w2 * mesh.p[o + 6]
                    gP[idx * 3 + 1] = w0 * mesh.p[o + 1] + w1 * mesh.p[o + 4] + w2 * mesh.p[o + 7]
                    gP[idx * 3 + 2] = w0 * mesh.p[o + 2] + w1 * mesh.p[o + 5] + w2 * mesh.p[o + 8]
                    gM[idx] = mesh.m[t]
                }
            }
        }
        // shade
        val V = floatArrayOf(0f, -sp, cp)
        val Hx = L[0] + V[0]; val Hy = L[1] + V[1]; val Hz = L[2] + V[2]
        val hl = sqrt(Hx * Hx + Hy * Hy + Hz * Hz)
        val hx = Hx / hl; val hy = Hy / hl; val hz = Hz / hl
        val rgb = FloatArray(W * H * 3)
        val aoR = max(2, (3.2f * ss * scale).toInt())
        val mats = mesh.mats
        for (idx in 0 until W * H) {
            val mi = gM[idx]
            if (mi < 0) continue
            val mt = mats[mi]
            val nx = gN[idx * 3]; val ny = gN[idx * 3 + 1]; val nz = gN[idx * 3 + 2]
            val px = idx % W; val py = idx / W
            // screen-space ambient occlusion: closer neighbours above us darken creases
            val d0 = depth[idx]
            var occ = 0f
            for (q in 0 until 8) {
                val a = q * 0.785f
                val qx = px + (cos(a) * aoR).toInt(); val qy = py + (sin(a) * aoR).toInt()
                if (qx < 0 || qy < 0 || qx >= W || qy >= H) continue
                val dd = depth[qx + qy * W] - d0
                if (dd > 0.6f) occ += min(1f, dd / 6f)
            }
            val ao = 1f - 0.55f * (occ / 8f)
            var edge = 1f
            if (px == 0 || py == 0 || px == W - 1 || py == H - 1 || gM[idx - 1] < 0 || gM[idx + 1] < 0 || gM[idx - W] < 0 || gM[idx + W] < 0) edge = 0.62f
            val ndl = nx * L[0] + ny * L[1] + nz * L[2]
            val diff = max(0f, ndl * 0.85f + 0.15f)
            val hemi = 0.5f + 0.5f * nz
            val ndv = max(0f, nx * V[0] + ny * V[1] + nz * V[2])
            val fres = (1f - ndv).pow(3f)
            val spec = max(0f, nx * hx + ny * hy + nz * hz).pow(mt.shine) * mt.spec
            // albedo with grime and panel seams
            var ar = ((mt.albedo shr 16) and 255) / 255f; var ag = ((mt.albedo shr 8) and 255) / 255f; var ab = (mt.albedo and 255) / 255f
            val mx = gP[idx * 3]; val my = gP[idx * 3 + 1]; val mz = gP[idx * 3 + 2]
            if (mt.grime > 0f) {
                val gr = Noise.fbm(mx * 0.09f + mz * 0.05f, my * 0.09f - mz * 0.04f, 3, 515)
                val streak = Noise.value(mx * 0.5f, my * 0.06f, 77)
                val g = 1f - mt.grime * (0.55f * smooth((gr - 0.45f) / 0.35f) + 0.25f * streak)
                ar *= g; ag *= g; ab *= g
            }
            if (mt.panels) {
                // irregular panel seams: rows every ~22 units, staggered columns per row
                val row = floor((my + 400f) / 22f)
                val fy = abs(((my + 400f) / 22f) % 1f - 0.5f)
                val colOff = Noise.hash(row.toInt(), 3, 9) * 30f
                val fx = abs(((mx + 400f + colOff) / 30f) % 1f - 0.5f)
                if (fy > 0.482f || fx > 0.488f) { ar *= 0.8f; ag *= 0.8f; ab *= 0.8f }
            }
            var r: Float; var g: Float; var b: Float
            if (mt.glass) {
                // dark glass reflecting a sky gradient
                val refl = clamp01(0.5f + 0.5f * (nz * 0.6f - ny * 0.8f))
                r = ar * 0.25f + lerp(0.08f, 0.75f, refl) * 0.6f; g = ag * 0.25f + lerp(0.1f, 0.85f, refl) * 0.6f; b = ab * 0.25f + lerp(0.18f, 1f, refl) * 0.6f
                r += spec * 1.4f + fres * 0.5f; g += spec * 1.4f + fres * 0.55f; b += spec * 1.4f + fres * 0.6f
            } else {
                val amb = 0.32f
                val lr = amb * lerp(BOUNCE[0], SKY[0], hemi) + diff * 1.02f
                val lg = amb * lerp(BOUNCE[1], SKY[1], hemi) + diff * 0.98f
                val lb = amb * lerp(BOUNCE[2], SKY[2], hemi) + diff * 0.9f
                r = ar * lr * ao + spec + fres * mt.rim * SKY[0] * 0.8f
                g = ag * lg * ao + spec + fres * mt.rim * SKY[1] * 0.8f
                b = ab * lb * ao + spec * 0.95f + fres * mt.rim * SKY[2] * 0.8f
            }
            if (mt.emissive != 0) {
                r += ((mt.emissive shr 16) and 255) / 255f; g += ((mt.emissive shr 8) and 255) / 255f; b += (mt.emissive and 255) / 255f
            }
            rgb[idx * 3] = r * edge; rgb[idx * 3 + 1] = g * edge; rgb[idx * 3 + 2] = b * edge
        }
        // resolve supersampling
        val ow = max(1, W / ss); val oh = max(1, H / ss)
        val out = IntArray(ow * oh)
        val n2 = ss * ss
        for (oy in 0 until oh) for (ox in 0 until ow) {
            var r = 0f; var g = 0f; var b = 0f; var c = 0
            for (j in 0 until ss) for (i in 0 until ss) {
                val idx = (ox * ss + i) + (oy * ss + j) * W
                if (gM[idx] < 0) continue
                r += rgb[idx * 3]; g += rgb[idx * 3 + 1]; b += rgb[idx * 3 + 2]; c++
            }
            if (c == 0) continue
            // tone curve keeps highlights from clipping harshly
            fun tm(v: Float): Int { val x = v / c; val y = x / (1f + max(0f, x - 0.85f) * 0.8f); return (clamp01(y) * 255f).toInt() }
            val a = c * 255 / n2
            out[ox + oy * ow] = (a shl 24) or (tm(r) shl 16) or (tm(g) shl 8) or tm(b)
        }
        // premultiplied edges look cleaner when drawn scaled: keep straight alpha but avoid dark fringes
        var shadow: IntArray? = null; var sw = 0; var shh = 0; var shw = 0f; var shH = 0f
        if (withShadow) {
            // straight-down coverage (pitch 0) for the cast shadow on the ground
            var bw = 1f; var bh = 1f
            for (i in 0 until nT * 3) { bw = max(bw, abs(wp[i * 3])); bh = max(bh, abs(wp[i * 3 + 1])) }
            bw += 3f; bh += 3f
            val sk = scale
            sw = ceil(bw * 2f * sk).toInt().coerceAtLeast(2); shh = ceil(bh * 2f * sk).toInt().coerceAtLeast(2)
            shw = bw; shH = bh
            val cov = BooleanArray(sw * shh)
            for (t in 0 until nT) {
                val o = t * 9
                for (v in 0 until 3) { sx[v] = sw / 2f + wp[o + v * 3] * sk; sy[v] = shh / 2f - wp[o + v * 3 + 1] * sk }
                val area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
                if (abs(area) < 1e-6f) continue
                val inv = 1f / area
                val x0 = max(0, floor(min(sx[0], min(sx[1], sx[2]))).toInt()); val x1 = min(sw - 1, ceil(max(sx[0], max(sx[1], sx[2]))).toInt())
                val y0 = max(0, floor(min(sy[0], min(sy[1], sy[2]))).toInt()); val y1 = min(shh - 1, ceil(max(sy[0], max(sy[1], sy[2]))).toInt())
                for (py in y0..y1) for (px in x0..x1) {
                    val fx = px + 0.5f; val fy = py + 0.5f
                    val w0 = ((sx[1] - fx) * (sy[2] - fy) - (sx[2] - fx) * (sy[1] - fy)) * inv
                    val w1 = ((sx[2] - fx) * (sy[0] - fy) - (sx[0] - fx) * (sy[2] - fy)) * inv
                    if (w0 < 0f || w1 < 0f || 1f - w0 - w1 < 0f) continue
                    cov[px + py * sw] = true
                }
            }
            shadow = IntArray(sw * shh)
            // soft edge: 3x3 blur of coverage
            for (y in 0 until shh) for (x in 0 until sw) {
                var cnt = 0
                for (j in -1..1) for (i in -1..1) { val xx = x + i; val yy = y + j; if (xx in 0 until sw && yy in 0 until shh && cov[xx + yy * sw]) cnt++ }
                if (cnt > 0) shadow[x + y * sw] = (cnt * 255 / 9) shl 24
            }
        }
        return Out(ow, oh, out, shadow, sw, shh, hw, hh, shw, shH)
    }

    fun toSpr(o: Out, size: Float = 1f): Spr {
        val b = Bitmap.createBitmap(o.w, o.h, Bitmap.Config.ARGB_8888)
        b.setPixels(o.color, 0, o.w, 0, 0, o.w, o.h)
        val fl = IntArray(o.color.size) { (o.color[it] and 0xFF000000.toInt()) or 0xFFFFFF }
        val fb = Bitmap.createBitmap(o.w, o.h, Bitmap.Config.ARGB_8888)
        fb.setPixels(fl, 0, o.w, 0, 0, o.w, o.h)
        var sb: Bitmap? = null
        val sc = o.shadowCov
        if (sc != null) { sb = Bitmap.createBitmap(o.sw, o.sh, Bitmap.Config.ARGB_8888); sb.setPixels(sc, 0, o.sw, 0, 0, o.sw, o.sh) }
        return Spr(b, sb, fb, o.halfW * 2f * size, o.halfH * 2f * size, o.shHalfW * 2f * size, o.shHalfH * 2f * size)
    }
}
