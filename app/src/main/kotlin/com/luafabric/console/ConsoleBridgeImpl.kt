package com.luafabric.console

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.TextView
import java.lang.ref.WeakReference
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.core.ConsoleState
import com.luafabric.console.core.EventTracker
import com.luafabric.console.core.FileStateTracker
import com.luafabric.console.core.SessionManager
import com.luafabric.console.core.StateMachine
import com.luafabric.console.env.LuaEnvironment
import com.luafabric.console.env.ModuleTracker
import com.luafabric.console.intercept.NewActivityInterceptor
import com.luafabric.console.logcat.LogcatManager
import com.luafabric.console.output.OutputEntry
import com.luafabric.console.output.OutputManager
import com.luafabric.console.output.TypeResolver
import com.luafabric.console.debug.FileLauncher
import com.luafabric.console.persist.ConsolePaths
import com.luafabric.console.persist.CrashCapture
import com.luafabric.console.persist.SessionArchiver
import com.luafabric.console.ui.ConsoleSheet
import com.luafabric.console.ui.OverlayController
import com.androlua.FirewallGate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luafabric.studio.falling.ui.settings.FirewallKind
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luajava.LuaState
import java.io.File
import muling.views.tool.utils.JsonUtil
import com.luafabric.studio.falling.core.console.DebugConsoleBridge
import com.luafabric.studio.falling.core.console.DebugConsoleRegistry
import com.luafabric.studio.falling.core.console.MethodCallResult
import com.luafabric.studio.falling.core.console.SessionInfo

/** 调试控制台桥实现：路由到会话/输出/拦截各管理器。 */
class ConsoleBridgeImpl(private val context: Context) : DebugConsoleBridge {

    private val settings = ConsoleSettings(context)
    private val overlay = OverlayController(context)
    private val newActivityInterceptor = NewActivityInterceptor(context) { primary ->
        // B：newActivity 允许后重放失败（目标文件被删等）→ error 条目入 F1 缓冲，不复播 toast
        reportError(primary)
    }
    /** 未读 Lua 错误计数 → 浮球右上角角标；打开面板 / 清空当前缓冲时清零。 */
    private val errorUnread = java.util.concurrent.atomic.AtomicInteger(0)

    /** 弹窗采集：make 登记实例→文本；show() 配对即时输出；当前 lua 文件切换时 dump 残留（均不标注 show 与否，仅捕获内容）。 */
    private val pendingPopups = java.util.WeakHashMap<Any, PopupInfo>()
    private var lastDumpFile: String? = null

    /** 弹窗登记信息：展示文本 + 类型（快照于 make 时，show/dump 时按开关门控输出）。 */
    private data class PopupInfo(val text: String, val snackbar: Boolean)

    /** 会话门控：仅 debugmode 项目激活捕获（非调试会话零捕获）。 */
    @Volatile
    private var active = false

    /** 弹窗宿主兜底：退出调试后 task() 后台代码触发拦截时 SessionManager.activity 已空，
     *  用最近一次 resume 的 Activity 顶替（弱引用防泄漏）。 */
    private var hostActivity: WeakReference<Activity> = WeakReference(null)

    init {
        CrashCapture.install()
        CrashCapture.onCrash = {
            Handler(Looper.getMainLooper()).post { overlay.setBallRed(true) }
        }
        // E：报错 toast 由设置项门控（默认关），同步到 core 注册表（LuaActivity.sendError 读取）
        DebugConsoleRegistry.setErrorToastEnabled(settings.toastLuaErrors)
        // F3：拦截器总开关须以持久化设置初始化（否则重建桥/清后台后 enabled 回落默认 true，开关显示关仍拦截）
        newActivityInterceptor.enabled = settings.interceptNavigation
        // E：打开控制台面板 → 未读错误角标清零（打开即视为已读）
        StateMachine.addListener(object : StateMachine.Listener {
            override fun onStateChanged(old: ConsoleState, new: ConsoleState) {
                if (new == ConsoleState.PANEL) clearErrorBadge()
            }
        })
        registerForegroundCallbacks()
    }

