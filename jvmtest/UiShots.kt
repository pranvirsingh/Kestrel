import android.graphics.Canvas
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class FakeHost : Host {
    val ints = HashMap<String, Int>()
    var sounds = 0
    var scene = -1
    override fun loadInt(key: String, def: Int) = ints[key] ?: def
    override fun saveInt(key: String, v: Int) { ints[key] = v }
    override fun sound(id: Int, vol: Float, rate: Float) { check(id in 0 until Sfx.COUNT); sounds++ }
    override fun setAudio(soundOn: Boolean, musicOn: Boolean) {}
    override fun setMusicState(scene: Int, intensity: Float) { this.scene = scene; check(!intensity.isNaN()) }
    override fun haptic(strong: Boolean) {}
    override val asyncTerrain = false
    override var spriteCacheDir: java.io.File? = null
}

object UiShots {
    const val W = 540; const val H = 1170
    fun shot(g: Game, name: String) {
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        g.draw(c)
        check(c.saveCount == 1) { "unbalanced save in $name" }
        ImageIO.write(img, "png", File("/home/claude/kestrel/shots/ui_$name.png"))
    }
    fun tap(g: Game, id: Int) { val o = FloatArray(2); check(g.debugBtn(id, o)) { "no button $id in ${g.visibleButtons()} mode ${g.mode}" }; g.touchDown(0, o[0], o[1]); g.update(0.016f); g.touchUp(0, o[0], o[1]) }
    fun run(g: Game, sec: Float) { var t = 0f; while (t < sec) { g.update(1f / 60f); t += 1f / 60f } }
    /** Fly with the autopilot through real touch input. */
    fun fly(g: Game, sec: Float) {
        var t = 0f
        val mv = FloatArray(2)
        var down = false
        var fx = 500f; var fy = 900f
        while (t < sec && g.mode == Game.M_PLAY) {
            val w = g.world!!
            if (!down) { g.touchDown(0, fx, fy); down = true }
            w.autopilot(mv)
            val k = 1.45f / g.s
            fx += mv[0] / k; fy += mv[1] / k
            g.touchMove(0, fx, fy)
            if (fx < 50f || fx > W - 50f || fy < 100f || fy > H - 100f) { g.touchUp(0, fx, fy); down = false; fx = W / 2f; fy = H / 2f }
            if (w.canOverdrive() && w.state == World.BOSS) { val o = FloatArray(2); g.debugBtn(Game.B_OVER, o); g.touchDown(1, o[0], o[1]); g.touchUp(1, o[0], o[1]) }
            g.update(1f / 60f); t += 1f / 60f
        }
        if (down) g.touchUp(0, fx, fy)
    }

    @JvmStatic fun main(a: Array<String>) {
        BattleShots.fonts()
        val host = FakeHost()
        val g = Game(host)
        g.resize(W, H, 1.5f); g.setInsets(0, 30, 0, 20)
        run(g, 0.5f); shot(g, "01_title")
        run(g, 5f); shot(g, "02_title_attract")
        tap(g, Game.B_CAMPAIGN); run(g, 0.5f); shot(g, "03_map")
        tap(g, Game.B_SECTOR0); run(g, 0.4f); shot(g, "04_brief")
        tap(g, Game.B_LAUNCH); run(g, 0.2f); shot(g, "05_load")
        var guard = 0
        while (g.mode == Game.M_LOAD && guard++ < 800) g.update(1f / 60f)
        run(g, 1.0f); shot(g, "06_intro")
        fly(g, 9f); shot(g, "07_play")
        tap(g, Game.B_PAUSE); run(g, 0.3f); shot(g, "08_pause")
        tap(g, Game.B_RESUME)
        fly(g, 400f)
        g.world?.let { println("after fly: state=${it.state} scroll=${it.scroll} boss=${it.boss?.alive} bossHp=${it.boss?.hp} entering=${it.boss?.entering} hp=${it.hp} paused=${g.isPaused}") }
        run(g, 1.5f); shot(g, "09_debrief")
        println("debrief mode=${g.mode} cores=${g.cores} badges=${g.totalBadges()} sounds=${host.sounds}")
        tap(g, Game.B_TO_HANGAR); run(g, 0.4f); shot(g, "10_hangar")
        g.debugCores(5000); run(g, 0.1f)
        tap(g, Game.B_UP0); tap(g, Game.B_UP0 + 1); tap(g, Game.B_UP0 + 3); run(g, 0.3f); shot(g, "11_hangar_buy")
        g.onBack(); run(g, 0.3f)
        g.onBack(); run(g, 0.3f)
        tap(g, Game.B_RANKS); run(g, 0.4f); shot(g, "12_ranks")
        g.onBack(); tap(g, Game.B_SETTINGS); run(g, 0.3f); shot(g, "13_settings")
        println("UI OK mode=${g.mode}")
    }
}
