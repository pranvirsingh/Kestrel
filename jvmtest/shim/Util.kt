package android.util

object Log {
    @JvmStatic fun e(tag: String, msg: String, t: Throwable? = null): Int { System.err.println("E/$tag: $msg ${t ?: ""}"); t?.printStackTrace(); return 0 }
    @JvmStatic fun w(tag: String, msg: String): Int { System.err.println("W/$tag: $msg"); return 0 }
}
