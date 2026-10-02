import android.graphics.Canvas
import android.graphics.RectF
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object Sheet3D {
    @JvmStatic fun main(a: Array<String>) {
        val sc = if (a.isNotEmpty()) a[0].toFloat() else 2f
        val PI = Math.PI.toFloat()
        val list = ArrayList<Pair<String, Spr>>()
        val t0 = System.currentTimeMillis()
        fun add(n: String, m: Mesh, yaw: Float = 0f, roll: Float = 0f) { val t = System.currentTimeMillis(); list.add(n to R3.toSpr(R3.render(m, sc, yaw, roll))); println("$n ${m.count} tris ${System.currentTimeMillis() - t} ms") }
        add("player", Models.kestrel(false)); add("bankL", Models.kestrel(true), 0f, -0.5f); add("bankR", Models.kestrel(true), 0f, 0.5f)
        add("drone", Models.drone()); add("missile", Models.playerMissile())
        add("dart", Models.dart(), PI); add("swoop", Models.swoop(), PI); add("wasp", Models.wasp(), PI); add("gunship", Models.gunship(), PI)
        add("bomber", Models.bomber(), PI); add("lancer", Models.lancer(), PI); add("mine", Models.mine(), PI); add("emissile", Models.enemyMissile(), PI)
        add("carrier", Models.carrier(), PI)
        add("tank", Models.tankHull(), PI); add("turret", Models.tankTurret(), PI * 0.8f); add("flakB", Models.flakBase()); add("flakG", Models.flakGun(), PI * 1.2f)
        add("sam", Models.sam(), PI); add("boat", Models.gunboat(), PI); add("sturret", Models.smallTurret(), PI); add("train", Models.trainCar(), PI)
        add("radar", Models.radarBase()); add("dish", Models.radarDish(), 0.6f); add("bunker", Models.bunker(), PI)
        add("bigCannon", Models.bigCannon(), PI); add("gatling", Models.gatling(), PI); add("ring", Models.ringCannon()); add("laser", Models.laserEmitter())
        add("bay", Models.bay(), PI); add("flamer", Models.flamer(), PI); add("mortar", Models.mortar(), PI); add("coreO", Models.core(true)); add("coreS", Models.core(false))
        println("total ${System.currentTimeMillis() - t0} ms")
        val W = 2000; val H = 1500
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        c.drawColor(0xFF6F8F6A.toInt())
        var x = 10f; var y = 10f; var rowH = 0f
        for ((n, s) in list) {
            val w = s.w * sc; val h = s.h * sc
            if (x + w > W) { x = 10f; y += rowH + 10f; rowH = 0f }
            s.shadow?.let { c.drawBitmap(it, null, RectF(x + w / 2 - s.sw * sc / 2 + 14f, y + h / 2 - s.sh * sc / 2 + 18f, x + w / 2 + s.sw * sc / 2 + 14f, y + h / 2 + s.sh * sc / 2 + 18f), android.graphics.Paint().apply { alpha = 90 }) }
            c.drawBitmap(s.bmp, null, RectF(x, y, x + w, y + h), null)
            x += w + 10f; rowH = maxOf(rowH, h)
        }
        ImageIO.write(img, "png", File("/home/claude/kestrel/shots/sheet3d.png"))
        if (a.size > 1) {
            val b = ArrayList<Spr>()
            val tb = System.currentTimeMillis()
            for (k in 0..5) { val tt = System.currentTimeMillis(); b.add(R3.toSpr(R3.render(Models.boss(k), 0.6f, PI))); println("boss $k ${System.currentTimeMillis() - tt} ms") }
            val img2 = BufferedImage(1800, 900, BufferedImage.TYPE_INT_ARGB)
            val c2 = Canvas(img2); c2.drawColor(0xFF6F8F6A.toInt())
            var xx = 10f; var yy = 10f; var rh = 0f
            for (s in b) { val w = s.w * 0.6f; val h = s.h * 0.6f; if (xx + w > 1800) { xx = 10f; yy += rh + 10f; rh = 0f }; c2.drawBitmap(s.bmp, null, RectF(xx, yy, xx + w, yy + h), null); xx += w + 10f; rh = maxOf(rh, h) }
            ImageIO.write(img2, "png", File("/home/claude/kestrel/shots/bosses3d.png"))
        }
    }
}
