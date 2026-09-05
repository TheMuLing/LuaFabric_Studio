package com.luafabric.console.core

import com.luafabric.studio.falling.core.console.SessionInfo
import java.io.File

/**
 * 当前文件状态：相对路径 + 布局三态识别。
 * 布局：aly 相对项目根路径（含 .aly 名）| 内联布局 | 无布局。
 * 判定：与 lua 同名的 .aly 存在于项目/扩展/资产目录 → aly；否则 setContentView 已观察 → 内联；均无 → 无布局。
 */
object FileStateTracker {

    enum class Layout(val label: String) {
        ALY("aly"), INLINE("内联布局"), NONE("无布局")
    }

    @Volatile
    var rawPath: String = ""
        private set

    @Volatile
    var relativePath: String = ""
        private set

    @Volatile
    var alyRelativePath: String = ""
        private set

    @Volatile
    var layout: Layout = Layout.NONE
        private set

    @Volatile
    var hasContent: Boolean = false
        private set

    private var luaDir: String? = null
    private var luaExtDir: String? = null
    private var filesDir: String? = null

    @Synchronized
    fun updateFromSession(info: SessionInfo) {
        luaDir = info.luaDir
        luaExtDir = info.luaExtDir
        filesDir = info.activity.filesDir?.absolutePath
        rawPath = info.luaPath ?: ""
        hasContent = rawPath.isNotBlank()
        relativePath = computeRelative(rawPath)
        alyRelativePath = ""
        layout = detectAly() ?: Layout.NONE
    }

    /** setContentView 已观察：无 aly 文件时判定为内联布局。 */
    @Synchronized
    fun onSetContentView() {
        if (layout == Layout.ALY) return
        layout = Layout.INLINE
    }

    private fun computeRelative(path: String): String {
        if (path.isBlank()) return path
        val roots = listOfNotNull(filesDir, luaDir).filter { !it.isNullOrBlank() }
        for (r in roots) {
            if (path.startsWith(r)) {
                return "/" + path.removePrefix(r).trimStart('/')
            }
        }
        return path
    }

    private fun detectAly(): Layout? {
        if (rawPath.isBlank()) return null
        val name = rawPath.substringAfterLast('/').substringBeforeLast('.')
        val alyName = "$name.aly"
        val fileCandidates = listOfNotNull(luaDir, luaExtDir).map { File(it, alyName) }
        for (f in fileCandidates) {
            if (f.exists()) {
                alyRelativePath = computeRelative(f.absolutePath)
                return Layout.ALY
            }
        }
        return try {
            SessionManager.current?.activity?.assets?.open(alyName)?.close()
            alyRelativePath = "/$alyName"
            Layout.ALY
        } catch (_: Exception) {
            null
        }
    }
}
