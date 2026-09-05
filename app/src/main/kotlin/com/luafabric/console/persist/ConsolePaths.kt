package com.luafabric.console.persist

import android.content.Context
import java.io.File

/**
 * 持久化根：android/data/<包名> 私有目录（getExternalFilesDir），
 * 下辖 outputs / logcat / crash / sessions 四个子目录。
 */
object ConsolePaths {

    @Volatile
    private var root: File? = null

    fun init(context: Context) {
        if (root == null) {
            synchronized(this) {
                if (root == null) {
                    val dir = File(context.getExternalFilesDir(null), "console")
                    dir.mkdirs()
                    root = dir
                }
            }
        }
    }

    fun root(): File = requireNotNull(root) { "ConsolePaths.init(context) must be called first" }

    private fun dir(name: String): File {
        val d = File(root(), name)
        d.mkdirs()
        return d
    }

    fun outputs(): File = dir("outputs")
    fun logcat(): File = dir("logcat")
    fun crash(): File = dir("crash")
    fun sessions(): File = dir("sessions")
}