    /** E：错误条目入 F1 缓冲 + 未读计数 +1 → 浮球角标更新。线程安全（onError 来自 Lua 线程）。 */
    private fun reportError(primary: String) {
        appendEntry("error", primary)
        errorUnread.incrementAndGet()
        Handler(Looper.getMainLooper()).post { overlay.setErrorCount(errorUnread.get()) }
    }

    /** E：清空未读错误角标（面板打开 / 清空当前文件缓冲）。 */
    fun clearErrorBadge() {
        errorUnread.set(0)
        Handler(Looper.getMainLooper()).post { overlay.setErrorCount(0) }
    }

    /** 设置页「拦截界面跳转/结束请求」开关：同步拦截器总开关（默认开，即时生效）。 */
    fun setInterceptEnabled(v: Boolean) {
        newActivityInterceptor.enabled = v
    }

    /** 前后台感知：后台藏球+面板强收，前台按 BALL 态恢复；会话/logcat 不中断。 */
    private fun registerForegroundCallbacks() {
        val app = context.applicationContext as? Application ?: return
        // started/stopped 计数：页内跳转 A.stop 晚于 B.start，计数不归零 → 不误判后台
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                if (++started == 1) onForeground()
            }
            override fun onActivityStopped(activity: Activity) {
                if (--started <= 0) {
                    started = 0
                    onBackground()
                }
            }
            override fun onActivityResumed(activity: Activity) {
                // 弹窗宿主兜底：记录最近 resume 的页（调试会话结束后防火墙弹窗仍能用它作宿主）
                hostActivity = WeakReference(activity)
                // 回旧页等恢复场景：宿主切到该页，消除 openSheet 读到已销毁宿主而吞点击的竞态
                SessionManager.onHostResumed(activity)
                // 页内返回/恢复：浮球缺失则重建（面板开在销毁页上被收走/宿主销毁竞态等场景）
                if (active && StateMachine.state == ConsoleState.BALL && !overlay.isBallShowing()) {
                    overlay.showBall()
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityDestroyed(activity: Activity) {
                // 宿主销毁（newActivity+finish 等）：清 stale 引用，防死 sheet 短路 openSheet / 浮球随 decorView 丢失。
                // 时序：晚于 LuaActivity.onDestroy → onSessionEnd，末页场景已 end() 全清，此处无操作；非末页场景补清理。
                if (hostActivity.get() === activity) hostActivity = WeakReference(null)
                SessionManager.onHostDestroyed(activity)
                overlay.onHostDestroyed(activity)
                // 兜底重建：fallback 浮球宿主销毁后 ball 引用被摘 → BALL 态缺失时立即重建挂新宿主
                if (active && StateMachine.state == ConsoleState.BALL && !overlay.isBallShowing()) {
                    overlay.showBall()
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    private fun onForeground() {
        if (!active) return
        // 状态自愈：PANEL 但无 sheet（宿主销毁/泄漏竞态）→ 回 BALL 重建
        if (StateMachine.state == ConsoleState.PANEL && !overlay.isSheetShowing()) {
            StateMachine.transition(ConsoleState.BALL)
        }
        if (StateMachine.state == ConsoleState.BALL) overlay.showBall()
    }

    private fun onBackground() {
        if (!active) return
        overlay.hideForBackground()
    }

    override fun onSessionStart(info: SessionInfo) {
        // F3：每次会话入/重启/换项目均重同步拦截开关（热重入时 init 不重跑，仅此处能对齐持久化值）
        newActivityInterceptor.enabled = settings.interceptNavigation
        val prev = SessionManager.current
        val fresh = SessionManager.begin(info)
        if (fresh) {
            // 重启/重建/文件调起：旧会话先归档 + 停旧 logcat，再开新会话
            if (prev != null && active) {
                SessionArchiver.archive(prev)
                LogcatManager.stop()
            }
            // 项目级输出隔离：新会话清空缓冲池，杜绝上一项目输出混入本会话
            OutputManager.clearAll()
            active = info.debugMode
            ConsolePaths.init(context)
            if (active) LogcatManager.start(info.luaDir, projectName(info))
            injectDebugParams(info)
        }
        // 防火墙：逐页注入（每页独立 LuaState 均需挂载 io/os/popen/lfs 重写；reconfigure 幂等）
        injectFirewall(info)
        // 每页上下文刷新（页面相关，跨页会话持续）
        FileStateTracker.updateFromSession(info)
        LuaEnvironment.probe(info.luaState)
        OutputManager.currentFile = info.luaPath ?: ""
        // 主线程早期 print（游标建立前落入兜底缓冲）并入当前文件，保证主线程输出可见
        OutputManager.rebaseCatchAll(OutputManager.currentFile)
        if (!active) return
        if (fresh) {
            StateMachine.transition(ConsoleState.BALL)
            overlay.showBall()
            overlay.setErrorCount(errorUnread.get()) // 新会话浮球重建后补回未读角标
        } else if (StateMachine.state == ConsoleState.BALL && !overlay.isBallShowing()) {
            // 宿主销毁后 join：浮球缺失则重建（兜底挂旧页 decorView 一并覆盖）
            overlay.showBall()
        }
    }

    override fun onSessionEnd(info: SessionInfo) {
        // 非当前代次（重启后旧页迟到销毁）→ 忽略；非末页 → 仅摘成员
        val last = SessionManager.detach(info)
        if (!last) return
        if (active) {
            SessionArchiver.archive(info)
            overlay.closeAll()
            StateMachine.transition(ConsoleState.IDLE)
            LogcatManager.stop()
            // 防火墙网关保持 active：退出调试后 task() 等后台代码仍在跑，仍须拦截 + 弹窗提醒
        }
        active = false
        EventTracker.clear()
        ModuleTracker.clear()
        ConsoleSheet.persistedTab = 0
        SessionManager.end()
    }

    private fun projectName(info: SessionInfo): String =
        (info.luaDir?.let { File(it).name }?.ifBlank { null }) ?: "project"

    /** F7：跨 Intent 的 debugParams JSON → Lua 全局 debugParams 真 table（须在 doFile 前调用）。 */
    private fun injectDebugParams(info: SessionInfo) {
        val json = info.debugParamsJson ?: return
        if (json.isBlank()) return
        val map = try {
            JsonUtil.parseObject(json)
        } catch (_: Exception) {
            return
        }
        try {
            info.luaState.newTable()
            pushMap(info.luaState, map)
            info.luaState.setGlobal("debugParams")
        } catch (_: Exception) {
        }
    }

    /**
     * 防火墙：越级写入拦截注入。
     * - firewall.lua（assets）→ 当前页 LuaState（io/os/popen/lfs 重写，脚本内 _FW_INJECTED 防重复包装）
     * - FirewallGate.reconfigure → 网关预热（项目根/两开关/上报回调；幂等，逐页调用）
     * - 上报 → 计数（SettingsManager 按项目名分计）+ 宿主弹窗
     * 仅 IDE 调试会话（debugMode）注入；产物恒不注入、网关恒 inactive。
     */
    private fun injectFirewall(info: SessionInfo) {
        if (!info.debugMode) return
        val script = try {
            context.assets.open("firewall.lua").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
        if (script != null) {
            try {
                info.luaState.LdoString(script)
            } catch (_: Exception) {
            }
        }
        try {
            val settings = SettingsManager.currentSettings
            FirewallGate.reconfigure(
                "/storage/emulated/0/LuaFabric-Studio",
                info.luaDir ?: "",
                settings.crossProjectWriteGuard,
                settings.selfGuard,
                object : FirewallGate.Reporter {
                    override fun onBlock(kind: Int, projectName: String?, target: String?) {
                        Handler(Looper.getMainLooper()).post {
                            SettingsManager.recordFirewallGuard(
                                if (kind == 1) FirewallKind.CROSS_WRITE else FirewallKind.SELF_GUARD,
                                projectName ?: "", context)
                            showFirewallDialog(kind, projectName, target)
                        }
                    }
                }
            )
        } catch (_: Exception) {
        }
    }

    /** 防火墙拦截弹窗：标题「<功能名>拦截」、双行消息、仅「好的」按钮、点击外部不关闭。
     *  宿主：调试会话页优先，已退出调试（后台 task 触发）回退最近 resume 的页。
     *  颜色/圆角全部跟随 luafabric「主题与外观」设置（ConsoleTheme）。 */
    private fun showFirewallDialog(kind: Int, projectName: String?, target: String?) {
        // 调试会话已结束后 SessionManager.activity 变空 —— 用最近 resume 页兜底
        val host = SessionManager.activity ?: hostActivity.get() ?: return
        if (host.isFinishing || host.isDestroyed) return
        try {
            val title = if (kind == 1) "越级写入拦截" else "自我守护拦截"
            val msg = buildString {
                appendLine("项目「${projectName ?: ""}」正在写入：")
                appendLine(target ?: "")
                appendLine()
                append(if (kind == 1) "已拦截对其它项目目录的写入/删除/修改操作。" else "已拦截对 LuaFabric-Studio 根目录的保护操作。")
            }
            val theme = com.luafabric.console.ui.ConsoleTheme
            theme.refresh(context)
            val dlg = MaterialAlertDialogBuilder(host)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("好的", null)
                .setCancelable(false)
                .create()
            dlg.show()
            dlg.setOnShowListener {
                dlg.window?.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(theme.surface)
                    cornerRadius = theme.cornerRadiusPx
                })
                dlg.findViewById<TextView>(android.R.id.title)?.setTextColor(theme.onSurface)
                dlg.findViewById<TextView>(android.R.id.message)?.setTextColor(theme.onSurface)
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(theme.primary)
            }
        } catch (_: Exception) {
        }
    }

    private fun pushMap(state: LuaState, map: Map<String, Any?>) {
        for ((k, v) in map) {
            pushValue(state, v)
            state.setField(-2, k)
        }
    }

    private fun pushValue(state: LuaState, v: Any?) {
        when (v) {
            null -> state.pushNil()
            is Boolean -> state.pushBoolean(v)
            is Int -> state.pushInteger(v.toLong())
            is Long -> state.pushInteger(v)
            is Number -> state.pushNumber(v.toDouble())
            is String -> state.pushString(v)
            is Map<*, *> -> {
                state.newTable()
                for ((k, value) in v) {
                    pushValue(state, value)
                    state.setField(-2, k?.toString() ?: "")
                }
            }
            is List<*> -> {
                state.newTable()
                v.forEachIndexed { i, value ->
                    pushValue(state, value)
                    state.setField(-2, (i + 1).toString())
                }
            }
            else -> state.pushObjectValue(v)
        }
    }

    override fun onPrint(text: String?, luaTypes: IntArray?, rawArgs: Array<out Any?>?) {
        // 门控：捕获 print 关闭则不解析不入缓冲（不 gate active：非调试会话本就 clearAll，见原注释）
        if (!settings.capturePrint) return
        // 不 gate active：主线程顶层 chunk 的 print 可能先于 onSessionStart 到达（游标未建），
        // 一律入兜底缓冲，会话建立后由 rebaseCatchAll 并入当前文件；非调试会话随后 clearAll 清空。
        val depth = settings.parseDepth
        val l1 = ArrayList<String>(luaTypes?.size ?: 0)
        val l2 = ArrayList<String>(luaTypes?.size ?: 0)
        val contents = ArrayList<String>(luaTypes?.size ?: 0)
        val fullContents = ArrayList<String>(luaTypes?.size ?: 0)
        if (luaTypes != null) {
            for (i in luaTypes.indices) {
                val r = TypeResolver.resolve(luaTypes[i], rawArgs?.getOrNull(i), depth)
                l1 += r.level1
                l2 += r.type
                contents += r.content
                fullContents += r.full ?: r.content
            }
        }
        // 内容区 = 真实解码内容（与 Lua print 同款 \t 连接）；元数据右侧 = 仅真实类型
        val primary = if (luaTypes != null) contents.joinToString("\t") else (text ?: "")
        // 完整内容（不截断）：复制选项弹窗预览用；非 print 通道沿用展示文本
        val fullText = if (luaTypes != null) fullContents.joinToString("\t") else (text ?: "")
        appendEntry("print", primary, l1, l2, fullText = fullText)
    }

    override fun onPopupCaptured(instance: Any, text: String?, snackbar: Boolean) {
        if (!active) return
        // 同实例重复 make（罕见）覆盖旧文本；WeakHashMap 弱键随 GC 回收未 show 残留
        pendingPopups[instance] = PopupInfo(text ?: "", snackbar)
    }

    override fun onPopupShown(instance: Any) {
        if (!active) return
        pendingPopups.remove(instance)?.let { info ->
            outputPopup(info)
        }
    }

    /** 当前 lua 文件切换锚点触发：残留「从未 show」的弹窗一次性落缓冲。 */
    private fun dumpPendingPopups() {
        if (pendingPopups.isEmpty()) return
        val it = pendingPopups.entries.iterator()
        while (it.hasNext()) {
            val (_, info) = it.next()
            it.remove()
            outputPopup(info)
        }
    }

    /** 弹窗输出：按类型开关门控（不标注是否调用 show，仅捕获内容）。 */
    private fun outputPopup(info: PopupInfo) {
        val want = if (info.snackbar) settings.captureSnackbar else settings.captureToast
        if (!want) return
        val label = if (info.snackbar) "snackbar" else "toast"
        appendEntry(label, info.text)
    }

    override fun onError(title: String?, message: String?) {
        if (!active) return
        val primary = if (title.isNullOrBlank()) (message ?: "") else "$title: ${message ?: ""}"
        reportError(primary)
    }

    override fun onMethodCall(
        receiver: Any?,
        methodName: String?,
        args: Array<out Any?>?,
        luaState: Long
    ): MethodCallResult {
        if (!active) return MethodCallResult.ALLOW
        // 弹窗残留 dump 锚点：当前 lua 文件变化（会话游标切换）→ 上一文件未 show 的弹窗一次性落缓冲
        val curFile = OutputManager.currentFile
        if (curFile != lastDumpFile) {
            lastDumpFile = curFile
            dumpPendingPopups()
        }
        // F2：观察 setContentView（布局判定）；显式字符串参数直传，否则靠 require 模块推 .aly
        if (methodName == "setContentView" && receiver is android.app.Activity) {
            FileStateTracker.onSetContentView(args?.firstOrNull() as? String)
        }
        // F3：newActivity 阻塞确认 / 同参丢弃 / 异参列表单选（弹窗用调用方 Activity 作 context）
        return newActivityInterceptor.intercept(receiver as? Activity, methodName, args)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!active) return false
        // IDLE（会话意外隐藏）/ CLOSED（显式关闭）状态下按音量下键恢复浮球，其余状态放行系统音量。
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            (StateMachine.state == ConsoleState.IDLE || StateMachine.state == ConsoleState.CLOSED)
        ) {
            StateMachine.transition(ConsoleState.BALL)
            overlay.showBall()
            return true
        }
        return false
    }

    override fun onEvent(funcName: String?, args: Array<out Any?>?) {
        if (!active) return
        EventTracker.record(
            file = FileStateTracker.relativePath.ifBlank { OutputManager.currentFile },
            funcName = funcName ?: "?",
            args = args,
            timeMs = System.currentTimeMillis(),
            isMainThread = Looper.getMainLooper().thread === Thread.currentThread()
        )
    }

    override fun onRequire(moduleName: String?, funcParams: Map<String, Int>?, nativeModule: Boolean) {
        if (!active) return
        ModuleTracker.recordRequire(OutputManager.currentFile, moduleName, funcParams, nativeModule)
    }

    override fun onBindClass(className: String?, clazz: Class<*>?) {
        if (!active) return
        ModuleTracker.recordBindClass(OutputManager.currentFile, className, clazz)
    }

    private fun appendEntry(label: String, primary: String, luaTypes: List<String> = emptyList(), typeDetails: List<String> = emptyList(), fullText: String? = null) {
        OutputManager.append(
            OutputEntry(
                id = OutputManager.nextId(),
                file = OutputManager.currentFile,
                relFile = FileStateTracker.relativePath.ifBlank { OutputManager.currentFile },
                label = label,
                primary = primary,
                luaTypes = luaTypes,
                typeDetails = typeDetails,
                isMainThread = Looper.getMainLooper().thread === Thread.currentThread(),
                timestampMs = System.currentTimeMillis(),
                fullText = fullText
            )
        )
    }
}
