package com.luafabric.studio.falling.ui.crash

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.luafabric.console.core.SessionManager
import com.luajava.LuaStateFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("NewApi")
object CrashManager {

    internal const val EXTRA_STACK_TRACE = "EXTRA_STACK_TRACE"
    internal const val EXTRA_EXCEPTION_TYPE = "EXTRA_EXCEPTION_TYPE"
    internal const val EXTRA_THREAD_INFO = "EXTRA_THREAD_INFO"
    internal const val EXTRA_CRASH_CONTEXT = "EXTRA_CRASH_CONTEXT"
    internal const val EXTRA_DIAGNOSTICS = "EXTRA_DIAGNOSTICS"

    private const val TAG = "crashmanager"
    private const val CAOC_HANDLER_PACKAGE_NAME = "com.crashmanager"
    private const val DEFAULT_HANDLER_PACKAGE_NAME = "com.android.internal.os"
    private const val MAX_STACK_TRACE_SIZE = 131071 // 128 KB - 1
    private const val MAX_DIAGNOSTICS_SIZE = 300_000 // 300 KB，防 dump 撑爆 Binder Intent

    private var application: Application? = null
    private var lastActivityCreated = WeakReference<Activity>(null)
    private var isInBackground = false

    @JvmStatic
    fun install(context: Context?) {
        try {
            if (context == null) {
                Log.e(TAG, "Install failed: context is null!")
                return
            }

            val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

            if (oldHandler != null && oldHandler.javaClass.name.startsWith(CAOC_HANDLER_PACKAGE_NAME)) {
                Log.e(TAG, "You have already installed crashmanager, doing nothing!")
                return
            }

            if (oldHandler != null && !oldHandler.javaClass.name.startsWith(DEFAULT_HANDLER_PACKAGE_NAME)) {
                Log.e(
                    TAG,
                    "IMPORTANT WARNING! You already have an UncaughtExceptionHandler, are you sure this is correct? If you use ACRA, Crashlytics or similar libraries, you must initialize them AFTER crashmanager! Installing anyway, but your original handler will not be called."
                )
            }

            application = context.applicationContext as Application

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e(
                    TAG,
                    "App has crashed, executing crashmanager's UncaughtExceptionHandler",
                    throwable
                )

                // 固定使用 CrashLogActivity 作为错误页面
                val errorActivityClass = CrashLogActivity::class.java

                if (isStackTraceLikelyConflictive(throwable)) {
                    Log.e(
                        TAG,
                        "Your application class or your error activity have crashed, the custom activity will not be launched!"
                    )
                } else {
                    // 始终尝试启动错误页面（忽略前后台状态）
                    val intent = Intent(application, errorActivityClass)
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    var stackTraceString = sw.toString()

                    if (stackTraceString.length > MAX_STACK_TRACE_SIZE) {
                        val disclaimer = " [stack trace too large]"
                        stackTraceString =
                            stackTraceString.substring(0, MAX_STACK_TRACE_SIZE - disclaimer.length) + disclaimer
                    }

                    intent.putExtra(EXTRA_STACK_TRACE, stackTraceString)
                    intent.putExtra(EXTRA_EXCEPTION_TYPE, throwable.javaClass.name)
                    intent.putExtra(
                        EXTRA_THREAD_INFO,
                        thread.name + " (ID:" + thread.id + ")"
                    )
                    intent.putExtra(EXTRA_CRASH_CONTEXT, getCrashContext(application!!))
                    // 崩溃现场诊断：全线程 dump + 锁 owner + LuaState 存活清单 + 会话上下文。
                    // 此刻进程未死（如 FinalizerWatchdogDaemon 刚抛超时、native 仍卡着），现场仍可采。
                    val diagnostics = buildCrashDiagnostics()
                    intent.putExtra(EXTRA_DIAGNOSTICS, diagnostics)
                    saveDiagnosticsFile(application!!, diagnostics)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    application!!.startActivity(intent)
                }

                val lastActivity = lastActivityCreated.get()
                if (lastActivity != null) {
                    lastActivity.finish()
                    lastActivityCreated.clear()
                }
                killCurrentProcess()
            }

            application!!.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                var currentlyStartedActivities = 0

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (activity.javaClass != CrashLogActivity::class.java) {
                        lastActivityCreated = WeakReference(activity)
                    }
                }

                override fun onActivityStarted(activity: Activity) {
                    currentlyStartedActivities++
                    isInBackground = currentlyStartedActivities == 0
                }

