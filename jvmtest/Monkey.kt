import android.graphics.Canvas
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.util.Random

object Monkey {
    @JvmStatic fun main(a: Array<String>) {
        BattleShots.fonts()
        val steps = if (a.isNotEmpty()) a[0].toInt() else 40000
        val rnd = Random(if (a.size > 1) a[1].toLong() else 42L)
        val host = FakeHost().apply { spriteCacheDir = java.io.File("/home/claude/kestrel/build/mcache") }
        val W = 360; val H = 780
        var g = Game(host)
        g.resize(W, H, 1f); g.setInsets(0, 24, 0, 16)
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        val down = BooleanArray(3)
        val px = FloatArray(3); val py = FloatArray(3)
        val modes = IntArray(7)
        var restores = 0; var frames = 0; var missions = 0
        var lastMode = -1
        for (i in 0 until steps) {
            val r = rnd.nextFloat()
            if (rnd.nextFloat() < 0.015f) {
                val vis = g.visibleButtons()
                if (vis.isNotEmpty()) { val o = FloatArray(2); if (g.debugBtn(vis[rnd.nextInt(vis.size)], o)) { g.touchDown(2, o[0], o[1]); g.update(0.016f); g.touchUp(2, o[0], o[1]) } }
            }
            val p = rnd.nextInt(2)
            when {
                r < 0.03f && !down[p] -> { px[p] = rnd.nextFloat() * W; py[p] = rnd.nextFloat() * H; g.touchDown(p, px[p], py[p]); down[p] = true }
                r < 0.06f && down[p] -> { g.touchUp(p, px[p], py[p]); down[p] = false }
                r < 0.2f && down[p] -> { px[p] += (rnd.nextFloat() - 0.5f) * 60f; py[p] += (rnd.nextFloat() - 0.5f) * 60f; g.touchMove(p, px[p], py[p]) }
                r < 0.202f -> { g.touchCancel(); down.fill(false) }
                r < 0.2035f -> g.onBack()
                r < 0.2045f -> g.onPause()
                r < 0.2048f -> { g.onPause(); g.release(); g = Game(host); g.resize(W, H, 1f); g.setInsets(0, 24, 0, 16); down.fill(false); restores++ }
                r < 0.2050f -> g.resize(W, H, 1f)
                r < 0.2052f -> g.debugCores(rnd.nextInt(60000))
            }
            // spend long stretches actually flying so missions get finished
            if (g.mode == Game.M_PLAY && !g.isPaused && (i / 2500) % 3 != 0) {
                val w = g.world!!
                val mv = FloatArray(2); w.autopilot(mv); w.move(mv[0], mv[1])
                if (rnd.nextFloat() < 0.01f) w.triggerOverdrive()
            }
            val dt = if (rnd.nextFloat() < 0.01f) rnd.nextFloat() * 0.3f else 1f / 60f
            if (g.mode == Game.M_LOAD || g.mode == Game.M_TITLE) Thread.sleep(0, 200000)
            g.update(dt)
            if (i % 9 == 0) { g.draw(c); check(c.saveCount == 1) { "save leak in mode ${g.mode}" }; frames++ }
            if (g.mode == Game.M_DEBRIEF && lastMode != Game.M_DEBRIEF) missions++
            lastMode = g.mode
            modes[g.mode]++
            check(g.cores >= 0) { "negative cores" }
            for (k in 0 until Up.COUNT) check(g.lv[k] in 0..Up.MAX[k])
            val w = g.world
            if (w != null) {
                check(!w.px.isNaN() && !w.py.isNaN()) { "NaN player" }
                check(w.px in 0f..1000f) { "player x ${w.px}" }
                check(w.hp <= w.maxHp + 0.01f) { "hp overflow" }
                check(w.enemies.size < 400) { "enemy leak ${w.enemies.size}" }
                for (e in w.enemies) check(!e.x.isNaN() && !e.y.isNaN()) { "NaN enemy ${e.kind}" }
            }
        }
        g.release()
        println("MONKEY OK steps=$steps frames=$frames restores=$restores missions=$missions modes=${modes.toList()} cores=${g.cores} badges=${g.totalBadges()} lv=${g.lv.toList()}")
    }
}
