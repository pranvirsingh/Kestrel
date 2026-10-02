package com.pranvir.kestrel

import android.graphics.Bitmap
import android.graphics.Canvas
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Ground chunks (1000 x 1000 world units) painted on a worker thread and handed to the UI thread.
 * Only the UI thread touches [ready]; bitmaps are recycled there too, never while drawable.
 */
class Terrain(val biome: Biome, val R: Float, async: Boolean) {
    val wpx = (WORLD_W * R).roundToInt().coerceAtLeast(8)
    val hpx = (Biome.CH * R).roundToInt().coerceAtLeast(8)
    private val ready = HashMap<Int, Bitmap>()
    private val pending = HashSet<Int>()
    private val incoming = ConcurrentLinkedQueue<Pair<Int, Bitmap>>()
    @Volatile private var dead = false
    private val exec: ExecutorService? = if (async) Executors.newSingleThreadExecutor { r -> Thread(r, "kestrel-terrain").apply { isDaemon = true; priority = Thread.MIN_PRIORITY + 1 } } else null

    fun get(k: Int): Bitmap? = ready[k]
    fun isReady(k: Int) = ready.containsKey(k)

    /** UI thread: collect finished chunks, ask for the window [k0, k1], free what scrolled away. */
    fun need(k0: Int, k1: Int) {
        while (true) {
            val p = incoming.poll() ?: break
            if (dead || p.first < k0 - 1 || p.first > k1 + 3) { p.second.recycle(); pending.remove(p.first); continue }
            ready.put(p.first, p.second)?.recycle()
            pending.remove(p.first)
        }
        for (k in k0..k1 + 1) {
            if (k < 0 || ready.containsKey(k) || pending.contains(k)) continue
            pending.add(k)
            val e = exec
            if (e == null) { ready[k] = render(biome, k, R); pending.remove(k) }
            else e.execute {
                if (!dead) {
                    try { incoming.add(Pair(k, render(biome, k, R))) } catch (t: Throwable) { android.util.Log.e("Kestrel", "terrain", t) }
                }
            }
        }
        val it = ready.entries.iterator()
        while (it.hasNext()) {
            val en = it.next()
            if (en.key < k0 - 1 || en.key > k1 + 3) { en.value.recycle(); it.remove() }
        }
    }

    fun release() {
        dead = true
        exec?.shutdownNow()
        for (b in ready.values) b.recycle()
        ready.clear()
        while (true) { val p = incoming.poll() ?: break; p.second.recycle() }
    }

