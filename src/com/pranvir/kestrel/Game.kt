package com.pranvir.kestrel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

interface Host {
    fun loadInt(key: String, def: Int): Int
    fun saveInt(key: String, v: Int)
    fun sound(id: Int, vol: Float = 1f, rate: Float = 1f)
    fun setAudio(soundOn: Boolean, musicOn: Boolean)
    fun setMusicState(scene: Int, intensity: Float)
    fun haptic(strong: Boolean)
    /** Background terrain painting is allowed (false in tests: paint synchronously). */
    val asyncTerrain: Boolean
    /** Where rendered sprites may be cached (null: no cache). */
    val spriteCacheDir: java.io.File?
}

class Btn(val id: Int) {
    val r = RectF()
    var label = ""
    var icon = 0
    var style = 0          // 0 panel, 1 primary, 2 icon square, 3 custom-drawn
    var visible = false
    var enabled = true
    var off = false
    var press = 0f
    fun set(l: Float, t: Float, rr: Float, b: Float): Btn { r.set(l, t, rr, b); visible = true; enabled = true; return this }
    fun hit(x: Float, y: Float, slop: Float) = visible && x >= r.left - slop && x <= r.right + slop && y >= r.top - slop && y <= r.bottom + slop
}

class Game(val host: Host) : WorldEvents {
    companion object {
        const val M_TITLE = 0; const val M_MAP = 1; const val M_HANGAR = 2; const val M_RANKS = 3; const val M_LOAD = 4; const val M_PLAY = 5; const val M_DEBRIEF = 6
        const val B_CAMPAIGN = 1; const val B_HANGAR = 2; const val B_RANKS = 3; const val B_SETTINGS = 4; const val B_BACK = 5
        const val B_LAUNCH = 6; const val B_CLOSE = 7; const val B_PAUSE = 8; const val B_RESUME = 9; const val B_RESTART = 10; const val B_ABORT = 11
        const val B_CONTINUE = 12; const val B_RETRY = 13; const val B_SOUND = 14; const val B_MUSIC = 15; const val B_VIBE = 16; const val B_SENS = 17
        const val B_OVER = 18; const val B_TO_HANGAR = 19
        const val B_SECTOR0 = 30; const val B_THREAT0 = 40; const val B_UP0 = 50
    }

    // screen
    var w = 1080f; var h = 2340f
    var u = 10.8f; private set
    var s = 1.08f; private set
    var vh = 2166f; private set
    private var inL = 0f; private var inT = 0f; private var inR = 0f; private var inB = 0f

    // flow
    var mode = M_TITLE; private set
    var paused = false; private set
    private var settingsOpen = false
    private var briefOpen = -1
    private var time = 0f
    private var fade = 1f
    private var modeT = 0f

