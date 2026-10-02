package com.pranvir.kestrel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D models for every unit and boss. Model space: x right, y forward (the nose), z up.
 * Enemies are modelled nose-forward and rendered turned to face down the screen.
 */
object Models {
    private const val HPI = (PI / 2).toFloat()

    // materials
    val GUNMETAL = Mat(0xFF5E6B7E.toInt(), 0.55f, 34f, 0.55f, grime = 0.28f, panels = true)
    val GUNMETAL_D = Mat(0xFF46505F.toInt(), 0.5f, 30f, 0.5f, grime = 0.3f, panels = true)
    val ACCENT = Mat(0xFF3FD2FF.toInt(), 0.7f, 40f, 0.4f, emissive = 0xFF0C3A50.toInt(), grime = 0.1f)
    val DARK = Mat(0xFF1A1E24.toInt(), 0.25f, 16f, 0.2f, grime = 0.2f)
    val GLASS_BLUE = Mat(0xFF2266AA.toInt(), 1.1f, 60f, 0.9f, glass = true, grime = 0f)
    val GLOW_CYAN = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFF7FE8FF.toInt(), grime = 0f)
    val GOLD = Mat(0xFFD9A93C.toInt(), 0.9f, 40f, 0.5f, grime = 0.15f)
    val LIGHT_RED = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFFFF3030.toInt(), grime = 0f)
    val LIGHT_GREEN = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFF30FF70.toInt(), grime = 0f)

    val E_HULL = Mat(0xFF484C57.toInt(), 0.45f, 28f, 0.45f, grime = 0.4f, panels = true)
    val E_HULL_D = Mat(0xFF34373F.toInt(), 0.4f, 24f, 0.4f, grime = 0.45f, panels = true)
    val E_RED = Mat(0xFFC8283A.toInt(), 0.55f, 30f, 0.4f, emissive = 0xFF250308.toInt(), grime = 0.2f)
    val E_GLASS = Mat(0xFFB0202A.toInt(), 1.1f, 60f, 0.9f, glass = true, grime = 0f)
    val E_GLOW = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFFFF8A3D.toInt(), grime = 0f)
    val E_PINK = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFFFF4FA8.toInt(), grime = 0f)
    val STEEL = Mat(0xFF767C87.toInt(), 0.5f, 26f, 0.4f, grime = 0.4f, panels = true)
    val STEEL_D = Mat(0xFF4F545D.toInt(), 0.4f, 22f, 0.35f, grime = 0.45f)
    val TREAD = Mat(0xFF24262B.toInt(), 0.2f, 12f, 0.2f, grime = 0.5f, panels = true)
    val RUST = Mat(0xFF775A47.toInt(), 0.25f, 14f, 0.25f, grime = 0.55f, panels = true)
    val CONCRETE = Mat(0xFF84867F.toInt(), 0.15f, 10f, 0.2f, grime = 0.6f, panels = true)
    val HAZARD = Mat(0xFFE8B23A.toInt(), 0.3f, 16f, 0.3f, grime = 0.35f)
    val WOOD = Mat(0xFF8A7558.toInt(), 0.15f, 10f, 0.2f, grime = 0.45f, panels = true)
    val SNOW = Mat(0xFFF2F6FA.toInt(), 0.2f, 12f, 0.5f, grime = 0.05f)
    val ICE_GLOW = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFF8FE6FF.toInt(), grime = 0f)
    val NEON_PINK = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFFFF3BD0.toInt(), grime = 0f)
    val NEON_CYAN = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFF1FA8C0.toInt(), grime = 0f)
    val FURNACE = Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFFFF6A1E.toInt(), grime = 0f)
    val VIOLET = Mat(0xFF3A3346.toInt(), 0.55f, 34f, 0.6f, grime = 0.3f, panels = true)
    val VIOLET_L = Mat(0xFF4D4560.toInt(), 0.55f, 34f, 0.6f, grime = 0.25f, panels = true)
    val PRISM = Mat(0xFFFFFFFF.toInt(), 0.4f, 30f, 0.3f, emissive = 0xFFC060FF.toInt(), grime = 0f)
    val SAND_ARMOR = Mat(0xFF8D7C62.toInt(), 0.35f, 20f, 0.35f, grime = 0.5f, panels = true)

    // ------------------------------------------------------------------ player

    fun kestrel(pods: Boolean, legend: Boolean = false): Mesh {
        val m = Mesh()
        val hull = m.mat(if (legend) GOLD else GUNMETAL); val hullD = m.mat(GUNMETAL_D); val acc = m.mat(ACCENT); val dark = m.mat(DARK)
        val glass = m.mat(GLASS_BLUE); val glow = m.mat(GLOW_CYAN)
        m.loft(arrayOf(
            floatArrayOf(-50f, 0f, 2f, 7f, 5f, 4f), floatArrayOf(-34f, 0f, 3f, 10f, 7f, 5f), floatArrayOf(-4f, 0f, 3f, 11.5f, 9f, 6f),
            floatArrayOf(18f, 0f, 4f, 9.5f, 9f, 5f), floatArrayOf(34f, 0f, 3f, 6.5f, 6.5f, 4f), floatArrayOf(46f, 0f, 2f, 3f, 3.5f, 2.5f),
            floatArrayOf(54f, 0f, 1.5f, 0.6f, 0.6f, 0.6f)), 18, hull, capBack = true)
        m.ellipsoid(0f, 24f, 9.5f, 5.2f, 13f, 5.5f, 14, glass)
        m.wing(floatArrayOf(8f, 6f, 46f, -20f, 47.5f, -29f, 40f, -31f, 10f, -25f), 1.5f, 4.5f, hull, tipThin = 0.4f)
        m.wing(floatArrayOf(9f, 5f, 45.5f, -19.5f, 46f, -22f, 9f, 2f), 3.9f, 0.8f, acc, tipThin = 1f)
        m.wing(floatArrayOf(8f, 28f, 21f, 19f, 21f, 14f, 9f, 17f), 3f, 2.2f, hullD)
        m.wing(floatArrayOf(6f, -32f, 23f, -45f, 23f, -51f, 6f, -48f), 2f, 2.4f, hullD)
        for (sx in floatArrayOf(-1f, 1f)) {
            m.push(); m.translate(sx * 8f, -38f, 6f); m.rotY(-sx * 0.35f); m.rotY(-sx * HPI)
            m.wing(floatArrayOf(0f, 6f, 15f, -6f, 15f, -11f, 0f, -14f), 0f, 2f, hullD, mirror = false)
            m.pop()
            m.box(sx * 11f, 0f, 3f, 3f, 16f, 6f, dark)
            m.cyl(sx * 4.6f, -47f, 2.5f, sx * 4.6f, -55f, 2.5f, 4.6f, 4.2f, 12, dark)
            m.ellipsoid(sx * 4.6f, -55.2f, 2.5f, 3f, 0.8f, 3f, 10, glow)
            m.ellipsoid(sx * 46.5f, -25f, 1.8f, 1.4f, 1.4f, 1.4f, 8, m.mat(if (sx < 0) LIGHT_RED else LIGHT_GREEN))
            if (pods) {
                m.cyl(sx * 29f, -14f, -2f, sx * 29f, 12f, -2f, 3.8f, 3.8f, 12, hullD)
                m.ellipsoid(sx * 29f, 13f, -2f, 3.6f, 5f, 3.6f, 10, m.mat(GOLD))
            }
        }
        return m
    }

    fun drone(): Mesh {
        val m = Mesh()
        m.ellipsoid(0f, 0f, 4f, 8f, 9f, 6f, 14, m.mat(GUNMETAL))
        m.cyl(0f, 0f, 2f, 0f, 0f, 6f, 13f, 13f, 20, m.mat(GUNMETAL_D))
        m.ellipsoid(0f, 5f, 7f, 3.5f, 3.5f, 2.5f, 10, m.mat(GLOW_CYAN))
        return m
    }

    fun playerMissile(): Mesh {
        val m = Mesh()
        m.cyl(0f, -16f, 0f, 0f, 14f, 0f, 3.6f, 3.6f, 10, m.mat(STEEL))
        m.ellipsoid(0f, 15f, 0f, 3.6f, 6f, 3.6f, 10, m.mat(ACCENT))
        m.wing(floatArrayOf(3f, -8f, 9f, -16f, 3f, -16f), 0f, 1f, m.mat(STEEL_D))
        m.ellipsoid(0f, -17f, 0f, 2.5f, 1f, 2.5f, 8, m.mat(GLOW_CYAN))
        return m
    }

    // ------------------------------------------------------------------ air enemies (nose +y; rendered facing down)

    fun dart(): Mesh {
        val m = Mesh()
        val h = m.mat(E_HULL); val r = m.mat(E_RED)
        m.loft(arrayOf(floatArrayOf(-30f, 0f, 2f, 4f, 3f, 3f), floatArrayOf(-20f, 0f, 3f, 7f, 5f, 4f), floatArrayOf(0f, 0f, 3f, 8f, 6f, 4f),
            floatArrayOf(18f, 0f, 3f, 5f, 5f, 3f), floatArrayOf(31f, 0f, 2f, 0.6f, 0.6f, 0.6f)), 14, h)
        m.wing(floatArrayOf(5f, 8f, 28f, -14f, 29.5f, -21f, 20f, -20f, 6f, -10f), 1f, 3f, h)
        m.box(28f, -17.5f, 1.5f, 3.2f, 7f, 3f, r); m.box(-28f, -17.5f, 1.5f, 3.2f, 7f, 3f, r)
        m.ellipsoid(0f, 12f, 6.5f, 3.5f, 7f, 3f, 10, m.mat(E_GLASS))
        m.ellipsoid(0f, -31f, 2f, 2.6f, 1f, 2.6f, 8, m.mat(E_GLOW))
        return m
    }

    fun swoop(): Mesh {
        val m = Mesh()
        val h = m.mat(Mat(0xFF4E434F.toInt(), 0.45f, 28f, 0.45f, grime = 0.4f, panels = true)); val r = m.mat(E_RED)
        m.wing(floatArrayOf(0f, 22f, 16f, 10f, 33f, -8f, 33f, -16f, 22f, -12f, 10f, -6f, 0f, -12f), 2f, 6f, h, tipThin = 0.3f, span = 33f)
        m.wing(floatArrayOf(15f, 10f, 32.5f, -8f, 31f, -10.5f, 14f, 7.5f), 4.6f, 0.8f, r, tipThin = 1f)
        m.ellipsoid(0f, 4f, 4f, 8f, 15f, 5f, 14, h)
        m.ellipsoid(0f, 8f, 7.5f, 4f, 6f, 2.6f, 10, m.mat(E_GLASS))
        for (sx in floatArrayOf(-8f, 8f)) m.ellipsoid(sx, -11f, 3f, 2.4f, 1.2f, 2.4f, 8, m.mat(E_GLOW))
        return m
    }

    fun wasp(): Mesh {
        val m = Mesh()
        val h = m.mat(Mat(0xFF54434A.toInt(), 0.45f, 28f, 0.45f, grime = 0.4f, panels = true)); val d = m.mat(DARK)
        m.cyl(0f, 0f, 0f, 0f, 0f, 9f, 13f, 11f, 6, h)
        for (k in 0 until 4) {
            val a = PI.toFloat() / 4f + k * HPI
            val x = cos(a) * 20f; val y = sin(a) * 20f
            m.push(); m.translate(x * 0.5f, y * 0.5f, 5f); m.rotZ(a); m.box(0f, 0f, 0f, 20f, 4f, 3f, d); m.pop()
            m.cyl(x, y, 3f, x, y, 9f, 4f, 3f, 10, d)
            m.push(); m.translate(x, y, 9.5f); m.rotZ(a * 1.7f)
            m.box(0f, 0f, 0f, 20f, 2.2f, 0.8f, m.mat(STEEL_D)); m.box(0f, 0f, 0f, 2.2f, 20f, 0.8f, m.mat(STEEL_D)); m.pop()
        }
        m.ellipsoid(0f, 4f, 9f, 5f, 5f, 3.5f, 12, m.mat(E_PINK))
        return m
    }

    fun gunship(): Mesh {
        val m = Mesh()
        val h = m.mat(E_HULL); val hd = m.mat(E_HULL_D); val r = m.mat(E_RED); val d = m.mat(DARK)
        m.loft(arrayOf(floatArrayOf(-58f, 0f, 6f, 16f, 10f, 8f), floatArrayOf(-30f, 0f, 8f, 26f, 15f, 10f), floatArrayOf(10f, 0f, 9f, 28f, 17f, 10f),
            floatArrayOf(38f, 0f, 7f, 18f, 12f, 8f), floatArrayOf(54f, 0f, 4f, 8f, 6f, 4f), floatArrayOf(58f, 0f, 3f, 2f, 2f, 2f)), 18, h)
        m.wing(floatArrayOf(20f, 10f, 70f, 2f, 74f, -14f, 22f, -18f), 8f, 7f, hd)
        for (sx in floatArrayOf(-62f, 62f)) {
            m.cyl(sx, -24f, 8f, sx, 26f, 8f, 8.5f, 7.5f, 14, h)
            m.cyl(sx, 26f, 8f, sx, 38f, 8f, 2.6f, 2.6f, 8, d)
            m.box(sx, -6f, 8f, 18f, 4f, 18f, r)
        }
        m.box(0f, 0f, 24f, 46f, 5f, 4f, r)
        m.ellipsoid(0f, 32f, 14f, 9f, 12f, 6f, 12, m.mat(E_GLASS))
        for (sx in floatArrayOf(-16f, 16f)) { m.cyl(sx, -38f, 18f, sx, -38f, 25f, 10f, 10f, 14, d); m.ellipsoid(sx, -38f, 25f, 5f, 5f, 1.5f, 10, m.mat(E_GLOW)) }
        return m
    }

    fun bomber(): Mesh {
        val m = Mesh()
        val h = m.mat(Mat(0xFF46434B.toInt(), 0.45f, 28f, 0.45f, grime = 0.4f, panels = true)); val r = m.mat(E_RED); val d = m.mat(DARK)
        m.wing(floatArrayOf(0f, 50f, 40f, 36f, 122f, -22f, 124f, -36f, 90f, -32f, 40f, -40f, 0f, -48f), 6f, 16f, h, tipThin = 0.3f, span = 124f)
        m.wing(floatArrayOf(40f, 35f, 121f, -21f, 120f, -25f, 38f, 31f), 13.5f, 1f, r, tipThin = 1f)
        for (sx in floatArrayOf(-74f, -40f, 40f, 74f)) {
            val sy = if (kotlin.math.abs(sx) > 50f) -30f else -18f
            m.cyl(sx, sy - 20f, 8f, sx, sy + 20f, 8f, 9.5f, 8.5f, 14, h)
            m.ellipsoid(sx, sy - 21f, 8f, 6f, 1.2f, 6f, 10, m.mat(E_GLOW))
        }
        m.ellipsoid(0f, 32f, 14f, 12f, 10f, 6f, 12, m.mat(E_GLASS))
        m.box(0f, 0f, 15f, 36f, 24f, 3f, d)
        return m
    }

    fun lancer(): Mesh {
        val m = Mesh()
        val h = m.mat(E_HULL); val r = m.mat(E_RED)
        m.loft(arrayOf(floatArrayOf(-46f, 0f, 3f, 5f, 4f, 3f), floatArrayOf(-24f, 0f, 4f, 10f, 7f, 5f), floatArrayOf(0f, 0f, 4f, 11f, 7f, 5f),
            floatArrayOf(14f, 0f, 4f, 8f, 6f, 4f), floatArrayOf(25f, 0f, 3f, 2f, 2f, 2f)), 14, h)
        for (sx in floatArrayOf(-9f, 9f)) m.loft(arrayOf(floatArrayOf(-8f, sx, 3f, 5f, 4f, 3f), floatArrayOf(28f, sx * 0.95f, 3f, 4f, 3f, 2f), floatArrayOf(46f, sx * 0.7f, 3f, 1f, 1f, 1f)), 10, h)
        m.wing(floatArrayOf(10f, -18f, 24f, -34f, 22f, -40f, 8f, -32f), 3f, 2.5f, m.mat(E_HULL_D))
        m.box(12f, -6f, 6f, 3f, 12f, 4f, r); m.box(-12f, -6f, 6f, 3f, 12f, 4f, r)
        m.ellipsoid(0f, 36f, 3f, 4f, 4f, 4f, 10, m.mat(E_PINK))
        m.ellipsoid(0f, -46f, 3f, 3f, 1f, 3f, 8, m.mat(E_GLOW))
        return m
    }

    fun mine(): Mesh {
        val m = Mesh()
        val b = m.mat(Mat(0xFF5A3A3F.toInt(), 0.6f, 30f, 0.5f, grime = 0.35f))
        val sp = m.mat(STEEL)
        m.ellipsoid(0f, 0f, 0f, 13f, 13f, 13f, 16, b)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            m.cyl(cos(a) * 10f, sin(a) * 10f, 0f, cos(a) * 21f, sin(a) * 21f, 0f, 3f, 0.6f, 8, sp)
        }
        m.cyl(0f, 0f, 10f, 0f, 0f, 20f, 3f, 0.6f, 8, sp)
        m.ellipsoid(0f, 0f, 12.5f, 4f, 4f, 2f, 10, m.mat(LIGHT_RED))
        return m
    }

    fun enemyMissile(): Mesh {
        val m = Mesh()
        m.cyl(0f, -16f, 0f, 0f, 14f, 0f, 4.2f, 4.2f, 10, m.mat(E_HULL))
        m.ellipsoid(0f, 15f, 0f, 4.2f, 6f, 4.2f, 10, m.mat(E_RED))
        m.wing(floatArrayOf(4f, -8f, 10f, -17f, 4f, -17f), 0f, 1f, m.mat(E_HULL_D))
        m.ellipsoid(0f, -17f, 0f, 3f, 1f, 3f, 8, m.mat(E_GLOW))
        return m
    }

    fun carrier(): Mesh {
        val m = Mesh()
        val h = m.mat(Mat(0xFF41454F.toInt(), 0.45f, 28f, 0.45f, grime = 0.4f, panels = true)); val d = m.mat(DARK); val r = m.mat(E_RED)
        m.wing(floatArrayOf(0f, 112f, 50f, 100f, 120f, 40f, 150f, -20f, 140f, -90f, 70f, -112f, 0f, -108f), 6f, 30f, h, tipThin = 0.45f, span = 150f)
        m.wing(floatArrayOf(0f, 82f, 40f, 72f, 46f, -82f, 0f, -88f), 21.5f, 3f, m.mat(Mat(0xFF2A2D33.toInt(), 0.3f, 16f, 0.3f, grime = 0.5f, panels = true)))
        for (k in 0 until 7) m.box(0f, -70f + k * 22f, 23.5f, 2f, 9f, 1f, m.mat(HAZARD))
        for (sx in floatArrayOf(-95f, 95f)) {
            m.box(sx, -10f, 16f, 46f, 62f, 8f, d)
            m.box(sx, 21f, 20f, 46f, 3f, 2f, m.mat(E_GLOW))
            m.box(sx, -42f, 20f, 46f, 3f, 2f, r)
        }
        m.box(70f, -30f, 34f, 18f, 40f, 26f, m.mat(E_HULL_D))
        m.ellipsoid(70f, -10f, 44f, 6f, 4f, 4f, 10, m.mat(E_GLASS))
        for (k in 0 until 3) m.ellipsoid(-60f + k * 60f, -104f, 8f, 8f, 3f, 8f, 10, m.mat(E_GLOW))
        return m
    }

    // ------------------------------------------------------------------ ground

    fun tankHull(): Mesh {
        val m = Mesh()
        val tr = m.mat(TREAD); val st = m.mat(STEEL)
        for (sx in floatArrayOf(-20f, 20f)) m.box(sx, 0f, 5f, 12f, 66f, 10f, tr)
        m.loft(arrayOf(floatArrayOf(-30f, 0f, 9f, 17f, 6f, 3f), floatArrayOf(22f, 0f, 9f, 18f, 6f, 3f), floatArrayOf(32f, 0f, 8f, 14f, 3f, 3f)), 8, st, capFront = true)
        m.box(0f, -24f, 14f, 30f, 6f, 2f, m.mat(E_RED))
        return m
    }
    fun tankTurret(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 14f, 0f, 0f, 22f, 13f, 11f, 16, m.mat(STEEL))
        m.cyl(0f, 8f, 18f, 0f, 38f, 18f, 2.6f, 2.4f, 10, m.mat(STEEL_D))
        m.cyl(0f, 36f, 18f, 0f, 40f, 18f, 3.6f, 3.6f, 10, m.mat(DARK))
        m.ellipsoid(-4f, -4f, 22.5f, 3f, 3f, 1.5f, 8, m.mat(E_RED))
        return m
    }
    fun flakBase(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 0f, 0f, 0f, 8f, 30f, 28f, 8, m.mat(CONCRETE))
        for (k in 0 until 8) { val a = k * PI.toFloat() / 4f; m.box(cos(a) * 24f, sin(a) * 24f, 8.3f, 4f, 4f, 0.8f, m.mat(HAZARD)) }
        return m
    }
    fun flakGun(): Mesh {
        val m = Mesh()
        m.box(0f, -2f, 14f, 26f, 24f, 12f, m.mat(STEEL))
        for (sx in floatArrayOf(-5f, 5f)) m.cyl(sx, 8f, 16f, sx, 34f, 20f, 2.2f, 2f, 8, m.mat(DARK))
        m.box(0f, -2f, 20.5f, 26f, 4f, 1f, m.mat(E_RED))
        return m
    }
    fun sam(): Mesh {
        val m = Mesh()
        m.box(0f, 0f, 6f, 48f, 52f, 12f, m.mat(STEEL_D))
        for (k in 0 until 4) {
            val x = -12f + (k % 2) * 24f; val y = -10f + (k / 2) * 20f
            m.cyl(x, y - 6f, 13f, x, y + 10f, 22f, 6.5f, 6.5f, 12, m.mat(STEEL))
            m.ellipsoid(x, y + 10.5f, 22.3f, 4f, 1.5f, 4f, 8, m.mat(E_RED))
        }
        return m
    }
    fun gunboat(): Mesh {
        val m = Mesh()
        m.loft(arrayOf(floatArrayOf(-58f, 0f, 4f, 15f, 5f, 6f), floatArrayOf(-20f, 0f, 4f, 20f, 5f, 7f), floatArrayOf(20f, 0f, 4f, 19f, 5f, 7f),
            floatArrayOf(46f, 0f, 4f, 11f, 5f, 6f), floatArrayOf(63f, 0f, 5f, 1f, 3f, 4f)), 16, m.mat(STEEL))
        m.wing(floatArrayOf(0f, 50f, 10f, 30f, 14f, -52f, 0f, -54f), 9f, 1.2f, m.mat(STEEL_D), tipThin = 1f)
        m.box(0f, -22f, 15f, 18f, 28f, 10f, m.mat(STEEL_D))
        m.box(0f, -12f, 18f, 18f, 6f, 5f, m.mat(E_GLASS))
        m.cyl(0f, -34f, 20f, 0f, -34f, 30f, 2f, 1.5f, 8, m.mat(DARK))
        m.box(0f, -34f, 21f, 20f, 3f, 2f, m.mat(E_RED))
        return m
    }
    fun smallTurret(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 10f, 0f, 0f, 16f, 9f, 8f, 14, m.mat(STEEL))
        m.cyl(0f, 6f, 13f, 0f, 26f, 13f, 2.2f, 2f, 8, m.mat(DARK))
        m.ellipsoid(0f, 0f, 16.3f, 3f, 3f, 1f, 8, m.mat(E_RED))
        return m
    }
    fun trainCar(): Mesh {
        val m = Mesh()
        m.box(0f, 0f, 3f, 40f, 104f, 6f, m.mat(TREAD))
        m.box(0f, 0f, 14f, 50f, 112f, 18f, m.mat(RUST))
        for (k in 0 until 7) m.box(0f, -48f + k * 16f, 23.5f, 46f, 3f, 1.5f, m.mat(Mat(0xFF5E4638.toInt(), 0.2f, 12f, 0.2f, grime = 0.6f)))
        m.box(0f, 0f, 23.8f, 52f, 8f, 1.5f, m.mat(E_RED))
        return m
    }
    fun radarBase(): Mesh {
        val m = Mesh()
        m.box(0f, 0f, 10f, 60f, 60f, 20f, m.mat(Mat(0xFF404657.toInt(), 0.4f, 24f, 0.4f, grime = 0.35f, panels = true)))
        m.box(0f, 0f, 20.3f, 50f, 50f, 0.8f, m.mat(NEON_CYAN))
        m.cyl(0f, 0f, 20f, 0f, 0f, 34f, 12f, 9f, 14, m.mat(STEEL))
        return m
    }
    fun radarDish(): Mesh {
        val m = Mesh()
        m.push(); m.translate(0f, 0f, 40f); m.rotX(0.5f)
        m.cyl(0f, -2f, 0f, 0f, 3f, 0f, 40f, 36f, 20, m.mat(Mat(0xFFD7DCE6.toInt(), 0.6f, 30f, 0.5f, grime = 0.2f)))
        m.pop()
        m.ellipsoid(0f, 0f, 40f, 4f, 4f, 4f, 10, m.mat(NEON_PINK))
        return m
    }
    fun bunker(): Mesh {
        val m = Mesh()
        m.box(0f, 0f, 12f, 100f, 72f, 24f, m.mat(CONCRETE))
        for (k in 0 until 9) m.box(-44f + k * 11f, 0f, 25f, 6f, 66f, 3f, m.mat(Mat(0xFF6A6C66.toInt(), 0.3f, 16f, 0.3f, grime = 0.5f)))
        for (k in 0 until 6) m.box(-42f + k * 17f, -35.5f, 8f, 8.5f, 1.5f, 10f, m.mat(if (k % 2 == 0) HAZARD else DARK))
        return m
    }

    // ------------------------------------------------------------------ boss parts

    fun bigCannon(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 6f, 0f, 0f, 24f, 44f, 38f, 20, m.mat(STEEL))
        m.cyl(0f, 20f, 18f, 0f, 76f, 18f, 8f, 7f, 14, m.mat(STEEL_D))
        m.cyl(0f, 72f, 18f, 0f, 80f, 18f, 10f, 10f, 14, m.mat(DARK))
        m.box(0f, -10f, 24.5f, 60f, 6f, 1.2f, m.mat(E_RED))
        return m
    }
    fun gatling(): Mesh {
        val m = Mesh()
        m.box(0f, -2f, 12f, 38f, 34f, 14f, m.mat(STEEL))
        for (k in 0 until 3) m.cyl(-7f + k * 7f, 12f, 14f, -7f + k * 7f, 48f, 14f, 2.4f, 2.4f, 8, m.mat(DARK))
        m.ellipsoid(0f, -4f, 19.5f, 4f, 4f, 1.5f, 8, m.mat(E_RED))
        return m
    }
    fun ringCannon(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 0f, 0f, 0f, 14f, 38f, 34f, 24, m.mat(STEEL))
        for (k in 0 until 12) { val a = k * PI.toFloat() / 6f; m.cyl(cos(a) * 28f, sin(a) * 28f, 14f, cos(a) * 28f, sin(a) * 28f, 16f, 4.5f, 4.5f, 8, m.mat(E_GLOW)) }
        m.ellipsoid(0f, 0f, 15f, 14f, 14f, 6f, 16, m.mat(E_GLOW))
        return m
    }
    fun laserEmitter(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 0f, 0f, 0f, 12f, 42f, 38f, 6, m.mat(STEEL_D))
        m.ellipsoid(0f, 0f, 22f, 16f, 22f, 16f, 6, m.mat(ICE_GLOW))
        return m
    }
    fun bay(): Mesh {
        val m = Mesh()
        m.box(0f, 0f, 8f, 104f, 72f, 16f, m.mat(E_HULL_D))
        m.box(0f, 2f, 16.5f, 76f, 48f, 1f, m.mat(DARK))
        m.box(0f, -24f, 17f, 76f, 3f, 1f, m.mat(E_GLOW))
        m.box(-20f, 2f, 17.2f, 34f, 44f, 0.6f, m.mat(E_HULL)); m.box(20f, 2f, 17.2f, 34f, 44f, 0.6f, m.mat(E_HULL))
        return m
    }
    fun flamer(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 6f, 0f, 0f, 26f, 36f, 30f, 16, m.mat(SAND_ARMOR))
        m.cyl(0f, 16f, 16f, 0f, 60f, 16f, 10f, 13f, 12, m.mat(STEEL_D))
        m.ellipsoid(0f, 61f, 16f, 10f, 2f, 10f, 10, m.mat(FURNACE))
        m.ellipsoid(0f, 0f, 26.5f, 9f, 9f, 2f, 10, m.mat(FURNACE))
        return m
    }
    fun mortar(): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 6f, 0f, 0f, 22f, 34f, 30f, 16, m.mat(SAND_ARMOR))
        m.cyl(0f, 6f, 22f, 0f, 6f, 30f, 16f, 14f, 14, m.mat(DARK))
        m.ellipsoid(0f, 6f, 30.2f, 11f, 11f, 1f, 10, m.mat(FURNACE))
        return m
    }
    fun core(open: Boolean): Mesh {
        val m = Mesh()
        m.cyl(0f, 0f, 0f, 0f, 0f, 16f, 58f, 54f, 24, m.mat(VIOLET))
        if (open) m.ellipsoid(0f, 0f, 18f, 40f, 40f, 22f, 20, m.mat(PRISM))
        else for (k in 0 until 6) {
            m.push(); m.rotZ(k * PI.toFloat() / 3f)
            m.wing(floatArrayOf(0f, 0f, 44f, -4f, 40f, 22f), 20f, 6f, m.mat(VIOLET_L), mirror = false, tipThin = 0.6f)
            m.pop()
        }
        return m
    }

    // ------------------------------------------------------------------ boss hulls (modelled nose +y; screen-down after the turn)

    /** Model-space y that lands on screen offset [oy] at height [z] once the hull is turned to face down. */
    fun my(oy: Float, z: Float): Float = (oy + z * sin(R3.pitch)) / cos(R3.pitch)

    private fun mount(m: Mesh, ox: Float, oy: Float, z: Float, r: Float) {
        val y = my(oy, z)
        m.cyl(-ox, y, z - 6f, -ox, y, z + 2f, r + 6f, r + 4f, 20, m.mat(STEEL_D))
        m.cyl(-ox, y, z + 2f, -ox, y, z + 3f, r + 3f, r + 3f, 20, m.mat(HAZARD), caps = true)
    }

    fun boss(type: Int): Mesh {
        val m = Mesh()
        when (type) {
            0 -> { // LEVIATHAN
                val deckZ = 18f
                m.loft(arrayOf(floatArrayOf(-362f, 0f, 0f, 120f, 18f, 24f), floatArrayOf(-280f, 0f, 0f, 160f, 18f, 26f), floatArrayOf(-60f, 0f, 0f, 168f, 18f, 26f),
                    floatArrayOf(150f, 0f, 0f, 150f, 18f, 26f), floatArrayOf(300f, 0f, 0f, 70f, 18f, 22f), floatArrayOf(366f, 0f, 2f, 3f, 14f, 16f)), 28, m.mat(STEEL))
                m.wing(floatArrayOf(0f, 340f, 60f, 290f, 140f, 150f, 152f, -60f, 146f, -270f, 108f, -338f, 0f, -344f), deckZ - 1f, 3f, m.mat(WOOD), tipThin = 1f, span = 152f)
                m.box(0f, my(0f, 40f), 34f, 140f, 220f, 34f, m.mat(STEEL_D))
                m.box(0f, my(-30f, 62f), 60f, 96f, 120f, 18f, m.mat(STEEL))
                m.box(0f, my(-60f, 72f), 72f, 96f, 10f, 8f, m.mat(GLASS_BLUE))
                m.cyl(0f, my(80f, 60f), 50f, 0f, my(80f, 60f), 80f, 22f, 18f, 16, m.mat(DARK))
                for (yy in floatArrayOf(-250f, -140f, 150f, 260f)) mount(m, 0f, yy, deckZ + 6f, 30f)
                for (sx in floatArrayOf(-100f, 100f)) mount(m, sx, 40f, deckZ + 6f, 28f)
                mount(m, 0f, -20f, 70f, 50f)
                for (sx in floatArrayOf(-1f, 1f)) m.box(sx * 162f, 40f, 6f, 6f, 360f, 6f, m.mat(E_RED))
            }
            1 -> { // SANDCRAWLER
                for (sx in floatArrayOf(-270f, -170f, 170f, 270f)) m.box(sx, 0f, 22f, 84f, 430f, 44f, m.mat(TREAD))
                m.box(0f, 0f, 50f, 460f, 410f, 40f, m.mat(SAND_ARMOR))
                m.loft(arrayOf(floatArrayOf(-170f, 0f, 70f, 170f, 30f, 2f), floatArrayOf(150f, 0f, 70f, 175f, 30f, 2f), floatArrayOf(170f, 0f, 70f, 140f, 18f, 2f)), 20, m.mat(SAND_ARMOR), capFront = true)
                for (sx in floatArrayOf(-80f, 80f)) { m.cyl(-sx, my(-170f, 110f), 90f, -sx, my(-170f, 110f), 125f, 18f, 15f, 14, m.mat(DARK)); m.ellipsoid(-sx, my(-170f, 125f), 125f, 12f, 12f, 3f, 10, m.mat(FURNACE)) }
                mount(m, 0f, -50f, 100f, 50f)
                for (sx in floatArrayOf(-210f, 210f)) mount(m, sx, -70f, 92f, 28f)
                for (sx in floatArrayOf(-150f, 150f)) mount(m, sx, 120f, 92f, 30f)
                mount(m, 0f, 95f, 100f, 52f)
                m.box(0f, my(60f, 101f), 101f, 340f, 8f, 2f, m.mat(E_RED))
            }
            2 -> { // FROSTWALL
                m.box(0f, 0f, 30f, 800f, my(300f, 60f), 60f, m.mat(CONCRETE))
                m.box(0f, my(0f, 62f), 62f, 600f, my(200f, 62f), 4f, m.mat(Mat(0xFF6E7A88.toInt(), 0.3f, 18f, 0.3f, grime = 0.5f, panels = true)))
                for (k in 0 until 12) m.ellipsoid(-370f + k * 67f, my(-150f, 62f), 62f, 30f, 12f, 8f, 12, m.mat(SNOW))
                for (sx in floatArrayOf(-330f, 330f)) for (sy in floatArrayOf(-120f, 120f)) {
                    val y = my(sy, 80f)
                    m.cyl(-sx, y, 0f, -sx, y, 80f, 66f, 60f, 24, m.mat(CONCRETE))
                    m.cyl(-sx, y, 80f, -sx, y, 82f, 62f, 62f, 24, m.mat(SNOW))
                    mount(m, sx, sy, 86f, 36f)
                }
                m.box(0f, my(-30f, 110f), 60f, 220f, 150f, 100f, m.mat(Mat(0xFF7E8C9C.toInt(), 0.35f, 20f, 0.35f, grime = 0.4f, panels = true)))
                mount(m, 0f, -40f, 112f, 58f)
                mount(m, 0f, 90f, 70f, 40f)
                for (sx in floatArrayOf(-200f, 200f)) m.box(sx, my(0f, 64f), 64f, 50f, 20f, 3f, m.mat(ICE_GLOW))
            }
            3 -> { // OVERSEER
                m.wing(floatArrayOf(0f, 150f, 120f, 110f, 300f, 40f, 320f, -20f, 250f, -110f, 90f, -150f, 0f, -140f), 20f, 36f, m.mat(Mat(0xFF393C46.toInt(), 0.5f, 32f, 0.5f, grime = 0.35f, panels = true)), tipThin = 0.4f, span = 320f)
                m.loft(arrayOf(floatArrayOf(-134f, 0f, 30f, 40f, 20f, 4f), floatArrayOf(-60f, 0f, 34f, 80f, 26f, 4f), floatArrayOf(110f, 0f, 34f, 60f, 22f, 4f), floatArrayOf(140f, 0f, 30f, 10f, 8f, 4f)), 18, m.mat(Mat(0xFF474B57.toInt(), 0.5f, 32f, 0.5f, grime = 0.3f, panels = true)))
                for (sx in floatArrayOf(-200f, 200f)) {
                    val y = my(-30f, 40f)
                    m.cyl(sx, y, 18f, sx, y, 48f, 74f, 74f, 28, m.mat(Mat(0xFF4C4F5A.toInt(), 0.5f, 30f, 0.5f, grime = 0.3f)), caps = false)
                    m.cyl(sx, y, 20f, sx, y, 22f, 68f, 68f, 28, m.mat(DARK))
                    for (k in 0 until 6) { m.push(); m.translate(sx, y, 30f); m.rotZ(k * PI.toFloat() / 3f + 0.3f); m.box(32f, 0f, 0f, 60f, 12f, 2f, m.mat(STEEL_D)); m.pop() }
                    m.cyl(sx, y, 22f, sx, y, 40f, 16f, 14f, 12, m.mat(STEEL))
                }
                for (sx in floatArrayOf(-1f, 1f)) { m.push(); m.translate(sx * 210f, my(74f, 40f), 40f); m.rotZ(-sx * 0.35f); m.box(0f, 0f, 0f, 190f, 4f, 3f, m.mat(NEON_PINK)); m.pop() }
                mount(m, 0f, -5f, 62f, 54f)
                for (sx in floatArrayOf(-240f, 240f)) mount(m, sx, 50f, 42f, 30f)
                mount(m, 0f, 110f, 52f, 36f)
                for (sx in floatArrayOf(-130f, 130f)) mount(m, sx, -70f, 50f, 30f)
                m.box(0f, my(-110f, 62f), 62f, 60f, 18f, 3f, m.mat(NEON_CYAN))
            }
            4 -> { // CRUCIBLE
                for (sx in floatArrayOf(-140f, 140f)) {
                    m.cyl(-sx, my(-200f, 0f), 0f, -sx, my(-150f, 60f), 60f, 34f, 30f, 14, m.mat(TREAD))
                    m.box(-sx, my(-228f, 10f), 10f, 100f, 46f, 20f, m.mat(STEEL_D))
                }
                m.cyl(0f, 0f, 30f, 0f, 0f, 90f, 150f, 140f, 6, m.mat(Mat(0xFF6E5E52.toInt(), 0.4f, 24f, 0.4f, grime = 0.45f, panels = true)))
                for (sx in floatArrayOf(-1f, 1f)) {
                    m.push(); m.translate(sx * 175f, my(50f, 70f), 70f); m.rotZ(sx * 0.45f)
                    m.box(0f, 0f, 0f, 60f, 150f, 40f, m.mat(SAND_ARMOR)); m.pop()
                    m.box(sx * 130f, my(-132f, 104f), 104f, 112f, 100f, 28f, m.mat(SAND_ARMOR))
                }
                for (k in 0 until 3) m.box(-60f + k * 60f, my(90f, 92f), 92f, 34f, 22f, 3f, m.mat(FURNACE))
                mount(m, 0f, -10f, 92f, 60f)
                for (sx in floatArrayOf(-220f, 220f)) mount(m, sx, 90f, 92f, 36f)
                for (sx in floatArrayOf(-130f, 130f)) mount(m, sx, -130f, 120f, 34f)
                m.box(0f, my(120f, 92f), 92f, 240f, 6f, 2f, m.mat(E_RED))
            }
            else -> { // HOLLOW KING
                m.wing(floatArrayOf(0f, 270f, 110f, 230f, 300f, 160f, 470f, 60f, 460f, -40f, 330f, -140f, 160f, -250f, 0f, -270f), 30f, 60f, m.mat(VIOLET), tipThin = 0.35f, span = 470f)
                m.wing(floatArrayOf(0f, 230f, 90f, 200f, 250f, 130f, 380f, 50f, 370f, -30f, 260f, -110f, 120f, -200f, 0f, -220f), 62f, 10f, m.mat(VIOLET_L), tipThin = 0.6f, span = 380f)
                for (sx in floatArrayOf(-1f, 1f)) {
                    m.push(); m.translate(sx * 200f, my(120f, 68f), 68f); m.rotZ(sx * 0.48f); m.box(0f, 0f, 0f, 340f, 5f, 3f, m.mat(NEON_PINK)); m.pop()
                    m.push(); m.translate(sx * 150f, my(-70f, 68f), 68f); m.rotZ(-sx * 0.4f); m.box(0f, 0f, 0f, 200f, 5f, 3f, m.mat(NEON_PINK)); m.pop()
                }
                m.loft(arrayOf(floatArrayOf(-250f, 0f, 70f, 60f, 30f, 4f), floatArrayOf(-150f, 0f, 70f, 40f, 40f, 4f)), 12, m.mat(VIOLET_L), capFront = true)
                for (k in 0 until 9) m.ellipsoid(-400f + k * 100f, my(80f - kotlin.math.abs(k - 4) * 22f, 62f), 62f, 5f, 5f, 3f, 8, m.mat(Mat(0xFF202020.toInt(), 0f, 8f, 0f, emissive = 0xFFFFD6F0.toInt(), grime = 0f)))
                mount(m, 0f, 30f, 72f, 70f)
                for (sx in floatArrayOf(-330f, 330f)) mount(m, sx, -60f, 72f, 40f)
                for (sx in floatArrayOf(-200f, 200f)) mount(m, sx, 150f, 70f, 34f)
                for (sx in floatArrayOf(-420f, 420f)) mount(m, sx, 110f, 50f, 30f)
                mount(m, 0f, 210f, 66f, 40f)
                for (sx in floatArrayOf(-110f, 110f)) mount(m, sx, 170f, 68f, 26f)
            }
        }
        return m
    }
}
