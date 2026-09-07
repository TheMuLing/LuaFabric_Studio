package com.luafabric.console.debug

import android.content.Intent
import android.net.Uri
import com.luafabric.console.core.SessionManager
import muling.views.tool.utils.JsonUtil
import java.io.File

/**
 * F7 文件调起/重启/重建：
 * - 文件树调起：带参数（JSON extra debugParams）以目标文件为 data URI 启动，旧会话自动归档
 * - 重启项目：同一 intent 重发（新会话 + 旧会话归档）
 * - 重建当前文件：recreate 当前 Activity（重跑 onCreate，同样触发新会话）
 * 不引用任何 com.androlua 类型（组件取自当前 intent 拷贝）。
 */
object FileLauncher {

    /** 文件树调起：参数注入 → 启动该文件（新会话）。 */
    fun launchFile(file: File, params: Map<String, Any?>?) {
        val activity = SessionManager.activity ?: return
        val json = params?.takeIf { it.isNotEmpty() }?.let { runCatching { JsonUtil.toFormattedString(it) }.getOrNull() }
        SessionManager.prepareNewSession()
        val intent = Intent(activity.intent).apply {
            data = Uri.fromFile(file)
            putExtra("luapath", file.absolutePath)
            if (json != null) putExtra("debugParams", json) else removeExtra("debugParams")
        }
        activity.finish()
        activity.startActivity(intent)
    }

    /** 重启项目：同文件新会话。 */
    fun restartProject() {
        val activity = SessionManager.activity ?: return
        SessionManager.prepareNewSession()
        val intent = Intent(activity.intent)
        activity.finish()
        activity.startActivity(intent)
    }

    /** 重建当前文件：recreate 重跑 onCreate（新代次，旧会话归档）。 */
    fun rebuildCurrentFile() {
        SessionManager.prepareNewSession()
        SessionManager.activity?.recreate()
    }
}
