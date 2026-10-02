package com.pranvir.kestrel

import java.io.ByteArrayOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

object Sfx {
    const val SHOT = 0; const val BOOM = 1; const val BOOM_BIG = 2; const val MISSILE = 3; const val ZAP = 4; const val OVERDRIVE = 5
    const val SIREN = 6; const val LAUNCH = 7; const val LASER = 8; const val HURT = 9; const val SHIELD_HIT = 10; const val PICK = 11
    const val POWER = 12; const val RESCUE = 13; const val CANNON = 14; const val FLAME = 15; const val TAP = 16; const val BUY = 17
    const val BADGE = 18; const val WIN = 19; const val FAIL = 20; const val NOPE = 21
    const val COUNT = 22
}

object Stem {
    const val PAD = 0; const val BASS = 1; const val DRUMS = 2; const val ARP = 3; const val LEAD = 4; const val BOSS = 5
    const val COUNT = 6
}

object Scene { const val MENU = 0; const val BATTLE = 1; const val BOSS = 2; const val PAUSED = 3; const val WIN = 4; const val FAIL = 5 }

object Synth {
    const val RATE = 22050
    const val BPM = 132f
    private const val TAU = (2 * PI).toFloat()
    val BEAT = 60f / BPM
    val BAR = BEAT * 4f
    val LOOP_BARS = 8
    val LOOP_SEC = BAR * LOOP_BARS
    val LOOP_N = (LOOP_SEC * RATE).toInt()

    fun hz(m: Float) = (440.0 * 2.0.pow((m - 69.0) / 12.0)).toFloat()
    private fun buf(sec: Float) = FloatArray((sec * RATE).toInt().coerceAtLeast(1))

    fun pcm(f: FloatArray, gain: Float): ShortArray {
        var peak = 0.0001f
        for (v in f) { val a = abs(v); if (a > peak) peak = a }
        val g = gain / peak
        return ShortArray(f.size) { (tanh((f[it] * g).toDouble()) * 30000).toInt().toShort() }
    }

    // ------------------------------------------------------------------ voices

    /** Detuned saw stack through a one-pole low-pass with its own envelope. */
    private fun saw(b: FloatArray, start: Float, dur: Float, f: Float, amp: Float, att: Float, rel: Float, cutoff: Float,
                    voices: Int = 3, detune: Float = 0.006f, wrap: Boolean = true, vib: Float = 0f) {
        val n = b.size
        val o = (start * RATE).toInt()
        val len = ((dur + rel) * RATE).toInt()
        val ph = FloatArray(voices) { it * 0.37f }
        var lp = 0f; var lp2 = 0f
        val a = 1f - exp(-TAU * cutoff / RATE)
        for (j in 0 until len) {
            val t = j / RATE.toFloat()
            val e = (if (t < att) t / att else 1f) * (if (t > dur) exp(-(t - dur) / rel * 3f) else 1f)
            var s = 0f
            val fm = if (vib > 0f) 1f + vib * sin(TAU * 5.2f * t) * clamp01(t / 0.4f) else 1f
            for (v in 0 until voices) {
                val fv = f * fm * (1f + (v - (voices - 1) / 2f) * detune)
                ph[v] += fv / RATE; if (ph[v] >= 1f) ph[v] -= 1f
                s += ph[v] * 2f - 1f
            }
            s /= voices
            lp += a * (s - lp); lp2 += a * (lp - lp2)
            val i = o + j
            if (!wrap && i >= n) break
            b[((i % n) + n) % n] += lp2 * e * amp
        }
    }

    private fun sine(b: FloatArray, start: Float, f: Float, amp: Float, decay: Float, att: Float = 0.002f, f2: Float = f, wrap: Boolean = false) {
        val n = b.size
        val o = (start * RATE).toInt()
        var ph = 0f
        val maxLen = (RATE * 4f).toInt()
        for (j in 0 until maxLen) {
            val t = j / RATE.toFloat()
            val e = (if (t < att) t / att else 1f) * exp(-(t - att).coerceAtLeast(0f) * decay)
            if (t > att && e < 0.0005f) break
            val fr = f2 + (f - f2) * exp(-t * 30f)
            ph += fr / RATE
            val i = o + j
            if (!wrap && i >= n) break
            b[((i % n) + n) % n] += sin(TAU * ph) * e * amp
        }
    }

