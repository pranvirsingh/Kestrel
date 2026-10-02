package com.pranvir.kestrel

/** Hangar upgrades. */
object Up {
    const val CANNON = 0; const val MISSILE = 1; const val LANCE = 2; const val DRONE = 3; const val HULL = 4; const val MAGNET = 5; const val OVERDRIVE = 6
    const val COUNT = 7
    val MAX = intArrayOf(5, 5, 5, 3, 5, 5, 5)
    val NAMES = arrayOf("MAIN CANNON", "HOMING MISSILES", "ARC LANCE", "WING DRONES", "HULL PLATING", "CORE MAGNET", "PRISM OVERDRIVE")
    val ICONS = intArrayOf(Icon.CANNON, Icon.MISSILE, Icon.LANCE, Icon.DRONE, Icon.HULL, Icon.MAGNET, Icon.OVERDRIVE)
    val BLURB = arrayOf(
        "Twin plasma cannons. More streams, more punch.",
        "Seekers that hunt the nearest target.",
        "Chain lightning that jumps between enemies.",
        "Escort drones flying on your wings.",
        "Armour. Take more hits before going down.",
        "Pulls cores in from further away.",
        "Bigger, longer prism burst. Bullets become loot.")
    private val BASE = intArrayOf(200, 500, 1100, 2300, 4500)
    private val WEIGHT = floatArrayOf(1f, 0.9f, 1f, 0f, 0.7f, 0.5f, 0.8f)
    private val DRONE_COST = intArrayOf(700, 2000, 4800)

    /** Cost to go from [lv] to lv+1, or -1 when maxed. */
    fun cost(u: Int, lv: Int): Int {
        if (lv >= MAX[u]) return -1
        if (u == DRONE) return DRONE_COST[lv]
        return ((BASE[lv] * WEIGHT[u]) / 10f).toInt() * 10
    }

    fun hull(lv: Int) = intArrayOf(100, 130, 160, 200, 240, 300)[lv.coerceIn(0, 5)]
    fun magnet(lv: Int) = floatArrayOf(130f, 180f, 230f, 290f, 360f, 450f)[lv.coerceIn(0, 5)]
    fun cannonDamage(lv: Int) = floatArrayOf(9f, 10f, 10f, 11f, 12f, 14f)[lv.coerceIn(0, 5)]
    fun cannonStreams(lv: Int) = intArrayOf(2, 2, 3, 4, 5, 5)[lv.coerceIn(0, 5)]
    fun missileCount(lv: Int) = intArrayOf(0, 1, 2, 2, 3, 4)[lv.coerceIn(0, 5)]
    fun missilePeriod(lv: Int) = floatArrayOf(9f, 2.2f, 2.0f, 1.7f, 1.6f, 1.4f)[lv.coerceIn(0, 5)]
    fun missileDamage(lv: Int) = 30f + lv * 7f
    fun lanceTargets(lv: Int) = lv + 1
    fun lancePeriod(lv: Int) = 3.1f - lv * 0.3f
    fun lanceDamage(lv: Int) = 22f + lv * 9f
    fun overdriveTime(lv: Int) = 4f + lv * 0.6f
    fun overdriveGain(lv: Int) = 1f + lv * 0.14f

    fun stat(u: Int, lv: Int): String = when (u) {
        CANNON -> "${cannonStreams(lv)} streams · ${(cannonStreams(lv) * cannonDamage(lv) * 11).toInt()} dps"
        MISSILE -> if (lv == 0) "offline" else "${missileCount(lv)} per salvo · ${missileDamage(lv).toInt()} dmg"
        LANCE -> if (lv == 0) "offline" else "${lanceTargets(lv)} jumps · ${lanceDamage(lv).toInt()} dmg"
        DRONE -> if (lv == 0) "offline" else "${if (lv == 1) 1 else 2} drone${if (lv == 1) "" else "s"}${if (lv == 3) " · heavy" else ""}"
        HULL -> "${hull(lv)} armour"
        MAGNET -> "${magnet(lv).toInt()} m reach"
        else -> "${"%.1f".format(overdriveTime(lv))} s burst"
    }
}

/** Pilot rank perks unlocked by total badges. */
object Perk {
    const val SALVAGER = 0; const val MEDIC = 1; const val HOT_START = 2; const val DEFLECTOR = 3; const val LUCKY = 4
    const val TRACKER = 5; const val SECOND_WIND = 6; const val PRISM_MASTER = 7; const val ACE = 8; const val LEGEND = 9
    const val COUNT = 10
    val NEED = intArrayOf(4, 8, 12, 18, 24, 32, 42, 56, 72, 96)
    val NAMES = arrayOf("SALVAGER", "FIELD MEDIC", "HOT START", "DEFLECTOR", "LUCKY DROPS", "TRACKER", "SECOND WIND", "PRISM MASTER", "ACE OF ACES", "LEGEND")
    val DESC = arrayOf(
        "+10% cores from every mission",
        "Repair kits heal 50% more",
        "Launch with Overdrive 40% charged",
        "Launch behind a 10 second shield",
        "Power-ups drop 25% more often",
        "+1 homing missile per salvo",
        "Survive one fatal hit per mission",
        "Overdrive lasts 2 seconds longer",
        "+25% cores from every mission",
        "Golden Kestrel livery and +10% damage")
    val RANKS = arrayOf("CADET", "PILOT", "FLIGHT OFFICER", "LIEUTENANT", "CAPTAIN", "MAJOR", "WING COMMANDER", "COLONEL", "GROUP CAPTAIN", "AIR MARSHAL", "LEGEND")
    fun mask(badges: Int): Int { var m = 0; for (i in 0 until COUNT) if (badges >= NEED[i]) m = m or (1 shl i); return m }
    fun rank(badges: Int): Int { var r = 0; for (i in 0 until COUNT) if (badges >= NEED[i]) r = i + 1; return r }
}