    companion object {
        /**
         * Paint chunk k at R pixels per world unit. The camera is tilted like the 3D sprites, so raised ground
         * slides up the screen and hides what lies behind it (a column "voxel" pass, near to far). Hills are
         * lit, cast soft shadows to the south-east and darken in their creases.
         */
        fun render(b: Biome, k: Int, R: Float): Bitmap {
            val w = (WORLD_W * R).roundToInt().coerceAtLeast(8)
            val h = (Biome.CH * R).roundToInt().coerceAtLeast(8)
            val top = (k + 1) * Biome.CH                 // projected y at the chunk's top row
            val bottom = k * Biome.CH
            val raised = b.zScale > 0f
            val margin = if (raised) Biome.MAX_LIFT + 4f else 0f
            // height grid: x from -SH to W, ground y from bottom-margin to top+SH (room for shadow rays)
            val SH = if (raised) 260f else 0f
            val step = 2.5f / max(0.3f, R)               // world units between grid nodes
            val gx0 = -SH; val gy0 = bottom - margin - step
            val gw = ((WORLD_W + SH) / step).toInt() + 4
            val gh = ((Biome.CH + margin + SH) / step).toInt() + 4
            val H = FloatArray(gw * gh)
            for (j in 0 until gh) { val gy = gy0 + j * step; for (i in 0 until gw) H[i + j * gw] = b.height(gx0 + i * step, gy) }
            val Z = FloatArray(gw * gh); for (i in H.indices) Z[i] = b.z(H[i])
            // sun: north-west and high (y grows to the north here)
            val ln = sqrt(b.lightX * b.lightX + b.lightY * b.lightY + b.lightZ * b.lightZ)
            val lx = b.lightX / ln; val ly = -b.lightY / ln; val lz = b.lightZ / ln
            val lh = sqrt(lx * lx + ly * ly).coerceAtLeast(0.01f)
            val rise = lz / lh
            // soft cast shadows
            val SHD = FloatArray(gw * gh)
            if (raised) {
                val sx = lx / lh * step * 2f; val sy = ly / lh * step * 2f
                for (j in 0 until gh) for (i in 0 until gw) {
                    val z0 = Z[i + j * gw]
                    var occ = 0f
                    var x = i.toFloat(); var y = j.toFloat()
                    var t = 0f
                    for (q in 0 until 40) {
                        x += sx / step; y += sy / step; t += step * 2f
                        val xi = x.toInt(); val yi = y.toInt()
                        if (xi < 0 || yi < 0 || xi >= gw || yi >= gh) break
                        val d = Z[xi + yi * gw] - (z0 + t * rise)
                        if (d > 0f) { occ = max(occ, clamp01(d / 14f)); if (occ >= 1f) break }
                    }
                    SHD[i + j * gw] = occ
                }
            }
            fun samp(arr: FloatArray, x: Float, gy: Float): Float {
                val fx = ((x - gx0) / step).coerceIn(0f, gw - 1.001f); val fy = ((gy - gy0) / step).coerceIn(0f, gh - 1.001f)
                val ix = fx.toInt(); val iy = fy.toInt(); val tx = fx - ix; val ty = fy - iy
                val i0 = ix + iy * gw
                return (arr[i0] * (1 - tx) + arr[i0 + 1] * tx) * (1 - ty) + (arr[i0 + gw] * (1 - tx) + arr[i0 + gw + 1] * tx) * ty
            }
            val rel = b.relief * 60f
            val px = IntArray(w * h)
            val seed = b.seed * 977 + k * 131
            val inv = 1f / step
            // each output row j covers projected y in (top - (j+1)/R, top - j/R]
            for (i in 0 until w) {
                val x = (i + 0.5f) / R
                var filled = h                                  // rows >= filled are already painted (we fill from the bottom up)
                var gy = bottom - margin
                val gstep = 0.5f / R
                while (gy <= top + gstep && filled > 0) {
                    val zz = samp(Z, x, gy)
                    val pY = gy + Biome.liftOf(zz)
                    val row = ((top - pY) * R).toInt()             // first row whose band contains pY
                    if (row < filled) {
                        val hh = samp(H, x, gy)
                        // normal from the grid
                        val fx = ((x - gx0) * inv).coerceIn(2f, gw - 3.001f); val fy = ((gy - gy0) * inv).coerceIn(2f, gh - 3.001f)
                        val ix = fx.toInt(); val iy = fy.toInt()
                        val c0 = ix + iy * gw
                        val gx = (H[c0 + 1] - H[c0 - 1]) * 0.5f * inv
                        val gyv = (H[c0 + gw] - H[c0 - gw]) * 0.5f * inv  // + towards north
                        val nx = -gx * rel; val ny = -gyv * rel
                        val nl = 1f / sqrt(nx * nx + ny * ny + 1f)
                        var light = ((nx * lx + ny * ly + lz) * nl).coerceIn(0f, 1f)
                        val slope = (sqrt(gx * gx + gyv * gyv) * rel).coerceIn(0f, 2f)
                        if (raised) {
                            val sh = samp(SHD, x, gy)
                            light *= 1f - 0.62f * sh
                            // creases: darker where the ground sits below its neighbourhood
                            val avg = (Z[c0 - 2] + Z[c0 + 2] + Z[c0 - 2 * gw] + Z[c0 + 2 * gw]) * 0.25f
                            val cav = clamp01((avg - zz) / 10f)
                            light *= 1f - 0.35f * cav
                        }
                        val n = Noise.hash(i, (gy * R).toInt(), seed) - 0.5f
                        var col = b.color(x, gy, hh, light, slope, n)
                        if (b.grit && zz > 0.5f) {
                            // gritty mid-scale detail on land: rock, grass and sand all read rougher
                            val d = Noise.value(x * 0.07f, gy * 0.07f, seed + 5) + Noise.value(x * 0.21f, gy * 0.21f, seed + 6) * 0.5f
                            col = scaleRgb(col, 0.9f + d * 0.14f + n * 0.06f)
                        }
                        val r0 = max(0, row)
                        // a big jump means we are looking at a cliff face: shade it as rock in shadow
                        val faceStart = min(filled, r0 + 2)
                        for (j in r0 until faceStart) px[i + j * w] = col
                        if (faceStart < filled) {
                            // small steps just continue the ground colour; only real drops read as rock faces
                            val amt = clamp01((filled - faceStart - 3) / (7f * max(1f, R * 2f)))
                            val faceBase = lerpColor(col, b.face(col), amt)
                            for (j in faceStart until filled) {
                                val k = (j - faceStart).toFloat() / max(1, filled - faceStart)
                                val nn = Noise.value(i * 0.25f, j * 0.05f, seed + 3) * 0.16f + Noise.hash(i, j / 3, seed + 9) * 0.05f
                                px[i + j * w] = scaleRgb(faceBase, 1f - amt * (0.22f - nn + 0.25f * k))
                            }
                        }
                        filled = min(filled, r0)
                    }
                    gy += gstep
                }
                if (filled > 0) { val col = px[i + filled * w].takeIf { filled < h } ?: b.base; for (j in 0 until filled) px[i + j * w] = col }
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            val c = Canvas(bmp)
            c.save()
            c.scale(R, R)
            b.overlay(c, k, top)
            c.restore()
            val out = bmp.copy(Bitmap.Config.RGB_565, false)
            bmp.recycle()
            return out
        }
    }
}