    private fun noise(b: FloatArray, start: Float, len: Float, amp: Float, lpHz: Float, hp: Boolean, rnd: Random, decay: Float, att: Float = 0.001f, wrap: Boolean = false) {
        val n = b.size
        val o = (start * RATE).toInt(); val cnt = (len * RATE).toInt()
        var y = 0f
        val a = 1f - exp(-TAU * lpHz / RATE)
        for (j in 0 until cnt) {
            val t = j / RATE.toFloat()
            val e = (if (t < att) t / att else 1f) * exp(-t * decay)
            val w = rnd.nextFloat() * 2f - 1f
            y += a * (w - y)
            val s = if (hp) w - y else y
            val i = o + j
            if (!wrap && i >= n) break
            b[((i % n) + n) % n] += s * e * amp
        }
    }

    private fun square(b: FloatArray, start: Float, dur: Float, f: Float, amp: Float, decay: Float, cutoff: Float) {
        val n = b.size
        val o = (start * RATE).toInt()
        val len = (dur * RATE).toInt()
        var ph = 0f; var lp = 0f
        val a = 1f - exp(-TAU * cutoff / RATE)
        for (j in 0 until len) {
            val t = j / RATE.toFloat()
            val e = min1(t / 0.003f) * exp(-t * decay)
            ph += f / RATE; if (ph >= 1f) ph -= 1f
            val s = if (ph < 0.5f) 1f else -1f
            lp += a * (s - lp)
            val i = o + j
            b[i % n] += lp * e * amp
        }
    }

    private fun min1(v: Float) = if (v > 1f) 1f else v

    /** Feedback echo, tempo-synced. */
    private fun echo(b: FloatArray, delaySec: Float, fb: Float, mix: Float) {
        val d = (delaySec * RATE).toInt()
        val n = b.size
        val out = b.copyOf()
        val tap = FloatArray(n)
        for (pass in 0 until 2) {
            for (i in 0 until n) {
                val src = (i - d + n) % n
                tap[i] = out[src] * mix + tap[src] * fb
            }
        }
        for (i in 0 until n) b[i] += tap[i]
    }

    // ------------------------------------------------------------------ sfx

    private fun sweepNoise(b: FloatArray, start: Float, len: Float, amp: Float, f0: Float, f1: Float, rnd: Random, att: Float, decay: Float) {
        val o = (start * RATE).toInt(); val cnt = (len * RATE).toInt()
        var y = 0f; var y2 = 0f
        for (j in 0 until cnt) {
            val t = j / RATE.toFloat()
            val e = (if (t < att) t / att else 1f) * exp(-(t - att).coerceAtLeast(0f) * decay)
            val c = f0 + (f1 - f0) * (t / len)
            val a = 1f - exp(-TAU * c / RATE)
            y += a * (rnd.nextFloat() * 2f - 1f - y); y2 += a * (y - y2)
            val i = o + j
            if (i >= b.size) break
            b[i] += y2 * e * amp
        }
    }

