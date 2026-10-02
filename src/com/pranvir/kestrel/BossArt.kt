package com.pranvir.kestrel

import kotlin.math.PI

/** Boss hull and part sprites, rendered in 3D when a mission loads (off the UI thread) and released after it. */
class BossArt(val type: Int, val scale: Float, cacheDir: java.io.File? = null, progress: (Float) -> Unit = {}) {
    private val all = ArrayList<Spr>()
    private val PI_F = PI.toFloat()
    private var done = 0
    private val total = 7 + 3 * Units3D.TURRET_FRAMES
    private val cacheFile = SprCache.file(cacheDir, "boss$type", scale)
    private val cached = SprCache.read(cacheFile, total)

    private fun r(m: Mesh, yaw: Float, pg: (Float) -> Unit): Spr {
        val s = cached?.removeFirstOrNull() ?: R3.toSpr(R3.render(m, scale, yaw))
        all.add(s); done++; pg(done / total.toFloat())
        return s
    }
    private fun frames(m: Mesh, pg: (Float) -> Unit) = Frames(Array(Units3D.TURRET_FRAMES) { r(m, it * 2f * PI_F / Units3D.TURRET_FRAMES, pg) })

    val hull: Spr = r(Models.boss(type), PI_F, progress)
    val bigCannon = frames(Models.bigCannon(), progress)
    val gatling = frames(Models.gatling(), progress)
    val flamer = frames(Models.flamer(), progress)
    val ringCannon = r(Models.ringCannon(), 0f, progress)
    val laser = r(Models.laserEmitter(), 0f, progress)
    val bay = r(Models.bay(), PI_F, progress)
    val mortar = r(Models.mortar(), PI_F, progress)
    val coreOpen = r(Models.core(true), 0f, progress)
    val coreShut = r(Models.core(false), 0f, progress)

    init {
        check(all.size == total) { "BossArt sprite count ${all.size} != $total" }
        if (cached == null) SprCache.write(cacheFile, all)
    }

    fun release() { for (s in all) s.release(); all.clear() }
}
