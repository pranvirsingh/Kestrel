import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.pranvir.kestrel.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object IconGen {
    val ground = Terrain.render(Biome.make(0), 4, 0.6f)
    fun art(c: Canvas, s: Float, safe: Float, bg: Boolean, mono: Boolean = false) {
        if (bg) {
            val p = Paint(Paint.FILTER_BITMAP_FLAG)
            c.drawBitmap(ground, null, RectF(-s * 0.15f, -s * 0.1f, s * 1.2f, s * 1.25f), p)
            val v = Paint(); v.shader = RadialGradient(s / 2f, s / 2f, s * 0.75f, intArrayOf(0x00000000, 0x22000000, 0xAA02060E.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s, s, v)
        }
        val sh = R3.toSpr(R3.render(Models.kestrel(true), safe / 100f * 1.2f, 0f, 0f, 2, true, 0.6f))
        c.save(); c.translate(s / 2f, s / 2f); c.rotate(-18f)
        val k = safe / 100f
        if (!mono) {
            c.drawBitmap(sh.shadow!!, null, RectF(-safe * 0.42f + safe * 0.12f, -safe * 0.45f + safe * 0.2f, safe * 0.42f + safe * 0.12f, safe * 0.45f + safe * 0.2f), Paint().apply { alpha = 110 })
            Draw.glow(c, -4.5f * k, 54f * k, 30f * k, 1f, 0xFF7FE8FF.toInt())
            Draw.glow(c, 4.5f * k, 54f * k, 30f * k, 1f, 0xFF7FE8FF.toInt())
            c.drawBitmap(sh.bmp, null, RectF(-safe * 0.4f, -safe * 0.43f, safe * 0.4f, safe * 0.43f), Paint(Paint.FILTER_BITMAP_FLAG))
        } else c.drawBitmap(sh.flash!!, null, RectF(-safe * 0.4f, -safe * 0.43f, safe * 0.4f, safe * 0.43f), Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
        sh.release()
    }
    fun save(img: BufferedImage, path: String) { val f = File(path); f.parentFile.mkdirs(); ImageIO.write(img, "png", f) }

    @JvmStatic fun main(a: Array<String>) {
        val res = "/home/claude/kestrel/res"
        for ((dn, k) in linkedMapOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)) {
            val fs = (108 * k).toInt()
            val fg = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            art(Canvas(fg), fs.toFloat(), fs * 0.6f, true)
            save(fg, "$res/mipmap-$dn/ic_launcher_fg.png")
            val mono = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            art(Canvas(mono), fs.toFloat(), fs * 0.6f, false, true)
            save(mono, "$res/mipmap-$dn/ic_launcher_mono.png")
            val ls = (48 * k).toInt()
            for (round in listOf(false, true)) {
                val big = ls * 4
                val img = BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB)
                val c = Canvas(img)
                val clip = Path(); val inset = big * 0.04f
                if (round) clip.addCircle(big / 2f, big / 2f, big / 2f - inset, Path.Direction.CW)
                else clip.addRoundRect(RectF(inset, inset, big - inset, big - inset), big * 0.18f, big * 0.18f, Path.Direction.CW)
                c.save(); c.clipPath(clip); art(c, big.toFloat(), big * 0.78f, true); c.restore()
                val out = BufferedImage(ls, ls, BufferedImage.TYPE_INT_ARGB)
                out.createGraphics().drawImage(img.getScaledInstance(ls, ls, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
                save(out, "$res/mipmap-$dn/" + (if (round) "ic_launcher_round.png" else "ic_launcher.png"))
            }
        }
        val prev = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        art(Canvas(prev), 512f, 512f * 0.75f, true)
        save(prev, "/home/claude/kestrel/shots/icon512.png")
        println("ICONS OK")
    }
}
