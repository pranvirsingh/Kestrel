import android.graphics.Canvas
import android.graphics.RectF
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object SpriteSheet {
    @JvmStatic fun main(a: Array<String>) {
        val sc = 2f
        val t0 = System.currentTimeMillis()
        val ss = SpriteSet(sc)
        println("sprites built in ${System.currentTimeMillis() - t0} ms")
        val list = listOf(ss.bulletPink, ss.bulletOrange, ss.bulletBig, ss.needle, ss.bolt, ss.boltHeavy, ss.droneShot, ss.coreSmall, ss.coreBig,
            ss.capRepair, ss.capShield, ss.capSurge, ss.capOver, ss.fireball, ss.smoke, ss.scorch, ss.flare, ss.survivor) + ss.clouds.toList()
        val W = 1800; val H = 1500
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        c.drawColor(0xFF6F8F6A.toInt())
        var x = 10f; var y = 10f; var rowH = 0f
        for (s in list) {
            val w = s.w * sc; val h = s.h * sc
            if (x + w > W) { x = 10f; y += rowH + 10f; rowH = 0f }
            s.shadow?.let { c.drawBitmap(it, null, RectF(x + 8f, y + 12f, x + 8f + w, y + 12f + h), android.graphics.Paint().apply { alpha = 90 }) }
            c.drawBitmap(s.bmp, null, RectF(x, y, x + w, y + h), null)
            x += w + 10f; rowH = maxOf(rowH, h)
        }
        ImageIO.write(img, "png", File("/home/claude/kestrel/shots/sprites.png"))
    }
}