    fun render(id: Int): ShortArray {
        val rnd = Random(91L + id)
        return when (id) {
            Sfx.SHOT -> { val b = buf(0.08f); sine(b, 0f, 1500f, 0.4f, 55f, 0.001f, 2600f); noise(b, 0f, 0.02f, 0.2f, 7000f, true, rnd, 150f); pcm(b, 0.5f) }
            Sfx.BOOM -> {
                val b = buf(0.7f)
                noise(b, 0f, 0.6f, 0.9f, 1600f, false, rnd, 7f)
                sine(b, 0f, 55f, 0.9f, 6f, 0.002f, 140f)
                noise(b, 0f, 0.08f, 0.4f, 6000f, true, rnd, 40f)
                pcm(b, 0.85f)
            }
            Sfx.BOOM_BIG -> {
                val b = buf(1.8f)
                noise(b, 0f, 1.6f, 1f, 900f, false, rnd, 2.6f)
                sine(b, 0f, 40f, 1f, 2.4f, 0.003f, 110f)
                noise(b, 0f, 0.15f, 0.6f, 7000f, true, rnd, 20f)
                for (k in 0 until 10) noise(b, 0.1f + rnd.nextFloat() * 0.8f, 0.05f, 0.25f, 5000f, true, rnd, 40f)
                pcm(b, 0.95f)
            }
            Sfx.MISSILE -> { val b = buf(0.45f); sweepNoise(b, 0f, 0.42f, 1f, 600f, 4500f, rnd, 0.03f, 6f); sine(b, 0f, 300f, 0.2f, 12f, 0.002f, 120f); pcm(b, 0.6f) }
            Sfx.ZAP -> {
                val b = buf(0.3f)
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val e = exp(-t * 12f)
                    val fm = sin(TAU * 87f * t) * 6f
                    b[i] = (sin(TAU * 1300f * t + fm) * 0.5f + (rnd.nextFloat() * 2f - 1f) * 0.4f * (if (rnd.nextFloat() < 0.3f) 1f else 0f)) * e
                }
                pcm(b, 0.6f)
            }
            Sfx.OVERDRIVE -> {
                val b = buf(1.8f)
                sweepNoise(b, 0f, 0.8f, 0.6f, 400f, 7000f, rnd, 0.6f, 0.5f)
                for ((k, m) in floatArrayOf(62f, 69f, 74f, 77f, 81f, 86f).withIndex()) saw(b, 0.55f + k * 0.04f, 0.5f, hz(m), 0.16f, 0.01f, 0.7f, 3600f, 3, 0.008f, false)
                sine(b, 0.55f, 36f, 0.9f, 2.5f, 0.003f, 80f)
                pcm(b, 0.85f)
            }
            Sfx.SIREN -> {
                val b = buf(1.7f)
                var ph = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val f = if ((t * 2.5f).toInt() % 2 == 0) 740f else 554f
                    ph += f / RATE
                    val e = clamp01(t / 0.03f) * clamp01((1.7f - t) / 0.2f)
                    val s = (ph % 1f) * 2f - 1f
                    b[i] = (s * 0.45f + sin(TAU * ph * 0.5f) * 0.3f) * e
                }
                pcm(b, 0.6f)
            }
            Sfx.LAUNCH -> { val b = buf(0.6f); sine(b, 0f, 90f, 0.8f, 9f, 0.002f, 180f); sweepNoise(b, 0f, 0.55f, 0.6f, 2500f, 900f, rnd, 0.01f, 5f); pcm(b, 0.65f) }
            Sfx.LASER -> {
                val b = buf(0.9f)
                var ph = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val f = 300f + 1500f * (t / 0.9f) * (t / 0.9f)
                    ph += f / RATE
                    b[i] = (sin(TAU * ph) * 0.5f + sin(TAU * ph * 1.5f) * 0.2f) * clamp01(t / 0.05f) * clamp01((0.9f - t) / 0.08f)
                }
                pcm(b, 0.55f)
            }
            Sfx.HURT -> { val b = buf(0.4f); noise(b, 0f, 0.3f, 0.9f, 3000f, false, rnd, 12f); sine(b, 0f, 160f, 0.8f, 10f, 0.001f, 320f); sine(b, 0f, 2300f, 0.25f, 25f); pcm(b, 0.8f) }
            Sfx.SHIELD_HIT -> { val b = buf(0.35f); sine(b, 0f, 1760f, 0.5f, 11f); sine(b, 0f, 2640f, 0.3f, 14f); noise(b, 0f, 0.05f, 0.2f, 8000f, true, rnd, 60f); pcm(b, 0.55f) }
            Sfx.PICK -> { val b = buf(0.14f); sine(b, 0f, 2093f, 0.5f, 30f); sine(b, 0.02f, 3136f, 0.35f, 34f); pcm(b, 0.45f) }
            Sfx.POWER -> { val b = buf(0.7f); for ((k, m) in floatArrayOf(74f, 78f, 81f, 86f).withIndex()) { sine(b, k * 0.06f, hz(m), 0.4f, 7f); saw(b, k * 0.06f, 0.08f, hz(m), 0.12f, 0.003f, 0.15f, 3000f, 2, 0.006f, false) }; pcm(b, 0.65f) }
            Sfx.RESCUE -> { val b = buf(1.1f); for ((k, m) in floatArrayOf(69f, 73f, 76f, 81f).withIndex()) sine(b, k * 0.07f, hz(m), 0.35f, 3.5f, 0.004f); noise(b, 0f, 0.6f, 0.08f, 9000f, true, rnd, 4f, 0.05f); pcm(b, 0.6f) }
            Sfx.CANNON -> { val b = buf(0.7f); sine(b, 0f, 70f, 1f, 6f, 0.002f, 200f); noise(b, 0f, 0.4f, 0.7f, 1200f, false, rnd, 9f); pcm(b, 0.8f) }
            Sfx.FLAME -> { val b = buf(1.5f); sweepNoise(b, 0f, 1.45f, 1f, 900f, 1400f, rnd, 0.08f, 1.2f); pcm(b, 0.6f) }
            Sfx.TAP -> { val b = buf(0.07f); sine(b, 0f, 1100f, 0.6f, 70f, 0.001f, 1600f); noise(b, 0f, 0.015f, 0.2f, 8000f, true, rnd, 200f); pcm(b, 0.5f) }
            Sfx.BUY -> { val b = buf(0.8f); noise(b, 0f, 0.05f, 0.5f, 6000f, true, rnd, 60f); for ((k, m) in floatArrayOf(79f, 83f, 86f, 91f).withIndex()) sine(b, 0.05f + k * 0.05f, hz(m), 0.35f, 6f); pcm(b, 0.65f) }
            Sfx.BADGE -> {
                val b = buf(1.3f)
                for ((k, m) in floatArrayOf(74f, 81f, 86f).withIndex()) { sine(b, k * 0.09f, hz(m), 0.4f, 3f, 0.003f); sine(b, k * 0.09f, hz(m) * 2.01f, 0.12f, 6f) }
                noise(b, 0.2f, 0.9f, 0.1f, 9000f, true, rnd, 3f, 0.05f)
                pcm(b, 0.7f)
            }
            Sfx.WIN -> {
                val b = buf(3f)
                val chords = arrayOf(floatArrayOf(62f, 66f, 69f), floatArrayOf(67f, 71f, 74f), floatArrayOf(69f, 73f, 76f), floatArrayOf(74f, 78f, 81f))
                val times = floatArrayOf(0f, 0.32f, 0.64f, 1.0f)
                val lens = floatArrayOf(0.28f, 0.28f, 0.32f, 1.6f)
                for (k in 0 until 4) for (m in chords[k]) saw(b, times[k], lens[k], hz(m), 0.14f, 0.012f, 0.5f, 2800f, 3, 0.008f, false)
                sine(b, 1.0f, hz(38f), 0.6f, 1.5f, 0.01f)
                noise(b, 1.0f, 1.5f, 0.08f, 9000f, true, rnd, 2.5f, 0.05f)
                pcm(b, 0.85f)
            }
            Sfx.FAIL -> {
                val b = buf(2.2f)
                for ((k, m) in floatArrayOf(69f, 65f, 62f, 57f).withIndex()) saw(b, k * 0.3f, 0.36f, hz(m), 0.2f, 0.02f, 0.6f, 1600f, 3, 0.01f, false)
                sine(b, 1.2f, hz(38f), 0.5f, 1.4f, 0.02f)
                pcm(b, 0.75f)
            }
            Sfx.NOPE -> { val b = buf(0.22f); square(b, 0f, 0.09f, 200f, 0.4f, 18f, 1500f); square(b, 0.1f, 0.11f, 150f, 0.4f, 14f, 1500f); pcm(b, 0.5f) }
            else -> ShortArray(10)
        }
    }

    // ------------------------------------------------------------------ music: D minor, 132 bpm

    private val ROOTS = intArrayOf(38, 34, 36, 33, 38, 34, 41, 36)
    private val CHORDS = arrayOf(
        intArrayOf(62, 65, 69), intArrayOf(62, 65, 70), intArrayOf(60, 64, 67), intArrayOf(60, 64, 69),
        intArrayOf(65, 69, 74), intArrayOf(65, 70, 74), intArrayOf(65, 69, 72), intArrayOf(64, 67, 72))

    fun renderStem(id: Int): ShortArray = when (id) {
        Stem.PAD -> pad(); Stem.BASS -> bass(); Stem.DRUMS -> drums(); Stem.ARP -> arp(); Stem.LEAD -> lead(); else -> bossLayer()
    }

    private fun stemBuf() = FloatArray(LOOP_N)

    private fun pad(): ShortArray {
        val b = stemBuf()
        for (bar in 0 until LOOP_BARS) {
            for (m in CHORDS[bar]) saw(b, bar * BAR, BAR * 0.97f, hz(m.toFloat()), 0.15f, 0.25f, 0.5f, 1300f, 3, 0.007f)
            sine(b, bar * BAR, hz(ROOTS[bar] + 24f), 0.1f, 0.5f, 0.15f, wrap = true)
        }
        echo(b, BEAT * 0.75f, 0.3f, 0.2f)
        return pcm(b, 0.55f)
    }

    private fun bass(): ShortArray {
        val b = stemBuf()
        val e8 = BEAT / 2f
        for (bar in 0 until LOOP_BARS) {
            val r = ROOTS[bar].toFloat()
            for (k in 0 until 8) {
                val m = if (k == 3 || k == 7) r + 12f else r
                saw(b, bar * BAR + k * e8, e8 * 0.6f, hz(m), 0.55f, 0.003f, 0.04f, 650f + (if (k % 2 == 1) 400f else 0f), 2, 0.004f)
            }
        }
        val q = (BEAT * RATE).toInt()
        for (i in b.indices) { val p = (i % q) / q.toFloat(); b[i] *= 0.45f + 0.55f * smooth(p / 0.35f) }
        return pcm(b, 0.6f)
    }

    private fun drums(): ShortArray {
        val b = stemBuf()
        val rnd = Random(9)
        val s16 = BEAT / 4f
        for (bar in 0 until LOOP_BARS) {
            for (beat in 0 until 4) {
                val t = bar * BAR + beat * BEAT
                sine(b, t, 50f, 1f, 10f, 0.001f, 160f, wrap = true)
                noise(b, t, 0.012f, 0.3f, 4000f, false, rnd, 300f, wrap = true)
                if (beat == 1 || beat == 3) {
                    noise(b, t, 0.3f, 0.55f, 6000f, false, rnd, 13f, wrap = true)
                    sine(b, t, 200f, 0.4f, 20f, 0.001f, 260f, wrap = true)
                }
                if (beat == 2 && bar % 2 == 1) sine(b, t + BEAT * 0.5f, 50f, 0.7f, 12f, 0.001f, 150f, wrap = true)
            }
            for (k in 0 until 16) {
                val acc = if (k % 4 == 2) 0.22f else if (k % 2 == 0) 0.08f else 0.13f
                noise(b, bar * BAR + k * s16, 0.04f, acc, 9000f, true, rnd, 80f, wrap = true)
            }
            if (bar % 4 == 3) for (k in 0 until 4) { sine(b, bar * BAR + (12 + k) * s16, 140f - k * 18f, 0.5f, 10f, 0.001f, 200f - k * 20f, wrap = true) }
        }
        return pcm(b, 0.8f)
    }

    private fun arp(): ShortArray {
        val b = stemBuf()
        val s16 = BEAT / 4f
        val pattern = intArrayOf(0, 1, 2, 1, 0, 2, 1, 2, 0, 1, 2, 1, 2, 1, 0, 2)
        for (bar in 0 until LOOP_BARS) {
            val ch = CHORDS[bar]
            for (k in 0 until 16) {
                val m = ch[pattern[k]] + 12 + (if (k % 8 == 7) 12 else 0)
                square(b, bar * BAR + k * s16, s16 * 0.85f, hz(m.toFloat()), 0.15f, 16f, 2800f)
            }
        }
        echo(b, BEAT * 0.75f, 0.35f, 0.35f)
        return pcm(b, 0.5f)
    }

    private fun lead(): ShortArray {
        val b = stemBuf()
        val mel = arrayOf(
            floatArrayOf(0f, 0f, 1.5f, 69f), floatArrayOf(0f, 1.5f, 0.5f, 65f), floatArrayOf(0f, 2f, 2f, 74f),
            floatArrayOf(1f, 0f, 1f, 77f), floatArrayOf(1f, 1f, 0.5f, 76f), floatArrayOf(1f, 1.5f, 0.5f, 74f), floatArrayOf(1f, 2f, 1f, 72f), floatArrayOf(1f, 3f, 1f, 74f),
            floatArrayOf(2f, 0f, 1.5f, 76f), floatArrayOf(2f, 1.5f, 0.5f, 72f), floatArrayOf(2f, 2f, 2f, 67f),
            floatArrayOf(3f, 0f, 3f, 69f), floatArrayOf(3f, 3f, 1f, 76f),
            floatArrayOf(4f, 0f, 1.5f, 74f), floatArrayOf(4f, 1.5f, 0.5f, 76f), floatArrayOf(4f, 2f, 2f, 77f),
            floatArrayOf(5f, 0f, 1f, 79f), floatArrayOf(5f, 1f, 1f, 77f), floatArrayOf(5f, 2f, 2f, 74f),
            floatArrayOf(6f, 0f, 1f, 72f), floatArrayOf(6f, 1f, 1f, 77f), floatArrayOf(6f, 2f, 1.5f, 81f), floatArrayOf(6f, 3.5f, 0.5f, 79f),
            floatArrayOf(7f, 0f, 2f, 76f), floatArrayOf(7f, 2f, 2f, 79f))
        for (n in mel) {
            val t = n[0] * BAR + n[1] * BEAT
            saw(b, t, n[2] * BEAT * 0.92f, hz(n[3]), 0.28f, 0.015f, 0.16f, 2600f, 3, 0.005f, vib = 0.006f)
            saw(b, t, n[2] * BEAT * 0.92f, hz(n[3] - 12f), 0.1f, 0.015f, 0.16f, 1400f, 2, 0.004f)
        }
        echo(b, BEAT * 0.75f, 0.3f, 0.28f)
        return pcm(b, 0.55f)
    }

    private fun bossLayer(): ShortArray {
        val b = stemBuf()
        val rnd = Random(77)
        // brass stabs on the off-beats and war drums
        for (bar in 0 until LOOP_BARS) {
            val r = ROOTS[bar].toFloat() + 12f
            for (k in intArrayOf(0, 3, 6)) {
                val t = bar * BAR + k * BEAT / 2f
                for (iv in floatArrayOf(0f, 7f, 12f)) saw(b, t, BEAT * 0.3f, hz(r + iv), 0.2f, 0.005f, 0.1f, 1800f, 3, 0.01f)
            }
            for (k in 0 until 8) {
                val t = bar * BAR + k * BEAT / 2f
                sine(b, t, 110f - (k % 4) * 12f, 0.5f, 9f, 0.002f, 160f, wrap = true)
                noise(b, t, 0.08f, 0.15f, 1500f, false, rnd, 25f, wrap = true)
            }
        }
        return pcm(b, 0.65f)
    }

    /** Stem gains for a scene; [intensity] 0..1 builds the battle arrangement. */
    fun targets(scene: Int, intensity: Float, music: Boolean, out: FloatArray) {
        for (i in out.indices) out[i] = 0f
        if (!music) return
        when (scene) {
            Scene.MENU -> { out[Stem.PAD] = 0.5f; out[Stem.ARP] = 0.22f; out[Stem.LEAD] = 0.32f; out[Stem.BASS] = 0.22f }
            Scene.BATTLE -> {
                out[Stem.PAD] = 0.32f; out[Stem.BASS] = 0.55f; out[Stem.DRUMS] = 0.5f + 0.15f * intensity
                out[Stem.ARP] = 0.22f + 0.15f * intensity; out[Stem.LEAD] = if (intensity > 0.5f) 0.3f else 0.08f
            }
            Scene.BOSS -> { out[Stem.PAD] = 0.25f; out[Stem.BASS] = 0.55f; out[Stem.DRUMS] = 0.62f; out[Stem.ARP] = 0.28f; out[Stem.LEAD] = 0.18f; out[Stem.BOSS] = 0.6f }
            Scene.PAUSED -> { out[Stem.PAD] = 0.42f; out[Stem.BASS] = 0.15f }
            Scene.WIN -> { out[Stem.PAD] = 0.45f; out[Stem.LEAD] = 0.4f; out[Stem.ARP] = 0.2f; out[Stem.BASS] = 0.25f }
            else -> { out[Stem.PAD] = 0.42f }
        }
    }

    fun wav(pcm: ShortArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size * 2)
        fun i32(v: Int) { out.write(v and 255); out.write((v shr 8) and 255); out.write((v shr 16) and 255); out.write((v shr 24) and 255) }
        fun i16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }
        out.write("RIFF".toByteArray()); i32(36 + pcm.size * 2); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(pcm.size * 2)
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) { bytes[i * 2] = (pcm[i].toInt() and 255).toByte(); bytes[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte() }
        out.write(bytes)
        return out.toByteArray()
    }
}

/** Pure mixing logic: smoothed stem gains, a muffle low-pass for pause and defeat. */
class Mixer(private val st: Array<ShortArray>) {
    private val g = FloatArray(st.size)
    private val target = FloatArray(st.size)
    private var pos = 0
    private var lp = 0f
    private var lpA = 1f

    fun render(out: ShortArray, scene: Int, intensity: Float, music: Boolean) {
        Synth.targets(scene, intensity, music, target)
        for (k in st.indices) g[k] = approach(g[k], target[k], 0.015f)
        val lpT = if (scene == Scene.PAUSED || scene == Scene.FAIL) 0.12f else 1f
        val n = st[0].size
        for (i in out.indices) {
            lpA += (lpT - lpA) * 0.0004f
            var s = 0f
            for (k in st.indices) { if (g[k] > 0.0005f) s += st[k][pos] * g[k] }
            lp += lpA * (s - lp)
            out[i] = lp.coerceIn(-32000f, 32000f).toInt().toShort()
            pos++
            if (pos >= n) pos = 0
        }
    }
}