    // assets
    private var sprites: SpriteSet? = null
    private var units: Units3D? = null
    private var render: Render? = null
    // 3D sprites are rendered on a worker thread and handed over here
    private val assetExec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "kestrel-assets").apply { isDaemon = true } }
    private val pendingUnits = java.util.concurrent.atomic.AtomicReference<Units3D?>(null)
    private val pendingArt = java.util.concurrent.atomic.AtomicReference<BossArt?>(null)
    private val pendingShowcase = java.util.concurrent.atomic.AtomicReference<Spr?>(null)
    @Volatile private var unitsProg = 0f
    @Volatile private var artProg = 0f
    private var unitsScale = 0f
    private var unitsWanted = 0f
    private var artWanted = -1
    private var showcase: Spr? = null
    private var showcaseKey = -1
    private var showcaseWanted = -1

    private fun runAsset(block: () -> Unit) { if (host.asyncTerrain) assetExec.execute { try { block() } catch (t: Throwable) { android.util.Log.e("Kestrel", "asset", t) } } else block() }

    private fun requestUnits() {
        if (unitsWanted == s) return
        unitsWanted = s
        unitsProg = 0f
        val sc = s
        runAsset { val u = Units3D(sc, host.spriteCacheDir) { unitsProg = it }; pendingUnits.getAndSet(u)?.release() }
    }

    private fun requestArt(type: Int) {
        artWanted = type
        artProg = 0f
        val sc = s
        runAsset { val a = BossArt(type, sc, host.spriteCacheDir) { artProg = it }; pendingArt.getAndSet(a)?.release() }
    }

    private fun pollAssets() {
        pendingUnits.getAndSet(null)?.let { u ->
            if (abs(u.scale - s) > 0.001f) { u.release() } else {
                units?.release(); units = u; unitsScale = u.scale
                val sp = sprites
                render = if (sp != null) Render(sp, u) else null
            }
        }
        pendingArt.getAndSet(null)?.let { a ->
            if (world == null || a.type != sector || abs(a.scale - s) > 0.001f) a.release() else { art?.release(); art = a }
        }
        pendingShowcase.getAndSet(null)?.let { sc -> showcase?.release(); showcase = sc }
    }

    private fun showcaseKeyNow() = (if (lv[Up.MISSILE] > 0) 1 else 0) + (if (perks() and (1 shl Perk.LEGEND) != 0) 2 else 0)

    private fun requestShowcase() {
        val key = showcaseKeyNow()
        if (key == showcaseWanted) return
        showcaseWanted = key
        val sc = h * 0.17f / 108f
        runAsset {
            val m = Models.kestrel(key and 1 != 0, key and 2 != 0)
            val sp = R3.toSpr(R3.render(m, sc, -0.35f, -0.2f, 2, true, 0.75f))
            pendingShowcase.getAndSet(sp)?.release()
        }
    }
    private var terrain: Terrain? = null
    private var art: BossArt? = null
    private var demo: World? = null
    private var demoTerrain: Terrain? = null
    private var demoSector = 0
    private var mapBmp: Bitmap? = null
    private val thumbs = arrayOfNulls<Bitmap>(6)

    // mission
    var world: World? = null; private set
    var sector = 0; private set
    var threat = 0; private set
    private var loadStep = 0
    private var debrief: Debrief? = null
    private var tutorialT = 0f

    // input
    private var steerId = -1
    private var lastX = 0f; private var lastY = 0f
    private var lastTapT = -9f
    private var pressed: Btn? = null
    private var pressId = -1
    private var downX = 0f; private var downY = 0f
    private var dragging = false
    private var hangarScroll = 0f
    private var hangarMax = 0f
    private var lastSeenBadges = 0
    private var badgeSnd = 0
    private var titleSh: LinearGradient? = null

    // save
    var cores = 0
    val lv = IntArray(Up.COUNT)
    val badges = Array(6) { IntArray(4) }
    val clearedSector = BooleanArray(6)
    var soundOn = true; var musicOn = true; var vibeOn = true
    var sens = 1
    var flown = 0
    var tutorialDone = false

    private val buttons = ArrayList<Btn>()
    private fun btn(id: Int): Btn = buttons.firstOrNull { it.id == id } ?: Btn(id).also { buttons.add(it) }
    private val rc = RectF()
    private val rc2 = RectF()
    private val path = Path()
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val primSh = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(0xFF1B8FC9.toInt(), 0xFF35C8F2.toInt(), 0xFF7CE6FF.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)

    class Debrief(val won: Boolean, val earned: Int, val newBits: Int, val payout: Int, val killPct: Int, val rescued: Int, val damage: Int,
                  val ace: String, val bossTime: Float, val sector: Int, val threat: Int, val unlockedThreat: Boolean, val unlockedSector: Boolean)

    init { load(); host.setAudio(soundOn, musicOn) }

    // ------------------------------------------------------------------ save

    private fun load() {
        cores = host.loadInt("cores", 0).coerceAtLeast(0)
        for (i in 0 until Up.COUNT) lv[i] = host.loadInt("up$i", 0).coerceIn(0, Up.MAX[i])
        for (s in 0 until 6) { clearedSector[s] = host.loadInt("clr$s", 0) == 1; for (t in 0 until 4) badges[s][t] = host.loadInt("b${s}_$t", 0) and 15 }
        soundOn = host.loadInt("snd", 1) == 1; musicOn = host.loadInt("mus", 1) == 1; vibeOn = host.loadInt("vib", 1) == 1
        sens = host.loadInt("sens", 1).coerceIn(0, 2)
        flown = host.loadInt("flown", 0)
        tutorialDone = host.loadInt("tut", 0) == 1
        lastSeenBadges = totalBadges()
    }

    private fun save() {
        host.saveInt("cores", cores)
        for (i in 0 until Up.COUNT) host.saveInt("up$i", lv[i])
        for (s in 0 until 6) { host.saveInt("clr$s", if (clearedSector[s]) 1 else 0); for (t in 0 until 4) host.saveInt("b${s}_$t", badges[s][t]) }
        host.saveInt("snd", if (soundOn) 1 else 0); host.saveInt("mus", if (musicOn) 1 else 0); host.saveInt("vib", if (vibeOn) 1 else 0)
        host.saveInt("sens", sens); host.saveInt("flown", flown); host.saveInt("tut", if (tutorialDone) 1 else 0)
    }

    fun totalBadges(): Int { var n = 0; for (s in 0 until 6) for (t in 0 until 4) n += Integer.bitCount(badges[s][t]); return n }
    fun sectorBadges(s: Int): Int { var n = 0; for (t in 0 until 4) n += Integer.bitCount(badges[s][t]); return n }
    fun sectorOpen(s: Int) = s == 0 || clearedSector[s - 1]
    fun threatOpen(s: Int, t: Int) = t == 0 || Integer.bitCount(badges[s][t - 1]) >= 3
    fun perks() = Perk.mask(totalBadges())

    // ------------------------------------------------------------------ WorldEvents

    override fun sfx(id: Int, vol: Float, rate: Float) { host.sound(id, vol, rate) }
    override fun haptic(strong: Boolean) { if (vibeOn) host.haptic(strong) }

    // ------------------------------------------------------------------ layout

    fun resize(width: Int, height: Int, density: Float) {
        val nw = max(1, width).toFloat(); val nh = max(1, height).toFloat()
        val changed = nw != w || nh != h || sprites == null
        w = nw; h = nh
        u = min(w / 100f, h / 170f)
        s = w / World.W
        vh = h / s
        if (changed) {
            sprites?.release()
            sprites = SpriteSet(s)
            render = null
            units?.let { if (abs(it.scale - s) < 0.001f) render = Render(sprites!!, it) }
            if (render == null) requestUnits()
            art?.release(); art = null
            showcaseWanted = -1
            mapBmp?.recycle(); mapBmp = null
            titleSh = null
            if (world != null && (mode == M_PLAY || mode == M_LOAD)) { requestArt(sector); paused = mode == M_PLAY }
            if (mode == M_LOAD) loadStep = 0
            demo = null
        }
        layout()
    }

    fun setInsets(l: Int, t: Int, r: Int, b: Int) { inL = l.toFloat(); inT = t.toFloat(); inR = r.toFloat(); inB = b.toFloat(); layout() }

    private fun layout() {
        for (b in buttons) b.visible = false
        val cx = w / 2f
        val top = inT + 3f * u
        val bottom = h - inB - 3f * u
        if (settingsOpen) { layoutSettings(); return }
        when (mode) {
            M_TITLE -> {
                val bw = 66f * u
                var y = bottom - 66f * u
                btn(B_CAMPAIGN).set(cx - bw / 2f, y, cx + bw / 2f, y + 14f * u).apply { label = "CAMPAIGN"; style = 1; icon = Icon.MAP }
                y += 18f * u
                btn(B_HANGAR).set(cx - bw / 2f, y, cx - 1.5f * u, y + 12f * u).apply { label = "HANGAR"; style = 0; icon = Icon.WRENCH }
                btn(B_RANKS).set(cx + 1.5f * u, y, cx + bw / 2f, y + 12f * u).apply { label = "RANKS"; style = 0; icon = Icon.RANK }
                btn(B_SETTINGS).set(w - inR - 15f * u, top, w - inR - 3f * u, top + 12f * u).apply { style = 2; icon = Icon.GEAR; label = "" }
            }
            M_MAP -> {
                btn(B_BACK).set(inL + 3f * u, top, inL + 15f * u, top + 12f * u).apply { style = 2; icon = Icon.BACK; label = "" }
                btn(B_HANGAR).set(w - inR - 15f * u, top, w - inR - 3f * u, top + 12f * u).apply { style = 2; icon = Icon.WRENCH; label = "" }
                if (briefOpen >= 0) layoutBrief()
                else for (i in 0 until 6) {
                    val p = nodePos(i)
                    btn(B_SECTOR0 + i).set(p[0] - 11f * u, p[1] - 11f * u, p[0] + 11f * u, p[1] + 11f * u).apply { style = 3; label = "" }
                }
            }
            M_HANGAR -> {
                btn(B_BACK).set(inL + 3f * u, top, inL + 15f * u, top + 12f * u).apply { style = 2; icon = Icon.BACK; label = "" }
                for (i in 0 until Up.COUNT) {
                    val r = upRect(i)
                    btn(B_UP0 + i).set(r.right - 30f * u, r.bottom - 12.5f * u, r.right - 3f * u, r.bottom - 3.5f * u).apply { style = 1; label = ""; icon = 0 }
                }
            }
            M_RANKS -> btn(B_BACK).set(inL + 3f * u, top, inL + 15f * u, top + 12f * u).apply { style = 2; icon = Icon.BACK; label = "" }
            M_PLAY -> {
                btn(B_PAUSE).set(inL + 3f * u, top, inL + 14f * u, top + 11f * u).apply { style = 2; icon = Icon.PAUSE; label = "" }
                val r = 11f * u
                btn(B_OVER).set(w - inR - 4f * u - 2 * r, bottom - 4f * u - 2 * r, w - inR - 4f * u, bottom - 4f * u).apply { style = 3; label = "" }
                if (paused) {
                    val bw = 60f * u
                    var y = h * 0.42f
                    btn(B_RESUME).set(cx - bw / 2f, y, cx + bw / 2f, y + 13f * u).apply { label = "RESUME"; style = 1; icon = Icon.PLAY }
                    y += 16f * u
                    btn(B_RESTART).set(cx - bw / 2f, y, cx - 1.5f * u, y + 11f * u).apply { label = "RESTART"; style = 0; icon = Icon.RETRY }
                    btn(B_ABORT).set(cx + 1.5f * u, y, cx + bw / 2f, y + 11f * u).apply { label = "ABORT"; style = 0; icon = Icon.CLOSE }
                    y += 15f * u
                    toggles(y)
                }
            }
            M_DEBRIEF -> {
                val bw = 66f * u
                val y = bottom - 30f * u
                btn(B_CONTINUE).set(cx - bw / 2f, y, cx + bw / 2f, y + 13f * u).apply { label = "CONTINUE"; style = 1; icon = Icon.ARROW }
                btn(B_RETRY).set(cx - bw / 2f, y + 16f * u, cx - 1.5f * u, y + 27f * u).apply { label = "RETRY"; style = 0; icon = Icon.RETRY }
                btn(B_TO_HANGAR).set(cx + 1.5f * u, y + 16f * u, cx + bw / 2f, y + 27f * u).apply { label = "HANGAR"; style = 0; icon = Icon.WRENCH }
            }
        }
    }

    private fun toggles(y: Float) {
        val cx = w / 2f; val r = 6f * u
        btn(B_SOUND).set(cx - 26f * u - r, y, cx - 26f * u + r, y + 2 * r).apply { style = 2; icon = Icon.SOUND; off = !soundOn; label = "" }
        btn(B_MUSIC).set(cx - 9f * u - r, y, cx - 9f * u + r, y + 2 * r).apply { style = 2; icon = Icon.MUSIC; off = !musicOn; label = "" }
        btn(B_VIBE).set(cx + 8f * u - r, y, cx + 8f * u + r, y + 2 * r).apply { style = 2; icon = Icon.VIBE; off = !vibeOn; label = "" }
        btn(B_SENS).set(cx + 18f * u, y, cx + 34f * u, y + 2 * r).apply { style = 0; icon = 0; label = arrayOf("SLOW", "NORMAL", "FAST")[sens] }
    }

    private fun layoutSettings() {
        val cx = w / 2f
        rc.set(cx - 40f * u, h * 0.3f, cx + 40f * u, h * 0.3f + 62f * u)
        toggles(h * 0.3f + 22f * u)
        btn(B_CLOSE).set(cx - 25f * u, h * 0.3f + 44f * u, cx + 25f * u, h * 0.3f + 55f * u).apply { label = "DONE"; style = 1; icon = Icon.CHECK }
    }

    private fun briefRect(out: RectF) { out.set(inL + 4f * u, inT + 18f * u, w - inR - 4f * u, h - inB - 4f * u) }

    private fun layoutBrief() {
        briefRect(rc)
        btn(B_CLOSE).set(rc.right - 13f * u, rc.top + 2f * u, rc.right - 2f * u, rc.top + 13f * u).apply { style = 2; icon = Icon.CLOSE; label = "" }
        val chipW = (rc.right - rc.left - 14f * u) / 4f
        val cy = threatY()
        for (t in 0 until 4) {
            val l = rc.left + 4f * u + t * (chipW + 2f * u)
            btn(B_THREAT0 + t).set(l, cy, l + chipW, cy + 11f * u).apply { style = 3; label = Threat.NAMES[t] }
        }
        val by = rc.bottom - 16f * u
        btn(B_LAUNCH).set(rc.left + 4f * u, by, rc.right - 28f * u, by + 12.5f * u).apply { label = "LAUNCH"; style = 1; icon = Icon.PLAY }
        btn(B_TO_HANGAR).set(rc.right - 25f * u, by, rc.right - 4f * u, by + 12.5f * u).apply { label = ""; style = 2; icon = Icon.WRENCH }
    }

    private fun threatY(): Float { briefRect(rc2); return rc2.top + min(70f * u, (rc2.bottom - rc2.top) * 0.42f) }

    fun nodePos(i: Int): FloatArray {
        val top = inT + 30f * u; val bot = h - inB - 32f * u
        val y = bot - (bot - top) * i / 5f
        val x = w / 2f + sin(i * 1.9f + 0.6f) * w * 0.26f
        return floatArrayOf(x, y)
    }

    private fun upRect(i: Int): RectF {
        val top = inT + h * 0.33f
        val ch = 25f * u
        val y = top + i * (ch + 2.5f * u) - hangarScroll
        rc2.set(inL + 4f * u, y, w - inR - 4f * u, y + ch)
        return rc2
    }

    private fun go(m: Int) {
        mode = m; modeT = 0f; fade = 1f
        pressed = null; steerId = -1; settingsOpen = false
        if (m != M_MAP) briefOpen = -1
        if (m == M_HANGAR) hangarScroll = 0f
        layout()
    }

    // ------------------------------------------------------------------ missions

    private fun launch(sec: Int, thr: Int) {
        sector = sec; threat = thr
        releaseMission()
        demoTerrain?.release(); demoTerrain = null; demo = null
        val wd = World(sec, thr, Loadout(lv.copyOf(), perks()), vh, System.nanoTime())
        wd.events = this
        world = wd
        terrain = Terrain(wd.biome, min(1f, s * 0.75f), host.asyncTerrain)
        requestArt(sec)
        loadStep = 0
        paused = false
        tutorialT = if (!tutorialDone) 0f else 99f
        go(M_LOAD)
    }

    private fun releaseMission() {
        terrain?.release(); terrain = null
        art?.release(); art = null
        world = null
    }

    private fun stepLoad() {
        val wd = world ?: return
        val tr = terrain ?: return
        when (loadStep) {
            0 -> Unit
            1, 2, 3 -> tr.need(loadStep - 1, loadStep - 1)
            else -> {
                tr.need(0, 2)
                val ready = tr.isReady(0) && tr.isReady(1) && tr.isReady(2) && art != null && render != null
                if (ready) { go(M_PLAY); flown++; save() }
            }
        }
        loadStep++
    }

    private fun finishMission() {
        val wd = world ?: return
        val won = wd.cleared()
        val pay = wd.payout()
        cores += pay
        var bits = 0
        var newBits = 0
        var unlockedThreat = false; var unlockedSector = false
        if (won) {
            bits = wd.badges()
            val before = badges[sector][threat]
            newBits = bits and before.inv()
            val hadNext = threat < 3 && threatOpen(sector, threat + 1)
            badges[sector][threat] = before or bits
            if (threat < 3 && !hadNext && threatOpen(sector, threat + 1)) unlockedThreat = true
            if (!clearedSector[sector]) { clearedSector[sector] = true; if (sector < 5) unlockedSector = true }
        }
        tutorialDone = true
        badgeSnd = 0
        debrief = Debrief(won, bits, newBits, pay, (wd.killRatio() * 100f).toInt(), wd.rescued, wd.damageTaken.toInt(), wd.aceProgress(), wd.bossTime, sector, threat, unlockedThreat, unlockedSector)
        save()
        host.sound(if (won) Sfx.WIN else Sfx.FAIL, 0.9f)
        go(M_DEBRIEF)
    }

    // ------------------------------------------------------------------ input (multi-pointer)

    fun touchDown(id: Int, x: Float, y: Float) {
        if (mode == M_PLAY && !paused && !settingsOpen) {
            val ob = btn(B_OVER)
            if (ob.visible && hypot(x - ob.r.centerX(), y - ob.r.centerY()) < ob.r.width() * 0.62f) { world?.triggerOverdrive(); ob.press = 1f; return }
            val pb = btn(B_PAUSE)
            if (pb.hit(x, y, u)) { pressed = pb; pressId = id; pb.press = 1f; return }
            if (steerId < 0) {
                steerId = id; lastX = x; lastY = y
                if (time - lastTapT < 0.3f) world?.triggerOverdrive()
                lastTapT = time
            }
            return
        }
        if (pressed != null) return
        downX = x; downY = y; dragging = false
        val b = buttons.firstOrNull { it.visible && it.hit(x, y, u * 0.8f) }
        pressed = b; pressId = id
        b?.press = 1f
        lastY = y
    }

    fun touchMove(id: Int, x: Float, y: Float) {
        if (mode == M_PLAY && id == steerId) {
            val k = floatArrayOf(1.1f, 1.45f, 1.85f)[sens] / s
            world?.move((x - lastX) * k, (y - lastY) * k)
            lastX = x; lastY = y
            return
        }
        if (id != pressId) return
        if (mode == M_HANGAR && !settingsOpen) {
            if (abs(y - downY) > 2.5f * u) { dragging = true; pressed = null }
            if (dragging) { hangarScroll = (hangarScroll - (y - lastY)).coerceIn(0f, hangarMax); lastY = y; layout(); return }
        }
        lastY = y
        val p = pressed ?: return
        if (!p.hit(x, y, u * 3f)) pressed = null
    }

    fun touchUp(id: Int, x: Float, y: Float) {
        if (id == steerId) { steerId = -1; return }
        if (id != pressId) return
        val p = pressed
        pressed = null; pressId = -1
        if (p != null && p.visible && p.hit(x, y, u * 3f)) onButton(p)
    }

    fun touchCancel() { steerId = -1; pressed = null; pressId = -1 }

    fun onBack(): Boolean {
        if (settingsOpen) { settingsOpen = false; layout(); return true }
        when (mode) {
            M_TITLE -> return false
            M_MAP -> if (briefOpen >= 0) { briefOpen = -1; layout() } else go(M_TITLE)
            M_HANGAR, M_RANKS -> go(if (lastMenu == M_MAP) M_MAP else M_TITLE)
            M_PLAY -> { paused = !paused; steerId = -1; layout() }
            M_LOAD -> { releaseMission(); go(M_MAP) }
            M_DEBRIEF -> go(M_MAP)
        }
        host.sound(Sfx.TAP, 0.5f)
        return true
    }

    fun onPause() {
        steerId = -1; pressed = null
        if (mode == M_PLAY && world?.state != World.DONE) { paused = true; layout() }
        save()
    }

    private var lastMenu = M_TITLE

    private fun onButton(b: Btn) {
        if (!b.enabled && b.id !in B_UP0 until B_UP0 + Up.COUNT) { host.sound(Sfx.NOPE, 0.5f); return }
        host.sound(Sfx.TAP, 0.5f)
        when (b.id) {
            B_CAMPAIGN -> go(M_MAP)
            B_HANGAR, B_TO_HANGAR -> { lastMenu = if (mode == M_MAP || mode == M_DEBRIEF) M_MAP else M_TITLE; go(M_HANGAR) }
            B_RANKS -> { lastMenu = mode; go(M_RANKS) }
            B_SETTINGS -> { settingsOpen = true; layout() }
            B_CLOSE -> { if (settingsOpen) settingsOpen = false else briefOpen = -1; layout() }
            B_BACK -> onBack()
            B_LAUNCH -> if (briefOpen >= 0 && threatOpen(briefOpen, briefThreat)) launch(briefOpen, briefThreat)
            B_PAUSE -> { paused = true; steerId = -1; layout() }
            B_RESUME -> { paused = false; layout() }
            B_RESTART -> { val wd = world; if (wd != null) { cores += wd.payout(); save() }; launch(sector, threat) }
            B_ABORT -> { val wd = world; if (wd != null) { cores += wd.payout(); save() }; releaseMission(); go(M_MAP) }
            B_CONTINUE -> { releaseMission(); go(M_MAP); if (debrief?.won == true) briefOpen = -1 }
            B_RETRY -> launch(sector, threat)
            B_SOUND -> { soundOn = !soundOn; host.setAudio(soundOn, musicOn); save(); layout() }
            B_MUSIC -> { musicOn = !musicOn; host.setAudio(soundOn, musicOn); save(); layout() }
            B_VIBE -> { vibeOn = !vibeOn; if (vibeOn) host.haptic(false); save(); layout() }
            B_SENS -> { sens = (sens + 1) % 3; save(); layout() }
            else -> {
                if (b.id in B_SECTOR0 until B_SECTOR0 + 6) {
                    val i = b.id - B_SECTOR0
                    if (sectorOpen(i)) { briefOpen = i; briefThreat = highestThreat(i); layout() } else host.sound(Sfx.NOPE, 0.5f)
                } else if (b.id in B_THREAT0 until B_THREAT0 + 4) {
                    val t = b.id - B_THREAT0
                    if (briefOpen >= 0 && threatOpen(briefOpen, t)) briefThreat = t else host.sound(Sfx.NOPE, 0.5f)
                } else if (b.id in B_UP0 until B_UP0 + Up.COUNT) buy(b.id - B_UP0)
            }
        }
    }

    private var briefThreat = 0
    private fun highestThreat(s: Int): Int { var t = 0; while (t < 3 && threatOpen(s, t + 1)) t++; return t }

    private val upFlash = FloatArray(Up.COUNT)
    private fun buy(i: Int) {
        val c = Up.cost(i, lv[i])
        if (c < 0) return
        if (cores < c) { host.sound(Sfx.NOPE, 0.5f); return }
        cores -= c
        lv[i]++
        upFlash[i] = 1f
        host.sound(Sfx.BUY, 0.8f)
        haptic(false)
        save()
    }

    // ------------------------------------------------------------------ update

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        time += dt
        modeT += dt
        if (fade > 0f) fade = max(0f, fade - dt * 3.5f)
        for (b in buttons) b.press = max(0f, b.press - dt * 5f)
        pollAssets()
        if (mode == M_HANGAR) requestShowcase()
        for (i in upFlash.indices) upFlash[i] = max(0f, upFlash[i] - dt * 2f)
        when (mode) {
            M_TITLE -> stepDemo(dt)
            M_DEBRIEF -> {
                val d = debrief
                if (d != null) for (k in 0 until 4) {
                    if (d.earned and (1 shl k) == 0 || badgeSnd and (1 shl k) != 0) continue
                    if (modeT > 0.5f + k * 0.25f) { badgeSnd = badgeSnd or (1 shl k); host.sound(Sfx.BADGE, 0.6f, 1f + k * 0.08f) }
                }
            }
            M_LOAD -> stepLoad()
            M_PLAY -> {
                val wd = world
                if (wd != null && !paused) {
                    wd.update(dt)
                    tutorialT += dt
                    terrain?.need(floor(wd.scroll / Biome.CH).toInt(), floor((wd.scroll + vh) / Biome.CH).toInt())
                    if (wd.state == World.DONE) finishMission()
                }
            }
        }
        val wd = world
        val scene = when (mode) {
            M_PLAY -> if (paused) Scene.PAUSED else if (wd != null && wd.state >= World.WARN && wd.state <= World.BOSS) Scene.BOSS
                else if (wd != null && wd.state == World.FAILED) Scene.FAIL else Scene.BATTLE
            M_DEBRIEF -> if (debrief?.won == true) Scene.WIN else Scene.FAIL
            M_LOAD -> Scene.PAUSED
            else -> Scene.MENU
        }
        val inten = if (wd != null) clamp01(wd.enemies.size / 8f + wd.eb.count / 60f) else 0f
        host.setMusicState(scene, inten)
    }

    private fun stepDemo(dt: Float) {
        var d = demo
        if (d == null || d.state >= World.WARN || !d.alive) {
            demoTerrain?.release()
            demoSector = if (d == null) 0 else (demoSector + 1) % 6
            d = World(demoSector, 0, Loadout(intArrayOf(4, 3, 3, 2, 5, 3, 3), 0), vh, 99L + demoSector)
            d.skipTo(500f)
            d.autoFire = true
            demo = d
            demoTerrain = Terrain(d.biome, min(1f, s * 0.6f), host.asyncTerrain)
        }
        val mv = FloatArray(2)
        d.autopilot(mv); d.move(mv[0] * 0.6f, mv[1] * 0.6f)
        d.update(dt)
        if (d.hp < d.maxHp * 0.5f) d.hp = d.maxHp
        if (d.scroll > 4200f) d.state = World.WARN
        demoTerrain?.need(floor(d.scroll / Biome.CH).toInt(), floor((d.scroll + vh) / Biome.CH).toInt())
    }

    // ------------------------------------------------------------------ draw

    fun draw(c: Canvas) {
        when (mode) {
            M_TITLE -> { drawBattle(c, demo, demoTerrain, null); drawTitle(c) }
            M_MAP -> drawMap(c)
            M_HANGAR -> drawHangar(c)
            M_RANKS -> drawRanks(c)
            M_LOAD -> drawLoad(c)
            M_PLAY -> { drawBattle(c, world, terrain, art); drawHud(c) }
            M_DEBRIEF -> drawDebrief(c)
        }
        if (settingsOpen) drawSettings(c)
        if (fade > 0f) { Draw.fill.color = alphaF(Col.INK, fade * 0.9f); c.drawRect(0f, 0f, w, h, Draw.fill) }
    }

    private fun drawBattle(c: Canvas, wd: World?, tr: Terrain?, ba: BossArt?) {
        val r = render
        if (wd == null || r == null) { c.drawColor(Col.INK); return }
        c.save()
        c.scale(s, s)
        r.draw(c, wd, tr, ba, time)
        c.restore()
        // damage vignette
        if (wd.hitFlash > 0f) { Draw.fill.color = alphaF(0x66FF2030, wd.hitFlash); c.drawRect(0f, 0f, w, h, Draw.fill) }
        if (wd.overT > 0f) { Draw.fill.color = 0x1AB98CFF; c.drawRect(0f, 0f, w, h, Draw.fill) }
    }

    private fun drawButton(c: Canvas, b: Btn) {
        val sc = 1f - 0.04f * b.press
        c.save()
        c.scale(sc, sc, b.r.centerX(), b.r.centerY())
        val r = b.r
        when (b.style) {
            1 -> {
                Draw.chamfer(r, u * 2.2f)
                Draw.fill.color = 0x55000000; c.save(); c.translate(0f, u * 0.7f); c.drawPath(Draw.path, Draw.fill); c.restore()
                c.save(); c.clipPath(Draw.path)
                if (b.enabled) Draw.shadeRect(c, primSh, r.left, r.top, r.right, r.bottom, r.left, 0f, r.width(), 1f)
                else { Draw.fill.color = 0xFF2A3442.toInt(); c.drawRect(r, Draw.fill) }
                Draw.fill.color = 0x26FFFFFF; c.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.45f, Draw.fill)
                c.restore()
                Draw.stroke.color = if (b.enabled) 0xFFBFF3FF.toInt() else 0xFF45546A.toInt(); Draw.stroke.strokeWidth = u * 0.25f
                c.drawPath(Draw.chamfer(r, u * 2.2f), Draw.stroke)
                label(c, b, if (b.enabled) Col.INK else Col.MUTED, 5.2f * u)
            }
            2 -> {
                Draw.panel(c, r, u, Col.PANEL, Col.EDGE, u * 1.6f)
                Icon.draw(c, b.icon, r.centerX(), r.centerY(), r.height() * 0.62f, if (b.off) Col.MUTED else Col.WHITE, b.off)
            }
            else -> {
                Draw.panel(c, r, u)
                label(c, b, Col.WHITE, 4.3f * u)
            }
        }
        c.restore()
    }

    private fun label(c: Canvas, b: Btn, col: Int, size: Float) {
        val r = b.r
        val sz = Draw.fit(b.label, size, r.width() * 0.66f, Fonts.bold, 0.12f)
        if (b.icon != 0) {
            val tw = Draw.width(b.label, sz, Fonts.bold, 0.12f)
            val iw = sz * 1.25f
            val x0 = r.centerX() - (tw + iw) / 2f
            Icon.draw(c, b.icon, x0 + iw * 0.4f, r.centerY(), sz * 1.15f, col)
            Draw.text(c, b.label, x0 + iw, r.centerY(), sz, col, Fonts.bold, Paint.Align.LEFT, 0.12f)
        } else Draw.text(c, b.label, r.centerX(), r.centerY(), sz, col, Fonts.bold, Paint.Align.CENTER, 0.12f)
    }

    private fun corePill(c: Canvas, x: Float, y: Float, alignRight: Boolean) {
        val label = "%,d".format(cores)
        val tw = Draw.width(label, 4.6f * u, Fonts.bold)
        val pw = tw + 13f * u
        val l = if (alignRight) x - pw else x
        rc.set(l, y, l + pw, y + 10f * u)
        Draw.panel(c, rc, u, Col.PANEL, 0x88FFC145.toInt(), u * 1.4f)
        Icon.draw(c, Icon.CORE, rc.left + 5.5f * u, rc.centerY(), 7f * u, Col.AMBER)
        Draw.text(c, label, rc.right - 3.5f * u, rc.centerY(), 4.6f * u, Col.WHITE, Fonts.bold, Paint.Align.RIGHT)
    }

    private fun header(c: Canvas, title: String, sub: String) {
        val y = inT + 9f * u
        Draw.text(c, title, w / 2f, y, 6.2f * u, Col.WHITE, Fonts.bold, Paint.Align.CENTER, 0.3f)
        if (sub.isNotEmpty()) Draw.text(c, sub, w / 2f, y + 6.5f * u, 2.9f * u, Col.CYAN, Fonts.semi, Paint.Align.CENTER, 0.3f)
    }

    // ------------------------------------------------------------------ title

    private fun drawTitle(c: Canvas) {
        // darken for legibility
        val g = titleSh ?: LinearGradient(0f, 0f, 0f, h, intArrayOf(0xCC050912.toInt(), 0x22050912, 0x22050912, 0xE6050912.toInt()), floatArrayOf(0f, 0.32f, 0.55f, 1f), Shader.TileMode.CLAMP).also { titleSh = it }
        Draw.grad.shader = g; c.drawRect(0f, 0f, w, h, Draw.grad); Draw.grad.shader = null
        val cx = w / 2f
        val ty = inT + h * 0.15f
        val size = Draw.fit("KESTREL", 21f * u, w * 0.86f, Fonts.bold, 0.22f)
        Draw.glow(c, cx, ty, 46f * u, 0.35f, Col.CYAN)
        Draw.text(c, "KESTREL", cx + 0.6f * u, ty + 0.8f * u, size, 0xAA000000.toInt(), Fonts.bold, Paint.Align.CENTER, 0.22f)
        Draw.text(c, "KESTREL", cx, ty, size, Col.WHITE, Fonts.bold, Paint.Align.CENTER, 0.22f)
        Draw.stroke.color = Col.CYAN; Draw.stroke.strokeWidth = 0.5f * u
        val lw = Draw.width("KESTREL", size, Fonts.bold, 0.22f) * 0.5f
        c.drawLine(cx - lw, ty + 10f * u, cx - 6f * u, ty + 10f * u, Draw.stroke)
        c.drawLine(cx + 6f * u, ty + 10f * u, cx + lw, ty + 10f * u, Draw.stroke)
        Icon.draw(c, Icon.ACE, cx, ty + 10f * u, 7f * u, Col.CYAN)
        Draw.text(c, "WINGS OVER THE HOLLOW", cx, ty + 16f * u, 3.6f * u, 0xDDEFF6FF.toInt(), Fonts.semi, Paint.Align.CENTER, 0.38f)
        // pilot line
        val tb = totalBadges()
        val rank = Perk.RANKS[Perk.rank(tb)]
        Draw.text(c, "$rank  ·  $tb / 96 BADGES", cx, h - inB - 76f * u, 3.3f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.25f)
        corePill(c, inL + 3f * u, inT + 4f * u, false)
        for (b in buttons) if (b.visible) drawButton(c, b)
        if (render == null) {
            val f = unitsProg
            Draw.text(c, "ASSEMBLING SQUADRON  ${(f * 100).toInt()}%", cx, h * 0.5f, 3.2f * u, Col.CYAN, Fonts.bold, Paint.Align.CENTER, 0.3f)
            Draw.bar(c, cx - 25f * u, h * 0.5f + 4f * u, 50f * u, 0.7f * u, f, Col.CYAN, 0xFF1A2230.toInt())
        }
    }

    // ------------------------------------------------------------------ map

    private fun ensureMap() {
        if (mapBmp != null) return
        val k = 0.5f
        val bw = max(8, (w * k).toInt()); val bh = max(8, (h * k).toInt())
        val px = IntArray(bw * bh)
        for (j in 0 until bh) for (i in 0 until bw) {
            val x = i / k / s; val y = j / k / s
            val n = Noise.fbm(x * 0.0022f, y * 0.0022f, 4, 777)
            val band = (n * 18f) % 1f
            val line = if (band < 0.06f) 1f else 0f
            var col = lerpColor(0xFF07101C.toInt(), 0xFF0E1E30.toInt(), n)
            if (line > 0f) col = lerpColor(col, 0xFF1F4A66.toInt(), 0.6f)
            if (((x / 100f).toInt() != ((x - 1f / k / s) / 100f).toInt()) || ((y / 100f).toInt() != ((y - 1f / k / s) / 100f).toInt())) col = lerpColor(col, 0xFF183247.toInt(), 0.5f)
            px[i + j * bw] = col
        }
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        b.setPixels(px, 0, bw, 0, 0, bw, bh)
        mapBmp = b
    }

    private fun drawMap(c: Canvas) {
        ensureMap()
        mapBmp?.let { rc.set(0f, 0f, w, h); bp.alpha = 255; c.drawBitmap(it, null, rc, bp) }
        header(c, "CAMPAIGN", "THEATRE OF THE HOLLOW ARMADA")
        // flight path
        Draw.stroke.color = 0x775CE1FF; Draw.stroke.strokeWidth = 0.6f * u
        for (i in 0 until 5) {
            val a = nodePos(i); val b = nodePos(i + 1)
            val n = 14
            for (k in 0 until n step 2) {
                val f0 = k / n.toFloat(); val f1 = (k + 1) / n.toFloat()
                c.drawLine(lerp(a[0], b[0], f0), lerp(a[1], b[1], f0), lerp(a[0], b[0], f1), lerp(a[1], b[1], f1), Draw.stroke)
            }
        }
        for (i in 0 until 6) drawNode(c, i)
        corePill(c, w - inR - 4f * u, h - inB - 16f * u, true)
        for (b in buttons) if (b.visible && b.style != 3 && b.id != B_CLOSE && b.id != B_LAUNCH && b.id != B_TO_HANGAR) drawButton(c, b)
        val tb = totalBadges()
        Draw.text(c, "${Perk.RANKS[Perk.rank(tb)]}  ·  $tb BADGES", inL + 5f * u, h - inB - 11f * u, 3.2f * u, Col.MUTED, Fonts.semi, Paint.Align.LEFT, 0.25f)
        if (briefOpen >= 0) drawBrief(c, briefOpen)
    }

    private fun drawNode(c: Canvas, i: Int) {
        val p = nodePos(i)
        val x = p[0]; val y = p[1]
        val open = sectorOpen(i)
        val d = Sectors.ALL[i]
        val r = 9.5f * u
        val pulse = 0.5f + 0.5f * sin(time * 3f + i)
        val next = open && !clearedSector[i]
        if (next) Draw.glow(c, x, y, r * 2.6f, 0.35f + 0.25f * pulse, d.accent)
        Draw.fill.color = if (open) 0xEE0E1A2A.toInt() else 0xCC0A0F18.toInt()
        Draw.hexagon(c, x, y, r, Draw.fill)
        Draw.stroke.color = if (open) d.accent else 0xFF33404F.toInt(); Draw.stroke.strokeWidth = 0.5f * u
        Draw.hexagon(c, x, y, r, Draw.stroke)
        if (open) Draw.text(c, "${i + 1}", x, y - 0.3f * u, 7f * u, Col.WHITE, Fonts.bold)
        else Icon.draw(c, Icon.LOCK, x, y, 7f * u, 0xFF55657A.toInt())
        // name plate on the outer side
        val right = x < w / 2f
        val lx = if (right) x + r + 3f * u else x - r - 3f * u
        val al = if (right) Paint.Align.LEFT else Paint.Align.RIGHT
        Draw.text(c, d.name, lx, y - 2.5f * u, 4.2f * u, if (open) Col.WHITE else 0xFF55657A.toInt(), Fonts.bold, al, 0.12f)
        Draw.text(c, if (open) "${sectorBadges(i)} / 16 badges" else "locked", lx, y + 2.8f * u, 2.8f * u, if (open) Col.MUTED else 0xFF45546A.toInt(), Fonts.semi, al, 0.1f)
        if (clearedSector[i]) {
            // threat pips
            for (t in 0 until 4) {
                val px = lx + (if (right) 1f else -1f) * (t * 3.2f * u + 1.2f * u)
                Draw.fill.color = if (Integer.bitCount(badges[i][t]) >= 3) Col.AMBER else if (threatOpen(i, t)) 0xFF5A6A80.toInt() else 0xFF26303D.toInt()
                c.drawRect(px - 1f * u, y + 5.6f * u, px + 1f * u, y + 7.4f * u, Draw.fill)
            }
        }
    }

    private fun thumb(i: Int): Bitmap {
        thumbs[i]?.let { return it }
        val b = Terrain.render(Biome.make(i), 3, 0.3f)
        thumbs[i] = b
        return b
    }

    private fun drawBrief(c: Canvas, i: Int) {
        Draw.fill.color = 0xAA02050A.toInt(); c.drawRect(0f, 0f, w, h, Draw.fill)
        val d = Sectors.ALL[i]
        briefRect(rc)
        val r0 = RectF(rc)
        Draw.panel(c, r0, u, 0xFA0E192A.toInt(), withAlpha(d.accent, 0xAA))
        // preview strip
        val th = thumb(i)
        val ph = min(36f * u, (r0.bottom - r0.top) * 0.22f)
        rc2.set(r0.left + 2f * u, r0.top + 2f * u, r0.right - 2f * u, r0.top + 2f * u + ph)
        c.save(); c.clipRect(rc2)
        val sc = (rc2.right - rc2.left) / th.width
        rc.set(rc2.left, rc2.centerY() - th.height * sc / 2f, rc2.right, rc2.centerY() + th.height * sc / 2f)
        bp.alpha = 255; c.drawBitmap(th, null, rc, bp)
        val sh = LinearGradient(0f, rc2.top, 0f, rc2.bottom, intArrayOf(0x00000000, 0xDD0B1422.toInt()), floatArrayOf(0.35f, 1f), Shader.TileMode.CLAMP)
        Draw.grad.shader = sh; c.drawRect(rc2, Draw.grad); Draw.grad.shader = null
        c.restore()
        Draw.text(c, "SECTOR ${i + 1}", r0.left + 5f * u, rc2.bottom - 13f * u, 3f * u, d.accent, Fonts.bold, Paint.Align.LEFT, 0.3f)
        Draw.text(c, d.name, r0.left + 5f * u, rc2.bottom - 6.5f * u, Draw.fit(d.name, 7.5f * u, (r0.right - r0.left) * 0.7f), Col.WHITE, Fonts.bold, Paint.Align.LEFT, 0.12f)
        var y = rc2.bottom + 4f * u
        Draw.text(c, d.tag.uppercase(), r0.left + 5f * u, y, 2.8f * u, Col.MUTED, Fonts.semi, Paint.Align.LEFT, 0.25f)
        y += 5f * u
        y = wrap(c, d.brief, r0.left + 5f * u, y, r0.right - r0.left - 10f * u, 3.6f * u, 0xDDEFF6FF.toInt())
        // threat chips
        val ty = threatY()
        Draw.text(c, "THREAT LEVEL", r0.left + 4f * u, ty - 4f * u, 2.8f * u, Col.MUTED, Fonts.bold, Paint.Align.LEFT, 0.3f)
        for (t in 0 until 4) {
            val b = btn(B_THREAT0 + t)
            val open = threatOpen(i, t)
            val sel = t == briefThreat
            Draw.panel(c, b.r, u, if (sel) 0xEE1B3B57.toInt() else Col.PANEL, if (sel) Col.CYAN else Col.EDGE, u * 1.2f)
            if (open) {
                Draw.text(c, Threat.NAMES[t], b.r.centerX(), b.r.centerY() - 1.6f * u, 4.6f * u, if (sel) Col.WHITE else Col.MUTED, Fonts.bold)
                Draw.text(c, Threat.LABEL[t], b.r.centerX(), b.r.centerY() + 2.8f * u, 2.3f * u, if (sel) Col.CYAN else 0xFF5A6A80.toInt(), Fonts.bold, Paint.Align.CENTER, 0.2f)
            } else Icon.draw(c, Icon.LOCK, b.r.centerX(), b.r.centerY(), 5f * u, 0xFF45546A.toInt())
        }
        val nextLocked = (1..3).firstOrNull { !threatOpen(i, it) }
        if (nextLocked != null) Draw.text(c, "Earn 3 badges at threat ${Threat.NAMES[nextLocked - 1]} to unlock threat ${Threat.NAMES[nextLocked]}", r0.left + 4f * u, ty + 14f * u, 2.6f * u, Col.MUTED, Fonts.semi, Paint.Align.LEFT)
        // objectives
        y = ty + 19f * u
        val bits = badges[i][briefThreat]
        val names = arrayOf("SWEEP", "LIFELINE", "UNTOUCHED", "ACE")
        val desc = arrayOf("Destroy 80% of hostiles", "Rescue all 5 survivors", "Take no damage", d.aceText)
        val rowH = min(9.5f * u, (btn(B_LAUNCH).r.top - y - 9f * u) / 4f)
        for (k in 0 until 4) {
            val got = bits and (1 shl k) != 0
            val cy = y + k * rowH + rowH / 2f
            Draw.fill.color = if (got) 0x33FFC145 else 0x22FFFFFF
            Draw.hexagon(c, r0.left + 9f * u, cy, 3.6f * u, Draw.fill)
            Icon.draw(c, Badge.ICONS[k], r0.left + 9f * u, cy, 5f * u, if (got) Col.AMBER else Col.MUTED)
            Draw.text(c, names[k], r0.left + 15f * u, cy - 1.7f * u, 3.5f * u, if (got) Col.AMBER else Col.WHITE, Fonts.bold, Paint.Align.LEFT, 0.15f)
            Draw.text(c, desc[k], r0.left + 15f * u, cy + 2.4f * u, 2.9f * u, Col.MUTED, Fonts.semi, Paint.Align.LEFT, 0.05f)
            if (got) Icon.draw(c, Icon.CHECK, r0.right - 8f * u, cy, 5f * u, Col.AMBER)
        }
        val ry = btn(B_LAUNCH).r.top - 4.5f * u
        Draw.text(c, "REWARDS ×${Threat.REWARD[briefThreat]}", r0.left + 5f * u, ry, 3f * u, Col.AMBER, Fonts.bold, Paint.Align.LEFT, 0.2f)
        Draw.text(c, "BOSS: ${d.bossName}", r0.right - 5f * u, ry, 3f * u, Col.DANGER, Fonts.bold, Paint.Align.RIGHT, 0.2f)
        for (id in intArrayOf(B_CLOSE, B_LAUNCH, B_TO_HANGAR)) drawButton(c, btn(id))
    }

    private fun wrap(c: Canvas, text: String, x: Float, y0: Float, maxW: Float, size: Float, col: Int): Float {
        var y = y0
        val words = text.split(" ")
        var line = ""
        for (wd in words) {
            val t = if (line.isEmpty()) wd else "$line $wd"
            if (Draw.width(t, size, Fonts.semi) > maxW && line.isNotEmpty()) {
                Draw.text(c, line, x, y, size, col, Fonts.semi, Paint.Align.LEFT)
                y += size * 1.35f; line = wd
            } else line = t
        }
        if (line.isNotEmpty()) { Draw.text(c, line, x, y, size, col, Fonts.semi, Paint.Align.LEFT); y += size * 1.35f }
        return y
    }

    // ------------------------------------------------------------------ hangar

    private fun drawHangar(c: Canvas) {
        c.drawColor(0xFF070C14.toInt())
        // floor grid with a spotlight
        Draw.glow(c, w / 2f, inT + h * 0.18f, w * 0.55f, 0.45f, 0xFF1F5C80.toInt())
        Draw.stroke.color = 0x2280D8FF; Draw.stroke.strokeWidth = 0.15f * u
        var gx = (time * 3f * u) % (8f * u); while (gx < w) { c.drawLine(gx, inT + 14f * u, gx, inT + h * 0.31f, Draw.stroke); gx += 8f * u }
        var gy = inT + 14f * u; while (gy < inT + h * 0.31f) { c.drawLine(0f, gy, w, gy, Draw.stroke); gy += 8f * u }
        header(c, "HANGAR", "KESTREL · MK ${1 + lv.sum() / 6}")
        corePill(c, w - inR - 3f * u, inT + 4f * u, true)
        // ship preview
        val r = render
        val u3 = units
        val ship = showcase
        val cx = w / 2f; val cy = inT + h * 0.19f + sin(time * 1.6f) * u
        if (ship != null && r != null && showcaseWanted == showcaseKeyNow()) {
            // the showcase is rendered at hangar size, drawn 1:1 in pixels
            val sc = h * 0.17f / 108f
            c.save(); c.translate(cx, cy); c.scale(sc, sc)
            r.shadowOf(c, ship, 0f, 0f, 0f, 70f, 0.5f)
            r.spr(c, ship, 0f, 0f)
            c.restore()
            if (u3 != null) {
                val k = h * 0.17f / 108f
                val nd = if (lv[Up.DRONE] == 0) 0 else if (lv[Up.DRONE] == 1) 1 else 2
                c.save(); c.translate(cx, cy); c.scale(k, k)
                for (i in 0 until nd) r.spr(c, u3.drone, if (i == 0) -82f else 82f, 26f + sin(time * 3f + i) * 4f)
                c.restore()
            }
        } else Draw.text(c, "PREPARING MODEL", cx, cy, 3f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.3f)
        // upgrade cards (scrollable)
        val listTop = inT + h * 0.33f
        val listBot = h - inB - 3f * u
        val total = Up.COUNT * (25f * u + 2.5f * u)
        hangarMax = max(0f, total - (listBot - listTop))
        c.save()
        rc.set(0f, listTop - u, w, listBot); c.clipRect(rc)
        for (i in 0 until Up.COUNT) upCard(c, i)
        c.restore()
        if (hangarMax > 0f) {
            val f = hangarScroll / hangarMax
            Draw.fill.color = 0x445CE1FF
            val th = (listBot - listTop) * (listBot - listTop) / total
            c.drawRect(w - inR - 1.6f * u, listTop + f * (listBot - listTop - th), w - inR - 0.8f * u, listTop + f * (listBot - listTop - th) + th, Draw.fill)
        }
        drawButton(c, btn(B_BACK))
    }

    private fun upCard(c: Canvas, i: Int) {
        val r = RectF(upRect(i))
        if (r.bottom < 0f || r.top > h) return
        Draw.panel(c, r, u, Col.PANEL, if (upFlash[i] > 0f) lerpColor(Col.EDGE, 0xFFFFC145.toInt(), upFlash[i]) else Col.EDGE)
        if (upFlash[i] > 0f) Draw.glow(c, r.centerX(), r.centerY(), r.width() * 0.5f, upFlash[i] * 0.4f, Col.AMBER)
        val ix = r.left + 9f * u; val iy = r.top + 9f * u
        Draw.fill.color = 0x2A5CE1FF; Draw.hexagon(c, ix, iy, 6f * u, Draw.fill)
        Icon.draw(c, Up.ICONS[i], ix, iy, 8f * u, Col.CYAN)
        Draw.text(c, Up.NAMES[i], r.left + 18f * u, r.top + 5.5f * u, 4.3f * u, Col.WHITE, Fonts.bold, Paint.Align.LEFT, 0.12f)
        // level pips
        for (k in 0 until Up.MAX[i]) {
            val px = r.left + 18f * u + k * 4.4f * u
            Draw.fill.color = if (k < lv[i]) Col.CYAN else 0xFF22303F.toInt()
            c.drawRect(px, r.top + 9.5f * u, px + 3.4f * u, r.top + 11f * u, Draw.fill)
        }
        Draw.text(c, Up.stat(i, lv[i]), r.left + 18f * u, r.top + 15f * u, 3.1f * u, Col.MUTED, Fonts.semi, Paint.Align.LEFT, 0.05f)
        val cost = Up.cost(i, lv[i])
        if (cost >= 0) Draw.text(c, "next: " + Up.stat(i, lv[i] + 1), r.left + 18f * u, r.top + 20f * u, 3f * u, 0xFF8FE6B0.toInt(), Fonts.semi, Paint.Align.LEFT, 0.05f)
        else Draw.text(c, Up.BLURB[i], r.left + 18f * u, r.top + 20f * u, 2.8f * u, Col.MUTED, Fonts.semi, Paint.Align.LEFT)
        val b = btn(B_UP0 + i)
        b.r.set(r.right - 30f * u, r.bottom - 12.5f * u, r.right - 3f * u, r.bottom - 3.5f * u)
        if (cost < 0) {
            Draw.text(c, "MAX", b.r.centerX(), b.r.centerY(), 4.4f * u, Col.AMBER, Fonts.bold, Paint.Align.CENTER, 0.3f)
            b.visible = false
        } else {
            b.visible = true
            b.enabled = cores >= cost
            val sc = 1f - 0.04f * b.press
            c.save(); c.scale(sc, sc, b.r.centerX(), b.r.centerY())
            Draw.panel(c, b.r, u, if (b.enabled) 0xEE3B2A08.toInt() else 0xCC161C26.toInt(), if (b.enabled) Col.AMBER else 0xFF33404F.toInt(), u * 1.2f)
            Icon.draw(c, Icon.CORE, b.r.left + 5f * u, b.r.centerY(), 5.5f * u, if (b.enabled) Col.AMBER else Col.MUTED)
            Draw.text(c, "%,d".format(cost), b.r.right - 3f * u, b.r.centerY(), 4.2f * u, if (b.enabled) Col.WHITE else Col.MUTED, Fonts.bold, Paint.Align.RIGHT)
            c.restore()
        }
    }

    // ------------------------------------------------------------------ ranks

    private fun drawRanks(c: Canvas) {
        c.drawColor(0xFF070C14.toInt())
        val tb = totalBadges()
        header(c, "PILOT RANK", Perk.RANKS[Perk.rank(tb)])
        val cx = w / 2f
        var y = inT + 22f * u
        Draw.bar(c, inL + 8f * u, y, w - inL - inR - 16f * u, 1.6f * u, tb / 96f, Col.AMBER, 0xFF1A2230.toInt())
        Draw.text(c, "$tb / 96 BADGES", cx, y + 5f * u, 3.2f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.2f)
        y += 10f * u
        val rowH = min(13.5f * u, (h - inB - 4f * u - y) / Perk.COUNT)
        for (i in 0 until Perk.COUNT) {
            val got = tb >= Perk.NEED[i]
            rc.set(inL + 4f * u, y + i * rowH, w - inR - 4f * u, y + i * rowH + rowH - 1.5f * u)
            Draw.panel(c, rc, u, if (got) 0xEE132436.toInt() else 0xCC0B1018.toInt(), if (got) 0xAAFFC145.toInt() else 0x44506080, u * 1.4f)
            val cy = rc.centerY()
            Draw.text(c, "${Perk.NEED[i]}", rc.left + 6f * u, cy, 4.6f * u, if (got) Col.AMBER else 0xFF45546A.toInt(), Fonts.bold)
            Draw.text(c, Perk.NAMES[i], rc.left + 13f * u, cy - 2f * u, 3.8f * u, if (got) Col.WHITE else Col.MUTED, Fonts.bold, Paint.Align.LEFT, 0.15f)
            Draw.text(c, Perk.DESC[i], rc.left + 13f * u, cy + 2.6f * u, 2.9f * u, if (got) 0xFF9FD8FF.toInt() else 0xFF55657A.toInt(), Fonts.semi, Paint.Align.LEFT)
            Icon.draw(c, if (got) Icon.CHECK else Icon.LOCK, rc.right - 6f * u, cy, 4.6f * u, if (got) Col.AMBER else 0xFF45546A.toInt())
        }
        drawButton(c, btn(B_BACK))
    }

    // ------------------------------------------------------------------ loading

    private fun drawLoad(c: Canvas) {
        c.drawColor(0xFF05080F.toInt())
        val d = Sectors.ALL[sector]
        val cx = w / 2f; val cy = h * 0.45f
        Draw.glow(c, cx, cy, 40f * u, 0.3f, d.accent)
        Draw.text(c, "SECTOR ${sector + 1}  ·  THREAT ${Threat.NAMES[threat]}", cx, cy - 12f * u, 3.4f * u, d.accent, Fonts.bold, Paint.Align.CENTER, 0.35f)
        Draw.text(c, d.name, cx, cy, Draw.fit(d.name, 10f * u, w * 0.84f), Col.WHITE, Fonts.bold, Paint.Align.CENTER, 0.15f)
        val f = clamp01((min(loadStep / 6f, 1f) + artProg + (if (render != null) 1f else unitsProg)) / 3f)
        Draw.bar(c, cx - 25f * u, cy + 10f * u, 50f * u, 0.8f * u, f, Col.CYAN, 0xFF1A2230.toInt())
        Draw.text(c, "DEPLOYING", cx, cy + 16f * u, 3f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.5f)
    }

    // ------------------------------------------------------------------ HUD

    private fun drawHud(c: Canvas) {
        val wd = world ?: return
        val top = inT + 3f * u
        // hull
        val hx = inL + 17f * u
        rc.set(hx, top + 1f * u, hx + 34f * u, top + 10f * u)
        Draw.panel(c, rc, u, 0xAA0B1422.toInt(), Col.EDGE, u * 1.2f)
        Icon.draw(c, Icon.HULL, rc.left + 4f * u, rc.centerY(), 5f * u, if (wd.shieldT > 0f) 0xFF7FB8FF.toInt() else Col.WHITE)
        val hf = wd.hp / wd.maxHp
        val hc = if (hf > 0.5f) Col.GREEN else if (hf > 0.25f) Col.AMBER else Col.DANGER
        val segs = 10
        val bw = (rc.width() - 10f * u) / segs
        for (k in 0 until segs) {
            Draw.fill.color = if ((k + 0.5f) / segs <= hf) hc else 0x33FFFFFF
            c.drawRect(rc.left + 8f * u + k * bw, rc.top + 2.6f * u, rc.left + 8f * u + (k + 1) * bw - 0.5f * u, rc.bottom - 2.6f * u, Draw.fill)
        }
        // cores
        val label = "%,d".format((wd.coresRaw * Threat.REWARD[threat]).toInt())
        val tw = Draw.width(label, 4.6f * u, Fonts.bold)
        rc.set(w - inR - 4f * u - tw - 12f * u, top + 1f * u, w - inR - 4f * u, top + 10f * u)
        Draw.panel(c, rc, u, 0xAA0B1422.toInt(), 0x88FFC145.toInt(), u * 1.2f)
        Icon.draw(c, Icon.CORE, rc.left + 5f * u, rc.centerY(), 6.5f * u, Col.AMBER)
        Draw.text(c, label, rc.right - 3f * u, rc.centerY(), 4.6f * u, Col.WHITE, Fonts.bold, Paint.Align.RIGHT)
        // mission progress / boss bar
        val b = wd.boss
        val py = top + 15f * u
        if (b != null && b.alive && !b.entering) {
            val bw2 = w - inL - inR - 16f * u
            Draw.text(c, b.name, w / 2f, py, 3.2f * u, Col.DANGER, Fonts.bold, Paint.Align.CENTER, 0.4f)
            Draw.bar(c, inL + 8f * u, py + 3f * u, bw2, 1.4f * u, b.hp / b.maxHp, Col.DANGER, 0x55000000)
        } else {
            val f = clamp01(wd.scroll / wd.level.length)
            val l = inL + 18f * u; val r = w - inR - 18f * u
            Draw.fill.color = 0x55000000; c.drawRect(l, py, r, py + 0.7f * u, Draw.fill)
            Draw.fill.color = Col.CYAN; c.drawRect(l, py, l + (r - l) * f, py + 0.7f * u, Draw.fill)
            Draw.fill.color = Col.DANGER; Draw.hexagon(c, r, py + 0.35f * u, 1.4f * u, Draw.fill)
            // survivors
            for (k in 0 until 5) {
                val st = wd.survivors.getOrNull(k)?.state ?: 0
                val sx = l + (k + 0.5f) * 4.2f * u
                Icon.draw(c, Icon.LIFELINE, sx, py + 4.5f * u, 4f * u, when (st) { 1 -> Col.GREEN; 2 -> 0x66FF4D5E; else -> 0x88FFFFFF.toInt() })
            }
            Draw.text(c, "${(wd.killRatio() * 100).toInt()}%", r, py + 4.5f * u, 2.8f * u, if (wd.killRatio() >= 0.8f) Col.AMBER else Col.MUTED, Fonts.bold, Paint.Align.RIGHT, 0.1f)
        }
        // overdrive
        val ob = btn(B_OVER)
        val ocx = ob.r.centerX(); val ocy = ob.r.centerY(); val orr = ob.r.width() / 2f
        val ready = wd.canOverdrive()
        if (ready) Draw.glow(c, ocx, ocy, orr * 2f, 0.5f + 0.3f * sin(time * 6f), 0xFFB98CFF.toInt())
        Draw.fill.color = 0xAA0B1422.toInt(); c.drawCircle(ocx, ocy, orr, Draw.fill)
        Draw.stroke.color = 0x55B98CFF; Draw.stroke.strokeWidth = 1.1f * u
        c.drawCircle(ocx, ocy, orr - 1f * u, Draw.stroke)
        Draw.stroke.color = if (wd.overT > 0f) 0xFFFFFFFF.toInt() else 0xFFB98CFF.toInt()
        Draw.r1.set(ocx - orr + 1f * u, ocy - orr + 1f * u, ocx + orr - 1f * u, ocy + orr - 1f * u)
        val frac = if (wd.overT > 0f) 1f else wd.over / 100f
        c.drawArc(Draw.r1, -90f, 360f * frac, false, Draw.stroke)
        Icon.draw(c, Icon.OVERDRIVE, ocx, ocy - (if (ready) 1.2f * u else 0f), orr * 0.95f, if (ready || wd.overT > 0f) Col.WHITE else 0xFF6E5A8F.toInt())
        if (ready) Draw.text(c, "TAP", ocx, ocy + orr * 0.55f, 2.4f * u, Col.WHITE, Fonts.bold, Paint.Align.CENTER, 0.3f)
        drawButton(c, btn(B_PAUSE))
        // surge / shield timers
        var ty = top + 26f * u
        if (wd.surgeT > 0f) { Draw.text(c, "SURGE ${wd.surgeT.toInt() + 1}", inL + 4f * u, ty, 3f * u, Col.AMBER, Fonts.bold, Paint.Align.LEFT, 0.2f); ty += 4.5f * u }
        if (wd.shieldT > 0f) { Draw.text(c, "SHIELD ${wd.shieldT.toInt() + 1}", inL + 4f * u, ty, 3f * u, 0xFF7FB8FF.toInt(), Fonts.bold, Paint.Align.LEFT, 0.2f) }
        // banners
        if (wd.state == World.INTRO || (wd.state == World.PLAY && wd.stateT < 2.2f)) {
            val t = if (wd.state == World.INTRO) wd.stateT else 1.6f + wd.stateT
            val a = if (t < 0.4f) t / 0.4f else if (t > 3f) 1f - (t - 3f) / 0.8f else 1f
            val d = Sectors.ALL[sector]
            Draw.text(c, "SECTOR ${sector + 1}  ·  THREAT ${Threat.NAMES[threat]}", w / 2f, h * 0.36f, 3.4f * u, alphaF(d.accent, a), Fonts.bold, Paint.Align.CENTER, 0.4f)
            Draw.text(c, d.name, w / 2f, h * 0.36f + 9f * u, Draw.fit(d.name, 9f * u, w * 0.84f, Fonts.bold, 0.15f), alphaF(Col.WHITE, a), Fonts.bold, Paint.Align.CENTER, 0.15f)
        }
        if (wd.state == World.WARN) warning(c, wd)
        if (wd.bannerT > 0f && wd.state != World.WARN) {
            val a = clamp01(wd.bannerT / 0.4f)
            Draw.text(c, wd.banner, w / 2f + 0.3f * u, h * 0.3f + 0.4f * u, Draw.fit(wd.banner, 6.6f * u, w * 0.88f, Fonts.bold, 0.2f), alphaF(0xAA000000.toInt(), a), Fonts.bold, Paint.Align.CENTER, 0.2f)
            Draw.text(c, wd.banner, w / 2f, h * 0.3f, Draw.fit(wd.banner, 6.6f * u, w * 0.88f, Fonts.bold, 0.2f), alphaF(Col.WHITE, a), Fonts.bold, Paint.Align.CENTER, 0.2f)
        }
        if (wd.state == World.CLEAR) {
            val a = clamp01((wd.stateT - 1.2f) / 0.4f)
            Draw.text(c, "SECTOR CLEAR", w / 2f, h * 0.4f, Draw.fit("SECTOR CLEAR", 10f * u, w * 0.88f, Fonts.bold, 0.2f), alphaF(Col.AMBER, a), Fonts.bold, Paint.Align.CENTER, 0.2f)
        }
        if (wd.state == World.FAILED) {
            val a = clamp01((wd.stateT - 0.6f) / 0.4f)
            Draw.text(c, "KESTREL DOWN", w / 2f, h * 0.4f, Draw.fit("KESTREL DOWN", 10f * u, w * 0.88f, Fonts.bold, 0.2f), alphaF(Col.DANGER, a), Fonts.bold, Paint.Align.CENTER, 0.2f)
        }
        // first-flight tutorial
        if (!tutorialDone && wd.state == World.PLAY) {
            val tips = arrayOf("DRAG ANYWHERE TO FLY", "YOUR CANNONS FIRE ON THEIR OWN", "HOVER OVER GREEN BEACONS TO RESCUE", "DOUBLE-TAP OR PRESS THE PRISM FOR OVERDRIVE")
            val k = ((tutorialT - 2f) / 3.6f).toInt()
            if (k in tips.indices) {
                val tt = (tutorialT - 2f) % 3.6f
                val a = if (tt < 0.3f) tt / 0.3f else if (tt > 3.1f) (3.6f - tt) / 0.5f else 1f
                rc.set(w / 2f - 40f * u, h * 0.62f, w / 2f + 40f * u, h * 0.62f + 10f * u)
                Draw.fill.color = alphaF(0xCC0B1422.toInt(), a); c.drawRect(rc, Draw.fill)
                Draw.text(c, tips[k], w / 2f, rc.centerY(), Draw.fit(tips[k], 3.6f * u, 76f * u, Fonts.bold, 0.15f), alphaF(Col.WHITE, a), Fonts.bold, Paint.Align.CENTER, 0.15f)
            }
        }
        if (paused) drawPause(c)
    }

    private fun warning(c: Canvas, wd: World) {
        val t = wd.warnT
        val a = (if (t < 0.3f) t / 0.3f else if (t > 2.6f) (3f - t) / 0.4f else 1f).coerceIn(0f, 1f)
        val blink = if (sin(t * 14f) > -0.3f) 1f else 0.5f
        val cy = h * 0.34f
        Draw.fill.color = alphaF(0x88200008.toInt(), a); c.drawRect(0f, cy - 13f * u, w, cy + 13f * u, Draw.fill)
        Draw.fill.color = alphaF(Col.DANGER, a * 0.9f)
        val off = (t * 30f * u) % (8f * u)
        c.save(); rc.set(0f, cy - 13f * u, w, cy - 10.5f * u); c.clipRect(rc)
        var x = -8f * u + off; while (x < w) { path.reset(); path.moveTo(x, cy - 13f * u); path.lineTo(x + 4f * u, cy - 13f * u); path.lineTo(x + 6.5f * u, cy - 10.5f * u); path.lineTo(x + 2.5f * u, cy - 10.5f * u); path.close(); c.drawPath(path, Draw.fill); x += 8f * u }
        c.restore()
        c.save(); rc.set(0f, cy + 10.5f * u, w, cy + 13f * u); c.clipRect(rc)
        x = -8f * u - off; while (x < w + 8f * u) { path.reset(); path.moveTo(x, cy + 10.5f * u); path.lineTo(x + 4f * u, cy + 10.5f * u); path.lineTo(x + 6.5f * u, cy + 13f * u); path.lineTo(x + 2.5f * u, cy + 13f * u); path.close(); c.drawPath(path, Draw.fill); x += 8f * u }
        c.restore()
        Draw.text(c, "WARNING", w / 2f, cy - 2.5f * u, 9f * u, alphaF(Col.DANGER, a * blink), Fonts.bold, Paint.Align.CENTER, 0.5f)
        Draw.text(c, "${Sectors.ALL[sector].bossName} APPROACHING", w / 2f, cy + 6f * u, 3.2f * u, alphaF(Col.WHITE, a), Fonts.bold, Paint.Align.CENTER, 0.4f)
    }

    private fun drawPause(c: Canvas) {
        Draw.fill.color = 0xCC03060C.toInt(); c.drawRect(0f, 0f, w, h, Draw.fill)
        val cx = w / 2f
        rc.set(cx - 36f * u, h * 0.42f - 26f * u, cx + 36f * u, h * 0.42f + 50f * u)
        Draw.panel(c, rc, u, Col.PANEL_HI)
        Draw.text(c, "PAUSED", cx, h * 0.42f - 16f * u, 7f * u, Col.WHITE, Fonts.bold, Paint.Align.CENTER, 0.4f)
        val wd = world
        if (wd != null) Draw.text(c, "${Sectors.ALL[sector].name}  ·  THREAT ${Threat.NAMES[threat]}", cx, h * 0.42f - 8f * u, 3f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.2f)
        Draw.text(c, "Abort keeps the cores you have collected", cx, h * 0.42f + 45f * u, 2.6f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER)
        for (b in buttons) if (b.visible && b.id != B_PAUSE && b.id != B_OVER) drawButton(c, b)
    }

    private fun drawSettings(c: Canvas) {
        Draw.fill.color = 0xCC03060C.toInt(); c.drawRect(0f, 0f, w, h, Draw.fill)
        val cx = w / 2f
        rc.set(cx - 40f * u, h * 0.3f, cx + 40f * u, h * 0.3f + 62f * u)
        Draw.panel(c, rc, u, Col.PANEL_HI)
        Draw.text(c, "SETTINGS", cx, h * 0.3f + 9f * u, 6f * u, Col.WHITE, Fonts.bold, Paint.Align.CENTER, 0.4f)
        Draw.text(c, "SOUND  ·  MUSIC  ·  VIBRATION  ·  CONTROL SPEED", cx, h * 0.3f + 17f * u, 2.6f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.2f)
        Draw.text(c, "Drag anywhere to fly. Double-tap for Overdrive.", cx, h * 0.3f + 39f * u, 2.8f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER)
        for (b in buttons) if (b.visible) drawButton(c, b)
    }

    // ------------------------------------------------------------------ debrief

    private fun drawDebrief(c: Canvas) {
        val d = debrief ?: return
        c.drawColor(0xFF05080F.toInt())
        val sd = Sectors.ALL[d.sector]
        Draw.glow(c, w / 2f, h * 0.15f, w * 0.6f, 0.35f, if (d.won) sd.accent else Col.DANGER)
        val cx = w / 2f
        val t = modeT
        val top = inT + 8f * u
        Draw.text(c, "${sd.name}  ·  THREAT ${Threat.NAMES[d.threat]}", cx, top, 3.2f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.3f)
        val title = if (d.won) "MISSION COMPLETE" else "MISSION FAILED"
        Draw.text(c, title, cx, top + 9f * u, Draw.fit(title, 8.5f * u, w * 0.9f, Fonts.bold, 0.12f), if (d.won) Col.WHITE else Col.DANGER, Fonts.bold, Paint.Align.CENTER, 0.12f)
        // badges
        val by = top + 30f * u
        val gap = 21f * u
        for (k in 0 until 4) {
            val x = cx + (k - 1.5f) * gap
            val got = d.earned and (1 shl k) != 0
            val isNew = d.newBits and (1 shl k) != 0
            val appear = clamp01((t - 0.5f - k * 0.25f) / 0.25f)
            val sc = if (got) easeOutBack(appear) else 1f
            c.save(); c.scale(sc, sc, x, by)
            if (got) Draw.glow(c, x, by, 12f * u, 0.5f, Col.AMBER)
            Draw.fill.color = if (got) 0xFF3B2A08.toInt() else 0xFF121A26.toInt()
            Draw.hexagon(c, x, by, 8f * u, Draw.fill)
            Draw.stroke.color = if (got) Col.AMBER else 0xFF2A3646.toInt(); Draw.stroke.strokeWidth = 0.5f * u
            Draw.hexagon(c, x, by, 8f * u, Draw.stroke)
            Icon.draw(c, Badge.ICONS[k], x, by, 9f * u, if (got) Col.AMBER else 0xFF3A4656.toInt())
            c.restore()
            Draw.text(c, Badge.NAMES[k], x, by + 12f * u, 2.6f * u, if (got) Col.AMBER else Col.MUTED, Fonts.bold, Paint.Align.CENTER, 0.15f)
            if (isNew && appear >= 1f) Draw.text(c, "NEW", x + 6f * u, by - 7f * u, 2.6f * u, Col.GREEN, Fonts.bold, Paint.Align.CENTER, 0.2f)
        }
        // stats
        var y = by + 22f * u
        rc.set(inL + 6f * u, y, w - inR - 6f * u, y + 46f * u)
        Draw.panel(c, rc, u)
        val rows = arrayOf(
            Triple("HOSTILES DESTROYED", "${d.killPct}%", d.killPct >= 80),
            Triple("SURVIVORS RESCUED", "${d.rescued} / 5", d.rescued >= 5),
            Triple("DAMAGE TAKEN", "${d.damage}", d.damage == 0 && d.won),
            Triple("ACE: ${sd.aceText.uppercase()}", d.ace, d.earned and 8 != 0))
        for ((k, r) in rows.withIndex()) {
            val ry = rc.top + 6f * u + k * 9.5f * u
            val a = clamp01((t - 0.2f - k * 0.1f) / 0.3f)
            Draw.text(c, r.first, rc.left + 4f * u, ry, Draw.fit(r.first, 3.1f * u, rc.width() * 0.62f, Fonts.semi, 0.1f), alphaF(Col.MUTED, a), Fonts.semi, Paint.Align.LEFT, 0.1f)
            Draw.text(c, r.second, rc.right - 4f * u, ry, 4.2f * u, alphaF(if (r.third) Col.AMBER else Col.WHITE, a), Fonts.bold, Paint.Align.RIGHT)
        }
        // payout
        y = rc.bottom + 12f * u
        val shown = (d.payout * smooth((t - 0.6f) / 1.2f)).toInt()
        Icon.draw(c, Icon.CORE, cx - Draw.width("+%,d".format(d.payout), 8f * u, Fonts.bold) / 2f - 6f * u, y, 10f * u, Col.AMBER)
        Draw.text(c, "+%,d".format(shown), cx, y, 8f * u, Col.AMBER, Fonts.bold)
        Draw.text(c, "CORES  ·  BANK %,d".format(cores), cx, y + 7f * u, 3f * u, Col.MUTED, Fonts.semi, Paint.Align.CENTER, 0.3f)
        var ny = y + 14f * u
        if (d.unlockedSector) { Draw.text(c, "NEW SECTOR UNLOCKED: ${Sectors.ALL[d.sector + 1].name}", cx, ny, 3.4f * u, Col.GREEN, Fonts.bold, Paint.Align.CENTER, 0.15f); ny += 5.5f * u }
        if (d.unlockedThreat) Draw.text(c, "THREAT ${Threat.NAMES[d.threat + 1]} UNLOCKED", cx, ny, 3.4f * u, Col.GREEN, Fonts.bold, Paint.Align.CENTER, 0.15f)
        if (t > 0.6f) for (b in buttons) if (b.visible) drawButton(c, b)
    }

    fun release() {
        releaseMission()
        demoTerrain?.release(); demoTerrain = null
        sprites?.release(); sprites = null
        units?.release(); units = null; render = null
        showcase?.release(); showcase = null
        pendingUnits.getAndSet(null)?.release(); pendingArt.getAndSet(null)?.release(); pendingShowcase.getAndSet(null)?.release()
        assetExec.shutdownNow()
        mapBmp?.recycle(); mapBmp = null
        for (i in thumbs.indices) { thumbs[i]?.recycle(); thumbs[i] = null }
    }

    // ------------------------------------------------------------------ test hooks

    fun debugBtn(id: Int, out: FloatArray): Boolean {
        val b = buttons.firstOrNull { it.id == id && it.visible } ?: return false
        out[0] = b.r.centerX(); out[1] = b.r.centerY(); return true
    }
    fun visibleButtons(): List<Int> = buttons.filter { it.visible }.map { it.id }
    fun debugCores(n: Int) { cores = n }
    fun debugBrief(i: Int) = briefOpen == i
    val isPaused get() = paused
}
