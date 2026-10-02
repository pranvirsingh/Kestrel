import com.pranvir.kestrel.*
object AudioTest {
    @JvmStatic fun main(a: Array<String>) {
        val t0 = System.currentTimeMillis()
        for (id in 0 until Sfx.COUNT) {
            val p = Synth.render(id)
            var peak = 0; var sum = 0.0
            for (v in p) { peak = maxOf(peak, Math.abs(v.toInt())); sum += v * v.toDouble() }
            println("sfx $id len=${"%.2f".format(p.size / 22050f)}s peak=$peak rms=${"%.0f".format(Math.sqrt(sum / p.size))}")
            check(peak > 2000) { "silent sfx $id" }
        }
        val t1 = System.currentTimeMillis()
        val stems = Array(Stem.COUNT) { Synth.renderStem(it) }
        val t2 = System.currentTimeMillis()
        for ((k, p) in stems.withIndex()) {
            check(p.size == Synth.LOOP_N)
            var peak = 0; var sum = 0.0
            for (v in p) { peak = maxOf(peak, Math.abs(v.toInt())); sum += v * v.toDouble() }
            println("stem $k peak=$peak rms=${"%.0f".format(Math.sqrt(sum / p.size))} seam=${Math.abs(p[0] - p[p.size - 1])}")
        }
        val out = ShortArray(1024)
        val prev = ShortArray(22050 * 30)
        val m = Mixer(stems)
        var o = 0
        var peak = 0
        while (o + 1024 <= prev.size) {
            val sec = o / 22050f
            val scene = if (sec < 8f) Scene.MENU else if (sec < 20f) Scene.BATTLE else Scene.BOSS
            m.render(out, scene, 0.8f, true); System.arraycopy(out, 0, prev, o, 1024); o += 1024
            for (v in out) peak = maxOf(peak, Math.abs(v.toInt()))
        }
        java.io.File("/home/claude/kestrel/shots/music_preview.wav").writeBytes(Synth.wav(prev))
        println("mixer peak=$peak sfx ${t1 - t0}ms stems ${t2 - t1}ms loop=${Synth.LOOP_SEC}s")
        println("AUDIO OK")
    }
}
