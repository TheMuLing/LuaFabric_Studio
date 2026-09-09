package com.luafabric.console.core

import com.luafabric.console.env.ModuleTracker
import com.luafabric.console.output.OutputManager
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

    /**
     * setContentView 已观察 → 判定布局来源。
     * @param layoutName setContentView 显式字符串参数（LuaActivity.setContentView(String) 直传）；无则为 null。
     * 判定顺序：显式文件名命中 .aly → ALY；按文件已 require 模块找「项目下同名 .aly」→ ALY（覆盖
     * main.lua + layout.aly 异名情况，require 发生在 loadlayout 模块内部，traced 仍捕获）；否则 INLINE。
     */
    @Synchronized
    fun onSetContentView(layoutName: String? = null) {
        if (layout == Layout.ALY) return
        if (!layoutName.isNullOrBlank() && resolveAlyByName(layoutName)) return
        if (resolveAlyFromRequiredModules()) return
        layout = Layout.INLINE
    }

    /** 按模块名解析 .aly（去路径去扩展名）：luaDir / luaExtDir / assets。 */
    private fun resolveAlyByName(name: String): Boolean {
        val alyName = name.substringAfterLast('/').substringBeforeLast('.') + ".aly"
        for (f in listOfNotNull(luaDir, luaExtDir).map { File(it, alyName) }) {
            if (f.exists()) {
                alyRelativePath = computeRelative(f.absolutePath)
                layout = Layout.ALY
                return true
            }
        }
        return try {
            SessionManager.current?.activity?.assets?.open(alyName)?.close()
            alyRelativePath = "/$alyName"
            layout = Layout.ALY
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 从 ModuleTracker 记录的 require 模块中找项目下同名 .aly（layout.aly 异名布局主判据）。 */
    private fun resolveAlyFromRequiredModules(): Boolean {
        val file = OutputManager.currentFile
        if (file.isBlank()) return false
        for (lib in ModuleTracker.luaLibs(file)) {
            if (lib.native) continue
            if (resolveAlyByName(lib.module)) return true
        }
        return false
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
