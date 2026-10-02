import android.graphics.Canvas
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.util.Random

/** Plays the whole campaign through the real UI: map → brief → launch → fly → debrief → hangar shopping. */
object Campaign {
    @JvmStatic fun main(a: Array<String>) {
        BattleShots.fonts()
        val skill = if (a.isNotEmpty()) a[0].toFloat() else 0.6f
        val host = FakeHost()
        val W = 360; val H = 780
        val g = Game(host)
        g.resize(W, H, 1f); g.setInsets(0, 24, 0, 16)
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        val rnd = Random(5)
        fun tap(id: Int): Boolean { val o = FloatArray(2); if (!g.debugBtn(id, o)) return false; g.touchDown(0, o[0], o[1]); g.update(0.016f); g.touchUp(0, o[0], o[1]); g.update(0.016f); return true }
        fun run(sec: Float) { var t = 0f; while (t < sec) { g.update(1f / 60f); t += 1f / 60f } }
        var flights = 0
        val t0 = System.currentTimeMillis()
        for (round in 0 until 40) {
            // pick the furthest open sector, at its highest open threat
            check(tap(Game.B_CAMPAIGN) || g.mode == Game.M_MAP) { "no campaign button in mode ${g.mode}" }
            run(0.2f)
            var sec = 0
            for (s in 0 until 6) if (g.sectorOpen(s)) sec = s
            // replay earlier sectors at higher threat now and then
            if (round % 3 == 2) sec = rnd.nextInt(sec + 1)
            check(tap(Game.B_SECTOR0 + sec)) { "sector $sec not tappable" }
            run(0.2f)
            var th = 0; for (t in 0 until 4) if (g.threatOpen(sec, t)) th = t
            tap(Game.B_THREAT0 + th)
            check(tap(Game.B_LAUNCH)) { "no launch" }
            var guard = 0
            while (g.mode == Game.M_LOAD && guard++ < 1000) g.update(1f / 60f)
            check(g.mode == Game.M_PLAY)
            val w = g.world!!
            w.apSkill = skill
            var t = 0f
            var frame = 0
            while (g.mode == Game.M_PLAY && t < 700f) {
                if (!g.isPaused) {
                    val mv = FloatArray(2); w.autopilot(mv); w.move(mv[0], mv[1])
                    if (w.canOverdrive() && (w.state == World.BOSS || w.enemies.size > 6)) w.triggerOverdrive()
                    if (rnd.nextFloat() < 0.0007f) { g.onPause() }
                } else tap(Game.B_RESUME)
                g.update(1f / 60f); t += 1f / 60f
                if (frame++ % 120 == 0) { g.draw(c); check(c.saveCount == 1) }
            }
            flights++
            check(g.mode == Game.M_DEBRIEF) { "mission did not finish: mode ${g.mode} state ${w.state} t=$t" }
            run(2.5f); g.draw(c)
            println("flight $flights: sector ${sec + 1} threat ${th + 1} ${if (w.cleared()) "CLEAR" else "FAIL "} kill=${(w.killRatio() * 100).toInt()}% resc=${w.rescued} dmg=${w.damageTaken.toInt()} +${w.payout()} → cores=${g.cores} badges=${g.totalBadges()}")
            // shopping: buy the cheapest affordable upgrade repeatedly
            check(tap(Game.B_TO_HANGAR)); run(0.2f)
            var bought = true
            while (bought) {
                bought = false
                var best = -1; var bc = Int.MAX_VALUE
                for (u in 0 until Up.COUNT) { val cst = Up.cost(u, g.lv[u]); if (cst in 1..g.cores && cst < bc) { bc = cst; best = u } }
                if (best >= 0) { val before = g.lv[best]; tap(Game.B_UP0 + best); if (g.lv[best] == before) { g.draw(c); tap(Game.B_UP0 + best) }; bought = g.lv[best] > before }
            }
            g.onBack(); run(0.2f)
            if (g.mode != Game.M_MAP && g.mode != Game.M_TITLE) g.onBack()
            if (g.mode == Game.M_MAP) { g.onBack(); run(0.2f) }
            if (g.clearedSector[5] && g.threatOpen(5, 3)) break
        }
        println("CAMPAIGN OK flights=$flights badges=${g.totalBadges()} lv=${g.lv.toList()} cores=${g.cores} cleared=${g.clearedSector.toList()} in ${(System.currentTimeMillis() - t0) / 1000}s")
    }
}
