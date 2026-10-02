import com.pranvir.kestrel.*

object Balance {
    var SKILL = 1f
    fun run(sector: Int, threat: Int, lv: IntArray, seed: Long, perks: Int = 0, skill: Float = SKILL): String {
        val vh = 2166f
        val w = World(sector, threat, Loadout(lv, perks), vh, seed)
        val mv = FloatArray(2)
        w.apSkill = skill
        var t = 0f
        val dt = 1f / 60f
        var bossT = 0f
        while (w.state != World.DONE && t < 600f) {
            w.autopilot(mv); w.move(mv[0], mv[1])
            if (w.canOverdrive() && (w.state == World.BOSS || w.enemies.size > 5)) w.triggerOverdrive()
            w.update(dt); t += dt
            if (w.state == World.BOSS) bossT += dt
        }
        return "s$sector t$threat lv${lv.joinToString("")}: ${if (w.cleared()) "CLEAR" else "FAIL "} t=${t.toInt()} boss=${bossT.toInt()}s kill=${(w.killRatio() * 100).toInt()}% resc=${w.rescued} dmg=${w.damageTaken.toInt()}/${w.maxHp.toInt()} badges=${Integer.toBinaryString(w.badges())} pay=${w.payout()} ace=${w.aceProgress()}"
    }
    @JvmStatic fun main(a: Array<String>) {
        if (a.size > 2) SKILL = a[2].toFloat()
        val sectors = if (a.isNotEmpty()) a[0].split(",").map { it.toInt() } else (0..5).toList()
        val threats = if (a.size > 1) a[1].split(",").map { it.toInt() } else listOf(0, 3)
        for (s in sectors) for (th in threats) {
            val L = (s + th * 2).coerceAtMost(5)
            val lv = intArrayOf(L, L, max(0, L - 1), min(3, L / 2), L, L, L)
            for (seed in 1L..2L) println(run(s, th, lv.map { it.coerceIn(0, 5) }.toIntArray().also { it[3] = it[3].coerceAtMost(3) }, seed))
        }
    }
    fun max(a: Int, b: Int) = if (a > b) a else b
    fun min(a: Int, b: Int) = if (a < b) a else b
}
