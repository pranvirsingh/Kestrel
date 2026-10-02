import com.pranvir.kestrel.*
object CacheTest {
    @JvmStatic fun main(a: Array<String>) {
        val dir = java.io.File("/tmp/claude-0/sprcache"); dir.deleteRecursively(); dir.mkdirs()
        var t = System.currentTimeMillis()
        val u1 = Units3D(1.08f, dir); val t1 = System.currentTimeMillis() - t
        t = System.currentTimeMillis()
        val u2 = Units3D(1.08f, dir); val t2 = System.currentTimeMillis() - t
        check(u1.dart.w == u2.dart.w && u1.bunker.bmp.width == u2.bunker.bmp.width && u1.radarDish.f.size == u2.radarDish.f.size)
        val px1 = IntArray(u1.player[2].bmp.width * u1.player[2].bmp.height); u1.player[2].bmp.getPixels(px1, 0, u1.player[2].bmp.width, 0, 0, u1.player[2].bmp.width, u1.player[2].bmp.height)
        val px2 = IntArray(px1.size); u2.player[2].bmp.getPixels(px2, 0, u2.player[2].bmp.width, 0, 0, u2.player[2].bmp.width, u2.player[2].bmp.height)
        check(px1.contentEquals(px2)) { "cache pixels differ" }
        t = System.currentTimeMillis(); BossArt(5, 1.08f, dir); val t3 = System.currentTimeMillis() - t
        t = System.currentTimeMillis(); BossArt(5, 1.08f, dir); val t4 = System.currentTimeMillis() - t
        println("units render ${t1}ms cached ${t2}ms; boss render ${t3}ms cached ${t4}ms; files ${dir.listFiles()!!.map { it.name + ":" + it.length() / 1024 + "KB" }}")
    }
}
