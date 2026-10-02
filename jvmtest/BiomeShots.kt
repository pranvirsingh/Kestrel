import android.graphics.Canvas
import android.graphics.RectF
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object BiomeShots {
    @JvmStatic fun main(a: Array<String>) {
        val R = if (a.isNotEmpty()) a[0].toFloat() else 0.5f
        val only = if (a.size > 1) a[1].toInt() else -1
        val ch = 2
        val pw = (1000 * R).toInt(); val ph = (1000 * R).toInt() * ch
        val n = if (only >= 0) 1 else 6
        val img = BufferedImage(pw * n + 10 * (n - 1), ph, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)
        for (bi in 0 until 6) {
            if (only >= 0 && bi != only) continue
            val col = if (only >= 0) 0 else bi
            val b = Biome.make(bi)
            val t0 = System.currentTimeMillis()
            for (k in 0 until ch) {
                val bmp = Terrain.render(b, k + 3, R)
                val y = (ch - 1 - k) * (1000 * R)
                c.drawBitmap(bmp, null, RectF(col * (pw + 10f), y, col * (pw + 10f) + pw, y + 1000 * R), null)
            }
            println("biome $bi ${(System.currentTimeMillis() - t0) / ch} ms/chunk")
        }
        ImageIO.write(img, "png", File("/home/claude/kestrel/shots/biomes.png"))
    }
}
