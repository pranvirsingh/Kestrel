package com.pranvir.kestrel

import kotlin.math.abs
import kotlin.math.floor

/** Value noise and friends. Deterministic, allocation-free, thread-safe. */
object Noise {
    private const val C = 0.7986f   // cos 37°: each octave is rotated so the lattice never lines up
    private const val S = 0.6018f
    fun hashI(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    fun hash(x: Int, y: Int, seed: Int): Float = (hashI(x, y, seed) and 0xFFFFFF) / 16777216f

    fun value(x: Float, y: Float, seed: Int): Float {
        val fx = floor(x); val fy = floor(y)
        val ix = fx.toInt(); val iy = fy.toInt()
        val tx = x - fx; val ty = y - fy
        val sx = tx * tx * tx * (tx * (tx * 6f - 15f) + 10f); val sy = ty * ty * ty * (ty * (ty * 6f - 15f) + 10f)
        val a = hash(ix, iy, seed); val b = hash(ix + 1, iy, seed)
        val c = hash(ix, iy + 1, seed); val d = hash(ix + 1, iy + 1, seed)
        val ab = a + (b - a) * sx
        val cd = c + (d - c) * sx
        return ab + (cd - ab) * sy
    }

    /** Fractal sum normalised to roughly 0..1. */
    fun fbm(x: Float, y: Float, oct: Int, seed: Int, gain: Float = 0.5f): Float {
        var sum = 0f; var amp = 1f; var norm = 0f; var f = 1f
        var px = x; var py = y
        for (o in 0 until oct) {
            sum += value(px * f + o * 17.3f, py * f - o * 9.1f, seed + o * 101) * amp
            val nx = px * C - py * S; py = px * S + py * C; px = nx
            norm += amp; amp *= gain; f *= 2.03f
        }
        return sum / norm
    }

    /** Sharp crests where the noise crosses its midline: rivers, cracks, ridges. */
    fun ridged(x: Float, y: Float, oct: Int, seed: Int): Float {
        var sum = 0f; var amp = 1f; var norm = 0f; var f = 1f
        var px = x; var py = y
        for (o in 0 until oct) {
            val v = 1f - abs(value(px * f + o * 31.7f, py * f + o * 5.3f, seed + o * 131) * 2f - 1f)
            val nx = px * C - py * S; py = px * S + py * C; px = nx
            sum += v * v * amp
            norm += amp; amp *= 0.5f; f *= 2.1f
        }
        return sum / norm
    }

    /** Billowy turbulence for cloud tops. */
    fun billow(x: Float, y: Float, oct: Int, seed: Int): Float {
        var sum = 0f; var amp = 1f; var norm = 0f; var f = 1f
        var px = x; var py = y
        for (o in 0 until oct) {
            sum += abs(value(px * f + o * 7.7f, py * f + o * 3.1f, seed + o * 71) * 2f - 1f) * amp
            val nx = px * C - py * S; py = px * S + py * C; px = nx
            norm += amp; amp *= 0.5f; f *= 2.0f
        }
        return sum / norm
    }
}
