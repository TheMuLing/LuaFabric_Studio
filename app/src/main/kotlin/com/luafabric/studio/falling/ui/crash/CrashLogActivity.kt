package com.luafabric.studio.falling.ui.crash

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.crash.CrashManager.EXTRA_CRASH_CONTEXT
import com.luafabric.studio.falling.ui.crash.CrashManager.EXTRA_EXCEPTION_TYPE
import com.luafabric.studio.falling.ui.crash.CrashManager.EXTRA_STACK_TRACE
import com.luafabric.studio.falling.ui.crash.CrashManager.EXTRA_THREAD_INFO
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.AppThemeWithObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CrashLogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            var isSettingsLoaded by remember { mutableStateOf(false) }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    SettingsManager.loadSavedSettings(context)
                }
                isSettingsLoaded = true
            }

            Crossfade(
                targetState = isSettingsLoaded,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(durationMillis = 300)
            ) { loaded: Boolean ->
                if (loaded) {
                    AppThemeWithObserver {
                        CrashLogScreen(intent = intent)
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrashLogScreen(intent: Intent) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = LocalClipboardManager.current
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // 从 Intent 获取崩溃信息
    val exceptionType = intent.getStringExtra(EXTRA_EXCEPTION_TYPE) ?: "Unknown"
    val threadInfo = intent.getStringExtra(EXTRA_THREAD_INFO) ?: "Unknown"
    val crashContext = intent.getStringExtra(EXTRA_CRASH_CONTEXT) ?: "Unknown"
    val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace"

    // 获取当前时间
    val crashTime = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) }

    // 获取应用版本信息
    val appVersionInfo = remember {
        buildString {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                append("• Name: ").append(packageInfo.versionName).append("\n")
                val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
                append("• Code: ").append(versionCode)
            } catch (t: Throwable) {
                append("Version info unavailable")
            }
        }
    }

    // 获取设备信息
    val deviceInfo = remember {
        "• Model: ${Build.MODEL}\n• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    }

    // 完整错误详情（用于复制）
    val fullErrorDetails = remember(intent) {
        CrashManager.getAllErrorDetailsFromIntent(context, intent)
    }

    // 按返回键关闭菜单（如果展开）
    BackHandler(fabMenuExpanded) {
        fabMenuExpanded = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_info_title)) }
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded }
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close
                                else Icons.Default.BugReport
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = if (fabMenuExpanded) "Close menu" else "Debug menu",
                            modifier = Modifier.animateIcon({ checkedProgress })
                        )
                    }
                }
            ) {
                // 复制菜单项
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        clipboardManager.setText(AnnotatedString(fullErrorDetails))
                        Toast.makeText(context, R.string.crash_info_copied, Toast.LENGTH_SHORT).show()
                    },
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    text = { Text(stringResource(R.string.crash_info_copy_crash_log)) }
                )

                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        activity?.let { CrashManager.closeApplication(it) }
                    },
                    icon = { Icon(Icons.Filled.Close, contentDescription = null) },
                    text = { Text(stringResource(R.string.crash_info_exit_the_app)) }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 基础样式定义
            val titleStyle = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily(Font(R.font.josefin_sans))
            )
            val normalStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily(Font(R.font.josefin_sans))
            )
            // 异常类型专用样式（红色 + 下划线）
            val exceptionTypeStyle = normalStyle.copy(
                color = MaterialTheme.colorScheme.error,
                // textDecoration = TextDecoration.Underline
            )

            // Crash Time
            Text(text = "Crash Time:", style = titleStyle)
            Text(text = crashTime, style = normalStyle)
            Spacer(modifier = Modifier.height(8.dp))

            // App Version
            Text(text = "App Version", style = titleStyle)
            Text(text = appVersionInfo, style = normalStyle)
            Spacer(modifier = Modifier.height(8.dp))

            // Device Info
            Text(text = "Device Info", style = titleStyle)
            Text(text = deviceInfo, style = normalStyle)
            Spacer(modifier = Modifier.height(12.dp))

            // Crash Details
            Text(text = "Crash Details: ", style = titleStyle)
            Spacer(modifier = Modifier.height(8.dp))

            // [Exception Type]
            Text(text = "[Exception Type]", style = titleStyle)
            Text(text = exceptionType, style = exceptionTypeStyle) // 红色下划线
            Spacer(modifier = Modifier.height(8.dp))

            // [Thread Info]
            Text(text = "[Thread Info]", style = titleStyle)
            Text(text = threadInfo, style = normalStyle)
            Spacer(modifier = Modifier.height(8.dp))

            // [Crash Context]
            Text(text = "[Crash Context]", style = titleStyle)
            Text(text = crashContext, style = normalStyle)
            Spacer(modifier = Modifier.height(8.dp))

            // [Stack Trace] - 对类名添加下划线
            Text(text = "[Stack Trace]", style = titleStyle)
            val annotatedStackTrace = remember(stackTrace) {
                processStackTraceWithUnderlinedClassNames(stackTrace, normalStyle)
            }
            Text(text = annotatedStackTrace)
        }
    }
}

/**
 * 处理堆栈跟踪字符串，为其中的类名添加下划线样式
 */
private fun processStackTraceWithUnderlinedClassNames(
    stackTrace: String,
    baseStyle: TextStyle
): AnnotatedString {
    // 正则匹配类名 + .java 或 .kt，考虑包名、内部类等情况
    val regex = "\\b[a-zA-Z_$][a-zA-Z0-9_$]*\\.(java|kt)\\b".toRegex()
    val matches = regex.findAll(stackTrace).toList()

    return buildAnnotatedString {
        // 先应用基础样式（无下划线）
        pushStyle(baseStyle.toSpanStyle())
        append(stackTrace)
        // 对每个匹配的类名应用下划线样式
        val underlineStyle = SpanStyle(textDecoration = TextDecoration.Underline)
        matches.forEach { match ->
            addStyle(underlineStyle, match.range.first, match.range.last + 1)
        }
        pop()
    }
}