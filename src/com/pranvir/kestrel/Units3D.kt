package com.pranvir.kestrel

import kotlin.math.PI
import kotlin.math.roundToInt

/** Pre-rendered rotation frames for turrets and dishes: pick by the game's aim angle (0 = facing down the screen). */
class Frames(val f: Array<Spr>) {
    fun pick(tur: Float): Spr {
        val n = f.size
        var k = (((tur + PI.toFloat()) / (2f * PI.toFloat())) * n).roundToInt() % n
        if (k < 0) k += n
        return f[k]
    }
}

/**
 * Every aircraft and ground unit rendered in 3D by [R3]. Built off the UI thread (no shared paints are
 * touched); [progress] reports 0..1 as it goes.
 */
class Units3D(val scale: Float, cacheDir: java.io.File? = null, private val progress: (Float) -> Unit = {}) {
    private val all = ArrayList<Spr>()
    private var done = 0
    private val total = 15 + 18 + 4 * TURRET_FRAMES
    private val cacheFile = SprCache.file(cacheDir, "units", scale)
    private val cached = SprCache.read(cacheFile, total)
    companion object {
        const val TURRET_FRAMES = 12
        val BANKS = floatArrayOf(-0.55f, -0.27f, 0f, 0.27f, 0.55f)
        /** Aircraft and ground units read better a little larger than their old flat art. */
        const val AIR = 1.22f
        const val GROUND = 1.15f
    }
    private val PI_F = PI.toFloat()

    /** [size] enlarges the model in the world (rendered at matching resolution so it stays sharp). */
    private fun r(m: Mesh, yaw: Float = 0f, roll: Float = 0f, size: Float = 1f): Spr {
        val s = cached?.removeFirstOrNull() ?: R3.toSpr(R3.render(m, scale * size, yaw, roll), size)
        all.add(s)
        done++
        progress(done / total.toFloat())
        return s
    }
    private fun rA(m: Mesh, yaw: Float) = r(m, yaw, 0f, AIR)
    private fun rG(m: Mesh, yaw: Float) = r(m, yaw, 0f, GROUND)
    private fun frames(m: Mesh, size: Float = 1.15f): Frames = Frames(Array(TURRET_FRAMES) { r(m, it * 2f * PI_F / TURRET_FRAMES, 0f, size) })

    val player: Array<Spr> = Models.kestrel(false).let { m -> Array(BANKS.size) { r(m, 0f, BANKS[it], AIR) } }
    val playerPods: Array<Spr> = Models.kestrel(true).let { m -> Array(BANKS.size) { r(m, 0f, BANKS[it], AIR) } }
    val playerGold: Array<Spr> = Models.kestrel(true, true).let { m -> Array(BANKS.size) { r(m, 0f, BANKS[it], AIR) } }
    val drone = r(Models.drone(), 0f, 0f, AIR)
    val missile = r(Models.playerMissile())
    val dart = rA(Models.dart(), PI_F)
    val swoop = rA(Models.swoop(), PI_F)
    val wasp = rA(Models.wasp(), PI_F)
    val gunship = rA(Models.gunship(), PI_F)
    val bomber = rA(Models.bomber(), PI_F)
    val lancer = rA(Models.lancer(), PI_F)
    val mine = rA(Models.mine(), PI_F)
    val carrier = rA(Models.carrier(), PI_F)
    val enemyMissile = rA(Models.enemyMissile(), PI_F)
    val tank = rG(Models.tankHull(), PI_F)
    val flakBase = rG(Models.flakBase(), 0f)
    val sam = rG(Models.sam(), PI_F)
    val gunboat = rG(Models.gunboat(), PI_F)
    val trainCar = rG(Models.trainCar(), PI_F)
    val radarBase = rG(Models.radarBase(), 0f)
    val bunker = rG(Models.bunker(), PI_F)
    val tankTurret = frames(Models.tankTurret())
    val flakGun = frames(Models.flakGun())
    val smallTurret = frames(Models.smallTurret())
    val radarDish = frames(Models.radarDish())

    init {
        check(all.size == total) { "Units3D sprite count ${all.size} != $total" }
        if (cached == null) SprCache.write(cacheFile, all)
    }

    fun release() { for (s in all) s.release(); all.clear() }
}