                override fun onActivityResumed(activity: Activity) {}

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {
                    currentlyStartedActivities--
                    isInBackground = currentlyStartedActivities == 0
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {}
            })

            Log.i(TAG, "crashmanager has been installed.")
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "An unknown error occurred while installing crashmanager, it may not have been properly initialized. Please report this as a bug if needed.",
                t
            )
        }
    }

    private fun isStackTraceLikelyConflictive(throwable: Throwable): Boolean {
        var t: Throwable? = throwable
        while (t != null) {
            val stackTrace = t.stackTrace
            for (element in stackTrace) {
                if ((element.className == "android.app.ActivityThread"
                            && element.methodName == "handleBindApplication")
                    || element.className == CrashLogActivity::class.java.name) {
                    return true
                }
            }
            t = t.cause
        }
        return false
    }

    @JvmStatic
    fun getStackTraceFromIntent(intent: Intent): String {
        val sb = StringBuilder()

        sb.append("\n[Exception Type]\n")
        sb.append(intent.getStringExtra(EXTRA_EXCEPTION_TYPE)).append("\n\n")

        sb.append("[Thread Info]\n")
        sb.append(intent.getStringExtra(EXTRA_THREAD_INFO)).append("\n\n")

        sb.append("[Crash Context]\n")
        sb.append(intent.getStringExtra(EXTRA_CRASH_CONTEXT)).append("\n\n")

        sb.append("[Stack Trace]\n")
        sb.append(intent.getStringExtra(EXTRA_STACK_TRACE))

        return sb.toString()
    }

    private fun getCrashContext(context: Context): String {
        return "Application: " + context.packageName +
                "\nTime: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    /**
     * 崩溃现场诊断采集。设计约束：
     * - 全线程 dump 走 ThreadMXBean.dumpAllThreads，ThreadInfo.toString() 自带 lock owner、
     *   锁对象名与栈，直接呈现谁持锁、谁等锁；
     * - LuaState 存活清单走 LuaStateFactory.snapshotStates()（只读裸指针，绝不触碰 synchronized 方法，
     *   否则在 LuaState monitor 卡死现场（LuaObject.finalize 超时）采集线程会自锁死循环）；
     * - 会话上下文读 SessionManager @Volatile 字段，全程 try/catch（崩溃时 console 体系可能未初始化）。
     * 全部采集成功后整体落盘私有目录，供 adb pull 挖掘。
     */
    private fun buildCrashDiagnostics(): String = try {
        buildString {
            append("\n[Thread Dump]\n")
            // Android 无 ThreadMXBean/锁 owner 公开 API，只能走 Thread.getAllStackTraces()。
            // 定位方式改为交叉推断：卡死线程（BLOCKED/等 monitor）与「停在 synchronized 的 LuaState
            // native 方法内不动的线程」互指 → 谁持 LuaState monitor 一目了然。
            val traces = Thread.getAllStackTraces()
            var bytes = 0
            for ((t, stack) in traces) {
                val state = t.state
                val marker = if (state != Thread.State.RUNNABLE && state != Thread.State.NEW && state != Thread.State.TERMINATED) {
                    " <-- " + state.name
                } else {
                    ""
                }
                val s = "Thread: " + t.name + " (id=" + t.id + ") state=" + state + marker + "\n" +
                        stack.joinToString("\n") { "\tat " + it } + "\n\n"
                bytes += s.length
                if (bytes > MAX_DIAGNOSTICS_SIZE) {
                    append("~~~ thread dump truncated at $MAX_DIAGNOSTICS_SIZE bytes ~~~\n")
                    break
                }
                append(s)
            }
            append("\n[LuaState Registry]\n")
            append(LuaStateFactory.snapshotStates()).append('\n')
            append("[Session Context]\n")
            append(buildSessionContext())
        }
    } catch (t: Throwable) {
        "Diagnostics unavailable: $t"
    }

    private fun buildSessionContext(): String {
        val sb = StringBuilder()
        try {
            val s = SessionManager.current
            sb.append("gen=").append(SessionManager.gen).append('\n')
            sb.append("luaDir=").append(s?.luaDir ?: "n/a").append('\n')
            sb.append("luaPath=").append(s?.luaPath ?: "n/a").append('\n')
            sb.append("startTimeMs=").append(s?.startTimeMs ?: "n/a").append('\n')
            sb.append("debugMode=").append(s?.debugMode ?: "n/a").append('\n')
            sb.append("sessionLuaStatePtr=").append(s?.luaState?.getPointerUnsafe() ?: "n/a").append('\n')
            sb.append("hostActivity=").append(SessionManager.activity?.javaClass?.name ?: "n/a").append('\n')
        } catch (t: Throwable) {
            sb.append("session context unavailable: ").append(t).append('\n')
        }
        return sb.toString()
    }

    /** 完整诊断落盘至私有目录 filesDir/crash_report/，文件名带毫秒时间戳防覆盖。 */
    private fun saveDiagnosticsFile(context: Context, diagnostics: String) {
        try {
            val dir = File(context.filesDir, "crash_report")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "crash_report_" + System.currentTimeMillis() + ".txt")
            f.writeText(diagnostics)
            Log.e(TAG, "Diagnostics saved to " + f.absolutePath)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to save crash diagnostics file", t)
        }
    }

    @JvmStatic
    fun getAllErrorDetailsFromIntent(context: Context, intent: Intent): String {
        val details = StringBuilder()

        details.append("Crash Time:\n")
        details.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        details.append("\n\n")

        details.append("App Version\n")
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            details.append("• Name: ").append(packageInfo.versionName).append("\n")
            val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            details.append("• Code: ").append(versionCode).append("\n")
        } catch (t: Throwable) {
            details.append("Version info unavailable\n")
        }
        details.append("\n")

        details.append("Device Info\n")
        details.append("• Model: ").append(Build.MODEL).append("\n")
        details.append("• Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        details.append("\n")

        details.append("Crash Details\n")
        details.append(getStackTraceFromIntent(intent))
        details.append("\n\n[Diagnostics]\n")
        details.append(intent.getStringExtra(EXTRA_DIAGNOSTICS) ?: "unavailable")

        return details.toString()
    }

    @JvmStatic
    fun closeApplication(activity: Activity) {
        activity.finish()
        killCurrentProcess()
    }

    private fun killCurrentProcess() {
        Process.killProcess(Process.myPid())
        System.exit(10)
    }
}