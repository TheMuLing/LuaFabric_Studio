package com.androlua

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.luajava.LuaFunction
import com.luajava.LuaStateFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class SplashWelcome : ComponentActivity() {

    private var isUpdata: Boolean = false
    private lateinit var app: LuaApplication
    private lateinit var luaMdDir: String
    private lateinit var localDir: String
    private var mLastTime: Long = 0
    private var mOldLastTime: Long = 0
    private var isVersionChanged: Boolean = false
    private lateinit var mVersionName: String
    private lateinit var mOldVersionName: String

    private var zipFile: ZipFile? = null
    private lateinit var destPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.welcome)

        window.apply {
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                true
            statusBarColor = Color.TRANSPARENT
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        app = application as LuaApplication
        luaMdDir = app.luaMdDir
        localDir = app.localDir

        if (checkInfo()) {
            checkAndStartUpdate()
        } else {
            startActivity()
        }
    }

    private fun checkAndStartUpdate() {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executor.execute {
            onUpdate()
            handler.post { startActivity() }
        }
        executor.shutdown()
    }

    fun startActivity() {
        startMainActivity()
    }

    private fun startMainActivity() {
        Intent(this@SplashWelcome, Main::class.java).apply {
            if (isVersionChanged) {
                putExtra("isVersionChanged", isVersionChanged)
                putExtra("newVersionName", mVersionName)
                putExtra("oldVersionName", mOldVersionName)
            }
            startActivity(this)
            finish()
        }
    }

    private fun checkInfo(): Boolean {
        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val lastTime = packageInfo.lastUpdateTime
            val versionName = packageInfo.versionName ?: ""  // 如果为 null，则使用空字符串
            val info: SharedPreferences = getSharedPreferences("appInfo", 0)
            val oldVersionName = info.getString("versionName", "") ?: ""  // 使用空安全操作符

            if (versionName != oldVersionName) {
                info.edit().apply {
                    putString("versionName", versionName)
                    apply()
                }
                isVersionChanged = true
                mVersionName = versionName
                mOldVersionName = oldVersionName
            } else {
                // 即使版本未变化，也确保变量有值
                mVersionName = versionName
                mOldVersionName = oldVersionName
            }

            val oldLastTime = info.getLong("lastUpdateTime", 0)
            if (oldLastTime != lastTime) {
                info.edit().apply {
                    putLong("lastUpdateTime", lastTime)
                    apply()
                }
                isUpdata = true
                mLastTime = lastTime
                mOldLastTime = oldLastTime
                return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        val mainFile = File(app.getLuaPath(entryNameIn(localDir)))
        return !(mainFile.exists() && mainFile.isFile)
    }

    // 读取打包期写入的 .entry 引导确定入口（默认 main.lua，支持子目录相对路径）
    private fun entryNameIn(dir: String): String {
        return try {
            val f = File(dir, ".entry")
            if (f.exists() && f.isFile) {
                f.readText().trim().ifEmpty { "main.lua" }.let {
                    if (it.startsWith("/") || it.contains("../")) "main.lua" else it
                }
            } else "main.lua"
        } catch (e: Exception) {
            "main.lua"
        }
    }

    private fun onUpdate() {
        val L = LuaStateFactory.newLuaState()
        L.openLibs()
        try {
            if (L.LloadBuffer(LuaUtil.readAsset(this, "update.lua"), "update") == 0) {
                if (L.pcall(0, 0, 0) == 0) {
                    (L.getFunction("onUpdate") as? LuaFunction)?.call(mVersionName, mOldVersionName)
                }
            }
        } catch (e: Exception) {
            sendMsg()
        }

        try {
            unApk("assets", localDir)
            unApk("lua", luaMdDir)
        } catch (e: IOException) {
            sendMsg()
        }
    }

    private fun sendMsg() {
        // TODO: Implement this method
    }

    @Throws(IOException::class)
    fun unApk(dir: String, extDir: String) {
        val dirtest = ArrayList<String>()
        val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val i = dir.length + 1
        destPath = extDir
        zipFile = ZipFile(applicationInfo.publicSourceDir)

        val entries = zipFile!!.entries()
        val inzipfile = ArrayList<ZipEntry>()

        while (entries.hasMoreElements()) {
            val zipEntry = entries.nextElement()
            val name = zipEntry.name
            if (!name.startsWith(dir)) continue
            val path = name.substring(i)
            val fp = "$extDir${File.separator}$path"

            if (!zipEntry.isDirectory) {
                inzipfile.add(zipEntry)
                dirtest.add("$fp${File.separator}")
                continue
            }
            File(fp).takeIf { !it.exists() }?.mkdirs()
        }

        val iter = inzipfile.iterator()
        while (iter.hasNext()) {
            val zipEntry = iter.next()
            val path = zipEntry.name.substring(i)
            val fp = "$extDir${File.separator}$path"
            if (dirtest.any { fp.startsWith(it) }) continue
            threadPool.execute(FileWritingTask(zipEntry, path))
        }

        threadPool.shutdown()
        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (e: InterruptedException) {
            Log.e("SplashWelcome", "ExecutorService interrupted: ${e.message}")
        }
        zipFile?.close()
    }

    private inner class FileWritingTask(
        private val zipEntry: ZipEntry,
        private val path: String
    ) : Runnable {
        override fun run() {
            val file = File("$destPath${File.separator}$path")
            try {
                // 强制删除已存在的文件或目录
                if (file.exists()) {
                    if (file.isDirectory) {
                        LuaUtil.rmDir(file)
                    } else {
                        file.delete()
                    }
                }

                val parentFile = file.parentFile
                // 确保父目录存在
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs()
                }

                // 再次检查父目录状态
                if (parentFile == null || !parentFile.isDirectory) {
                    Log.e("SplashWelcome", "Invalid parent directory: ${parentFile?.absolutePath}")
                    return
                }

                // 确保文件不存在后再写入
                if (file.exists()) {
                    Log.w("SplashWelcome", "File still exists after deletion: ${file.absolutePath}")
                    return
                }

                zipFile?.getInputStream(zipEntry)?.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        Log.d("SplashWelcome", "Successfully extracted: ${file.name}")
                    }
                }
            } catch (e: IOException) {
                Log.e("SplashWelcome", "Failed to extract file: ${file.absolutePath}", e)
            } catch (e: Exception) {
                Log.e("SplashWelcome", "Unexpected error extracting file", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        zipFile?.close()
        zipFile = null
    }
}