object Threat {
    val NAMES = arrayOf("I", "II", "III", "IV")
    val LABEL = arrayOf("RECON", "STRIKE", "SIEGE", "NIGHTMARE")
    val HP = floatArrayOf(0.85f, 1.5f, 2.3f, 3.3f)
    val BULLET_SPEED = floatArrayOf(0.78f, 0.92f, 1.08f, 1.25f)
    val FIRE = floatArrayOf(0.5f, 0.8f, 1.2f, 1.75f)
    val DAMAGE = floatArrayOf(6f, 9f, 12f, 16f)
    val REWARD = floatArrayOf(1f, 1.7f, 2.5f, 3.5f)
}

object Badge {
    const val SWEEP = 0; const val LIFELINE = 1; const val UNTOUCHED = 2; const val ACE = 3
    val NAMES = arrayOf("SWEEP", "LIFELINE", "UNTOUCHED", "ACE")
    val ICONS = intArrayOf(Icon.SWEEP, Icon.LIFELINE, Icon.UNTOUCHED, Icon.ACE)
}

class SectorDef(
    val id: Int, val name: String, val tag: String, val brief: String,
    val length: Float, val aceText: String, val bossName: String, val accent: Int
)

object Sectors {
    val ALL = arrayOf(
        SectorDef(0, "CORAL COAST", "Archipelago · sea lanes", "The Armada's fleet holds the islands. Sink their gunboats and break the blockade.",
            11200f, "Sink every gunboat", "LEVIATHAN", 0xFF3FD0D2.toInt()),
        SectorDef(1, "DUST CANYON", "Mesas · rail line", "An armoured train runs supplies through the canyon. Derail it.",
            11800f, "Destroy the armoured train", "SANDCRAWLER", 0xFFE79A5A.toInt()),
        SectorDef(2, "GLACIER LINE", "Ice shelf · fjords", "The Frostwall guards the northern pass. Strike fast before it seals the sky.",
            12200f, "Destroy the boss in 75 s", "FROSTWALL", 0xFFBFE3FF.toInt()),
        SectorDef(3, "NEON SPRAWL", "Megacity · night", "Their radar spires watch the city. Blind them, then hunt the Overseer.",
            12600f, "Destroy all 6 radar spires", "OVERSEER", 0xFFFF4FD8.toInt()),
        SectorDef(4, "EMBER FORGE", "Lava fields · foundries", "The forge builds their war machines. Keep the chain of fire going.",
            13000f, "Chain 35 kills", "CRUCIBLE", 0xFFFF7A1F.toInt()),
        SectorDef(5, "STRATOS", "Above the clouds", "The Hollow Armada's mothership waits above the storm. End this.",
            13400f, "Destroy all 3 carriers", "HOLLOW KING", 0xFFFFD6F0.toInt())
    )
}

/** Enemy kinds and their base stats. */
object EK {
    const val DART = 0; const val SWOOP = 1; const val WASP = 2; const val GUNSHIP = 3; const val BOMBER = 4; const val LANCER = 5; const val MINE = 6; const val CARRIER = 7
    const val TANK = 8; const val FLAK = 9; const val SAM = 10; const val BOAT = 11; const val TRAIN = 12; const val RADAR = 13; const val BUNKER = 14
    const val EMISSILE = 15
    const val COUNT = 16
    val HP = floatArrayOf(14f, 18f, 30f, 170f, 280f, 50f, 10f, 700f, 40f, 60f, 70f, 90f, 120f, 80f, 60f, 6f)
    val R = floatArrayOf(28f, 30f, 26f, 66f, 92f, 26f, 20f, 138f, 27f, 30f, 25f, 25f, 30f, 32f, 52f, 11f)
    val CORES = intArrayOf(5, 6, 9, 40, 60, 15, 3, 130, 12, 15, 18, 20, 26, 22, 20, 0)
    val AIR = booleanArrayOf(true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, true)
    /** Counts towards SWEEP. */
    val HOSTILE = booleanArrayOf(true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, false)
    val BIG = booleanArrayOf(false, false, false, true, true, false, false, true, false, false, false, false, false, false, false, false)
}
