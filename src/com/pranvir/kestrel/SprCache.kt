package com.pranvir.kestrel

import android.graphics.Bitmap
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Rendered 3D sprites are deterministic for a given scale, so they are kept on disk (gzipped pixels)
 * and the next launch skips the renderer entirely. Any mismatch simply falls back to rendering.
 */
object SprCache {
    private const val MAGIC = 0x4B535431  // "KST1"
    const val VERSION = 3

    fun file(dir: File?, name: String, scale: Float): File? = dir?.let { File(it, "spr_v${VERSION}_${name}_${(scale * 1000).toInt()}.bin") }

    fun read(f: File?, expected: Int): ArrayDeque<Spr>? {
        if (f == null || !f.exists()) return null
        return try {
            val bytes = GZIPInputStream(f.inputStream(), 1 shl 16).use { it.readBytes() }
            val bb = java.nio.ByteBuffer.wrap(bytes)
            if (bb.int != MAGIC || bb.int != VERSION) return null
            val n = bb.int
            if (n != expected) return null
            val out = ArrayDeque<Spr>()
            repeat(n) {
                val w = bb.int; val h = bb.int
                val px = IntArray(w * h); bb.asIntBuffer().get(px); bb.position(bb.position() + px.size * 4)
                val sw = bb.int; val sh = bb.int
                var spx: IntArray? = null
                if (sw > 0) { spx = IntArray(sw * sh); bb.asIntBuffer().get(spx); bb.position(bb.position() + spx.size * 4) }
                val uw = bb.float; val uh = bb.float; val usw = bb.float; val ush = bb.float
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.setPixels(px, 0, w, 0, 0, w, h)
                for (i in px.indices) px[i] = (px[i] and 0xFF000000.toInt()) or 0xFFFFFF
                val fb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); fb.setPixels(px, 0, w, 0, 0, w, h)
                var sb: Bitmap? = null
                if (spx != null) { sb = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888); sb.setPixels(spx, 0, sw, 0, 0, sw, sh) }
                out.addLast(Spr(b, sb, fb, uw, uh, usw, ush))
            }
            out
        } catch (t: Throwable) { null }
    }

    fun write(f: File?, list: List<Spr>) {
        if (f == null) return
        try {
            var size = 12
            for (s in list) { size += 8 + s.bmp.width * s.bmp.height * 4 + 8 + (s.shadow?.let { it.width * it.height * 4 } ?: 0) + 16 }
            val bb = java.nio.ByteBuffer.allocate(size)
            bb.putInt(MAGIC); bb.putInt(VERSION); bb.putInt(list.size)
            for (s in list) {
                val w = s.bmp.width; val h = s.bmp.height
                val px = IntArray(w * h); s.bmp.getPixels(px, 0, w, 0, 0, w, h)
                bb.putInt(w); bb.putInt(h); bb.asIntBuffer().put(px); bb.position(bb.position() + px.size * 4)
                val sh = s.shadow
                if (sh != null) {
                    val sp = IntArray(sh.width * sh.height); sh.getPixels(sp, 0, sh.width, 0, 0, sh.width, sh.height)
                    bb.putInt(sh.width); bb.putInt(sh.height); bb.asIntBuffer().put(sp); bb.position(bb.position() + sp.size * 4)
                } else { bb.putInt(0); bb.putInt(0) }
                bb.putFloat(s.w); bb.putFloat(s.h); bb.putFloat(s.sw); bb.putFloat(s.sh)
            }
            val tmp = File(f.parentFile, f.name + ".tmp")
            GZIPOutputStream(tmp.outputStream(), 1 shl 16).use { it.write(bb.array(), 0, bb.position()) }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (t: Throwable) { }
    }
}
