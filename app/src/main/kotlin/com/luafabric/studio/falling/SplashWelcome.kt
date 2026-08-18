package com.luafabric.studio.falling

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.androlua.LuaApplication
import com.androlua.LuaUtil
import com.luafabric.studio.falling.ui.crash.CrashManager
import com.luafabric.studio.falling.ui.editor.persistence.EditorStateUtil
import com.luafabric.studio.falling.ui.editor.viewmodel.CompletionDataManager
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.AppThemeWithObserver
import muling.views.tool.utils.IconManager
import muling.views.tool.utils.LogCatcher
import com.luajava.LuaFunction
import com.luajava.LuaStateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    private var mVersionName: String = ""
    private var mOldVersionName: String = ""

    private val isExtracting = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrashManager.install(this)
        IconManager.initIconSetting(this)
        LogCatcher.init(this)

        app = application as LuaApplication
        luaMdDir = app.mdDir
        localDir = app.getLocalDir()

        lifecycleScope.launch {
            handleSplashLogic()
        }
    }

    /**
     * Handles splash screen logic including settings loading and resource extraction
     */
    private suspend fun handleSplashLogic() {
        SettingsManager.loadSavedSettings(this@SplashWelcome)

        val savedLanguageTag = SettingsManager.currentSettings.languageTag
        val currentLanguageTag = getCurrentAppLanguageTag()
        if (savedLanguageTag != currentLanguageTag) {
            SettingsManager.setAppLanguage(this@SplashWelcome, savedLanguageTag)
            return
        }

        val shouldUpdate = checkInfo()

        coroutineScope {
            launch(Dispatchers.IO) {
                // 确保 lua 运行库已解包（import.lua 缺失时补解包，不依赖版本变更）
                ensureLuaResources()
                // 根据版本是否变更，决定是强制重新加载还是普通初始化
                if (isVersionChanged) {
                    CompletionDataManager.reload(this@SplashWelcome)
                } else {
                    CompletionDataManager.initialize(this@SplashWelcome)
                }
                EditorStateUtil.cleanupExpiredStateFiles(this@SplashWelcome)
                EditorStateUtil.cleanupAllNonExistentFiles(this@SplashWelcome)
            }
        }

        setupSplashUI(shouldUpdate)
    }

    /**
     * Returns the current application language tag
     */
    private fun getCurrentAppLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) {
            Locale.getDefault().toLanguageTag()
        } else {
            locales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
        }
    }

    /**
     * Starts the main activity and finishes splash
     */
    private fun startMainActivity() {
        Intent(this@SplashWelcome, MainActivity::class.java).apply {
            if (isVersionChanged) {
                putExtra("isVersionChanged", isVersionChanged)
                putExtra("newVersionName", mVersionName)
                putExtra("oldVersionName", mOldVersionName)
            }
            startActivity(this)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    /**
     * 确保 lua 运行库已解包到私有目录
     * 幂等：import.lua 缺失时补解包，避免绕过 Splash 或数据被清后 import 失效
     */
    private fun ensureLuaResources() {
        try {
            if (!File(luaMdDir, "import.lua").exists()) {
                unApk("lua", luaMdDir)
            }
        } catch (_: IOException) {
            // 解包失败不阻塞启动
        }
    }

    /**
     * Checks if application resources need to be updated
     */
    private fun checkInfo(): Boolean {
        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val lastTime = packageInfo.lastUpdateTime
            val versionName = packageInfo.versionName ?: ""
            val info: SharedPreferences = getSharedPreferences("appInfo", 0)
            val oldVersionName = info.getString("versionName", "") ?: ""

            if (versionName != oldVersionName) {
                info.edit().apply {
                    putString("versionName", versionName)
                    apply()
                }
                isVersionChanged = true
                mVersionName = versionName
                mOldVersionName = oldVersionName
            } else {
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
        } catch (_: PackageManager.NameNotFoundException) {
            // Ignored
        }

        val mainFile = File(app.getLuaPath("main.lua"))
        return !(mainFile.exists() && mainFile.isFile)
    }

    private fun setupSplashUI(shouldUpdate: Boolean) {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppThemeWithObserver {
                SplashScreen(
                    shouldUpdate = shouldUpdate,
                    onCheckComplete = { shouldUpdateInner ->
                        if (shouldUpdateInner) {
                            checkAndStartUpdate()
                        } else {
                            startMainActivity()
                        }
                    }
                )
            }
        }
    }

    private fun checkAndStartUpdate() {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executor.execute {
            onUpdate()
            handler.post { startMainActivity() }
        }
        executor.shutdown()
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
        } catch (_: Exception) {
            // Update script execution failed, continue with extraction
        }

        try {
            unApk("assets", localDir)
            unApk("lua", luaMdDir)
        } catch (_: IOException) {
            // Extraction failed
        }
    }

    /**
     * Extracts resources from APK to external directory
     * Thread-safe implementation using local variables
     */
    @Throws(IOException::class)
    fun unApk(dir: String, extDir: String) {
        if (!isExtracting.compareAndSet(false, true)) {
            return
        }

        val dirtest = ArrayList<String>()
        val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val i = dir.length + 1
        val destPath = extDir
        
        var zipFile: ZipFile? = null
        
        try {
            zipFile = ZipFile(applicationInfo.publicSourceDir)
            val entries = zipFile.entries()
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
                threadPool.execute(FileWritingTask(zipFile, zipEntry, path, destPath))
            }

            threadPool.shutdown()
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            try {
                zipFile?.close()
            } catch (_: Exception) {
                // Ignore close errors
            }
            isExtracting.set(false)
        }
    }

    /**
     * Task for writing a single zip entry to disk
     */
    private inner class FileWritingTask(
        private val zipFile: ZipFile,
        private val zipEntry: ZipEntry,
        private val path: String,
        private val destPath: String
    ) : Runnable {
        override fun run() {
            val file = File("$destPath${File.separator}$path")
            try {
                if (file.exists()) {
                    if (file.isDirectory) {
                        LuaUtil.rmDir(file)
                    } else {
                        file.delete()
                    }
                }

                val parentFile = file.parentFile
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs()
                }

                if (parentFile == null || !parentFile.isDirectory) {
                    return
                }

                if (file.exists()) {
                    return
                }

                zipFile.getInputStream(zipEntry)?.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (_: IOException) {
                // File extraction failed, skip this file
            } catch (_: Exception) {
                // Unexpected error, skip this file
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ZipFile lifecycle is managed within unApk method
    }
}

@Composable
fun SplashScreen(
    shouldUpdate: Boolean,
    onCheckComplete: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )

    LaunchedEffect(Unit) {
        onCheckComplete(shouldUpdate)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(130.dp)
                        .scale(1.25f),
                    contentScale = ContentScale.Fit
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .clipToBounds()
            ) {
                LinearProgressIndicator(
                    progress = 1f,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
