import android.graphics.Canvas
import android.graphics.Typeface
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object BattleShots {
    fun fonts() {
        Fonts.semi = Typeface.createFromFile("/home/claude/kestrel/assets/fonts/raj_semi.ttf")
        Fonts.bold = Typeface.createFromFile("/home/claude/kestrel/assets/fonts/raj_bold.ttf")
    }
    @JvmStatic fun main(a: Array<String>) {
        fonts()
        val sector = if (a.isNotEmpty()) a[0].toInt() else 0
        val times = if (a.size > 1) a[1].split(",").map { it.toFloat() } else listOf(8f, 40f)
        val W = 540; val H = 1170
        val s = W / 1000f
        val vh = H / s
        val ss = SpriteSet(s)
        val lv = intArrayOf(3, 2, 2, 2, 3, 3, 2)
        val w = World(sector, 0, Loadout(lv, 0), vh, 7L)
        w.events = object : WorldEvents { override fun sfx(id: Int, vol: Float, rate: Float) {}; override fun haptic(strong: Boolean) {} }
        val terr = Terrain(w.biome, s * 0.75f, false)
        val art = BossArt(sector, s)
        val r = Render(ss, Units3D(s))
        val mv = FloatArray(2)
        var t = 0f
        var shot = 0
        val dt = 1f / 60f
        val boss = a.size > 2 && a[2] == "boss"
        if (boss) { w.skipTo(w.level.length - 50f) }
        w.secondWind = true
        while (shot < times.size && t < 600f) {
            w.autopilot(mv); w.move(mv[0], mv[1])
            if (w.over >= 100f && w.state == World.BOSS) w.triggerOverdrive()
            w.update(dt); t += dt
            if (w.hp < 30f) w.hp = w.maxHp
            terr.need(kotlin.math.floor(w.scroll / 1000f).toInt(), kotlin.math.floor((w.scroll + vh) / 1000f).toInt())
            if (t >= times[shot]) {
                val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
                val c = Canvas(img)
                c.save(); c.scale(s, s)
                r.draw(c, w, terr, art, t)
                c.restore()
                check(c.saveCount == 1)
                ImageIO.write(img, "png", File("/home/claude/kestrel/shots/battle_${sector}_${shot}.png"))
                println("shot $shot t=$t state=${w.state} scroll=${w.scroll.toInt()} enemies=${w.enemies.size} eb=${w.eb.count} hp=${w.hp} cores=${w.coresRaw} kills=${w.hostileKilled}/${w.level.hostiles} resc=${w.rescued}")
                shot++
            }
        }
    }
}
