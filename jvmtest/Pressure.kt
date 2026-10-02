import com.pranvir.kestrel.*
/** How busy is the screen? Average and peak enemy rounds, measured with a lazy bot that barely dodges. */
object Pressure {
    @JvmStatic fun main(a: Array<String>) {
        for (sector in listOf(0, 2, 5)) for (threat in 0..3) {
            val lv = intArrayOf(0, 0, 0, 0, 0, 0, 0)
            val w = World(sector, threat, Loadout(lv, 0), 2222f, 3L)
            w.apSkill = 0f
            val mv = FloatArray(2)
            var t = 0f; var sum = 0L; var n = 0; var peak = 0; var hits = 0f
            w.secondWind = true
            while (w.state != World.DONE && t < 400f) {
                w.autopilot(mv); w.move(mv[0] * 0.35f, mv[1] * 0.35f)   // sluggish thumb
                w.update(1f / 60f); t += 1f / 60f
                if (w.state == World.PLAY) { sum += w.eb.count; n++; peak = maxOf(peak, w.eb.count) }
                if (w.hp < w.maxHp * 0.3f) { hits += w.maxHp - w.hp; w.hp = w.maxHp }
            }
            println("sector ${sector + 1} threat ${threat + 1}: avg ${sum / maxOf(1, n)} peak $peak bullets; damage taken ${(w.damageTaken).toInt()} over ${t.toInt()}s (hull ${w.maxHp.toInt()})")
        }
    }
}
