package com.luafabric.compose.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.luafabric.compose.LuaActivity
import com.luafabric.compose.utils.ComposeConfig
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaFunction
import com.luajava.LuaState
import java.io.File

/**
 * Compose 运行时宿主（compose 模块内建，服务 compose 模板项目；包名 com.luafabric.compose.ui，
 * 与 view 模块的 muling.views.tool.ui.ComposeUiHost 同构但异 FQN，避免双重身份宿主同 dex 冲突）。
 *
 * 约定（与 compose_tpl/main.lua 定稿对齐）：
 *   ui.project()                      读 build.gradle.b85 解码 → cfg 表；无效回退 {name=目录名}
 *   ui.state{tbl}                     响应式代理：__newindex 写即触发重组（整块主脚本重跑）
 *   ui.Scaffold/TopAppBar/Column/Text/Button  数据树节点（._kind 标记，无逐参反射）
 *   ui.render(tree)                   首次挂 ComposeView 为内容视图，此后写状态即重组
 *   ui.later(delay, fn)               主线程定时回调（传统 timer/thread 的 compose 替代方案）
 *   回调（onClick / 状态写）统一经主线程 Handler 派发，不跨线程动 LuaState
 */
class ComposeUiHost(
    private val activity: LuaActivity,
    private val L: LuaState,
    private val luaPath: String,
    private val luaDir: String,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRecompose = false
    private var contentSet = false
    private val root = mutableStateOf<Map<*, *>?>(null)

    private val composeView: ComposeView by lazy {
        ComposeView(activity).apply { setContent { AppRoot() } }
    }

    // ---------- 供 LuaActivity 调用 ----------

    /** 注册全局 Java 函数 + 宿主 Lua 模块（package.loaded["ui"] / 全局 ui） */
    fun register() {
        L.pushJavaFunction(projectFn())
        L.setGlobal("_lf_ui_project")
        L.pushJavaFunction(renderFn())
        L.setGlobal("_lf_ui_render")
        L.pushJavaFunction(stateChangedFn())
        L.setGlobal("_lf_ui_state_changed")
        L.pushJavaFunction(laterFn())
        L.setGlobal("_lf_ui_later")

        val bytecode = KIT_SOURCE.toByteArray(Charsets.UTF_8)
        if (L.LloadBuffer(bytecode, KIT_NAME) != 0) {
            throw LuaException("ui host load: " + L.toString(-1))
        }
        if (L.pcall(0, 1, 0) != 0) {
            throw LuaException("ui host init: " + L.toString(-1))
        }
        L.pop(1)
    }

    /** 主脚本每次执行前调用：重置 state 序号，保证同调用点重跑时复用同一代理 */
    fun beginRun() {
        try {
            val begin = L.getLuaObject("ui").getField("_beginRun")
            begin.call()
        } catch (e: Exception) {
            // 宿主未就绪（注册失败降级）时静默
        }
    }

    /** 项目名（b85 解码 name；无效 b85 用目录名；无 b85 返回 null） */
    fun getProjectName(): String? {
        val cfg = loadProjectConfig() ?: return null
        return (cfg["name"] as? String)?.takeIf { it.isNotBlank() } ?: File(luaDir).name
    }

    // ---------- Java 函数 ----------

    private fun projectFn() = object : JavaFunction(L) {
        override fun execute(): Int {
            val cfg = loadProjectConfig() ?: defaultProjectConfig()
            pushLuaTable(cfg)
            return 1
        }
    }

    private fun renderFn() = object : JavaFunction(L) {
        override fun execute(): Int {
            // JavaFunction 约定：index 1 为函数自身（userdata），实参自 index 2 起（与 LuaPrint 一致）
            if (L.getTop() < 2) return 0
            try {
                val tree = L.getLuaObject(2).getTable()
                if (tree["_kind"] != null) render(tree)
            } catch (e: Exception) {
                Log.e(TAG, "ui.render failed", e)
            }
            return 0
        }
    }

    private fun stateChangedFn() = object : JavaFunction(L) {
        override fun execute(): Int {
            notifyStateChanged()
            return 0
        }
    }

    /** ui.later(delayMs, fn)：主线程 postDelayed 回调 Lua 函数（传统 timer/thread 的 compose 替代） */
    private fun laterFn() = object : JavaFunction(L) {
        override fun execute(): Int {
            // index 1 为函数自身，实参自 index 2 起
            if (L.getTop() < 3) return 0
            val delay = L.toInteger(2).toLong()
            val fn = L.getLuaObject(3)
            if (!fn.isFunction()) return 0
            mainHandler.postDelayed({
                try {
                    fn.call()
                } catch (e: Exception) {
                    Log.e(TAG, "ui.later", e)
                }
            }, delay)
            return 0
        }
    }

    // ---------- 状态写 → 重组 ----------

    fun notifyStateChanged() {
        if (pendingRecompose) return
        pendingRecompose = true
        mainHandler.post {
            pendingRecompose = false
            recompose()
        }
    }

    /** 重跑主脚本：state 序号复位 → 树重建 → ui.render 更新根 → Compose 重组 */
    private fun recompose() {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            beginRun()
            activity.doFile(luaPath, emptyArray<Any>())
        } catch (e: Exception) {
            Log.e(TAG, "recompose failed", e)
        }
    }

    // ---------- Compose 视图 ----------

    private fun render(node: Map<*, *>) {
        if (!contentSet) {
            contentSet = true
            activity.markComposeViewSet()
            activity.setContentView(composeView)
        }
        @Suppress("UNCHECKED_CAST")
        root.value = node as Map<String, Any?>
    }

    @Composable
    private fun AppRoot() {
        MaterialTheme {
            root.value?.let { Node(it) }
        }
    }

    @Composable
    private fun Node(node: Map<*, *>) {
        when (node["_kind"]) {
            "Scaffold" -> ScaffoldNode(node)
            "TopAppBar" -> TopAppBarNode(node)
            "Column" -> ColumnNode(node)
            "Text" -> TextNode(node)
            "Button" -> ButtonNode(node)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ScaffoldNode(node: Map<*, *>) {
        Scaffold(
            topBar = {
                (node["topBar"] as? Map<*, *>)?.let { Node(it) }
            },
            content = { padding ->
                Box(Modifier.padding(padding)) {
                    (node["content"] as? Map<*, *>)?.let { Node(it) }
                }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TopAppBarNode(node: Map<*, *>) {
        CenterAlignedTopAppBar(
            title = { (node["title"] as? Map<*, *>)?.let { Node(it) } }
        )
    }

    @Composable
    private fun ColumnNode(node: Map<*, *>) {
        val padding = (node["padding"] as? Number)?.toFloat() ?: 0f
        val spacing = (node["spacing"] as? Number)?.toFloat() ?: 0f
        Column(
            modifier = Modifier.fillMaxSize().padding(padding.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp)
        ) {
            childrenOf(node).forEach { Node(it) }
        }
    }

    @Composable
    private fun TextNode(node: Map<*, *>) {
        val style = when (node["style"] as? String) {
            "headlineMedium" -> MaterialTheme.typography.headlineMedium
            "headlineSmall" -> MaterialTheme.typography.headlineSmall
            "titleLarge" -> MaterialTheme.typography.titleLarge
            "titleMedium" -> MaterialTheme.typography.titleMedium
            "titleSmall" -> MaterialTheme.typography.titleSmall
            "bodyLarge" -> MaterialTheme.typography.bodyLarge
            "bodyMedium" -> MaterialTheme.typography.bodyMedium
            "labelLarge" -> MaterialTheme.typography.labelLarge
            else -> MaterialTheme.typography.bodyLarge
        }
        Text(text = node["text"] as? String ?: "", style = style)
    }

    @Composable
    private fun ButtonNode(node: Map<*, *>) {
        val click = node["onClick"] as? LuaFunction<*>
        Button(onClick = {
            if (click != null) {
                val fn = click
                mainHandler.post {
                    try {
                        fn.call()
                    } catch (e: Exception) {
                        Log.e(TAG, "ui.Button onClick", e)
                    }
                }
            }
        }) {
            childrenOf(node).forEach { Node(it) }
        }
    }

    /** children 单节点（Button）或子节点数组（Column）统一归一 */
    private fun childrenOf(node: Map<*, *>): List<Map<*, *>> {
        val raw = node["children"] as? Map<*, *> ?: return emptyList()
        if (raw["_kind"] != null) return listOf(raw)
        val out = mutableListOf<Map<*, *>>()
        val keys = raw.keys.filterIsInstance<Number>().sortedBy { it.toLong() }
        for (k in keys) {
            val v = raw[k]
            if (v is Map<*, *>) out.add(v)
        }
        return out
    }

    // ---------- 项目配置 ----------

    private fun loadProjectConfig(): Map<String, Any?>? {
        return try {
            val f = File(luaDir, ComposeConfig.FILE_NAME)
            if (!f.exists()) null else ComposeConfig.load(f.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun defaultProjectConfig(): Map<String, Any?> = mapOf("name" to File(luaDir).name)

    // ---------- Map/List → Lua 表 ----------

    private fun pushLuaValue(v: Any?) {
        when (v) {
            null -> L.pushNil()
            is Boolean -> L.pushBoolean(v)
            is Double, is Float -> L.pushNumber(v.toDouble())
            is Number -> L.pushInteger(v.toLong())
            is String -> L.pushString(v)
            is Map<*, *> -> pushLuaTable(v)
            is List<*> -> pushLuaList(v)
            else -> L.pushNil()
        }
    }

    private fun pushLuaTable(m: Map<*, *>) {
        L.newTable()
        m.forEach { (k, v) ->
            L.pushString(k.toString())
            pushLuaValue(v)
            L.setTable(-3)
        }
    }

    private fun pushLuaList(list: List<*>) {
        L.newTable()
        list.forEachIndexed { i, v ->
            L.pushInteger(i + 1L)
            pushLuaValue(v)
            L.setTable(-3)
        }
    }

    companion object {
        private const val TAG = "ComposeUiHost"
        private const val KIT_NAME = "@compose_ui_host"

        /** 宿主 Lua 模块源码：state 写即重组 + 数据树节点 + ui.later 定时回调 */
        private val KIT_SOURCE = """
            -- LuaFabric Compose 运行时宿主（compose 模块内建，非项目资产）
            local M = {}

            -- ---- 响应式 state：写即重组（由 _lf_ui_state_changed 抛回主线程） ----
            -- 值存 __stateStore，proxy 空表纯走 metatable：__index 读、__newindex 写。
            -- 若值 rawset 进 proxy 本体，Lua 对已存在的 key 赋值不触发 __newindex → state 写失灵。
            local __stateIdx = 0
            local __states = {}
            local __stateStore = {}
            local __stateMeta = {}
            __stateMeta.__index = function(t, k)
              return __stateStore[t][k]
            end
            __stateMeta.__newindex = function(t, k, v)
              __stateStore[t][k] = v
              _lf_ui_state_changed()
            end

            function M._beginRun()
              __stateIdx = 0
            end

            function M.state(init)
              __stateIdx = __stateIdx + 1
              local existing = __states[__stateIdx]
              if existing then
                return existing
              end
              local proxy = setmetatable({}, __stateMeta)
              __stateStore[proxy] = {}
              for k, v in pairs(init or {}) do
                __stateStore[proxy][k] = v
              end
              __states[__stateIdx] = proxy
              return proxy
            end

            -- ---- 节点构造器：数据树（._kind 标记，无逐参反射） ----
            function M.Scaffold(o) o._kind = "Scaffold" return o end
            function M.TopAppBar(o) o._kind = "TopAppBar" return o end
            function M.Column(o) o._kind = "Column" return o end
            function M.Text(o) o._kind = "Text" return o end
            function M.Button(o) o._kind = "Button" return o end

            -- ---- Java 桥 ----
            M.project = _lf_ui_project
            M.render = _lf_ui_render
            M.later = _lf_ui_later

            package.loaded["ui"] = M
            ui = M
            return M
        """.trimIndent()
    }
}